package com.hashimjacobs.spacecase.garage;

import java.util.List;
import java.util.Set;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        int price = Upgrade.FIREPOWER.costFor(0);

        session.handleKey(KeyCode.SHIFT);

        assertEquals(1, session.loadout(0).level(Upgrade.FIREPOWER));
        assertEquals(500 - price, session.credits(0));
    }

    @Test
    void anUnaffordablePurchaseChangesNothing() {
        GarageSession session = solo(0);

        session.handleKey(KeyCode.SHIFT);

        assertEquals(0, session.loadout(0).level(Upgrade.FIREPOWER));
        assertEquals(0, session.credits(0));
        assertEquals("Not enough credits", session.message(0));
    }

    @Test
    void aMaxedUpgradeStopsChargingAndStopsRising() {
        GarageSession session = solo(100000);
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

        assertEquals(2, session.cursor(0));
        assertEquals(0, session.cursor(1));

        session.handleKey(KeyCode.DOWN);

        assertEquals(2, session.cursor(0));
        assertEquals(1, session.cursor(1));
    }

    @Test
    void buyingInOneBayDoesNotSpendTheOtherPilotsCredits() {
        GarageSession session = pair(500, 500);

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
        assertEquals(rows - 1, session.cursor(0), "up from the top wraps to the bottom");

        session.handleKey(KeyCode.S);
        assertEquals(0, session.cursor(0));
    }

    @Test
    void browsingReachesLockedPaintSoItCanBeBought() {
        GarageSession session = solo(1000);
        moveTo(session, Upgrade.values().length);

        // Step along the catalogue until something unowned is in view, then buy it.
        session.handleKey(KeyCode.D);
        session.handleKey(KeyCode.D);
        GarageSession.Row paint = session.rows(0).get(Upgrade.values().length);
        assertFalse(paint.maxed(), "a locked paint job shows a price rather than reading as owned");

        session.handleKey(KeyCode.SHIFT);

        assertTrue(session.credits(0) < 1000, "buying locked paint costs credits");
        assertTrue(session.loadout(0).owns(session.loadout(0).livery()));
    }

    @Test
    void reselectingPaintAlreadyOwnedIsFree() {
        GarageSession session = solo(1000);
        moveTo(session, Upgrade.values().length);
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
        moveTo(session, GarageSession.launchRow());

        session.handleKey(KeyCode.SHIFT);

        assertTrue(session.done(0));
        assertFalse(session.everyoneDone(), "one pilot launching must not warp the other out");

        for (int i = 0; i < GarageSession.launchRow(); i++) {
            session.handleKey(KeyCode.DOWN);
        }
        session.handleKey(KeyCode.COMMA);

        assertTrue(session.everyoneDone());
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
        moveTo(session, GarageSession.launchRow());
        session.handleKey(KeyCode.SHIFT);

        boolean consumed = session.handleKey(KeyCode.W);

        assertFalse(consumed);
        assertEquals(GarageSession.launchRow(), session.cursor(0));
    }

    private static void moveTo(GarageSession session, int row) {
        for (int i = 0; i < row; i++) {
            session.handleKey(KeyCode.S);
        }
    }
}
