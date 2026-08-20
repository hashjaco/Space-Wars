package com.hashimjacobs.spacecase.mode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Levels are the only place progression is configured, so their data is worth pinning down. */
class LevelTest {

    @Test
    void everyLevelHasItsOwnSky() {
        Set<Sprite> seen = new HashSet<>();
        for (Level level : Level.values()) {
            assertEquals(3, level.layers().size(), level + " needs a far, mid and near layer");
            for (Sprite layer : level.layers()) {
                assertTrue(seen.add(layer),
                        layer + " is shared between levels, so two levels look alike");
            }
        }
    }

    @Test
    void everyLevelHasItsOwnBoss() {
        Set<Boss> seen = new HashSet<>();
        for (Level level : Level.values()) {
            assertTrue(seen.add(level.boss()),
                    level.boss() + " guards more than one level");
        }
    }

    @Test
    void everyBossIsUsedBySomeLevel() {
        List<Boss> claimed = new ArrayList<>();
        for (Level level : Level.values()) {
            claimed.add(level.boss());
        }
        for (Boss boss : Boss.values()) {
            assertTrue(claimed.contains(boss), boss + " is unreachable: no level fields it");
        }
    }

    @Test
    void everyLevelFieldsWavesBeforeItsBoss() {
        for (Level level : Level.values()) {
            assertTrue(level.wavesBeforeBoss() > 0,
                    level + " would open on its boss with no waves first");
        }
    }

    @Test
    void theLastLevelWrapsBackToTheFirst() {
        Level[] all = Level.values();
        Level last = all[all.length - 1];
        assertEquals(all[0], last.next(), "the run loops rather than ending");
    }

    @Test
    void nextWalksTheLevelsInOrder() {
        Level[] all = Level.values();
        for (int i = 0; i < all.length - 1; i++) {
            assertEquals(all[i + 1], all[i].next(), all[i] + " should lead to the next level");
        }
    }

    @Test
    void everyLevelIsLabelled() {
        for (Level level : Level.values()) {
            assertTrue(level.label() != null && !level.label().isBlank(),
                    level + " needs a label for the HUD");
        }
    }

    /**
     * Every level's six sprites must point at its own directory, in the right order.
     *
     * The one error class that nothing else catches. Writing L23_MID("level-23/near.png") compiles,
     * points at a file that exists so provenance passes, is unique so everyLevelHasItsOwnSky passes
     * -- and ships a level whose mid and near layers are the same image. Same for a hull pasted from
     * the level above with its number left behind. With six constants per level to hand-write, that
     * is a matter of time rather than of care.
     */
    @Test
    void everyLevelsArtFollowsTheNamingConvention() {
        String[] layerNames = {"far", "mid", "near"};
        for (Level level : Level.values()) {
            String directory = "/sprites/level-" + level.number() + "/";
            for (int layer = 0; layer < layerNames.length; layer++) {
                assertEquals(directory + layerNames[layer] + ".png",
                        level.layers().get(layer).resourcePath(),
                        level + " layer " + layerNames[layer] + " points somewhere else");
            }
            for (EnemyShip.EnemyKind kind : EnemyShip.EnemyKind.values()) {
                String expected = directory + "enemy-" + kind.name().toLowerCase(Locale.ROOT) + ".png";
                assertEquals(expected, level.enemySprite(kind).resourcePath(),
                        level + " " + kind + " hull points somewhere else");
            }
        }
    }

    /**
     * A side-on level's hostiles declare transposed sizes.
     *
     * The one failure in this codebase that nothing else catches and that no exception reports. A
     * side-view level is a matched set of four things: the sky tiles horizontally, the hulls are cut
     * pointing left, the {@code Sprite} constants swap width for height, and the flagship's frames
     * are turned once by the generator. Get the third wrong and the ship is drawn one way round with
     * a hitbox at right angles to it -- shots pass through the nose and connect with empty space
     * beside the wing. It looks like a collision bug and it is a typo.
     *
     * Compares against the top-down levels rather than hard-coding numbers, so it keeps working when
     * the archetype sizes are retuned.
     */
    @Test
    void sideOnLevelsDeclareTransposedEnemySizes() {
        Level upright = null;
        for (Level level : Level.values()) {
            if (level.orientation() == Orientation.TOP_DOWN) {
                upright = level;
                break;
            }
        }
        assertTrue(upright != null, "no top-down level to compare against");

        int sideOn = 0;
        for (Level level : Level.values()) {
            if (level.orientation() == Orientation.TOP_DOWN) {
                continue;
            }
            sideOn++;
            for (EnemyShip.EnemyKind kind : EnemyShip.EnemyKind.values()) {
                Sprite turned = level.enemySprite(kind);
                Sprite plain = upright.enemySprite(kind);
                assertEquals(plain.height(), turned.width(), 1e-9,
                        level + " " + kind + " is not turned: its width should be a top-down height");
                assertEquals(plain.width(), turned.height(), 1e-9,
                        level + " " + kind + " is not turned: its height should be a top-down width");
            }
        }
        assertTrue(sideOn > 0, "no side-on levels found, so this test proved nothing");
    }
}
