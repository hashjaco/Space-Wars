package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ships that arrive on the same tick must not fire on the same tick.
 *
 * The fault this pins is invisible in every other test and obvious in play: a wave group goes up
 * whole, so its ships used to share one firing phase and nothing ever re-based it. Five scouts in
 * a rank fired as one gun for the length of the level -- a line of bullets with no gap in it
 * rather than a stream with gaps, which is the difference between a wave you read and a wave you
 * absorb.
 *
 * Driven through {@code tickWeapon} rather than by reading the timer, because the phase is private
 * and the thing worth asserting is when a ship actually shoots.
 */
class EnemyFirePhaseTest {

    /** Long enough that every phase in the spread lands inside one cycle, and stays readable. */
    private static final int COOLDOWN = 84;

    /** The tick a ship first fires, or -1 if it somehow never does. */
    private static int firstShotTick(EnemyShip enemy) {
        for (int tick = 0; tick < COOLDOWN * 2; tick++) {
            if (enemy.tickWeapon(COOLDOWN)) {
                return tick;
            }
        }
        return -1;
    }

    private static Set<Integer> phasesOf(List<EnemyShip> ships) {
        Set<Integer> phases = new HashSet<>();
        for (EnemyShip ship : ships) {
            phases.add(firstShotTick(ship));
        }
        return phases;
    }

    @Test
    void aRankOfOneArchetypeDoesNotFireAsOneGun() {
        World world = new World(GameMode.SOLO);
        List<EnemyShip> rank = new ArrayList<>();
        // The shape of RUST_CANYON's first wave: five scouts, one depth, one tick.
        for (int i = 0; i < 5; i++) {
            EnemyShip scout = new EnemyShip(
                    EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 140 + i * 180, 0);
            world.addEnemy(scout);
            rank.add(scout);
        }

        assertEquals(rank.size(), phasesOf(rank).size(),
                "scouts launched together fired as one gun");
    }

    /**
     * The same guarantee for a flagship's heads, which arrive together for the same reason.
     *
     * Worth its own case because the heads set an offset in their own constructor before
     * {@code World.addEnemy} ever sees them, so this is the path where the arrival spread has to
     * compose with an offset that is already there rather than replace it.
     */
    @Test
    void aFlagshipsHeadsDoNotFireAsOneMouth() {
        World world = new World(GameMode.SOLO);
        EnemyShip torso = new EnemyShip(Boss.HYDRA, 400, 90, 1);
        world.addEnemy(torso);
        for (EnemyShip head : torso.parts()) {
            world.addEnemy(head);
        }

        assertEquals(torso.parts().size(), phasesOf(torso.parts()).size(),
                "the heads fired as one mouth");
    }
}
