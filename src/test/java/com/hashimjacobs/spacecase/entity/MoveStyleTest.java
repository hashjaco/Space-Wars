package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six ways a ship can cross the arena, and the two properties every one of them has to keep.
 *
 * The first is determinism, which the set-piece flagships already hold themselves to -- two ships
 * built alike must stay alike to the last bit, or a paused fight and a resumed one are different
 * fights, and two machines running the same seed drift apart.
 *
 * The second is that no style holds an axis of its own. Everything is written in {@code along} and
 * {@code across}, so a style authored in a top-down level has to behave identically in a side-on
 * one without a branch anywhere. That is the property {@code StormSerpentTest} exists to hold for
 * the worm, and it is worth holding here for the same reason: it is invisible until level 9.
 */
class MoveStyleTest {

    private static final int TICKS = 600;

    @Test
    void everyStyleIsAPureFunctionOfItsClock() {
        for (MoveStyle style : MoveStyle.values()) {
            EnemyShip one = ship(style, 300, Orientation.TOP_DOWN);
            EnemyShip two = ship(style, 300, Orientation.TOP_DOWN);
            PlayerShip target = target(Orientation.TOP_DOWN);

            for (int tick = 0; tick < TICKS; tick++) {
                one.trackAcross(target);
                two.trackAcross(target);
                one.update();
                two.update();
            }

            assertEquals(one.x(), two.x(), 1e-9, style + " drifted apart in x");
            assertEquals(one.y(), two.y(), 1e-9, style + " drifted apart in y");
        }
    }

    /**
     * The load-bearing one: the same style, in arena terms, flies the same way whichever way the
     * level runs. Compared through {@link Orientation} rather than in screen coordinates, because
     * screen coordinates are exactly what is supposed to differ.
     */
    @Test
    void everyStyleSteersTheSameWayWhicheverWayTheLevelRuns() {
        for (MoveStyle style : MoveStyle.values()) {
            EnemyShip upright = ship(style, 300, Orientation.TOP_DOWN);
            EnemyShip turned = ship(style, 300, Orientation.RIGHT_TO_LEFT);
            PlayerShip uprightTarget = target(Orientation.TOP_DOWN);
            PlayerShip turnedTarget = target(Orientation.RIGHT_TO_LEFT);

            for (int tick = 0; tick < TICKS; tick++) {
                upright.trackAcross(uprightTarget);
                turned.trackAcross(turnedTarget);
                upright.update();
                turned.update();
            }

            double uprightAcross = Orientation.TOP_DOWN.across(upright.x(), upright.y());
            double turnedAcross = Orientation.RIGHT_TO_LEFT.across(turned.x(), turned.y());
            assertEquals(uprightAcross, turnedAcross, 1e-9,
                    style + " ended up somewhere else across the lane when the level turned");

        }
    }

    /**
     * Down-arena, a style either travels at a speed or holds at a station, and the two turn
     * differently on purpose.
     *
     * Everything that simply advances covers the same number of pixels a tick whichever way the
     * level runs, so it has gone the same absolute distance. {@link MoveStyle#HOLD} is the
     * exception, and deliberately: it parks at a <em>fraction</em> of the arena's depth, and the
     * two arenas are 864 and 996 deep. Asserting one rule for both would have to be wrong about one
     * of them, so this says which is which.
     */
    @Test
    void aStyleEitherTravelsAtASpeedOrHoldsAtAStation() {
        for (MoveStyle style : MoveStyle.values()) {
            EnemyShip upright = ship(style, 300, Orientation.TOP_DOWN);
            EnemyShip turned = ship(style, 300, Orientation.RIGHT_TO_LEFT);
            PlayerShip uprightTarget = target(Orientation.TOP_DOWN);
            PlayerShip turnedTarget = target(Orientation.RIGHT_TO_LEFT);

            for (int tick = 0; tick < TICKS; tick++) {
                upright.trackAcross(uprightTarget);
                turned.trackAcross(turnedTarget);
                upright.update();
                turned.update();
            }

            double uprightDepth = Orientation.TOP_DOWN
                    .depth(upright.x(), upright.y(), upright.width(), upright.height());
            double turnedDepth = Orientation.RIGHT_TO_LEFT
                    .depth(turned.x(), turned.y(), turned.width(), turned.height());

            if (style == MoveStyle.HOLD) {
                assertEquals(uprightDepth / Orientation.TOP_DOWN.arenaDepth(),
                        turnedDepth / Orientation.RIGHT_TO_LEFT.arenaDepth(), 0.01,
                        "a holding ship should take the same station in a lane of either length");
            } else {
                assertEquals(uprightDepth, turnedDepth, 1e-9,
                        style + " travelled a different distance down-arena when the level turned");
            }
        }
    }

    /**
     * A straight ship holds the lane it entered by, and a hunting one does not.
     *
     * This is the property the whole authored-wave feature rests on. Without it, {@code trackAcross}
     * pulls every ship in a wave onto the player within about a second and the formation somebody
     * drew is gone before it is on screen.
     */
    @Test
    void aStraightShipHoldsItsLaneWhileAHuntingOneClosesOnYou() {
        PlayerShip target = target(Orientation.TOP_DOWN);
        EnemyShip straight = ship(MoveStyle.STRAIGHT, 200, Orientation.TOP_DOWN);
        EnemyShip hunting = ship(MoveStyle.HUNT, 200, Orientation.TOP_DOWN);
        double lane = straight.x();

        for (int tick = 0; tick < 200; tick++) {
            straight.trackAcross(target);
            hunting.trackAcross(target);
            straight.update();
            hunting.update();
        }

        assertEquals(lane, straight.x(), 1e-9, "a straight ship should never leave its lane");
        assertTrue(hunting.x() > lane + 50, "a hunting ship should have closed on the player");
    }

    /** A weaving ship crosses the player's lane in both directions and never settles on it. */
    @Test
    void aWeavingShipNeverConvergesOnThePlayer() {
        PlayerShip target = target(Orientation.TOP_DOWN);
        EnemyShip weaving = ship(MoveStyle.WEAVE, 400, Orientation.TOP_DOWN);

        boolean wentLeft = false;
        boolean wentRight = false;
        for (int tick = 0; tick < 400; tick++) {
            weaving.trackAcross(target);
            wentLeft |= weaving.velocityX() < -0.5;
            wentRight |= weaving.velocityX() > 0.5;
            weaving.update();
        }

        assertTrue(wentLeft && wentRight, "a weave has to go both ways, or it is a drift");
    }

    /**
     * A diving ship builds up speed and then commits, and never outruns the player doing it.
     *
     * The cap is the same one {@code ENEMY_SPEED_SCALE_CAP} exists for: a hostile that closes faster
     * than the player can leave has no answer at all, only a health bar.
     */
    @Test
    void aDivingShipCommitsAndNeverOutrunsThePlayer() {
        PlayerShip target = target(Orientation.TOP_DOWN);
        EnemyShip diving = ship(MoveStyle.DIVE, 400, Orientation.TOP_DOWN);

        diving.trackAcross(target);
        double opening = diving.velocityY();
        double fastest = opening;
        for (int tick = 0; tick < 400; tick++) {
            diving.trackAcross(target);
            fastest = Math.max(fastest, diving.velocityY());
            diving.update();
        }

        assertTrue(fastest > opening * 2, "a dive has to actually commit");
        assertTrue(fastest < GameConfig.PLAYER_SPEED,
                "a dive must never be faster than the ship it is diving at");
    }

    /**
     * A holding ship parks inside the arena instead of flying out the far side.
     *
     * The one style that cannot be waited out, and the one with a hazard worth stating in a test
     * rather than only in a comment: it is still on the field at the end, so it is still occupying
     * one of the difficulty preset's enemy slots. A lane of these wedges the arena shut.
     */
    @Test
    void aHoldingShipParksInsideTheArenaAndIsNeverCulled() {
        for (Orientation facing : Orientation.values()) {
            PlayerShip target = target(facing);
            EnemyShip holding = ship(MoveStyle.HOLD, 400, facing);

            for (int tick = 0; tick < 2000; tick++) {
                holding.trackAcross(target);
                holding.update();
            }

            double depth = facing.depth(holding.x(), holding.y(), holding.width(), holding.height());
            assertTrue(depth > 0 && depth < facing.arenaDepth(),
                    facing + " holding ship left the arena at depth " + depth);
        }
    }

    /**
     * A ship at the entry edge, centred on the given lane, flying the given style.
     *
     * The hull is the level's own, which means the transposed one in a side-on level -- exactly as
     * {@code Level.enemySprite} supplies it. Using a top-down sprite in a side-on orientation would
     * give the ship a different width across the lane in each, and the comparison would be
     * measuring the fixture rather than the style.
     *
     * Placed by its centre rather than its corner for the same reason: {@code trackAcross} steers
     * on centres, so two ships whose corners line up but whose centres do not are not at the same
     * place as far as any style is concerned.
     */
    private static EnemyShip ship(MoveStyle style, double centreAcross, Orientation facing) {
        WaveShip ordered = WaveShip.at(EnemyShip.EnemyKind.SCOUT, 0).move(style);
        Sprite art = facing == Orientation.TOP_DOWN ? Sprite.L1_SCOUT : Sprite.L9_SCOUT;
        double w = ordered.width(art);
        double h = ordered.height(art);
        double across = centreAcross - facing.acrossExtent(w, h) / 2;
        EnemyShip ship = new EnemyShip(EnemyShip.EnemyKind.SCOUT, art,
                facing.atX(0, across, w, h), facing.atY(0, across, w, h), 1, ordered);
        ship.enter(facing);
        return ship;
    }

    /** A player well down-arena and off to one side, so "toward the target" has a sign. */
    private static PlayerShip target(Orientation facing) {
        PlayerShip player = new PlayerShip(1, "P1", facing.playerFacing(), 0, 0);
        double depth = facing.arenaDepth() - 130;
        double across = 700 - facing.acrossExtent(player.width(), player.height()) / 2;
        double w = player.width();
        double h = player.height();
        player.setPosition(facing.atX(depth, across, w, h), facing.atY(depth, across, w, h));
        return player;
    }
}
