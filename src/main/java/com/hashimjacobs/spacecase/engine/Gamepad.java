package com.hashimjacobs.spacecase.engine;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

    private static final int SUPPORTED_PADS = 2;

    private final Scene scene;
    private final Settings settings;
    private final ControllerManager controllers;
    private final GamepadMapping[] mappings = new GamepadMapping[SUPPORTED_PADS];
    private final Set<PadButton> buttonsDown = EnumSet.noneOf(PadButton.class);

    private AnimationTimer timer;
    private boolean wasEnabled = true;

    /** Returns a pad reader, or one that does nothing if no controller support could be started. */
    public static Gamepad start(Scene scene, Settings settings) {
        try {
            // Constructing the manager is what loads the native, so it belongs inside the guard --
            // an unsupported platform fails here rather than in initSDLGamepad.
            ControllerManager manager = new ControllerManager();
            initQuietly(manager);
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

    /**
     * Jamepad looks for an optional community mapping database on the classpath and prints a stack
     * trace when it is absent, even though it then falls back to the mappings built into SDL, which
     * already cover every mainstream pad. Bundling a megabyte of third-party data to quiet a
     * non-error is the worse trade, so the noise is muted for the length of the call instead. A
     * genuine failure still arrives as the IllegalStateException the caller handles, and SDL's own
     * diagnostics go to stdout, which is untouched.
     */
    private static void initQuietly(ControllerManager manager) {
        PrintStream realErr = System.err;
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            manager.initSDLGamepad();
        } finally {
            System.setErr(realErr);
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

        double deadzone = settings.gamepadDeadzone();
        PadButton fireButton = settings.gamepadFireButton();
        PadButton pauseButton = settings.gamepadPauseButton();
        for (int slot = 0; slot < SUPPORTED_PADS; slot++) {
            ControllerState reading = controllers.getState(slot);
            PadState state = snapshot(reading);
            List<GamepadMapping.KeyChange> changes =
                    mappings[slot].poll(state, deadzone, fireButton, pauseButton);
            apply(changes);
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

    /** Stops polling and shuts SDL down. Safe to call on a reader that never started. */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (controllers != null) {
            controllers.quitSDLGamepad();
        }
    }
}
