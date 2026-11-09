package com.hashimjacobs.spacecase.garage;

import java.util.List;
import java.util.Set;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KeyCode is a plain enum, so none of this needs a JavaFX toolkit -- the same reason
 * {@code scene.MenuNavigatorTest} runs headless.
 */
class GarageSessionTest {

    private static GarageSession.Seat playerOne() {
        return new GarageSession.Seat("ONE", Set.of(KeyCode.W), Set.of(KeyCode.S),
                Set.of(KeyCode.A), Set.of(KeyCode.D), Set.of(KeyCode.SHIFT));
    }

    private static GarageSession.Seat playerTwo() {
        return new GarageSession.Seat("TWO", Set.of(KeyCode.UP), Set.of(KeyCode.DOWN),
                Set.of(KeyCode.LEFT), Set.of(KeyCode.RIGHT), Set.of(KeyCode.COMMA));
    }

    private static GarageSession solo(int credits) {
        return new GarageSession(List.of(playerOne()), List.of(credits),
                List.of(Loadout.stock(1)));
    }

    private static GarageSession pair(int creditsOne, int creditsTwo) {
        return new GarageSession(List.of(playerOne(), playerTwo()),
                List.of(creditsOne, creditsTwo),
                List.of(Loadout.stock(1), Loadout.stock(2)));
    }

    @Test
    void buyingChargesCreditsAndRaisesTheLevel() {
        GarageSession session = solo(500);
        toUpgrades(session);
        int price = Upgrade.FIREPOWER.costFor(0);

        session.handleKey(KeyCode.SHIFT);

        assertEquals(1, session.loadout(0).level(Upgrade.FIREPOWER));
        assertEquals(500 - price, session.credits(0));
    }

    @Test
    void anUnaffordablePurchaseChangesNothing() {
        GarageSession session = solo(0);
        toUpgrades(session);

        session.handleKey(KeyCode.SHIFT);

        assertEquals(0, session.loadout(0).level(Upgrade.FIREPOWER));
        assertEquals(0, session.credits(0));
        assertEquals("Not enough credits", session.message(0));
    }

    @Test
    void aMaxedUpgradeStopsChargingAndStopsRising() {
        GarageSession session = solo(100000);
        toUpgrades(session);
        for (int i = 0; i < GameConfig.UPGRADE_MAX_LEVEL; i++) {
            session.handleKey(KeyCode.SHIFT);
        }
        int spent = session.credits(0);

        session.handleKey(KeyCode.SHIFT);

        assertEquals(GameConfig.UPGRADE_MAX_LEVEL, session.loadout(0).level(Upgrade.FIREPOWER));
        assertEquals(spent, session.credits(0), "a maxed track must not keep taking credits");
    }

    @Test
    void eachPlayersKeysDriveOnlyTheirOwnBay() {
        GarageSession session = pair(500, 500);

        // Player one moves down twice; player two should not have moved at all.
        session.handleKey(KeyCode.S);
        session.handleKey(KeyCode.S);

        assertEquals(1, session.cursor(0));
        assertEquals(-1, session.cursor(1), "still on the tab strip");

        session.handleKey(KeyCode.DOWN);

        assertEquals(1, session.cursor(0));
        assertEquals(0, session.cursor(1));
    }

    @Test
    void buyingInOneBayDoesNotSpendTheOtherPilotsCredits() {
        GarageSession session = pair(500, 500);
        toUpgrades(session);

        session.handleKey(KeyCode.SHIFT);

        assertTrue(session.credits(0) < 500);
        assertEquals(500, session.credits(1));
        assertEquals(0, session.loadout(1).level(Upgrade.FIREPOWER));
    }

    @Test
    void theCursorWrapsAtBothEnds() {
        GarageSession session = solo(0);
        int rows = session.rows(0).size();

        session.handleKey(KeyCode.W);
        assertEquals(rows - 1, session.cursor(0), "up from the tab strip wraps to LAUNCH");

        session.handleKey(KeyCode.S);
        assertEquals(-1, session.cursor(0), "and down from LAUNCH back to the strip");
    }

    /** The paint row on the Ship tab, found by its label rather than counted. */
    private static int paintRow() {
        List<GarageSession.Row> rows = solo(0).rows(0);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).label().equals("Paint")) {
                return i;
            }
        }
        throw new AssertionError("no paint row on the Ship tab");
    }

    private static int launchRow(GarageSession session) {
        return session.rows(0).size() - 1;
    }

    @Test
    void browsingReachesLockedPaintSoItCanBeBought() {
        GarageSession session = solo(1000);
        moveTo(session, paintRow());

        // Step along the catalogue until something unowned is in view, then buy it.
        session.handleKey(KeyCode.D);
        session.handleKey(KeyCode.D);
        GarageSession.Row paint = session.rows(0).get(paintRow());
        assertFalse(paint.maxed(), "a locked paint job shows a price rather than reading as owned");

        session.handleKey(KeyCode.SHIFT);

        assertTrue(session.credits(0) < 1000, "buying locked paint costs credits");
        assertTrue(session.loadout(0).owns(session.loadout(0).livery()));
    }

    @Test
    void reselectingPaintAlreadyOwnedIsFree() {
        GarageSession session = solo(1000);
        moveTo(session, paintRow());
        session.handleKey(KeyCode.D);
        session.handleKey(KeyCode.SHIFT);
        int afterBuying = session.credits(0);

        // Browse away and back, then buy the same thing again.
        session.handleKey(KeyCode.A);
        session.handleKey(KeyCode.D);
        session.handleKey(KeyCode.SHIFT);

        assertEquals(afterBuying, session.credits(0), "paint already owned must not be charged twice");
    }

    @Test
    void theSessionIsDoneOnlyOnceEveryBayHasLaunched() {
        GarageSession session = pair(0, 0);
        moveTo(session, launchRow(session));

        session.handleKey(KeyCode.SHIFT);

        assertTrue(session.done(0));
        assertFalse(session.everyoneDone(), "one pilot launching must not warp the other out");

        for (int i = 0; i <= launchRow(session); i++) {
            session.handleKey(KeyCode.DOWN);
        }
        session.handleKey(KeyCode.COMMA);

        assertTrue(session.everyoneDone());
    }

    @Test
    void playerTwosPadFireBuysWithoutLaunchingAnybody() {
        // A pad sends the player's fire key and then a menu confirm tap; player two's confirm is
        // Enter, which used to end every bay -- so buying from a pad warped the crew out instead.
        GarageSession session = pair(500, 500);
        toUpgrades(session);
        int price = Upgrade.FIREPOWER.costFor(0);

        session.handleKey(KeyCode.COMMA);
        session.handleKey(KeyCode.ENTER);

        assertEquals(1, session.loadout(1).level(Upgrade.FIREPOWER));
        assertEquals(500 - price, session.credits(1));
        assertFalse(session.everyoneDone(), "the confirm tap must not launch the garage");
        assertFalse(session.done(1));
    }

    @Test
    void escapeEndsEveryBaySoNobodyIsTrapped() {
        GarageSession session = pair(0, 0);

        session.handleKey(KeyCode.ESCAPE);

        assertTrue(session.everyoneDone());
    }

    @Test
    void anEmptySessionIsAlreadyDone() {
        GarageSession session = new GarageSession(List.of(), List.of(), List.of());

        assertTrue(session.everyoneDone(), "no bays means nothing to wait for");
    }

    @Test
    void aKeyBelongingToNoBayIsNotConsumed() {
        GarageSession session = solo(0);

        assertFalse(session.handleKey(KeyCode.F11), "fullscreen must still reach the window");
    }

    @Test
    void aFinishedBayIgnoresFurtherInput() {
        GarageSession session = solo(500);
        moveTo(session, launchRow(session));
        session.handleKey(KeyCode.SHIFT);

        boolean consumed = session.handleKey(KeyCode.W);

        assertFalse(consumed);
        assertEquals(launchRow(session), session.cursor(0));
    }

    /** Both bays across to the Weapons tab and down onto its first row, FIREPOWER. */
    private static void toUpgrades(GarageSession session) {
        session.handleKey(KeyCode.D);
        session.handleKey(KeyCode.RIGHT);
        session.handleKey(KeyCode.S);
        session.handleKey(KeyCode.DOWN);
    }

    @Test
    void theLooksComeFirstAndBrowsingAKitPreviewsIt() {
        GarageSession session = solo(0);
        assertEquals("Airframe", session.rows(0).get(0).label());
        assertEquals("Paint", session.rows(0).get(1).label());
        assertEquals("Body kit", session.rows(0).get(2).label());
        moveTo(session, 2);
        session.handleKey(KeyCode.D);
        assertNotEquals(Kit.STOCK, session.look(0).kit(), "the stand shows the kit being browsed");
        assertFalse(session.look(0).owned(), "and says it is only a preview");
        assertEquals(Kit.STOCK, session.loadout(0).kit(), "nothing is worn until it is bought");
        session.handleKey(KeyCode.S);
        assertEquals(Kit.STOCK, session.look(0).kit(), "moving off the row puts the worn kit back");
    }

    // ---- tabs ------------------------------------------------------------------------------------

    @Test
    void leftAndRightOnTheStripChangeTabAndWrap() {
        GarageSession session = solo(0);
        assertEquals(GarageSession.Tab.SHIP, session.tab(0));
        session.handleKey(KeyCode.D);
        assertEquals(GarageSession.Tab.WEAPONS, session.tab(0));
        assertEquals(Upgrade.FIREPOWER, session.rows(0).get(0).upgrade());
        session.handleKey(KeyCode.A);
        session.handleKey(KeyCode.A);
        assertEquals(GarageSession.Tab.SYSTEMS, session.tab(0), "left from the first tab wraps to the last");
        session.handleKey(KeyCode.SHIFT);
        assertFalse(session.done(0), "fire on the strip buys and launches nothing");
    }

    @Test
    void everyUpgradeIsOnExactlyOneTabAndEveryTabEndsInLaunch() {
        GarageSession session = solo(0);
        java.util.Map<Upgrade, Integer> seen = new java.util.EnumMap<>(Upgrade.class);
        for (GarageSession.Tab tab : GarageSession.Tab.values()) {
            List<GarageSession.Row> rows = session.rows(0);
            assertEquals("LAUNCH", rows.get(rows.size() - 1).label(), tab + " ends in LAUNCH");
            for (GarageSession.Row row : rows) {
                if (row.upgrade() != null) {
                    seen.merge(row.upgrade(), 1, Integer::sum);
                    assertTrue(tab.holds(row.upgrade()));
                }
            }
            session.handleKey(KeyCode.D);
        }
        for (Upgrade upgrade : Upgrade.values()) {
            assertEquals(1, seen.getOrDefault(upgrade, 0), upgrade + " is listed once");
        }
    }

    /** Down from the tab strip onto row {@code row} of the current tab. */
    private static void moveTo(GarageSession session, int row) {
        for (int i = 0; i <= row; i++) {
            session.handleKey(KeyCode.S);
        }
    }
}
