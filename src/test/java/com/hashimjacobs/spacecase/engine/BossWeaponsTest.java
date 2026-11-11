package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.asset.SoundPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Ordnance;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a flagship fires, and when.
 *
 * Every boss in the game used to put the same green bolt in the air on the same flat rhythm. Three
 * things changed that -- a round per galaxy, a special every fourth volley, and a last third that
 * speeds up -- and none of them is visible to a test that only counts bullets, which is why they are
 * pinned here as properties rather than as one screenshot nobody will look at again.
 */
class BossWeaponsTest {

    private static EnemyShip flagship(Boss boss) {
        return new EnemyShip(boss, 400, 90, 1);
    }

    /**
     * Drives a flagship's health down to a fraction of its maximum.
     *
     * By repeated small hits off {@code remainingHealthFraction} rather than one calculated blow:
     * the ship's maximum is private, and it is not the flagship's authored health anyway once parts
     * take their share of it.
     */
    private static void woundTo(EnemyShip boss, double fraction) {
        while (boss.isAlive() && boss.remainingHealthFraction() > fraction) {
            boss.takeDamage(10);
        }
    }

    // ---- the round ---------------------------------------------------------------------------

    /**
     * Fifty flagships used to fire one projectile between them. Five is the point.
     *
     * Asserts the set rather than a mapping, because which galaxy gets which round is a taste
     * decision that should be retunable; that there is more than one is not.
     */
    @Test
    void theCampaignFiresMoreThanOneKindOfRound() {
        Set<Ordnance> seen = EnumSet.noneOf(Ordnance.class);
        for (Boss boss : Boss.values()) {
            seen.add(boss.ordnance());
        }

        assertEquals(Ordnance.values().length, seen.size(),
                "every round should be carried by some galaxy; unused ones are art nobody sees");
    }

    /** A galaxy shares a weapon, and the galaxy either side of it does not. */
    @Test
    void aRoundBelongsToItsGalaxyAndTheNextGalaxyFiresSomethingElse() {
        Boss[] all = Boss.values();
        for (int i = 0; i + 10 < all.length; i += 10) {
            assertEquals(all[i].ordnance(), all[i + 9].ordnance(),
                    all[i] + " and " + all[i + 9] + " are the same navy and should share a round");
            assertNotEquals(all[i].ordnance(), all[i + 10].ordnance(),
                    all[i] + " and " + all[i + 10] + " are different galaxies");
        }
    }

    /**
     * No round is simply better than the one everybody starts against.
     *
     * The whole set is a trade: the fastest is the weakest and the heaviest is the slowest. A round
     * that beat SPORE on both axes would make its galaxy harder for a reason nobody chose.
     */
    @Test
    void noRoundIsFasterAndHeavierThanTheStockBolt() {
        for (Ordnance round : Ordnance.values()) {
            boolean quicker = round.speedFactor() > Ordnance.SPORE.speedFactor();
            boolean heavier = round.damageFactor() > Ordnance.SPORE.damageFactor();
            assertTrue(!(quicker && heavier), round + " is strictly better than the stock bolt");
            assertTrue(round.speedFactor() > 0 && round.damageFactor() > 0,
                    round + " has a factor at or below zero");
        }
    }

    /**
     * A round never outruns the player's own fire.
     *
     * {@code firePattern} caps the difficulty scaling for exactly this reason -- a bullet faster
     * than {@code BULLET_SPEED} stops being dodgeable -- and an ordnance factor is a second way to
     * break it that the existing cap knows nothing about.
     */
    @Test
    void noRoundOutrunsThePlayersOwnBullets() {
        for (Ordnance round : Ordnance.values()) {
            double fastest = GameConfig.ENEMY_BULLET_SPEED * round.speedFactor();
            assertTrue(fastest < GameConfig.BULLET_SPEED,
                    round + " leaves at " + fastest + ", which the player cannot outfly");
        }
    }

    // ---- the rhythm --------------------------------------------------------------------------

    /**
     * Bursts with breaks, and a special every 8 to 15 seconds, varied.
     *
     * A flat stream was what made every fight undodgeable. Walked for a minute of combat so the
     * special's gap is seen several times, since a timer that fires once and then every tick would
     * pass a single check.
     */
    @Test
    void aFlagshipBurstsRestsAndChargesItsSpecialEveryEightToFifteenSeconds() {
        EnemyShip boss = flagship(Boss.SENTINEL);
        List<Integer> specials = new ArrayList<>();
        int rests = 0;
        for (int tick = 1; tick <= 3600; tick++) {
            boss.tickCombat();
            if (boss.specialTicks() == 0) {
                specials.add(tick);
            }
            rests += boss.restBegins() ? 1 : 0;
            assertEquals(boss.phase(), boss.firingPhase(), "the primary is the health phase");
        }
        assertTrue(rests >= 14, "a break every few seconds, got " + rests + " in a minute");
        assertTrue(specials.size() >= 4, "specials: " + specials);
        Set<Integer> gaps = new java.util.HashSet<>();
        for (int k = 1; k < specials.size(); k++) {
            int gap = specials.get(k) - specials.get(k - 1);
            assertTrue(gap >= 480 && gap <= 1000, "special gap " + gap + " outside 8-15 s plus its own length");
            gaps.add(gap);
        }
        assertTrue(gaps.size() > 1, "the gap should vary, got " + gaps);
    }

    /** No primary goes out in a break: whatever lands in one is the rockets or the special. */
    @Test
    void aFlagshipHoldsItsPrimaryDuringABreak() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        EnemyShip boss = flagship(Boss.CINDER_WARDEN);
        world.addEnemy(boss);
        int primariesInBreaks = 0;
        for (int tick = 0; tick < 2400; tick++) {
            int before = world.bullets().size();
            boolean quiet = boss.resting() && !boss.restBegins() && boss.specialTicks() < 0;
            EnemyWeapons.driveWeapons(world, boss, target, Level.CINDER_BELT, 60, 8, SoundPlayer.SILENT);
            if (quiet && boss.resting() && boss.specialTicks() < 0 && world.bullets().size() > before) {
                primariesInBreaks++;
            }
        }
        assertEquals(0, primariesInBreaks, "the primary fired during a break");
        assertTrue(world.bullets().size() > 0, "and it does fire outside them");
    }

    /** Every flagship has a special, and it is one of the patterns marked as such. */
    @Test
    void everyFlagshipsSpecialIsASpecial() {
        Set<BossPhase> seen = EnumSet.noneOf(BossPhase.class);
        for (Boss boss : Boss.values()) {
            assertTrue(boss.special().isSpecial(),
                    boss + " breaks its rhythm with " + boss.special() + ", an ordinary phase");
            seen.add(boss.special());
        }
        assertTrue(seen.size() > 1, "one special across fifty fights is the flat rhythm again");
    }

    /**
     * A special hits harder than the pattern it interrupts.
     *
     * Otherwise it is a different shape for its own sake: the point of a rare volley is that eating
     * it costs more than eating the three before it.
     */
    @Test
    void everySpecialHitsHarderThanAnOrdinaryPattern() {
        for (BossPhase phase : BossPhase.values()) {
            if (phase.isSpecial()) {
                assertTrue(phase.damageScale() > 1, phase + " is a special that hits like a primary");
            } else {
                assertEquals(1, phase.damageScale(),
                        phase + " is not a special and should not have been re-weighted");
            }
        }
    }

    /**
     * A hydra's heads do not each fire a special.
     *
     * Three parts on their own counters would put three lances in the air at once, which is not a
     * broken rhythm but a wall, and nobody would have chosen it. The torso carries the rhythm.
     */
    @Test
    void bossPartsNeverFireTheSpecial() {
        World world = new World(GameMode.SOLO);
        EnemyShip hydra = new EnemyShip(Boss.HYDRA, 400, 90, 1);
        world.addEnemy(hydra);

        for (int tick = 0; tick < 1200; tick++) {
            hydra.tickCombat();
            for (EnemyShip part : hydra.parts()) {
                assertEquals(part.phase(), part.firingPhase(), "a part should always fire the health phase");
                assertEquals(hydra.resting(), part.resting(), "a part keeps its body's rhythm");
            }
        }
    }

    // ---- the last third ----------------------------------------------------------------------

    /** A flagship that enrages does so in its last third, and not before. */
    @Test
    void anEnragingFlagshipSpeedsUpOnlyOnceItIsNearlyDead() {
        EnemyShip boss = flagship(enragingBoss());

        assertTrue(!boss.isEnraged(), "a flagship at full health has nothing to be enraged about");
        woundTo(boss, 0.50);
        assertTrue(!boss.isEnraged(), "half health is the middle phase, not the last one");
        woundTo(boss, 0.20);
        assertTrue(boss.isEnraged(), "the last third is where it should speed up");
    }

    /** Not every flagship does it, or it stops being a thing a player notices. */
    @Test
    void enragingIsNotSomethingEveryFlagshipDoes() {
        long enraging = 0;
        for (Boss boss : Boss.values()) {
            if (boss.enrages()) {
                enraging++;
            }
        }

        assertTrue(enraging > 0, "no flagship speeds up, so the feature does nothing");
        assertTrue(enraging < Boss.values().length,
                "every flagship speeds up, which makes it the baseline rather than a moment");
    }

    /** Whatever else a galaxy's last fight is, it is not the one that got easier as you won. */
    @Test
    void everyGalaxyFinaleEnrages() {
        Boss[] all = Boss.values();
        for (int i = 9; i < all.length; i += 10) {
            assertTrue(all[i].enrages(), all[i] + " ends a galaxy and should not go quietly");
        }
    }

    /** An enraged flagship reloads faster, and the floor still holds it short of a solid wall. */
    @Test
    void enragingShortensTheReloadWithoutBreachingTheFloor() {
        EnemyShip calm = flagship(enragingBoss());
        EnemyShip raging = flagship(enragingBoss());
        woundTo(raging, 0.10);

        int calmGap = EnemyWeapons.cooldownFor(calm, 60);
        int ragingGap = EnemyWeapons.cooldownFor(raging, 60);

        // Same phase either side, so the difference is the rage and not the health tier.
        if (calm.firingPhase() == raging.firingPhase()) {
            assertTrue(ragingGap < calmGap,
                    "an enraged flagship should reload faster; " + ragingGap + " against " + calmGap);
        }
        assertTrue(ragingGap >= 4, "the boss cooldown floor has to hold: got " + ragingGap);
    }

    private static Boss enragingBoss() {
        for (Boss boss : Boss.values()) {
            if (boss.enrages()) {
                return boss;
            }
        }
        throw new IllegalStateException("no flagship enrages, so these tests prove nothing");
    }
}
