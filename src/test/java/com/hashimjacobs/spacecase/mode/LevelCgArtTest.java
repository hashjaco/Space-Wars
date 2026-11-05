package com.hashimjacobs.spacecase.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hashimjacobs.spacecase.asset.EnemyArt;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Orientation;
import org.junit.jupiter.api.Test;

/** The level-to-CG-art rules: a side-on level only ever gets a turned loop, and special levels keep their own sky. */
class LevelCgArtTest {

    @Test
    void sideOnLevelsResolveTurnedLoopsOrNothing() {
        for (Level level : Level.values()) {
            boolean sideOn = level.orientation() != Orientation.TOP_DOWN;
            for (EnemyShip.EnemyKind kind : EnemyShip.EnemyKind.values()) {
                EnemyArt art = level.enemyArt(kind);
                if (art == null) {
                    continue;
                }
                assertEquals(sideOn, art.name().endsWith("_SIDE"), level + " " + kind);
                assertTrue(art.name().startsWith(level.galaxy().name() + "_" + kind.name()), level + " " + kind);
            }
            assertEquals(level.enemyArts().size(), level.enemyArts().stream().distinct().count());
        }
    }

    @Test
    void cavesSideOnRunsAndFinalesKeepTheirOwnSky() {
        for (Level level : Level.values()) {
            boolean special = level.template() != WorldTemplate.OPEN_FIELD
                    || level.orientation() != Orientation.TOP_DOWN
                    || level.indexInGalaxy() == Galaxy.LEVELS_PER_GALAXY;
            if (special) {
                assertSame(level.layers(), level.skyLayers(), level.name());
            } else {
                assertEquals(3, level.skyLayers().size(), level.name());
            }
        }
    }
}
