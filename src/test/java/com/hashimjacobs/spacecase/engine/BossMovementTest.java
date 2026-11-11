package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flagship moves around while it fights instead of parking over the player's column. */
class BossMovementTest {

    @Test
    void aFlagshipPatrolsTheLaneAndComesAtYouAndBacksOff() {
        World world = new World(GameMode.SOLO);
        PlayerShip pilot = world.players().get(0);
        EnemyShip boss = new EnemyShip(Boss.CINDER_WARDEN, GameConfig.WIDTH / 2, 90);
        world.addEnemy(boss);
        double left = Double.MAX_VALUE, right = 0, shallow = Double.MAX_VALUE, deep = 0;
        for (int tick = 0; tick < 1500; tick++) {
            boss.tickCombat();
            boss.trackAcross(pilot);
            world.update();
            if (tick > 300) {
                left = Math.min(left, boss.centerX());
                right = Math.max(right, boss.centerX());
                double depth = Orientation.TOP_DOWN.depth(boss.x(), boss.y(), boss.width(), boss.height());
                shallow = Math.min(shallow, depth);
                deep = Math.max(deep, depth);
            }
        }
        assertTrue(right - left > GameConfig.WIDTH / 3, "it should sweep the lane, covered " + (right - left));
        assertTrue(shallow >= 60 && deep <= 180, "depth should stay in its band, was " + shallow + ".." + deep);
        assertTrue(deep - shallow > 40, "and come at you and back off, ranged " + (deep - shallow));
    }
}
