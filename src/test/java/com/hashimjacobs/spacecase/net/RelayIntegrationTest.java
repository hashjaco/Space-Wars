package com.hashimjacobs.spacecase.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.engine.Intent;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.SaveSlot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The whole stack over a real socket: two clients, the real relay, real packets.
 *
 * {@link com.hashimjacobs.spacecase.engine.LockstepTest} proves the netcode by wiring two peers to
 * each other's byte queues, which is where nearly every bug actually lives. This proves the one
 * thing that cannot: that the bytes survive an actual WebSocket, that the relay hands them to the
 * right peer, and that the JDK client reassembles them.
 *
 * <b>Skipped unless a relay is running.</b> Start one with {@code cd server && npm run dev}, or
 * point {@code -Drelay.base=https://...} at a deployed one. Skipped rather than failed on purpose:
 * CI has no wrangler, and a suite that goes red over a missing optional dependency is a suite
 * everyone learns to ignore.
 */
class RelayIntegrationTest {

    private static final URI BASE =
            URI.create(System.getProperty("relay.base", "http://127.0.0.1:8787"));
    private static final Duration PATIENCE = Duration.ofSeconds(5);

    /**
     * The other half of the server, which is request and response rather than a socket.
     *
     * Here rather than in {@code CloudTest} for the same reason the packet exchange below is here:
     * what these methods do beyond parsing is speak HTTP to a Durable Object, and the only way to
     * find out whether they do it correctly is to speak it.
     *
     * The identifiers are fresh per run rather than taken from {@code prefs.Account}, because a
     * board row and an uploaded profile both outlive the run that wrote them -- a fixed code would
     * make this a test that only passes the first time it is run.
     */
    @Test
    void aProfileMakesTheRoundTripAndAScorePostsToTheBoard() throws Exception {
        assumeTrue(relayIsUp(), "no relay at " + BASE);

        String syncCode = newSyncCode();
        assertNull(Cloud.pull(BASE, syncCode),
                "a code nobody has uploaded under has no profile, which is not an error");

        String profile = "1\nsaves|checkpoint|2,SOLO,UNDERCITY,3,1\npilots|player1|NOVA";
        Cloud.push(BASE, syncCode, profile);
        assertEquals(profile, Cloud.pull(BASE, syncCode),
                "a profile has to come back byte for byte: it is written straight into somebody's "
                        + "preferences");

        String replacement = "1\npilots|player1|ACE";
        Cloud.push(BASE, syncCode, replacement);
        assertEquals(replacement, Cloud.pull(BASE, syncCode), "an upload replaces, never appends");

        assertTrue(Cloud.submit(BASE, UUID.randomUUID().toString(), GameMode.SOLO, "NOVA", 12_400) >= 1,
                "a posted score comes back with a place on the board");
        assertTrue(Cloud.top(BASE, GameMode.SOLO).stream()
                        .anyMatch(entry -> entry.name().equals("NOVA")),
                "and the board it was posted to is the board that is read back");
    }

    /** Twelve characters of the sync alphabet, which is what {@code prefs.Account} hands out. */
    private static String newSyncCode() {
        StringBuilder code = new StringBuilder();
        while (code.length() < 12) {
            code.append(LobbyModel.CODE_ALPHABET.charAt(
                    (int) (Math.random() * LobbyModel.CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    @Test
    void twoClientsMeetInARoomAndExchangePackets() throws Exception {
        assumeTrue(relayIsUp(), "no relay at " + BASE);

        String room = RelayClient.newRoom(BASE);
        assertEquals(LobbyModel.CODE_LENGTH, room.length(),
                "the relay and the field that types its codes have to agree on the length");

        try (RelayClient one = RelayClient.join(BASE, room);
             RelayClient two = RelayClient.join(BASE, room)) {

            assertEquals(1, one.welcome().slot());
            assertEquals(2, two.welcome().slot());
            assertEquals(room, one.welcome().room());
            assertTrue(one.welcome().isHost(), "the first into the room hosts");
            assertFalse(two.welcome().isHost(), "and only the first");

            waitUntil(() -> two.peers().size() == 2, "peer two never saw peer one arrive");

            Intent intent = new Intent(-0.7071067811865476, 0.25, true);
            one.send(Packet.intent(1, 4242, intent).encode());

            List<byte[]> arrived = new ArrayList<>();
            waitUntil(() -> {
                two.drainTo(arrived::add);
                return !arrived.isEmpty();
            }, "the packet never reached peer two");

            Packet received = Packet.decode(arrived.get(0));
            assertEquals(1, received.playerNumber());
            assertEquals(4242, received.tick());
            // Exactly. A socket that rounded would be a socket that desyncs.
            assertEquals(intent, received.intent(), "the intent must survive the wire bit for bit");

            List<byte[]> echo = new ArrayList<>();
            one.drainTo(echo::add);
            assertTrue(echo.isEmpty(), "a peer must not receive its own packets back");
        }
    }

    /**
     * The lobby handshake, over a real socket: everyone says what they are flying, the host names
     * the terms, and the guest reads back exactly what was sent.
     *
     * {@code LockstepTest} proves the barrier between levels with two peers wired to each other in
     * one JVM. This is the one before the first level, which has no barrier in front of it -- and
     * it is the only place a {@link LevelStart} is put on a wire and taken off again.
     */
    @Test
    void theOpeningTermsSurviveTheRelay() throws Exception {
        assumeTrue(relayIsUp(), "no relay at " + BASE);

        String room = RelayClient.newRoom(BASE);
        try (RelayClient host = RelayClient.join(BASE, room);
             RelayClient guest = RelayClient.join(BASE, room)) {

            waitUntil(() -> host.peers().size() == 2, "the host never saw the guest arrive");

            // Each pilot announces what it is flying, the way the lobby does on joining.
            guest.send(Packet.ready(2, 0, "guest-ship").encode());
            LobbyModel lobby = new LobbyModel();
            lobby.joined(room, 1, List.of(1, 2), true);
            lobby.loadout(1, "host-ship");

            waitUntil(() -> {
                host.drainTo(bytes -> {
                    Packet packet = Packet.decode(bytes);
                    if (packet.kind() == Packet.Kind.READY) {
                        lobby.loadout(packet.playerNumber(), packet.text());
                    }
                });
                return lobby.canStart();
            }, "the host never heard what the guest was flying");

            assertEquals(List.of("host-ship", "guest-ship"), lobby.loadoutsInSeatOrder());

            LevelStart terms = new LevelStart(20260828L, 0, Difficulty.HARD,
                    SaveSlot.forReplay(GameMode.COOP, Level.values()[0]),
                    lobby.loadoutsInSeatOrder());
            host.send(Packet.start(1, terms).encode());

            List<LevelStart> received = new ArrayList<>();
            waitUntil(() -> {
                guest.drainTo(bytes -> {
                    Packet packet = Packet.decode(bytes);
                    if (packet.kind() == Packet.Kind.START) {
                        LevelStart.decode(packet.text()).ifPresent(received::add);
                    }
                });
                return !received.isEmpty();
            }, "the guest never received the opening terms");

            LevelStart back = received.get(0);
            assertEquals(terms.seed(), back.seed(), "the seed decides every spawn in the run");
            assertEquals(terms.difficulty(), back.difficulty(), "the host's difficulty, not the guest's");
            assertEquals(terms.slot().level(), back.slot().level());
            assertEquals(List.of("host-ship", "guest-ship"), back.loadouts(),
                    "and every pilot's ship, in seat order");
        }
    }

    /** A room holds four. The fifth is refused before the upgrade, so joining throws. */
    @Test
    void aFifthPlayerIsTurnedAway() throws Exception {
        assumeTrue(relayIsUp(), "no relay at " + BASE);

        String room = RelayClient.newRoom(BASE);
        List<RelayClient> joined = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                joined.add(RelayClient.join(BASE, room));
            }
            assertEquals(4, joined.size(), "four should fit");

            IOException refused = null;
            try {
                joined.add(RelayClient.join(BASE, room));
            } catch (IOException expected) {
                refused = expected;
            }
            assertTrue(refused != null, "a fifth player should not have been let in");
            assertTrue(refused.getMessage().contains(room),
                    "the failure should name the room: " + refused.getMessage());
        } finally {
            joined.forEach(RelayClient::close);
        }
    }

    private static boolean relayIsUp() {
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpRequest probe = HttpRequest.newBuilder(BASE.resolve("/new"))
                    .timeout(Duration.ofSeconds(2))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            return http.send(probe, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception noRelay) {
            return false;
        }
    }

    /** Polls rather than sleeping a fixed time, so a fast machine is not held back by a slow one. */
    private static void waitUntil(BooleanSupplier done, String complaint) throws InterruptedException {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < deadline) {
            if (done.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError(complaint + " within " + PATIENCE);
    }
}
