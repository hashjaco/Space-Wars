package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.mode.WorldTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rock, as arithmetic. No JavaFX, nothing drawn.
 *
 * Two of these are load-bearing rather than routine. {@link #theLaneNeverClosesBelowItsGuarantee()}
 * is what makes an unwinnable level impossible instead of unlikely, and
 * {@link #theSurfaceHasNoStepInIt()} is what stops a discontinuity teleporting a ship across the
 * tunnel. The rest guard against the ways this could be subtly wrong while looking fine.
 */
class TerrainTest {

    private static final long SEED = 4242;

    private static Terrain cave(Orientation facing) {
        return new Terrain(WorldTemplate.CAVE, facing, SEED);
    }

    /** Any entity will do; an asteroid is the smallest thing to construct. */
    private static Entity boxAt(Orientation facing, double depth, double across) {
        Asteroid rock = new Asteroid(Sprite.ASTEROID_SMALL, 0, 0, 10, 5, 1);
        rock.setPosition(facing.atX(depth, across, rock.width(), rock.height()),
                facing.atY(depth, across, rock.width(), rock.height()));
        return rock;
    }

    @Test
    void anOpenFieldHasNoRockAtAll() {
        Terrain open = new Terrain(WorldTemplate.OPEN_FIELD, Orientation.TOP_DOWN, SEED);

        assertTrue(open.isEmpty());
        assertEquals(0, open.inset(100, -1), 1e-9);
        assertFalse(open.solidAt(0, 0), "the very corner of an open level is still flyable");
        assertFalse(open.pushInside(boxAt(Orientation.TOP_DOWN, 100, 0)));
    }

    @Test
    void theSameSeedGivesTheSameRock() {
        Terrain first = cave(Orientation.TOP_DOWN);
        Terrain second = cave(Orientation.TOP_DOWN);

        for (int depth = 0; depth < 2000; depth += 4) {
            assertEquals(first.inset(depth, -1), second.inset(depth, -1), 1e-12);
            assertEquals(first.inset(depth, 1), second.inset(depth, 1), 1e-12);
        }
    }

    @Test
    void differentSeedsGiveDifferentRock() {
        Terrain first = new Terrain(WorldTemplate.CAVE, Orientation.TOP_DOWN, 1);
        Terrain second = new Terrain(WorldTemplate.CAVE, Orientation.TOP_DOWN, 2);

        boolean differs = false;
        for (int depth = 0; depth < 2000 && !differs; depth += 4) {
            differs = Math.abs(first.inset(depth, -1) - second.inset(depth, -1)) > 1e-6;
        }
        assertTrue(differs, "two seeds produced identical rock, so the seed is being ignored");
    }

    /**
     * The fairness gate. Whatever the harmonics do, there is always room to fly through.
     *
     * Derived rather than clamped, so this cannot be defeated by turning the roughness up -- which
     * is exactly what someone tuning a new template will try first.
     */
    @Test
    void theLaneNeverClosesBelowItsGuarantee() {
        for (Orientation facing : Orientation.values()) {
            Terrain terrain = cave(facing);
            double guaranteed = WorldTemplate.CAVE.minLaneFraction() * facing.arenaBreadth();
            for (double depth = 0; depth < facing.arenaDepth() * 5; depth += Terrain.PITCH / 2) {
                double lane = terrain.laneHigh(depth) - terrain.laneLow(depth);
                assertTrue(lane >= guaranteed - 1e-6,
                        facing + " closed to " + lane + " at depth " + depth
                                + ", below its guaranteed " + guaranteed);
            }
        }
    }

    /**
     * No wall face is steeper than the ship can fly off.
     *
     * The real invariant, and worth stating as one rather than as a pixel budget. As the tunnel
     * scrolls past, a sloped face pushes a ship sideways at the slope times the scroll speed. If
     * that ever exceeds {@code PLAYER_SPEED}, a pilot caught against the rock is shoved along it
     * faster than they can steer away -- which reads as the game taking the controls off you.
     *
     * This is what ruled out sharpening the profile with a fractional power: {@code |x|^(1/3)} has
     * infinite slope where the wobble crosses zero, and it measured a 56-pixel step across a
     * 9-pixel lattice, six times over budget. tanh reaches the same extremes with bounded slope.
     *
     * Checked over several seeds, because one seed can miss its own worst face.
     */
    @Test
    void noWallFaceOutrunsTheShip() {
        double scrollPerTick = com.hashimjacobs.spacecase.GameConfig.BACKGROUND_SCROLL_SPEED * 1.5;
        double budget = com.hashimjacobs.spacecase.GameConfig.PLAYER_SPEED;

        for (long seed = 0; seed < 12; seed++) {
            Terrain terrain = new Terrain(WorldTemplate.CAVE, Orientation.TOP_DOWN, seed);
            double previous = terrain.inset(0, -1);
            for (double depth = Terrain.PITCH; depth < 4 * 864; depth += Terrain.PITCH) {
                double here = terrain.inset(depth, -1);
                double slope = Math.abs(here - previous) / Terrain.PITCH;
                double sideways = slope * scrollPerTick;
                assertTrue(sideways < budget,
                        "seed " + seed + ": a face pushes " + sideways
                                + "px a tick sideways, against a ship that moves " + budget);
                previous = here;
            }
        }
    }

    /** The rock has actual throats in it, not a smooth taper. */
    @Test
    void theTunnelHasThroatsAndChambers() {
        Terrain terrain = cave(Orientation.TOP_DOWN);
        double breadth = Orientation.TOP_DOWN.arenaBreadth();
        double narrowest = Double.MAX_VALUE;
        double widest = 0;
        for (double depth = 0; depth < 4 * 864; depth += Terrain.PITCH) {
            double lane = terrain.laneHigh(depth) - terrain.laneLow(depth);
            narrowest = Math.min(narrowest, lane);
            widest = Math.max(widest, lane);
        }
        assertTrue(narrowest < breadth * 0.5,
                "nothing here is tight enough to have to steer through: " + narrowest);
        assertTrue(widest > breadth * 0.65, "and nothing opens up again: " + widest);
    }

    @Test
    void theRockRepeatsWithoutASeam() {
        Terrain terrain = cave(Orientation.TOP_DOWN);
        double period = 4 * Orientation.TOP_DOWN.arenaDepth();

        // The table is periodic, so a whole period later has to be the same rock to the pixel.
        for (double depth = 0; depth < 400; depth += Terrain.PITCH) {
            assertEquals(terrain.inset(depth, -1), terrain.inset(depth + period, -1), 1e-6,
                    "the tunnel does not close on itself at depth " + depth);
        }
    }

    @Test
    void aBoxInsideRockIsPushedFullyIntoTheLane() {
        for (Orientation facing : Orientation.values()) {
            Terrain terrain = cave(facing);
            Entity box = boxAt(facing, 300, 0);

            assertTrue(terrain.pushInside(box), facing + ": a box at the wall should be moved");

            double depth = facing.depth(box.x(), box.y(), box.width(), box.height());
            double across = facing.across(box.x(), box.y());
            double extent = facing.acrossExtent(box.width(), box.height());
            assertTrue(across >= terrain.laneLow(depth) - 1e-6,
                    facing + ": still overlapping the near wall");
            assertTrue(across + extent <= terrain.laneHigh(depth) + 1e-6,
                    facing + ": pushed out through the far wall");
        }
    }

    /**
     * A ship already flying clear is left exactly alone.
     *
     * Without this the push would report a move every tick, and since a move is what triggers the
     * scrape damage, flying down the middle of an open tunnel would cost health.
     */
    @Test
    void aBoxAlreadyInTheLaneIsNotMoved() {
        Terrain terrain = cave(Orientation.TOP_DOWN);
        double depth = 300;
        double middle = (terrain.laneLow(depth) + terrain.laneHigh(depth)) / 2;
        Entity box = boxAt(Orientation.TOP_DOWN, depth, middle);
        double x = box.x();
        double y = box.y();

        assertFalse(terrain.pushInside(box), "a box in open lane must not be reported as scraping");
        assertEquals(x, box.x(), 1e-9);
        assertEquals(y, box.y(), 1e-9);
    }

    /**
     * Both orientations describe the same tunnel, in their own coordinates.
     *
     * Compared as fractions, not pixels: the arena is 996 across and 864 deep, so the two run down
     * different-length lanes and identical absolute numbers would be the wrong assertion.
     *
     * Not compared exactly either, and the reason is worth knowing. The lattice holds a whole
     * number of nodes, and 4 x 864 / 9 comes to 384 where 4 x 996 / 9 comes to 442.67 and rounds to
     * 443 -- so the two tables cover very slightly different periods and the harmonic argument
     * drifts apart as the depth grows. It stays inside a pixel, which is what "the same shape" can
     * honestly mean here. A real orientation bug -- reaching for the breadth where the depth was
     * wanted, say -- moves this by tens of percent, not by a pixel.
     */
    @Test
    void bothOrientationsAgreeOnTheShapeOfTheTunnel() {
        Terrain down = cave(Orientation.TOP_DOWN);
        Terrain across = cave(Orientation.RIGHT_TO_LEFT);

        for (double fraction = 0; fraction < 1; fraction += 0.02) {
            double laneDown = (down.laneHigh(fraction * Orientation.TOP_DOWN.arenaDepth())
                    - down.laneLow(fraction * Orientation.TOP_DOWN.arenaDepth()))
                    / Orientation.TOP_DOWN.arenaBreadth();
            double laneAcross = (across.laneHigh(fraction * Orientation.RIGHT_TO_LEFT.arenaDepth())
                    - across.laneLow(fraction * Orientation.RIGHT_TO_LEFT.arenaDepth()))
                    / Orientation.RIGHT_TO_LEFT.arenaBreadth();
            // Five parts in a thousand: about five pixels, against the tens of percent a genuine
            // mix-up of the two axes would produce.
            assertEquals(laneDown, laneAcross, 5e-3,
                    "the tunnel should be the same shape whichever way the level runs");
        }
    }

    @Test
    void rockStopsAShotAndOpenLaneDoesNot() {
        Terrain terrain = cave(Orientation.TOP_DOWN);
        double depth = 300;
        double middle = (terrain.laneLow(depth) + terrain.laneHigh(depth)) / 2;

        assertTrue(terrain.solidAt(1, depth), "the very edge of the arena is rock in a tunnel");
        assertFalse(terrain.solidAt(middle, depth), "the middle of the lane is not");
    }

    /**
     * The chamber opens for the flagship and stays open.
     *
     * Both halves matter. Without the opening, a boss wider than the lane cannot be fought; without
     * the staying, the victory lap flies back through rock that has closed behind it -- and the lap
     * ticks scenery without running collision, so nothing would push the ships out again.
     */
    @Test
    void theChamberOpensForTheFlagshipAndStaysOpen() {
        Terrain terrain = cave(Orientation.TOP_DOWN);
        double closed = terrain.laneHigh(200) - terrain.laneLow(200);

        for (int i = 0; i < 300; i++) {
            terrain.tick(false);
        }
        assertTrue(terrain.laneHigh(200) - terrain.laneLow(200) < closed * 1.5,
                "the tunnel should still be a tunnel before the flagship arrives");

        for (int i = 0; i < 400; i++) {
            terrain.tick(true);
        }
        double open = terrain.laneHigh(200) - terrain.laneLow(200);
        assertEquals(Orientation.TOP_DOWN.arenaBreadth(), open, 1e-6,
                "the chamber should be the full arena once it has opened");

        for (int i = 0; i < 600; i++) {
            terrain.tick(false);
        }
        assertEquals(Orientation.TOP_DOWN.arenaBreadth(),
                terrain.laneHigh(200) - terrain.laneLow(200), 1e-6,
                "the chamber must not close again behind the fight");
    }

    @Test
    void theRockScrollsWithTheLevel() {
        Terrain terrain = cave(Orientation.TOP_DOWN);
        double before = terrain.inset(200, -1);

        for (int i = 0; i < 40; i++) {
            terrain.tick(false);
        }

        assertTrue(Math.abs(terrain.inset(200, -1) - before) > 1e-6,
                "the tunnel should move past the ship, not sit still");
    }
}
