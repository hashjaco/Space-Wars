package com.hashimjacobs.spacecase.net;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a board off the wire, with no wire.
 *
 * The parsing is the only part of {@link Cloud} that decides anything -- everything else is one
 * request and its status code, which is the server's business and is covered by
 * {@code server/test/relay.test.mjs}. Same split as {@code LobbyModelTest}: the rules are tested
 * here in microseconds, the transport is tested where the transport is.
 */
class CloudTest {

    @Test
    void aBoardIsReadBestFirstInTheOrderItArrives() {
        List<Cloud.Entry> board = Cloud.parse("NOVA,9000\nACE,5000\nPIP,100");

        assertEquals(3, board.size());
        assertEquals(new Cloud.Entry("NOVA", 9000), board.get(0));
        assertEquals(new Cloud.Entry("PIP", 100), board.get(2));
    }

    @Test
    void anEmptyBoardIsAnEmptyList() {
        assertTrue(Cloud.parse("").isEmpty());
        assertTrue(Cloud.parse("\n\n").isEmpty());
    }

    /**
     * One bad row costs that row and not the board.
     *
     * The same bargain {@code RelayClient} makes with a control frame it does not recognise: a
     * build that refused the whole answer over one line it could not read would be a build that
     * stops showing scores the first time the server learns to say anything new.
     */
    @Test
    void anUnreadableRowIsSkippedRatherThanFatal() {
        List<Cloud.Entry> board = Cloud.parse("NOVA,9000\nWHAT\nACE,not a number\nPIP,100");

        assertEquals(List.of(new Cloud.Entry("NOVA", 9000), new Cloud.Entry("PIP", 100)), board);
    }
}
