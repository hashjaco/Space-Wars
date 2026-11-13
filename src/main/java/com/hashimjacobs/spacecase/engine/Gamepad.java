package com.hashimjacobs.spacecase.engine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;

import javafx.animation.AnimationTimer;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;

import com.hashimjacobs.spacecase.prefs.PadButton;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * Bluetooth and USB gamepad support, delivered as synthetic key events.
 *
 * JavaFX has no controller API, so SDL is read through Jamepad and each pad's state is translated
 * into the key presses the game already understands. Firing those at the {@link Scene} rather than
 * poking the engine directly is what keeps this to a single integration point: the router reuses one
 * Scene for the whole app, and every screen -- gameplay, the menu screens, the pause and settings
 * overlays, the fullscreen filter -- already hangs off it.
 *
 * The only class in the project that touches native code. Everything decidable without a controller
 * lives in {@link GamepadMapping}.
 */
public final class Gamepad {

    /**
     * ponytail: a slot is SDL's joystick device index, not a stable pad identity. If a device SDL
     * cannot open as a gamepad holds index 0, a lone pad drives player two; and if player one's pad
     * dies mid-match, player two's can slide down into slot 0. Key slots off getDeviceInstanceID()
     * and hold an assignment map across reconnects if anyone actually hits this.
     */
    private static final int SUPPORTED_PADS = 2;

    /**
     * How far a trigger or the right stick travels before it counts as a pressed button. Halfway:
     * past any resting drift, and well short of the end stop a worn trigger no longer reaches.
     */
    private static final float AXIS_PRESS = 0.5f;

    private final Scene scene;
    private final Settings settings;
    /**
     * Non-final so {@link #stop()} can drop it before SDL goes away.
     *
     * Null already means "no pad support" -- it is how the reader built in {@link #start}'s catch
     * block is constructed -- so releasing it at shutdown reuses a state the class already knows
     * rather than adding a shutting-down flag.
     */
    private ControllerManager controllers;
    private final GamepadMapping[] mappings = new GamepadMapping[SUPPORTED_PADS];
    private final Set<PadButton> buttonsDown = EnumSet.noneOf(PadButton.class);

    private final PadState[] latest = new PadState[SUPPORTED_PADS];
    private final String[] names = new String[SUPPORTED_PADS];
    /** Per slot: who is waiting for that pad's next button, or null when it drives the game. */
    private final List<Consumer<PadButton>> captures = new java.util.ArrayList<>(
            java.util.Collections.nCopies(SUPPORTED_PADS, null));

    private AnimationTimer timer;
    private boolean wasEnabled = true;
    private int recognizedPads = -1;

    /** Returns a pad reader, or one that does nothing if no controller support could be started. */
    public static Gamepad start(Scene scene, Settings settings) {
        try {
            // Constructing the manager is what loads the native, so it belongs inside the guard --
            // an unsupported platform fails here rather than in initSDLGamepad.
            ControllerManager manager = new ControllerManager();
            // Jamepad loads /gamecontrollerdb.txt from the classpath here. If that file ever goes
            // missing, its complaint on stderr is the only warning that SDL has dropped to its
            // built-in mappings and stopped recognising most pads -- so it is not muted.
            manager.initSDLGamepad();
            Gamepad gamepad = new Gamepad(scene, settings, manager);
            gamepad.startPolling();
            return gamepad;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            // Jamepad reports a missing native, an unsupported platform and a failed SDL_Init as
            // three different unchecked throwables, none of them a type of its own. Whichever
            // arrives, the game has to stay playable on the keyboard rather than refuse to launch.
            System.err.println("No gamepad support on this machine: " + e);
            Gamepad disabled = new Gamepad(scene, settings, null);
            return disabled;
        }
    }

    private Gamepad(Scene scene, Settings settings, ControllerManager controllers) {
        this.scene = scene;
        this.settings = settings;
        this.controllers = controllers;
        for (int slot = 0; slot < SUPPORTED_PADS; slot++) {
            mappings[slot] = new GamepadMapping(slot);
        }
    }

    /**
     * Polls on the JavaFX application thread, which on macOS is the process main thread SDL expects.
     *
     * ponytail: sharing the render thread costs a poll of two pads per frame, which is nothing today.
     * If SDL ever stalls a frame, move polling to a daemon thread that publishes a PadState snapshot
     * and dispatch the key events back through Platform.runLater.
     */
    private void startPolling() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                pollOnce();
            }
        };
        timer.start();
    }

    private void pollOnce() {
        // AnimationTimer.stop() does not cancel a pulse already in flight, so this can run once
        // more after stop() has shut SDL down. Jamepad answers that with an IllegalStateException
        // on the JavaFX thread, which killed the frame loop on the way out.
        if (controllers == null) {
            return;
        }
        boolean enabled = settings.gamepadEnabled();
        if (!enabled) {
            // Switching the pad off mid-thrust must not leave the ship holding its last direction.
            if (wasEnabled) {
                releaseEverything();
            }
            wasEnabled = false;
            return;
        }
        wasEnabled = true;
        reportPads();

        for (int slot = 0; slot < SUPPORTED_PADS; slot++) {
            ControllerState reading = controllers.getState(slot);
            PadState state = snapshot(reading);
            latest[slot] = state;
            names[slot] = reading.isConnected ? reading.controllerType : null;
            drive(slot, state);
        }
    }

    /**
     * One pad's state from a platform that pushes it rather than being polled: Android, where SDL
     * does not run and the buttons arrive as key events (see SceneRouter.installAndroidController).
     * From here on that pad is the same as a desktop one -- same bindings, same controller screen.
     *
     * ponytail: ignores the Controller on/off setting, since on a television the pad is the only
     * way back to the switch.
     */
    public void offer(int slot, PadState state) {
        recognizedPads = Math.max(recognizedPads, slot + 1);
        latest[slot] = state;
        names[slot] = "Controller";
        drive(slot, state);
    }

    private void drive(int slot, PadState state) {
        int player = slot + 1;
        Consumer<PadButton> capture = captures.get(slot);
        if (capture != null) {
            PadButton fresh = mappings[slot].capture(state);
            if (fresh != null) {
                capture.accept(fresh);
            }
            return;
        }
        // The bindings are read here, per poll: each pad carries its own, and they can change
        // between one frame and the next.
        apply(mappings[slot].poll(state, settings.gamepadDeadzone(),
                action -> settings.padButton(player, action),
                action -> settings.key(player, action)));
    }

    /**
     * Hands one pad's next button press to a listener instead of the game, or back with null.
     *
     * For the controller screen while a row waits to be bound. That pad sends no keys until it is
     * handed back; the keyboard and the other pad carry on, so Escape still backs out.
     */
    public void setCapture(int slot, Consumer<PadButton> onButton) {
        captures.set(slot, onButton);
        if (onButton != null) {
            apply(mappings[slot].releaseKeys());
        }
    }

    /**
     * Jamepad answers an unplugged or absent slot with a disconnected state rather than throwing, so
     * hot-plugging needs no special case beyond letting go of that pad's keys.
     */
    private PadState snapshot(ControllerState reading) {
        if (!reading.isConnected) {
            PadState absent = PadState.disconnected();
            return absent;
        }
        buttonsDown.clear();
        addIf(reading.a, PadButton.A);
        addIf(reading.b, PadButton.B);
        addIf(reading.x, PadButton.X);
        addIf(reading.y, PadButton.Y);
        addIf(reading.lb, PadButton.LEFT_BUMPER);
        addIf(reading.rb, PadButton.RIGHT_BUMPER);
        addIf(reading.start, PadButton.START);
        addIf(reading.back, PadButton.BACK);
        addIf(reading.leftTrigger > AXIS_PRESS, PadButton.LEFT_TRIGGER);
        addIf(reading.rightTrigger > AXIS_PRESS, PadButton.RIGHT_TRIGGER);
        addIf(reading.leftStickClick, PadButton.LEFT_STICK);
        addIf(reading.rightStickClick, PadButton.RIGHT_STICK);
        // Same sign convention as the left stick, which GamepadMapping reads as negative-is-up.
        addIf(reading.rightStickY < -AXIS_PRESS, PadButton.C_UP);
        addIf(reading.rightStickY > AXIS_PRESS, PadButton.C_DOWN);
        addIf(reading.rightStickX < -AXIS_PRESS, PadButton.C_LEFT);
        addIf(reading.rightStickX > AXIS_PRESS, PadButton.C_RIGHT);

        PadState state = new PadState(true, reading.leftStickX, reading.leftStickY,
                reading.dpadUp, reading.dpadDown, reading.dpadLeft, reading.dpadRight, buttonsDown);
        return state;
    }

    private void addIf(boolean down, PadButton button) {
        if (down) {
            buttonsDown.add(button);
        }
    }

    private void apply(List<GamepadMapping.KeyChange> changes) {
        for (GamepadMapping.KeyChange change : changes) {
            KeyEvent event = new KeyEvent(
                    change.pressed() ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                    // KeyEvent rejects a real character on a press or release; nothing downstream
                    // reads the text, only getCode().
                    KeyEvent.CHAR_UNDEFINED,
                    change.code().getName(),
                    change.code(),
                    false, false, false, false);
            Event.fireEvent(scene, event);
        }
    }

    private void releaseEverything() {
        for (GamepadMapping mapping : mappings) {
            List<GamepadMapping.KeyChange> changes = mapping.releaseEverything();
            apply(changes);
        }
    }

    /**
     * Says how many pads SDL recognized, whenever that number changes.
     *
     * SDL only opens a joystick it has a mapping for, so a pad missing from gamecontrollerdb.txt
     * reads as zero here, exactly like no pad at all. Telling those two apart would need a raw
     * joystick count, which Jamepad does not bind at all -- so this reports the half that is
     * knowable, which still beats a connected pad doing nothing with no explanation anywhere.
     *
     * On change rather than once at startup: a Bluetooth pad switched on after launch is not
     * enumerated when SDL starts, so a one-shot line would report a false "none" for exactly the
     * player who needs telling.
     */
    private void reportPads() {
        int count = controllers.getNumControllers();
        if (count == recognizedPads) {
            return;
        }
        recognizedPads = count;
        System.err.println(count == 0
                ? "No gamepad recognized. If one is connected, SDL has no mapping for it -- see"
                        + " the controller notes in README.md."
                : count + " gamepad(s) recognized.");
    }

    /** How many pads SDL has a mapping for, or zero on a build with no controller support. */
    public int recognizedPads() {
        return Math.max(recognizedPads, 0);
    }

    /** This slot's pad name, or null when nothing is recognized there. */
    public String padName(int slot) {
        return names[slot];
    }

    /** This slot's last reading, never null once polling has run. */
    public PadState latest(int slot) {
        PadState state = latest[slot];
        return state == null ? PadState.disconnected() : state;
    }

    /** This player's last reading, for the analog stick. Slot 0 is player one. */
    public PadState latestForPlayer(int player) {
        return player <= SUPPORTED_PADS ? latest(player - 1) : PadState.disconnected();
    }

    /** Stops polling and shuts SDL down. Safe to call on a reader that never started. */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        ControllerManager closing = controllers;
        controllers = null;
        if (closing != null) {
            closing.quitSDLGamepad();
        }
    }
}
