package com.hashimjacobs.spacecase.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where ships line up, for one player through four.
 *
 * The arena fielded exactly two ships until an online room needed to seat four, and the placement
 * was written in terms of player one versus player two -- lanes at thirds, and a {@code second}
 * flag. Generalising that is the kind of change that silently moves a solo ship two hundred pixels,
 * so the one- and two-player numbers are pinned here as arithmetic rather than as "looks right".
 */
class WorldSpawnTest {

    /** The breadth a lane is measured across. Read from the enum so the test cannot drift from it. */
    private static final double BREADTH = Orientation.TOP_DOWN.arenaBreadth();

    @Test
    void aLoneShipHoldsTheCentreLine() {
        assertEquals(BREADTH / 2, laneOf(seat(GameMode.SOLO, 1), 0), 0.5,
                "a solo ship has always started in the middle");
    }

    @Test
    void twoShipsHoldTheThirds() {
        List<PlayerShip> ships = seat(GameMode.COOP, 2);

        assertEquals(BREADTH / 3, laneOf(ships, 0), 0.5);
        assertEquals(BREADTH * 2 / 3, laneOf(ships, 1), 0.5);
    }

    /** n/(count+1), which is what generalises the thirds without moving them. */
    @Test
    void fourShipsSpreadEvenlyAcrossTheArena() {
        List<PlayerShip> ships = seat(GameMode.COOP, 4);

        assertEquals(4, ships.size(), "four names must field four ships");
        for (int i = 0; i < 4; i++) {
            assertEquals(BREADTH * (i + 1) / 5.0, laneOf(ships, i), 0.5,
                    "ship " + (i + 1) + " should sit at " + (i + 1) + "/5 of the breadth");
        }
    }

    @Test
    void threeShipsSpreadEvenlyToo() {
        List<PlayerShip> ships = seat(GameMode.COOP, 3);

        assertEquals(BREADTH / 4, laneOf(ships, 0), 0.5);
        assertEquals(BREADTH / 2, laneOf(ships, 1), 0.5);
        assertEquals(BREADTH * 3 / 4, laneOf(ships, 2), 0.5);
    }

    /** Two facing off still meet on the centre line, at opposite ends, pointing at each other. */
    @Test
    void battleStillPutsTwoShipsNoseToNose() {
        List<PlayerShip> ships = seat(GameMode.BATTLE, 2);

        assertEquals(BREADTH / 2, laneOf(ships, 0), 0.5);
        assertEquals(BREADTH / 2, laneOf(ships, 1), 0.5);
        assertNotEquals(ships.get(0).facing(), ships.get(1).facing(),
                "the second seat turns to face back down the arena");
        assertTrue(ships.get(0).y() > ships.get(1).y(),
                "player one holds the near end and player two the far one");
    }

    /**
     * Four in a battle are two a side rather than a queue at one end, and the sides alternate by
     * player number so neighbouring seats are opponents.
     */
    @Test
    void fourInABattleTakeTwoEndsTwoAbreast() {
        List<PlayerShip> ships = seat(GameMode.BATTLE, 4);

        assertEquals(ships.get(0).facing(), ships.get(2).facing(), "one and three share an end");
        assertEquals(ships.get(1).facing(), ships.get(3).facing(), "two and four share the other");
        assertNotEquals(ships.get(0).facing(), ships.get(1).facing(), "and the ends face off");

        assertEquals(BREADTH / 3, laneOf(ships, 0), 0.5, "each end spreads across its own half");
        assertEquals(BREADTH * 2 / 3, laneOf(ships, 2), 0.5);
    }

    /** Every ship must start inside the arena, whatever the count. */
    @Test
    void noShipSpawnsOutsideTheArena() {
        for (GameMode mode : GameMode.values()) {
            for (int count = 1; count <= 4; count++) {
                for (PlayerShip ship : seat(mode, count)) {
                    assertTrue(ship.x() >= 0 && ship.x() + ship.width() <= BREADTH,
                            mode + " with " + count + ": ship " + ship.playerNumber()
                                    + " starts off the arena at x=" + ship.x());
                }
            }
        }
    }

    /**
     * Builds a world with a given number of seats.
     *
     * Names decide the count, which is the change this file exists to pin: passing a fixed two while
     * the mode said one used to be harmless, and now would field a wingman in a solo run.
     */
    private static List<PlayerShip> seat(GameMode mode, int count) {
        List<String> names = List.of("P1", "P2", "P3", "P4").subList(0, count);
        World world = new World(mode, names);
        world.setOrientation(Orientation.TOP_DOWN);
        return world.players();
    }

    /** A ship's position across the arena, measured at its centre the way the lane is set. */
    private static double laneOf(List<PlayerShip> ships, int index) {
        PlayerShip ship = ships.get(index);
        return ship.x() + ship.width() / 2;
    }

    static {
        // Guards the assumption every expectation above rests on.
        assert Orientation.TOP_DOWN.playerFacing() == Facing.UP;
    }
}
