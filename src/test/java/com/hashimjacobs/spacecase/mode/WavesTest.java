package com.hashimjacobs.spacecase.mode;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.WaveShip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What thirteen hundred hand-written numbers need somebody to check.
 *
 * These tests catch <em>illegal</em>, not <em>bad</em>. Whether a wave is a good fight is a thing
 * you find out by flying it; whether it spawns inside a wall, overruns the arena or fields four
 * waves on a five-wave level is arithmetic, and arithmetic is what a suite is for. Everything here
 * is a property over all fifty levels rather than a number about one of them, so waves can be
 * retuned freely and only a genuine mistake trips anything.
 */
class WavesTest {

    /**
     * The HUD prints WAVE 2/4 off {@code wavesBeforeBoss} and the flagship arrives off the same
     * count. Authoring three waves for a four-wave level ships a level whose third wave is followed
     * by twenty-five seconds of nothing at all, and nothing else in the game would report it.
     */
    @Test
    void everyLevelFieldsExactlyTheWavesItPromises() {
        for (Level level : Level.values()) {
            assertEquals(level.wavesBeforeBoss(), Waves.forLevel(level).size(),
                    level + " authors a different number of waves than it fields");
        }
    }

    /**
     * An empty wave clears on the tick it launches, so the level races through it in silence. If a
     * wave is meant to be a breather, it should author one ship rather than none.
     */
    @Test
    void noWaveIsEmpty() {
        for (Level level : Level.values()) {
            List<List<WaveShip>> waves = Waves.forLevel(level);
            for (int i = 0; i < waves.size(); i++) {
                assertFalse(waves.get(i).isEmpty(), level + " wave " + (i + 1) + " is empty");
            }
        }
    }

    /**
     * Every ship has to fit the lane it spawns in, at the size it was authored at.
     *
     * Reads the level's own sprite through {@link Orientation#acrossExtent}, which is what catches
     * the side-on mistake: those levels declare transposed sizes, so a cruiser's across-extent is
     * its width in one orientation and its height in the other. A row written against the wrong one
     * looks right in a top-down level and hangs out of the arena on level 9.
     */
    @Test
    void everyShipFitsTheArenaItSpawnsIn() {
        forEachShip((level, wave, index, ship) -> {
            double extent = acrossFraction(level, ship);
            assertTrue(ship.across() >= 0,
                    where(level, wave, index, ship) + " starts off the near edge of the lane");
            assertTrue(ship.across() + extent <= 1,
                    where(level, wave, index, ship) + " overruns the far edge of the lane");
        });
    }

    /**
     * In a tunnel every ship sits inside the band the lane is guaranteed to leave open.
     *
     * A template's rock insets at most {@code (1 - minLaneFraction) / 2} from each side, whatever
     * its seed or roughness, so that band is open at every depth and every tick. Author outside it
     * and nothing breaks: {@code SpawnDirector} clamps the ship into the lane and the game carries
     * on. What is lost is the formation -- two ships authored apart both end up against the same
     * wall, in one column, and the only way anybody finds out is by looking at it.
     */
    @Test
    void aCaveWaveFitsTheLaneItSpawnsIn() {
        forEachShip((level, wave, index, ship) -> {
            if (!level.template().hasRock()) {
                return;
            }
            double inset = (1 - level.template().minLaneFraction()) / 2;
            double extent = acrossFraction(level, ship);
            assertTrue(ship.across() >= inset,
                    where(level, wave, index, ship) + " is authored inside the near wall");
            assertTrue(ship.across() + extent <= 1 - inset,
                    where(level, wave, index, ship) + " is authored inside the far wall");
        });
    }

    /**
     * No hull is wider than the narrowest a lane can ever be.
     *
     * Past this the clamp has no correct answer -- there is no position that fits -- and
     * {@code Terrain.pushInside} centres the ship instead, which on screen reads as it teleporting.
     */
    @Test
    void noHullIsWiderThanTheNarrowestLaneCanEverBe() {
        forEachShip((level, wave, index, ship) -> {
            if (!level.template().hasRock()) {
                return;
            }
            assertTrue(acrossFraction(level, ship) <= level.template().minLaneFraction(),
                    where(level, wave, index, ship) + " cannot fit the lane at its worst pinch");
        });
    }

    /**
     * Every ship enters from off-screen.
     *
     * A positive setback would put a hull inside the arena on the tick it spawned -- materialising
     * in front of the player rather than flying in at them, which reads as a bug in the spawner.
     */
    @Test
    void everyShipEntersFromOffScreen() {
        forEachShip((level, wave, index, ship) -> assertTrue(ship.setback() <= 0,
                where(level, wave, index, ship) + " spawns already inside the arena"));
    }

    /**
     * The typo guard, and the reason it is worth having: a misplaced decimal point somewhere in
     * thirteen hundred hand-written numbers is a matter of time rather than of care.
     *
     * Bounds far looser than the bands {@code WaveShip}'s javadoc recommends, deliberately. This is
     * not here to enforce taste -- an author who wants a genuinely enormous cruiser should get one
     * without arguing with a test. It is here to catch {@code .health(16)} written for
     * {@code .health(1.6)}.
     */
    @Test
    void everyTuningFactorIsSane() {
        forEachShip((level, wave, index, ship) -> {
            String at = where(level, wave, index, ship);
            assertInRange(ship.size(), 0.5, 4, at + " size");
            assertInRange(ship.health(), 0.25, 5, at + " health");
            assertInRange(ship.speed(), 0.4, 2, at + " speed");
            assertInRange(ship.fireGap(), 0.25, 4, at + " fireGap");
            assertTrue(ship.shots() >= 1 && ship.shots() <= 4, at + " shots is " + ship.shots());
        });
    }

    /**
     * A wave is a group, not a single arrival.
     *
     * The whole feature is that a level fields designed encounters; a one-ship wave is the trickle
     * with extra steps. Four is a low bar -- most waves here field five or six -- and it exists so
     * that a level half-authored during a retune cannot ship looking finished.
     */
    @Test
    void everyWaveIsAGroupRatherThanASingleShip() {
        for (Level level : Level.values()) {
            List<List<WaveShip>> waves = Waves.forLevel(level);
            for (int i = 0; i < waves.size(); i++) {
                assertTrue(waves.get(i).size() >= 4,
                        level + " wave " + (i + 1) + " fields only " + waves.get(i).size());
            }
        }
    }

    /** A level's last wave should not be lighter than its first, or the level winds down into its boss. */
    @Test
    void aLevelsWavesGetNoEasierOnTheWayToItsFlagship() {
        for (Level level : Level.values()) {
            List<List<WaveShip>> waves = Waves.forLevel(level);
            double first = threat(waves.get(0));
            double last = threat(waves.get(waves.size() - 1));
            assertTrue(last > first,
                    level + " ends on a lighter wave than it opens with: " + first + " -> " + last);
        }
    }

    /**
     * The three groups a wave is actually launched as, checked the same way the rows are.
     *
     * {@code SpawnDirector} does not field an authored row -- it fields {@code Waves.group} of one,
     * three times, and the infill ships in those groups exist nowhere in {@code Waves.java} for
     * anybody to read. So every property the rows are held to has to be held over the groups too,
     * or the twelve cave levels are guarded on the half of their ships somebody typed and not on
     * the half the game generates.
     *
     * The cave case is the one that can genuinely fail rather than the one that rounds out the
     * test: an infill ship copies its <em>left</em> neighbour, so a wide cruiser to the left of a
     * narrow scout puts a cruiser-sized hull at their midpoint, further across the lane than that
     * cruiser was ever authored. Nothing else in the suite would see it, and in play it is one ship
     * silently clamped to the wall.
     */
    @Test
    void everyLaunchedGroupIsALegalWave() {
        for (Level level : Level.values()) {
            List<List<WaveShip>> waves = Waves.forLevel(level);
            for (int w = 0; w < waves.size(); w++) {
                for (int g = 0; g < Waves.GROUPS_PER_WAVE; g++) {
                    List<WaveShip> group = Waves.group(waves.get(w), g);
                    String at = level + " wave " + (w + 1) + " group " + (g + 1);
                    assertTrue(group.size() >= 6 && group.size() <= 10,
                            at + " fields " + group.size() + " ships, outside 6..10");
                    for (WaveShip ship : group) {
                        double extent = acrossFraction(level, ship);
                        double inset = level.template().hasRock()
                                ? (1 - level.template().minLaneFraction()) / 2
                                : 0;
                        assertTrue(ship.across() >= inset,
                                at + " puts a " + ship.kind() + " at " + ship.across()
                                        + ", inside the near wall");
                        assertTrue(ship.across() + extent <= 1 - inset,
                                at + " puts a " + ship.kind() + " at " + ship.across()
                                        + ", inside the far wall");
                        assertTrue(ship.setback() <= 0,
                                at + " spawns a " + ship.kind() + " already inside the arena");
                    }
                }
            }
        }
    }

    /** Rough weight of a wave: hull points on the field, counting size as presence. */
    private static double threat(List<WaveShip> wave) {
        double total = 0;
        for (WaveShip ship : wave) {
            total += ship.kind().health() * ship.health() * ship.size();
        }
        return total;
    }

    /**
     * This ship's width across the lane, as a fraction of the lane's breadth.
     *
     * The level supplies the hull, so this is the same lookup {@code SpawnDirector} does at spawn
     * time -- which is the point: a test that measured a different sprite would prove nothing.
     */
    private static double acrossFraction(Level level, WaveShip ship) {
        Sprite art = level.enemySprite(ship.kind());
        Orientation facing = level.orientation();
        double extent = facing.acrossExtent(ship.width(art), ship.height(art));
        return extent / facing.arenaBreadth();
    }

    private static void assertInRange(double value, double low, double high, String what) {
        assertTrue(value >= low && value <= high, what + " is " + value + ", outside " + low + ".." + high);
    }

    private static String where(Level level, int wave, int index, WaveShip ship) {
        return level + " wave " + (wave + 1) + " ship " + (index + 1) + " (" + ship.kind() + ")";
    }

    private static void forEachShip(ShipCheck check) {
        for (Level level : Level.values()) {
            List<List<WaveShip>> waves = Waves.forLevel(level);
            for (int w = 0; w < waves.size(); w++) {
                List<WaveShip> wave = waves.get(w);
                for (int i = 0; i < wave.size(); i++) {
                    check.check(level, w, i, wave.get(i));
                }
            }
        }
    }

    private interface ShipCheck {
        void check(Level level, int wave, int index, WaveShip ship);
    }
}
