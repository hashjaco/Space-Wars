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

    // ---- Format version 2: upgrades keyed by name rather than by position --------------------

    /**
     * The migration that matters, and the reason version 2 exists.
     *
     * A version 1 record holds five upgrade levels in fixed positions, with the paint job in the
     * field after the last of them. Appending a sixth upgrade would move that boundary, so every
     * existing record would start reading its paint out of an upgrade slot and its ownership masks
     * out of thin air. These assertions name the upgrades, so they keep testing the right thing
     * after the catalogue grows rather than silently following it.
     */
    @Test
    void aVersionOneRecordLandsOnTheUpgradesItWasWrittenFor() {
        // FIREPOWER=4, FIRE_RATE=3, SPEED=2, SHIELDING=1, HULL=0, then ION_BLUE paint and FINS kit.
        Loadout back = Loadout.decode("1,4,3,2,1,0,2,1,7,3", 1);

        assertEquals(4, back.level(Upgrade.FIREPOWER));
        assertEquals(3, back.level(Upgrade.FIRE_RATE));
        assertEquals(2, back.level(Upgrade.SPEED));
        assertEquals(1, back.level(Upgrade.SHIELDING));
        assertEquals(0, back.level(Upgrade.HULL));
        assertEquals(Livery.values()[2], back.livery(), "paint must not be read from an upgrade slot");
        assertEquals(Kit.values()[1], back.kit());
    }

    @Test
    void aVersionTwoRecordRoundTripsEveryUpgrade() {
        Loadout saved = Loadout.stock(1);
        for (Upgrade upgrade : Upgrade.values()) {
            saved.raise(upgrade);
            saved.raise(upgrade);
        }
        saved.unlock(Livery.values()[2]);
        saved.unlock(Kit.values()[1]);

        Loadout back = Loadout.decode(saved.encode(), 1);

        for (Upgrade upgrade : Upgrade.values()) {
            assertEquals(saved.level(upgrade), back.level(upgrade), upgrade + " did not survive");
        }
        assertEquals(saved.livery(), back.livery());
        assertEquals(saved.kit(), back.kit());
    }

    /** A record from a newer build yields everything this one still recognises. */
    @Test
    void anUpgradeThisBuildDoesNotHaveIsSkippedRatherThanRejected() {
        Loadout back = Loadout.decode("2,FIREPOWER=3|WARP_DRIVE=2|HULL=1,0,0,3,1", 1);

        assertEquals(3, back.level(Upgrade.FIREPOWER));
        assertEquals(1, back.level(Upgrade.HULL));
        assertEquals(Livery.stockFor(1), back.livery(), "the rest of the record still applies");
    }

    @Test
    void anUpgradeMissingFromTheBlockReadsAsUnbought() {
        Loadout back = Loadout.decode("2,FIREPOWER=2,0,0,3,1", 1);

        assertEquals(2, back.level(Upgrade.FIREPOWER));
        assertEquals(0, back.level(Upgrade.HULL));
    }

    /** A pilot who has bought nothing should not pay a field per entry in the catalogue. */
    @Test
    void anUnspentLoadoutEncodesToAlmostNothing() {
        String code = Loadout.stock(1).encode();

        assertTrue(code.length() < 20, "a stock ship encoded to " + code.length() + " chars: " + code);
        assertEquals(0, Loadout.decode(code, 1).level(Upgrade.FIREPOWER));
    }

    @Test
    void aFullyBoughtLoadoutStaysWellInsideAPreferencesValue() {
        Loadout maxed = Loadout.stock(1);
        for (Upgrade upgrade : Upgrade.values()) {
            for (int i = 0; i < GameConfig.UPGRADE_MAX_LEVEL; i++) {
                maxed.raise(upgrade);
            }
        }
        for (Livery livery : Livery.values()) {
            maxed.unlock(livery);
        }
        for (Kit kit : Kit.values()) {
            maxed.unlock(kit);
        }

        assertTrue(maxed.encode().length() < 512,
                "encoded to " + maxed.encode().length() + " chars: " + maxed.encode());
    }

    @Test
    void anUnknownVersionYieldsAStockShip() {
        Loadout back = Loadout.decode("9,FIREPOWER=4,2,1,7,3", 1);

        assertEquals(0, back.level(Upgrade.FIREPOWER));
        assertEquals(Livery.stockFor(1), back.livery());
    }
}
