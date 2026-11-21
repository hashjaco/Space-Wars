package com.hashimjacobs.spacecase.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.net.Cloud;
import com.hashimjacobs.spacecase.net.GroupListing;
import com.hashimjacobs.spacecase.net.GroupSpec;
import com.hashimjacobs.spacecase.net.LevelStart;
import com.hashimjacobs.spacecase.net.NetworkedLevel;
import com.hashimjacobs.spacecase.net.Packet;
import com.hashimjacobs.spacecase.net.RelayClient;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.SaveSlot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The whole story over real sockets: a pilot opens a group and starts alone; another finds it on
 * the board, joins while it is in flight, is seated at the next level, flies it in step with the
 * host, is credited for helping, and leaves without stopping the host's game.
 *
 * {@link LockstepTest} proves each piece with the network taken out. This puts the network back,
 * because what is left to get wrong is exactly what a wire adds: what the relay says and when,
 * and whether two machines that only share a socket end up in the same fight.
 *
 * Skipped when no relay answers, like {@code RelayIntegrationTest}. Run one with
 * {@code cd server && npx wrangler dev --var MIN_LEVEL_MS:1000}; against a relay with the real
 * thirty-second minimum, pass {@code -Drelay.minLevelMs=30000}.
 */
class OnlineGroupTest {

    private static final URI BASE =
            URI.create(System.getProperty("relay.base", "http://127.0.0.1:8787"));
    private static final long MIN_LEVEL_MS = Long.getLong("relay.minLevelMs", 1000);
    private static final Duration PATIENCE = Duration.ofSeconds(8);
    private static final long SEED = 20261120L;
    private static final Level FIRST = Level.values()[0];

    @Test
    void aHelperJoinsMidRunFliesTheNextLevelIsCreditedAndLeaves() throws Exception {
        assumeTrue(relayIsUp(), "no relay at " + BASE);
        // Fresh per run: a helpers' row and a listing both outlive the run that made them.
        String helperName = "H" + Long.toString(System.nanoTime(), 36).toUpperCase().substring(0, 7);
        String helperId = UUID.randomUUID().toString();

        // ---- A pilot opens a public group for two and starts alone -----------------------------
        String code = Cloud.createGroup(BASE, new GroupSpec(true, 2, FIRST, Difficulty.NORMAL), "HOSTY");
        RelayClient host = RelayClient.join(BASE, code, "HOSTY", UUID.randomUUID().toString());
        RelayClient helper = null;
        try {
            assertTrue(host.isHost());
            assertEquals(2, host.group().orElseThrow().maxPlayers());

            LevelStart opening = new LevelStart(SEED, 0, Difficulty.NORMAL,
                    SaveSlot.forReplay(GameMode.COOP, FIRST), List.of(1), List.of(""));
            LockstepTest.Seat alone = new LockstepTest.Seat(1, opening, null, new ArrayDeque<>());
            host.sendControl(NetworkedLevel.stateLine(FIRST, List.of(1)));
            long levelBegan = System.currentTimeMillis();
            for (int tick = 0; tick < 120; tick++) {
                assertTrue(alone.lockstep.canStep(tick), "a group of one waits for nobody");
                flush(alone, host);
                alone.stepFight();
            }

            // ---- Another pilot finds it on the board, in flight, with a seat free -------------
            waitUntil(() -> listing(code) != null && listing(code).playing(), "the group never showed as in flight");
            GroupListing found = listing(code);
            assertEquals("HOSTY", found.host());
            assertTrue(found.isOpen());
            assertEquals(FIRST, found.level());
            assertTrue(Cloud.groups(BASE, FIRST.galaxy().name(), "NORMAL").orElseThrow()
                    .stream().anyMatch(group -> group.code().equals(code)), "the filters find it");
            assertFalse(Cloud.groups(BASE, FIRST.galaxy().name(), "HARD").orElseThrow()
                    .stream().anyMatch(group -> group.code().equals(code)), "and exclude it");

            helper = RelayClient.join(BASE, code, helperName, helperId);
            RelayClient joined = helper;
            assertFalse(helper.isHost(), "the newcomer is a guest even though they see the host's seat");
            assertTrue(helper.group().orElseThrow().playing(), "and is told a fight is under way");
            waitUntil(() -> helperName.equals(host.names().get(2)), "the host never learned the helper's name");
            assertEquals("HOSTY", helper.names().get(1));

            // ---- They ask to fly, and the host seats them at the next level -------------------
            helper.send(Packet.join(2, "").encode());
            waitUntil(() -> {
                host.drainTo(alone.lockstep::receive);
                return alone.lockstep.joiners().containsKey(2);
            }, "the host never heard the request");

            LevelStart[] rebuilt = new LevelStart[1];
            NetworkedLevel barrier = new NetworkedLevel(alone.lockstep, true, SEED, Difficulty.NORMAL,
                    opening::slot, () -> "",
                    new NetworkedLevel.Roster(List.of(1), alone::nextCheckpoint, host::peers, 2,
                            host::sendControl, terms -> rebuilt[0] = terms, () -> { }));
            // The host's level is won. Nobody helped with this one, so nobody is credited for it.
            Thread.sleep(Math.max(0, MIN_LEVEL_MS + 200 - (System.currentTimeMillis() - levelBegan)));
            barrier.cleared();
            barrier.readyToFight(1, alone.world);
            flush(alone, host);
            LevelStart terms = rebuilt[0];
            assertNotNull(terms, "a pilot joining changes the roster");
            assertEquals(List.of(1, 2), terms.seats());
            assertEquals(FIRST.next(), terms.slot().level());

            LevelStart[] heard = new LevelStart[1];
            waitUntil(() -> {
                joined.drainTo(bytes -> {
                    Packet packet = Packet.decode(bytes);
                    if (packet.kind() == Packet.Kind.START) {
                        heard[0] = LevelStart.decode(packet.text()).orElseThrow();
                    }
                });
                return heard[0] != null;
            }, "the helper never received the terms");
            assertEquals(terms.encode(), heard[0].encode());

            // ---- Both build the level from the terms and fly it in step -----------------------
            LockstepTest.Seat one = new LockstepTest.Seat(1, terms, alone.lockstep, alone.outbox);
            LockstepTest.Seat two = new LockstepTest.Seat(2, heard[0], null, new ArrayDeque<>());
            host.sendControl(NetworkedLevel.stateLine(terms.slot().level(), terms.seats()));
            long secondLevelBegan = System.currentTimeMillis();
            int tick = terms.resumeTick();
            for (int end = tick + 180; tick < end; tick++) {
                int now = tick;
                waitUntil(() -> {
                    host.drainTo(one.lockstep::receive);
                    joined.drainTo(two.lockstep::receive);
                    boolean ready = one.lockstep.canStep(now) & two.lockstep.canStep(now);
                    flush(one, host);
                    flush(two, joined);
                    return ready;
                }, "the two machines stalled at tick " + tick);
                one.stepFight();
                two.stepFight();
                assertEquals(one.world.checksum(), two.world.checksum(), "host and helper disagreed at " + tick);
            }
            waitUntil(() -> listing(code) != null && listing(code).level() == FIRST.next()
                    && !listing(code).isOpen(), "the board never showed the new level, full");

            // ---- The level is won, and the relay writes the helper down -----------------------
            Thread.sleep(Math.max(0, MIN_LEVEL_MS + 200 - (System.currentTimeMillis() - secondLevelBegan)));
            host.sendControl("cleared");
            waitUntil(() -> Cloud.helpers(BASE).stream().anyMatch(row ->
                    row.name().equals(helperName) && row.score() == 100), "the helper was never credited");
            assertFalse(Cloud.helpers(BASE).stream().anyMatch(row -> row.name().equals("HOSTY") && row.score() == 0),
                    "the host is not on the table for their own group");

            // ---- The helper leaves, and the host flies on without a pause ---------------------
            two.lockstep.leave();
            flush(two, joined);
            helper.leave();
            helper = null;
            waitUntil(() -> {
                host.drainTo(one.lockstep::receive);
                return host.left().contains(2) && one.lockstep.gone().contains(2);
            }, "the host never heard the helper leave");
            for (int end = tick + 60; tick < end; tick++) {
                assertTrue(one.lockstep.canStep(tick), "the host was held at " + tick + " by a pilot who left");
                flush(one, host);
                one.stepFight();
            }
            waitUntil(() -> listing(code) != null && listing(code).isOpen(), "the seat never showed as free");

            // ---- The host leaves, and the group is gone ---------------------------------------
            host.leave();
            waitUntil(() -> listing(code) == null, "the listing outlived its host");
        } finally {
            if (helper != null) {
                helper.close();
            }
            host.close();
        }
    }

    /** Hands everything a machine has produced to its socket, as {@code relay::send} would have. */
    private static void flush(LockstepTest.Seat seat, RelayClient relay) {
        while (!seat.outbox.isEmpty()) {
            relay.send(seat.outbox.poll());
        }
    }

    private static GroupListing listing(String code) {
        return Cloud.groups(BASE, "", "").orElse(List.of()).stream()
                .filter(group -> group.code().equals(code)).findFirst().orElse(null);
    }

    private static boolean relayIsUp() {
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpRequest probe = HttpRequest.newBuilder(BASE.resolve("/groups"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return http.send(probe, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception noRelay) {
            return false;
        }
    }

    private static void waitUntil(BooleanSupplier done, String complaint) throws InterruptedException {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < deadline) {
            if (done.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError(complaint);
    }
}
