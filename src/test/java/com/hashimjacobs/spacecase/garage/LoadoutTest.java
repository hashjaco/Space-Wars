package com.hashimjacobs.spacecase.garage;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutTest {

    @Test
    void encodeThenDecodeRestoresEveryField() {
        Loadout saved = Loadout.stock(1);
        saved.raise(Upgrade.FIREPOWER);
        saved.raise(Upgrade.FIREPOWER);
        saved.raise(Upgrade.HULL);
        saved.unlock(Livery.VOID_VIOLET);
        saved.unlock(Kit.LANCE);

        Loadout reloaded = Loadout.decode(saved.encode(), 1);

        assertEquals(2, reloaded.level(Upgrade.FIREPOWER));
        assertEquals(1, reloaded.level(Upgrade.HULL));
        assertEquals(0, reloaded.level(Upgrade.SPEED));
        assertEquals(Livery.VOID_VIOLET, reloaded.livery());
        assertEquals(Kit.LANCE, reloaded.kit());
        assertTrue(reloaded.owns(Livery.VOID_VIOLET));
        assertTrue(reloaded.owns(Kit.LANCE));
        assertFalse(reloaded.owns(Livery.CHROME), "unbought paint must not come back owned");
    }

    /**
     * A preferences value is user-writable and can be left half-written by a crash. Every one of
     * these has to produce a flyable ship rather than an exception on the way into a level.
     */
    @Test
    void anUnusableCodeYieldsTheStockLoadout() {
        // Note "1,2" is absent on purpose: a short record is legal, not corrupt -- trailing fields
        // default, which is what lets a later version add one without breaking today's saves. It is
        // covered by aShorterOlderRecordLeavesNewFieldsAtTheirDefaults instead.
        String[] rubbish = {null, "", "   ", "nonsense", "1", "9,1,1,1,1,1,0,0,0,0",
                "1,x,1,1,1,1,0,0,0,0", "1,,,,,,,,,", "1,2;3", "-1,1,1,1,1,1,0,0,0,0"};
        for (String code : rubbish) {
            Loadout loadout = Loadout.decode(code, 1);
            assertEquals(Livery.MILITIA_GREEN, loadout.livery(), "for code: " + code);
            assertEquals(Kit.STOCK, loadout.kit(), "for code: " + code);
            assertEquals(0, loadout.level(Upgrade.FIREPOWER), "for code: " + code);
        }
    }

    @Test
    void aShorterOlderRecordLeavesNewFieldsAtTheirDefaults() {
        // Version and the five upgrade levels only: what a future field would find in today's save.
        Loadout loadout = Loadout.decode("1,4,0,0,0,0", 2);

        assertEquals(4, loadout.level(Upgrade.FIREPOWER));
        assertEquals(Livery.MILITIA_GREEN, loadout.livery(), "ordinal 0 reads as the first livery");
        assertEquals(Kit.STOCK, loadout.kit());
    }

    @Test
    void anOutOfRangeLiveryFallsBackToTheSeatsStockHull() {
        Loadout loadout = Loadout.decode("1,0,0,0,0,0,99,99,0,0", 2);

        assertEquals(Livery.CORSAIR_RED, loadout.livery());
        assertEquals(Kit.STOCK, loadout.kit());
    }

    @Test
    void aStoredLevelBeyondTheMaximumIsClamped() {
        Loadout loadout = Loadout.decode("1,999,0,0,0,0,0,0,0,0", 1);

        assertEquals(GameConfig.UPGRADE_MAX_LEVEL, loadout.level(Upgrade.FIREPOWER));
    }

    @Test
    void aLockedLiveryCannotBeWornWithoutBuyingIt() {
        Loadout loadout = Loadout.stock(1);

        loadout.select(Livery.CHROME);

        assertEquals(Livery.MILITIA_GREEN, loadout.livery());
        assertFalse(loadout.owns(Livery.CHROME));
    }

    @Test
    void bothStockHullsAreOwnedFromTheStart() {
        Loadout loadout = Loadout.stock(1);

        assertTrue(loadout.owns(Livery.MILITIA_GREEN));
        assertTrue(loadout.owns(Livery.CORSAIR_RED), "swapping seats must not cost credits");
        assertTrue(loadout.owns(Kit.STOCK));
    }

    @Test
    void anUpgradeStopsAtItsMaximum() {
        Loadout loadout = Loadout.stock(1);
        for (int i = 0; i < GameConfig.UPGRADE_MAX_LEVEL + 5; i++) {
            loadout.raise(Upgrade.SPEED);
        }

        assertEquals(GameConfig.UPGRADE_MAX_LEVEL, loadout.level(Upgrade.SPEED));
        assertTrue(loadout.isMaxed(Upgrade.SPEED));
        assertEquals(0, Upgrade.costFor(GameConfig.UPGRADE_MAX_LEVEL), "a maxed track is not for sale");
    }

    @Test
    void eachUpgradeStepCostsMoreThanTheLast() {
        int previous = 0;
        for (int level = 0; level < GameConfig.UPGRADE_MAX_LEVEL; level++) {
            int cost = Upgrade.costFor(level);
            assertTrue(cost > previous, "step " + level + " should cost more than the one before");
            previous = cost;
        }
    }
}
