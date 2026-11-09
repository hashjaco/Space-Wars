package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PilotedMech;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vaunt's arms and cockpit sit inside the body's box, and the body is first in the enemy list. The
 * sealed hull used to eat every shot aimed at them, which made Caldera Heart unbeatable.
 */
class RigHitTest {

    @Test
    void shotsOnThePartsReachThemThroughTheSealedHull() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        PilotedMech rig = new PilotedMech(Boss.VAUNT, 400, 90, 1);
        world.addEnemy(rig);
        rig.parts().forEach(world::addEnemy);
        rig.update();
        rig.parts().forEach(EnemyShip::update);
        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT);

        // Both arms first: the cockpit stays sealed until they are gone.
        for (EnemyShip part : rig.parts()) {
            double before = part.remainingHealthFraction();
            for (int shot = 0; shot < 2000 && part.isAlive(); shot++) {
                world.addBullet(new Bullet(Sprite.PLAYER_BULLET, part.centerX(), part.centerY(), 0, -10, player, 50));
                collisions.resolve(world);
            }
            assertTrue(part.remainingHealthFraction() < before, part.bossArt() + " never took a hit");
            assertTrue(!part.isAlive(), part.bossArt() + " survived 2000 shots");
        }
        world.addBullet(new Bullet(Sprite.PLAYER_BULLET, rig.centerX(), rig.centerY(), 0, -10, player, 50));
        double body = rig.remainingHealthFraction();
        collisions.resolve(world);
        assertTrue(rig.remainingHealthFraction() < body, "with every part down the body takes the hit");
    }
}
