package com.hashimjacobs.spacecase.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hashimjacobs.spacecase.asset.Round;
import com.hashimjacobs.spacecase.asset.Sprite;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Creature flagships fire their own art; everything else, and all tuning, stays the galaxy's. */
class OrdnanceTest {

    @Test
    void creaturesFireTheirOwnRound() {
        assertEquals(Sprite.ROUND_SPIKE, Ordnance.artFor(Boss.VENT_CRAWLER));
        assertEquals(Sprite.ROUND_STARDUST, Ordnance.artFor(Boss.DUNE_LEVIATHAN));
    }

    @Test
    void everyOtherFlagshipKeepsItsGalaxysRound() {
        assertEquals(Ordnance.forBoss(Boss.HYDRA).art(), Ordnance.artFor(Boss.HYDRA));
        assertEquals(Ordnance.EMBER.art(), Ordnance.artFor(Boss.SLAG_BARON));
    }

    @Test
    void everyRoundHasAShooter() {
        Set<Round> fired = EnumSet.noneOf(Round.class);
        for (Boss boss : Boss.values()) {
            Round round = Round.of(Ordnance.artFor(boss));
            if (round != null) {
                fired.add(round);
            }
        }
        assertEquals(EnumSet.allOf(Round.class), fired);
    }

    @Test
    void aLevelFiresTheRoundOfItsGalaxysBoss() {
        for (Boss boss : Boss.values()) {
            assertEquals(Ordnance.forBoss(boss), Ordnance.forLevel(boss.ordinal()));
        }
        assertTrue(Ordnance.forLevel(10_000) == Ordnance.values()[Ordnance.values().length - 1]);
    }
}
