package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuNavigatorTest {

    @Test
    void theFirstItemStartsFocused() {
        List<Spy> spies = spies(3);
        new MenuNavigator(List.copyOf(spies));

        assertTrue(spies.get(0).focused);
        assertFalse(spies.get(1).focused);
    }

    @Test
    void onlyOneItemIsEverFocused() {
        List<Spy> spies = spies(4);
        MenuNavigator navigator = new MenuNavigator(List.copyOf(spies));

        navigator.handleKey(KeyCode.DOWN);
        navigator.handleKey(KeyCode.DOWN);

        long focused = spies.stream().filter(spy -> spy.focused).count();
        assertEquals(1, focused);
        assertEquals(2, navigator.focusedIndex());
    }

    @Test
    void movingPastTheEndWrapsToTheStart() {
        MenuNavigator navigator = new MenuNavigator(List.copyOf(spies(3)));

        navigator.handleKey(KeyCode.DOWN);
        navigator.handleKey(KeyCode.DOWN);
        navigator.handleKey(KeyCode.DOWN);

        assertEquals(0, navigator.focusedIndex());
    }

    @Test
    void movingUpFromTheStartWrapsToTheEnd() {
        MenuNavigator navigator = new MenuNavigator(List.copyOf(spies(3)));

        navigator.handleKey(KeyCode.UP);

        assertEquals(2, navigator.focusedIndex());
    }

    @Test
    void wasdMirrorsTheArrowKeys() {
        MenuNavigator navigator = new MenuNavigator(List.copyOf(spies(3)));

        navigator.handleKey(KeyCode.S);
        assertEquals(1, navigator.focusedIndex());

        navigator.handleKey(KeyCode.W);
        assertEquals(0, navigator.focusedIndex());
    }

    @Test
    void enterAndSpaceActivateTheFocusedItem() {
        List<Spy> spies = spies(2);
        MenuNavigator navigator = new MenuNavigator(List.copyOf(spies));

        navigator.handleKey(KeyCode.DOWN);
        navigator.handleKey(KeyCode.ENTER);
        navigator.handleKey(KeyCode.SPACE);

        assertEquals(0, spies.get(0).activations, "the unfocused item must not fire");
        assertEquals(2, spies.get(1).activations);
    }

    @Test
    void escapeRunsTheBackAction() {
        MenuNavigator navigator = new MenuNavigator(List.copyOf(spies(2)));
        int[] backCount = {0};
        navigator.setOnBack(() -> backCount[0]++);

        navigator.handleKey(KeyCode.ESCAPE);

        assertEquals(1, backCount[0]);
    }

    @Test
    void unrelatedKeysAreNotConsumed() {
        MenuNavigator navigator = new MenuNavigator(List.copyOf(spies(2)));

        assertTrue(navigator.handleKey(KeyCode.DOWN));
        assertFalse(navigator.handleKey(KeyCode.F11), "F11 must reach the fullscreen handler");
        assertFalse(navigator.handleKey(KeyCode.Q));
    }

    @Test
    void anEmptyMenuIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MenuNavigator(List.of()));
    }

    private static List<Spy> spies(int count) {
        List<Spy> spies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            spies.add(new Spy());
        }
        return spies;
    }

    private static final class Spy implements MenuNavigator.Item {
        private boolean focused;
        private int activations;

        @Override
        public void setHighlighted(boolean highlighted) {
            this.focused = highlighted;
        }

        @Override
        public void activate() {
            activations++;
        }
    }
}
