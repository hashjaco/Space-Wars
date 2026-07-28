package com.hashimjacobs.spacecase.engine;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;

/**
 * Tracks which keys are currently held.
 *
 * The old engine re-registered its key handlers from inside the game loop, sixty times a second,
 * and moved ships straight out of the key-pressed event -- so movement speed depended on the OS
 * key-repeat rate and diagonals were impossible. Handlers are now installed once and the loop polls
 * this set instead.
 */
public final class InputState {

    private final Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);
    private Runnable onPausePressed = () -> {
    };
    private Scene attachedScene;
    private ChangeListener<Boolean> focusListener;
    private Predicate<KeyCode> menuRouter;

    /** Installs the handlers. The scene must already belong to a window. */
    public void attachTo(Scene scene) {
        detach();
        attachedScene = scene;

        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            // While an overlay menu is up it gets first refusal, so Escape and the arrows drive the
            // menu rather than the ship.
            if (menuRouter != null && menuRouter.test(code)) {
                return;
            }
            if (code == KeyCode.ESCAPE) {
                onPausePressed.run();
                return;
            }
            held.add(code);
        });
        scene.setOnKeyReleased(event -> held.remove(event.getCode()));

        // Losing focus mid-keypress would otherwise leave the key stuck down forever.
        focusListener = (observable, wasFocused, isFocused) -> {
            if (!isFocused) {
                held.clear();
            }
        };
        Window window = scene.getWindow();
        if (window != null) {
            window.focusedProperty().addListener(focusListener);
        }
    }

    /**
     * Removes the handlers. The router reuses one Scene across every screen, so without this the
     * focus listeners would pile up, one per round played.
     */
    public void detach() {
        if (attachedScene != null) {
            attachedScene.setOnKeyPressed(null);
            attachedScene.setOnKeyReleased(null);
            Window window = attachedScene.getWindow();
            if (window != null && focusListener != null) {
                window.focusedProperty().removeListener(focusListener);
            }
        }
        attachedScene = null;
        focusListener = null;
        held.clear();
    }

    public void setOnPausePressed(Runnable handler) {
        this.onPausePressed = handler;
    }

    /**
     * Diverts key presses to an overlay menu. Pass null to hand control back to the ships. The
     * predicate returns true for keys it consumed.
     */
    public void setMenuRouter(Predicate<KeyCode> menuRouter) {
        this.menuRouter = menuRouter;
        held.clear();
    }

    public boolean isHeld(KeyCode code) {
        boolean down = held.contains(code);
        return down;
    }

    public void clear() {
        held.clear();
    }
}
