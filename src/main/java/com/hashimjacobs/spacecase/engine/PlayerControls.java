package com.hashimjacobs.spacecase.engine;

import java.util.Set;

import javafx.scene.input.KeyCode;

/**
 * Key bindings for one player. Each direction accepts a set so single-player can drive with either
 * WASD or the arrow keys.
 */
public record PlayerControls(
        Set<KeyCode> up,
        Set<KeyCode> down,
        Set<KeyCode> left,
        Set<KeyCode> right,
        Set<KeyCode> fire) {

    public static PlayerControls playerOne(boolean alsoArrowKeys) {
        if (alsoArrowKeys) {
            return new PlayerControls(
                    Set.of(KeyCode.W, KeyCode.UP),
                    Set.of(KeyCode.S, KeyCode.DOWN),
                    Set.of(KeyCode.A, KeyCode.LEFT),
                    Set.of(KeyCode.D, KeyCode.RIGHT),
                    Set.of(KeyCode.SHIFT, KeyCode.SPACE));
        }
        return new PlayerControls(
                Set.of(KeyCode.W),
                Set.of(KeyCode.S),
                Set.of(KeyCode.A),
                Set.of(KeyCode.D),
                Set.of(KeyCode.SHIFT));
    }

    public static PlayerControls playerTwo() {
        return new PlayerControls(
                Set.of(KeyCode.UP),
                Set.of(KeyCode.DOWN),
                Set.of(KeyCode.LEFT),
                Set.of(KeyCode.RIGHT),
                Set.of(KeyCode.COMMA, KeyCode.PERIOD));
    }

    public boolean anyHeld(InputState input, Set<KeyCode> codes) {
        for (KeyCode code : codes) {
            if (input.isHeld(code)) {
                return true;
            }
        }
        return false;
    }
}
