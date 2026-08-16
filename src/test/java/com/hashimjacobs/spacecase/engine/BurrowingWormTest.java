package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.BurrowingWorm;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Dune Leviathan, which is a movement script more than it is a ship. */
class BurrowingWormTest {

    private static BurrowingWorm inSideView(World world) {
        BurrowingWorm worm = new BurrowingWorm(Boss.DUNE_LEVIATHAN, GameConfig.WIDTH, 400, 1);
        world.addEnemy(worm);
        return worm;
    }

    private static World sideView() {
        World world = new World(GameMode.SOLO);
        world.setOrientation(Orientation.RIGHT_TO_LEFT);
        return world;
    }

    private static double depthOf(BurrowingWorm worm) {
        return Orientation.RIGHT_TO_LEFT.depth(worm.x(), worm.y(), worm.width(), worm.height());
    }

    @Test
    void itLungesIntoTheArenaAndWithdrawsAgain() {
        World world = sideView();
        BurrowingWorm worm = inSideView(world);

        double deepest = 0;
        double shallowest = Double.MAX_VALUE;
        for (int tick = 0; tick < 600; tick++) {
            world.update();
            deepest = Math.max(deepest, depthOf(worm));
            shallowest = Math.min(shallowest, depthOf(worm));
        }

        assertTrue(deepest > GameConfig.WIDTH * 0.4,
                "should strike well into the arena, reached " + deepest);
        assertTrue(shallowest < 150, "and pull back to the burrow, retreated to " + shallowest);
    }

    @Test
    void itNeverLeavesTheArenaAndIsNeverCulled() {
        World world = sideView();
        BurrowingWorm worm = inSideView(world);

        for (int tick = 0; tick < 3000; tick++) {
            world.update();
            world.sweep();
            assertTrue(worm.isAlive(), "culled at tick " + tick);
            assertTrue(worm.y() > -worm.height() && worm.y() < GameConfig.HEIGHT,
                    "drifted out of the lane at y=" + worm.y());
        }
    }

    /**
     * The lunge cycle and the drift are near-coprime, so strikes land in different lanes. Without
     * that the fight could be stood still through.
     */
    @Test
    void consecutiveStrikesArriveInDifferentLanes() {
        World world = sideView();
        BurrowingWorm worm = inSideView(world);

        double firstStrikeY = 0;
        double secondStrikeY = 0;
        double deepestSoFar = 0;
        for (int tick = 0; tick < 260; tick++) {
            world.update();
            if (depthOf(worm) > deepestSoFar) {
                deepestSoFar = depthOf(worm);
                firstStrikeY = worm.centerY();
            }
        }
        deepestSoFar = 0;
        for (int tick = 0; tick < 260; tick++) {
            world.update();
            if (depthOf(worm) > deepestSoFar) {
                deepestSoFar = depthOf(worm);
                secondStrikeY = worm.centerY();
            }
        }

        assertTrue(Math.abs(firstStrikeY - secondStrikeY) > 40,
                "two strikes landed in the same lane: " + firstStrikeY + " and " + secondStrikeY);
    }

    /**
     * On arrival the whole body is still in the burrow.
     *
     * A ring's position is the head's from further back in time, and before the fight began there
     * is no such time. Wrapping a negative age would drop the tail into the middle of a strike
     * that never happened, and it would come out of the wall ahead of the head.
     *
     * Only the first strike is checked. Once the head starts withdrawing the tail is legitimately
     * further into the arena -- it is following the same path, fifty-odd ticks behind.
     */
    @Test
    void theWholeBodyStartsInTheBurrow() {
        World world = sideView();
        BurrowingWorm worm = inSideView(world);
        world.update();

        double headDepth = Orientation.RIGHT_TO_LEFT.depth(worm.centerX(), worm.centerY(), 0, 0);
        for (int ring = 1; ring <= BurrowingWorm.SEGMENTS; ring++) {
            double tailDepth = Orientation.RIGHT_TO_LEFT.depth(
                    worm.trailX(ring), worm.trailY(ring), 0, 0);
            assertTrue(tailDepth <= headDepth + 1e-6,
                    "ring " + ring + " was out in front at " + tailDepth
                            + " with the head at " + headDepth);
        }
    }

    @Test
    void itIsASinglePieceRatherThanAMultiPartBoss() {
        World world = sideView();
        BurrowingWorm worm = inSideView(world);

        assertTrue(worm.parts().isEmpty(), "the worm is deliberately not shot apart");
        assertTrue(worm.isBoss());
    }
}
