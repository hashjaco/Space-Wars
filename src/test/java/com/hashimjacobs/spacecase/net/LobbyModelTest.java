package com.hashimjacobs.spacecase.net;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Getting into a room, without a room to get into.
 *
 * The whole point of keeping the lobby's rules apart from its screen: the code being entered, the
 * roster changing under you, and the conditions for starting are all exercised here in
 * milliseconds, with no toolkit, no socket and no relay. There is no JavaFX left in it at all.
 *
 * The code arrives from {@code OnScreenKeyboard} as a finished string rather than a keystroke at a
 * time, but the filtering stays here: {@link LobbyModel#codeIsComplete} checks length alone and the
 * screen hands {@link LobbyModel#typed} to the relay, so this is the last guard before the wire.
 */
class LobbyModelTest {

    private static LobbyModel typing(String characters) {
        LobbyModel lobby = new LobbyModel();
        lobby.setTyped(characters);
        return lobby;
    }

    @Test
    void aCodeIsTypedAndCompletesAtSixCharacters() {
        LobbyModel lobby = typing("K7QPM");
        assertEquals("K7QPM", lobby.typed());
        assertFalse(lobby.codeIsComplete());

        lobby.setTyped("K7QPMR");
        assertEquals("K7QPMR", lobby.typed());
        assertTrue(lobby.codeIsComplete());
    }

    /**
     * Pinned as an arithmetic fact rather than a literal, because the length is shared with the
     * relay: this is the assertion that fails if only one side of that pair is ever changed.
     */
    @Test
    void typingPastTheCodeLengthIsIgnored() {
        assertEquals(LobbyModel.CODE_LENGTH, typing("K7QPMRXY").typed().length(),
                "a seventh character has nowhere to go");
    }

    @Test
    void theCodeCanBeClearedBackToEmpty() {
        LobbyModel lobby = typing("AB");
        lobby.setTyped("");
        assertEquals("", lobby.typed(), "the keyboard's CLEAR has to reach the model");
    }

    /**
     * The alphabet drops I, O, 0 and 1 so a code read out loud cannot be mistyped.
     *
     * The on-screen keyboard is built from the same alphabet and cannot offer them, so this is
     * belt and braces -- but it is the belt: {@code codeIsComplete} checks length alone, and the
     * screen hands what is here straight to {@code RelayClient.join}.
     */
    @Test
    void charactersOutsideTheCodeAlphabetAreDropped() {
        assertEquals("", typing("IO01").typed(), "none of them can appear in a room code");
        assertEquals("K7", typing("KI7O").typed(), "and they are dropped, not truncated at");
    }

    @Test
    void lowercaseIsRaisedRatherThanRefused() {
        assertEquals("K7QPMR", typing("k7qpmr").typed());
    }

    @Test
    void theCodeStopsBeingEditableOnceTheSocketIsOpening() {
        LobbyModel lobby = typing("K7QPMR");
        lobby.connecting();

        lobby.setTyped("AAAAAA");
        assertEquals("K7QPMR", lobby.typed(), "the code is already sent; editing is over");
    }

    @Test
    void joiningRecordsTheSlotAndWhoHosts() {
        LobbyModel lobby = new LobbyModel();
        lobby.joined("K7QPMR", 1, List.of(1), true);

        assertEquals(LobbyModel.State.WAITING, lobby.state());
        assertEquals("K7QPMR", lobby.room());
        assertEquals(1, lobby.localSlot());
        assertTrue(lobby.isHost());
    }

    /** Nobody starts alone, and nobody starts before every pilot has said what they are flying. */
    @Test
    void theHostCannotStartAloneOrWithAShipItHasNotHeardAbout() {
        LobbyModel lobby = new LobbyModel();
        lobby.joined("K7QPMR", 1, List.of(1), true);
        lobby.loadout(1, "mine");
        assertFalse(lobby.canStart(), "one pilot is not a multiplayer game");

        lobby.roster(List.of(1, 2));
        assertFalse(lobby.canStart(), "player two has not said what it is flying");

        lobby.loadout(2, "theirs");
        assertTrue(lobby.canStart());
    }

    @Test
    void aGuestNeverStartsTheGame() {
        LobbyModel lobby = new LobbyModel();
        lobby.joined("K7QPMR", 2, List.of(1, 2), false);
        lobby.loadout(1, "one");
        lobby.loadout(2, "two");

        assertFalse(lobby.canStart(), "only the host names the terms");
    }

    /** A peer who leaves must take its ship with it, or the host starts on a stale roster. */
    @Test
    void aDepartingPeerDropsOutOfTheLoadouts() {
        LobbyModel lobby = new LobbyModel();
        lobby.joined("K7QPMR", 1, List.of(1, 2, 3), true);
        lobby.loadout(1, "one");
        lobby.loadout(2, "two");
        lobby.loadout(3, "three");
        assertTrue(lobby.canStart());

        lobby.roster(List.of(1, 3));

        assertEquals(List.of("one", "three"), lobby.loadoutsInSeatOrder(),
                "player two's ship must be gone, and three must not slide into its place");
        assertTrue(lobby.canStart(), "two players left is still a game");
    }

    /** The list travels positionally, so a race in the lobby must not shuffle the ships. */
    @Test
    void loadoutsComeBackInSeatOrderNotArrivalOrder() {
        LobbyModel lobby = new LobbyModel();
        lobby.joined("K7QPMR", 1, List.of(1, 2, 3), true);
        lobby.loadout(3, "three");
        lobby.loadout(1, "one");
        lobby.loadout(2, "two");

        assertEquals(List.of("one", "two", "three"), lobby.loadoutsInSeatOrder());
    }

    @Test
    void aFailureSaysWhatHappenedAndCanBeRetried() {
        LobbyModel lobby = typing("K7QPMR");
        lobby.connecting();
        lobby.failed("that room is full");

        assertEquals(LobbyModel.State.FAILED, lobby.state());
        assertEquals("that room is full", lobby.failure());

        lobby.reset();
        assertEquals(LobbyModel.State.CHOOSING, lobby.state());
        assertEquals("", lobby.typed(), "a failed attempt leaves nothing behind");
        assertEquals("", lobby.failure());
    }

    /** The filter must accept every character the relay can actually issue. */
    @Test
    void everyCharacterTheRelayCanIssueCanBeTyped() {
        for (char c : LobbyModel.CODE_ALPHABET.toCharArray()) {
            assertEquals(String.valueOf(c), typing(String.valueOf(c)).typed(),
                    "'" + c + "' is in the relay's alphabet and must be typeable");
        }
    }
}
