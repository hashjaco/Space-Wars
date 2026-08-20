package com.hashimjacobs.spacecase.engine;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hull flown side-on: the cut it uses, the box it fills, and which way a push banks it.
 *
 * The bank half is the gap this closes -- {@code PlayerShipTest} pins lean-to-sprite, and
 * {@code AnalogStickTest} pins the stick curve, but nothing pinned which axis feeds the lean, which
 * is the thing that has to stay put for a turned hull to read correctly.
 */
class SideOnHullTest {

    private static PlayerShip soloIn(Orientation orientation) {
        World world = new World(GameMode.SOLO);
        world.setOrientation(orientation);
        return world.players().get(0);
    }

    @Test
    void aPilotFlyingSideOnUsesTheTurnedCutAndAnUprightOneOtherwise() {
        assertSame(Sprite.P1_STRAIGHT, soloIn(Orientation.TOP_DOWN).sprite());
        assertSame(Sprite.P1_STRAIGHT_SIDE, soloIn(Orientation.RIGHT_TO_LEFT).sprite());
    }

    /** The box has to turn with the art, or the hull stops short of one wall and through another. */
    @Test
    void theBoxTurnsWithTheHull() {
        PlayerShip upright = soloIn(Orientation.TOP_DOWN);
        PlayerShip sideOn = soloIn(Orientation.RIGHT_TO_LEFT);

        assertNotEquals(upright.width(), upright.height(), "the test is pointless on a square hull");
        assertEquals(upright.height(), sideOn.width(), 1e-9);
        assertEquals(upright.width(), sideOn.height(), 1e-9);
    }

    @Test
    void aTurnedHullReachesTheWallItIsPointedAt() {
        World world = new World(GameMode.SOLO);
        world.setOrientation(Orientation.RIGHT_TO_LEFT);
        PlayerShip solo = world.players().get(0);

        solo.setPosition(10_000, solo.y());
        world.update();

        assertEquals(com.hashimjacobs.spacecase.GameConfig.WIDTH - solo.width(), solo.x(), 1e-9,
                "should sit flush against the right wall");
    }

    /**
     * Banking keys off movement across the lane, so a side-view level banks on the vertical push.
     * Driven through the stick, which is the one input path a test can feed without a live Scene.
     */
    @Test
    void aSideViewLevelBanksOnTheVerticalPushRatherThanTheHorizontalOne() {
        assertSame(PlayerShip.Lean.HARD_RIGHT, leanFromStick(Orientation.RIGHT_TO_LEFT, 0, 1));
        assertSame(PlayerShip.Lean.HARD_LEFT, leanFromStick(Orientation.RIGHT_TO_LEFT, 0, -1));
        assertSame(PlayerShip.Lean.NONE, leanFromStick(Orientation.RIGHT_TO_LEFT, 1, 0));
    }

    @Test
    void aTopDownLevelStillBanksOnTheHorizontalPush() {
        assertSame(PlayerShip.Lean.HARD_RIGHT, leanFromStick(Orientation.TOP_DOWN, 1, 0));
        assertSame(PlayerShip.Lean.HARD_LEFT, leanFromStick(Orientation.TOP_DOWN, -1, 0));
        assertSame(PlayerShip.Lean.NONE, leanFromStick(Orientation.TOP_DOWN, 0, 1));
    }

    private static PlayerShip.Lean leanFromStick(Orientation orientation, double x, double y) {
        World world = new World(GameMode.SOLO);
        world.setOrientation(orientation);
        PlayerShip solo = world.players().get(0);
        PadState pushed = new PadState(true, x, y, false, false, false, false, Set.of());

        ShipController controller = new ShipController(solo, someKeys(),
                player -> pushed);
        controller.apply(new InputState(), world, null, new StickTuning(0.2, true, 1.0));

        return leanOf(solo);
    }

    /** Any workable set of keys: this drives the stick, not the keyboard. */
    private static PlayerControls someKeys() {
        return new PlayerControls(Set.of(javafx.scene.input.KeyCode.W),
                Set.of(javafx.scene.input.KeyCode.S), Set.of(javafx.scene.input.KeyCode.A),
                Set.of(javafx.scene.input.KeyCode.D), Set.of(javafx.scene.input.KeyCode.SHIFT));
    }

    /** Read back off the sprite, since the lean itself is private to the ship. */
    private static PlayerShip.Lean leanOf(PlayerShip ship) {
        Sprite flown = ship.sprite();
        for (PlayerShip.Lean lean : PlayerShip.Lean.values()) {
            Sprite candidate = ship.loadout().livery()
                    .pose(lean.ordinal(), false, ship.facing().horizontal());
            if (candidate == flown) {
                return lean;
            }
        }
        throw new AssertionError("no lean matches " + flown);
    }

    @Test
    void everyPlayerFrameHasATurnedTwinAndEveryOtherSpriteIsItsOwn() {
        assertSame(Sprite.ASTEROID_BIG, Sprite.ASTEROID_BIG.sideOn());
        assertSame(Sprite.P1_STRAIGHT_HIT_SIDE, Sprite.P1_STRAIGHT_HIT.sideOn());
        assertTrue(Sprite.KIT_LANCE_LEFT.sideOn() != Sprite.KIT_LANCE_LEFT,
                "kit decals need a turned cut too");
    }
}
