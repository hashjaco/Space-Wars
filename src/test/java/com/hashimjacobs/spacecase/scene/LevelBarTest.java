package com.hashimjacobs.spacecase.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelBarTest {

    @Test
    void clampsPastEitherEnd() {
        assertEquals(0, LevelBar.valueAt(-40, 150, 0, 1, 0.01));
        assertEquals(1, LevelBar.valueAt(400, 150, 0, 1, 0.01));
    }

    @Test
    void snapsToTheStepWithoutFloatDust() {
        assertEquals(0.3, LevelBar.valueAt(45.2, 150, 0, 1, 0.01));
        assertEquals(0.5, LevelBar.valueAt(74, 150, 0, 1, 0.1));
    }

    @Test
    void honoursANonZeroFloor() {
        // Sensitivity runs 0.5..2.0: the left edge is the floor, the middle is 1.25.
        assertEquals(0.5, LevelBar.valueAt(0, 150, 0.5, 2.0, 0.05));
        assertEquals(1.25, LevelBar.valueAt(75, 150, 0.5, 2.0, 0.05));
        // Deadzone's floor is 0.05, and snapping counts from it, not from zero.
        assertEquals(0.05, LevelBar.valueAt(0.1, 150, 0.05, 0.80, 0.01));
    }
}
