package com.hashimjacobs.spacecase.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.garage.Upgrade;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.net.LevelStart;
import com.hashimjacobs.spacecase.net.Lockstep;
import com.hashimjacobs.spacecase.net.NetworkedLevel;
import com.hashimjacobs.spacecase.net.Packet;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.SaveSlot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two machines, one fight: the whole netcode design, with the sockets taken out.
 *
 * Each Peer below is a whole game -- its own World, its own director, its own controllers -- and
 * the two are wired to each other's inboxes rather than to a network. That is what {@link Lockstep}
 * taking a byte sink and a byte source instead of a socket buys: everything that can actually be
 * wrong here (the codec, the ring, the gate, the delay, who sends what and when) is wrong the same
 * way in one JVM as it is across a continent, and is found in milliseconds rather than by two
 * people with two phones.
 *
 * In the engine package rather than net, because it drives the simulation the way GameLoop does and
 * needs {@link InputState#press} and {@link EnemyWeapons}, both package-private here. The classes
 * under test are public.
 *
 * {@link DeterminismTest} proves one machine reproduces itself. This proves two agree.
 */
class LockstepTest {

    private static final int TICKS = 900;
    private static final long SEED = 4242;

    @Test
    void twoPeersStepTheSameFightAndAgreeOnEveryTick() {
        Match match = new Match();

        for (int tick = 0; tick < TICKS; tick++) {
            match.advanceOneTick(tick);
            assertEquals(match.one.world.checksum(), match.two.world.checksum(),
                    "the two peers disagreed at tick " + tick);
        }

        assertNull(match.one.lockstep.divergence(), "peer one reported a divergence");
        assertNull(match.two.lockstep.divergence(), "peer two reported a divergence");
    }

    /**
     * Not padding, twice over: it proves the fight is busy enough for agreement to mean something,
     * and that the peers are running a game rather than two idle worlds.
     */
    @Test
    void theAgreedFightIsActuallyAFight() {
        Match match = new Match();
        for (int tick = 0; tick < TICKS; tick++) {
            match.advanceOneTick(tick);
        }

        World world = match.one.world;
        assertEquals(TICKS, world.tick(), "every tick should have run");
        assertEquals(2, world.players().size(), "co-op fields two ships");
        assertTrue(world.enemies().size() + world.asteroids().size() > 0,
                "an empty sky would agree trivially");
        assertNotEquals(0, world.checksum(), "a zero checksum would agree trivially");
    }

    /**
     * The gate has to gate. A peer that runs a tick before the other has spoken for it is not in
     * lockstep, and the first slow network turns that into a desync.
     */
    @Test
    void aPeerWaitsUntilTheOtherHasSentThatTick() {
        Match match = new Match();

        assertFalse(match.one.lockstep.canStep(0),
                "peer one must not run tick 0 before peer two has sent its input for it");

        match.two.lockstep.canStep(0);
        match.deliver();

        assertTrue(match.one.lockstep.canStep(0), "with both inputs in hand the tick may run");
    }

    /** Input goes out INPUT_DELAY_TICKS ahead of its tick, or there is no travel time at all. */
    @Test
    void inputIsSentAheadOfTheTickItIsFor() {
        Match match = new Match();
        match.one.lockstep.canStep(0);

        int furthest = -1;
        for (byte[] bytes : match.one.outbox) {
            Packet packet = Packet.decode(bytes);
            if (packet.kind() == Packet.Kind.INTENT) {
                furthest = Math.max(furthest, packet.tick());
            }
        }
        assertEquals(Lockstep.INPUT_DELAY_TICKS, furthest,
                "asking to run tick 0 should already have sent input for tick "
                        + Lockstep.INPUT_DELAY_TICKS);
    }

    /**
     * A frame that earns two steps must produce two intents. Producing per frame rather than per
     * tick leaves a hole at the second one, and every peer stalls on it forever.
     */
    @Test
    void skippingAheadStillProducesEveryInterveningTick() {
        Match match = new Match();
        match.one.lockstep.canStep(0);
        match.one.outbox.clear();
        match.one.lockstep.canStep(3);

        List<Integer> sent = new ArrayList<>();
        for (byte[] bytes : match.one.outbox) {
            sent.add(Packet.decode(bytes).tick());
        }
        assertEquals(List.of(4, 5, 6), sent, "ticks 4 to 6 must all have been produced");
    }

    /**
     * The other half of the checksum: it has to fire when the worlds really do differ.
     *
     * Without this the tick guard added to {@code compare} could be tightened until nothing ever
     * matched, and every test above would still pass.
     */
    @Test
    void aRealDisagreementIsReported() {
        Match match = new Match();
        // Captured rather than assumed: publishChecksum only fires every CHECKSUM_EVERY_TICKS, so
        // the tick injected below has to be one peer one actually fingerprinted.
        long ourSixtieth = 0;
        for (int tick = 0; tick < 120; tick++) {
            match.advanceOneTick(tick);
            if (tick == 60) {
                ourSixtieth = match.one.world.checksum();
            }
        }
        assertNull(match.one.lockstep.divergence(), "the two agree so far");

        // One bit different, which is all a divergence ever is.
        match.one.lockstep.receive(Packet.checksum(2, 60, ourSixtieth ^ 1L).encode());

        Lockstep.Divergence divergence = match.one.lockstep.divergence();
        assertNotNull(divergence, "a disagreeing peer must be reported");
        assertEquals(60, divergence.tick(), "and reported against the tick it disagreed on");
        assertEquals(2, divergence.peerPlayer());
        assertEquals(ourSixtieth, divergence.localChecksum());
    }

    /** A peer talking about a tick we have no fingerprint for is not evidence of anything. */
    @Test
    void aChecksumForAnUnknownTickIsNotADivergence() {
        Match match = new Match();
        match.one.lockstep.receive(Packet.checksum(2, 9999, 123456789L).encode());

        assertNull(match.one.lockstep.divergence(),
                "no tick in common means no opinion, not an alarm");
    }

    @Test
    void anIntentSurvivesTheRoundTrip() {
        Intent intent = new Intent(-0.7071067811865476, 0.5, true);
        Packet decoded = Packet.decode(Packet.intent(2, 12345, intent).encode());

        assertEquals(Packet.Kind.INTENT, decoded.kind());
        assertEquals(2, decoded.playerNumber());
        assertEquals(12345, decoded.tick());
        // Exactly, not within a tolerance. A wire that rounds is a wire that desyncs: at eight bits
        // the worst velocity error is 2.8e-2 against AnalogStickTest's 1e-4 tolerances.
        assertEquals(intent, decoded.intent(), "the intent must survive the wire bit for bit");
    }

    @Test
    void aChecksumSurvivesTheRoundTrip() {
        Packet decoded = Packet.decode(Packet.checksum(1, 60, -2789495059051683404L).encode());

        assertEquals(Packet.Kind.CHECKSUM, decoded.kind());
        assertEquals(1, decoded.playerNumber());
        assertEquals(60, decoded.tick());
        assertEquals(-2789495059051683404L, decoded.checksum());
    }

    /** Two peers wired to each other's inboxes. */
    private static final class Match {

        private final Peer one = new Peer(1);
        private final Peer two = new Peer(2);

        Match() {
            one.start();
            two.start();
        }

        /**
         * One tick on both peers, the way two machines actually reach it: each asks, neither may go
         * until the other has spoken, the packets cross, then both run.
         */
        void advanceOneTick(int tick) {
            one.lockstep.canStep(tick);
            two.lockstep.canStep(tick);
            deliver();

            assertTrue(one.lockstep.canStep(tick), "peer one stuck at tick " + tick);
            assertTrue(two.lockstep.canStep(tick), "peer two stuck at tick " + tick);
            one.stepFight();
            two.stepFight();

            one.lockstep.publishChecksum(tick, one.world.checksum());
            two.lockstep.publishChecksum(tick, two.world.checksum());
            deliver();
        }

        /**
         * The whole between-levels handshake: both announce, the host names the terms, both begin.
         *
         * Mirrors what the loop will do once the phase machine is wired to this -- which is why the
         * host is peer one and the terms come from it rather than from anywhere local.
         */
        void crossBarrier(Level next) {
            int level = next.ordinal();
            one.lockstep.ready(level, one.loadoutCode());
            two.lockstep.ready(level, two.loadoutCode());
            deliver();
            assertTrue(one.lockstep.everyoneReady(), "everyone announced");

            // The host picks a tick comfortably past both peers' clocks, so neither runs a tick
            // number it has already used.
            int resumeAt = Math.max(one.world.tick(), two.world.tick()) + 1;
            one.lockstep.start(new LevelStart(SEED + level, resumeAt, Difficulty.NORMAL,
                    SaveSlot.forReplay(GameMode.COOP, next), one.lockstep.readyLoadouts()));
            deliver();

            one.beginLevel(one.lockstep.takeStart().orElseThrow());
            two.beginLevel(two.lockstep.takeStart().orElseThrow());
        }

        /** Empties each outbox into the other peer: a perfect network, which is the point here. */
        void deliver() {
            drain(one.outbox, two);
            drain(two.outbox, one);
        }

        private static void drain(Deque<byte[]> outbox, Peer to) {
            while (!outbox.isEmpty()) {
                to.lockstep.receive(outbox.poll());
            }
        }
    }

    /** One machine: a whole game, plus the lockstep that keeps it with the other one. */
    private static final class Peer {

        private static final PlayerControls KEYS = new PlayerControls(
                Set.of(KeyCode.W), Set.of(KeyCode.S), Set.of(KeyCode.A), Set.of(KeyCode.D),
                Set.of(KeyCode.SPACE));

        private final int localPlayer;
        private final World world = new World(GameMode.COOP);
        private SpawnDirector director;
        private CollisionSystem collisions;
        private final List<ShipController> controllers = new ArrayList<>();
        private final Deque<byte[]> outbox = new ArrayDeque<>();
        private final InputState input = new InputState();
        private Lockstep lockstep;

        Peer(int localPlayer) {
            this.localPlayer = localPlayer;
            // Both peers seed from the same number, which is what the host's START message carries.
            Random random = new Random(SEED);
            world.enterLevel(Level.values()[0]);
            director = new SpawnDirector(random, Difficulty.NORMAL, GameMode.COOP.rules());
            collisions = new CollisionSystem(SoundPlayer.SILENT, random);
            for (PlayerShip player : world.players()) {
                controllers.add(new ShipController(player, KEYS, number -> null));
            }
        }

        void start() {
            lockstep = new Lockstep(localPlayer, new int[] {1, 2}, this::sampleLocal, outbox::add);
        }

        /** The handshake as GameLoop would hold it, for this machine. */
        NetworkedLevel handshake(boolean isHost) {
            return new NetworkedLevel(lockstep, isHost, SEED, Difficulty.NORMAL,
                    () -> SaveSlot.forReplay(GameMode.COOP, Level.values()[1]),
                    this::loadoutCode);
        }

        /** What this pilot is flying, which the garage changes locally and the barrier publishes. */
        String loadoutCode() {
            return controllerFor(localPlayer).ship().loadout().encode();
        }

        /** The victory lap, debrief, garage and warp, as far as the simulation is concerned. */
        void celebrateLocally(int ticks) {
            for (int i = 0; i < ticks; i++) {
                world.tickScenery();
            }
        }

        /**
         * Rebuilds everything the host's terms decide, which is everything that would otherwise be
         * read from this machine.
         *
         * {@code enterLevel} matters as much as the seed: it builds a fresh {@code Terrain} from
         * the level's ordinal, and the terrain is scrolled by {@code tickScenery} during the local
         * phases. Two peers that spent different amounts of time between levels have scrolled the
         * rock by different amounts, and the rock pushes enemies around -- so without this the next
         * fight desyncs even with the clock realigned.
         */
        void beginLevel(LevelStart start) {
            world.resumeAt(start.resumeTick());
            world.enterLevel(start.slot().level());
            Random random = new Random(start.seed());
            director = new SpawnDirector(random, start.difficulty(), GameMode.COOP.rules());
            collisions = new CollisionSystem(SoundPlayer.SILENT, random);
        }

        /**
         * This machine's own input, sampled through the real {@link ShipController#sample} so the
         * test exercises the path a keyboard does. The tape differs per player, so peers that mixed
         * the two up would be caught rather than agreeing by symmetry.
         */
        private Intent sampleLocal() {
            script(input, world.tick(), localPlayer);
            return controllerFor(localPlayer).sample(input, StickTuning.none());
        }

        private ShipController controllerFor(int playerNumber) {
            for (ShipController controller : controllers) {
                if (controller.ship().playerNumber() == playerNumber) {
                    return controller;
                }
            }
            throw new IllegalStateException("no controller for player " + playerNumber);
        }

        /**
         * A mirror of {@link GameLoop#stepFight()}, with intents coming from the lockstep rather
         * than from this machine's keyboard. The tick order is the load-bearing part.
         */
        void stepFight() {
            int tick = world.tick();
            for (ShipController controller : controllers) {
                int number = controller.ship().playerNumber();
                controller.apply(lockstep.intentFor(number, tick), world, SoundPlayer.SILENT);
            }
            world.update();
            driveEnemies();
            director.update(world);
            collisions.resolve(world);
            world.sweep();
        }

        private void driveEnemies() {
            int cooldown = Difficulty.NORMAL.enemyFireCooldown();
            int cap = Difficulty.NORMAL.maxEnemies();
            List<EnemyShip> enemies = world.enemies();
            for (int i = 0, count = enemies.size(); i < count; i++) {
                EnemyShip enemy = enemies.get(i);
                PlayerShip target = world.nearestPlayer(enemy);
                if (target == null) {
                    continue;
                }
                enemy.trackAcross(target);
                if (enemy.hasEntered()) {
                    EnemyWeapons.driveWeapons(world, enemy, target, director.level(), cooldown, cap,
                            SoundPlayer.SILENT);
                }
            }
        }
    }

    /**
     * Between two fights each machine runs the victory lap, debrief, garage and warp at its own
     * pace, so their clocks come apart. This is the barrier that puts them back together.
     */
    @Test
    void twoLevelsAgreeAcrossABarrierThePeersReachAtDifferentSpeeds() {
        Match match = new Match();
        for (int tick = 0; tick < 300; tick++) {
            match.advanceOneTick(tick);
        }
        assertEquals(match.one.world.checksum(), match.two.world.checksum(), "level one agreed");

        // Deliberately lopsided: peer one breezes through the debrief while peer two shops. If the
        // barrier did not re-base the clock this is exactly the gap that would desync level two,
        // and it is invisible to any test where both peers take the same path.
        match.one.celebrateLocally(40);
        match.two.celebrateLocally(137);
        assertNotEquals(match.one.world.tick(), match.two.world.tick(),
                "the peers should have drifted apart, or this test proves nothing");

        match.crossBarrier(Level.values()[1]);

        assertEquals(match.one.world.tick(), match.two.world.tick(),
                "the barrier must put the clocks back together");
        // Captured once: the bound must not be re-read from a clock the loop is advancing.
        int from = match.one.world.tick();
        for (int tick = from; tick < from + 300; tick++) {
            match.advanceOneTick(tick);
            assertEquals(match.one.world.checksum(), match.two.world.checksum(),
                    "the peers disagreed at tick " + tick + " of level two");
        }
        assertNull(match.one.lockstep.divergence(), "no divergence across two levels");
    }

    /** Nobody crosses until everybody has said they are ready. */
    @Test
    void theBarrierHoldsUntilEveryPeerAnnounces() {
        Match match = new Match();

        match.one.lockstep.ready(1, "one");
        match.deliver();
        assertFalse(match.one.lockstep.everyoneReady(), "one peer is not everyone");

        match.two.lockstep.ready(1, "two");
        match.deliver();
        assertTrue(match.one.lockstep.everyoneReady(), "both have announced");
    }

    /** A peer racing ahead must not free the barrier the others are still standing at. */
    @Test
    void anAnnouncementForAnotherLevelDoesNotFreeTheBarrier() {
        Match match = new Match();

        match.one.lockstep.ready(1, "one");
        match.two.lockstep.ready(2, "two");
        match.deliver();

        assertFalse(match.one.lockstep.everyoneReady(),
                "peer two announced for level two, not for the level peer one is waiting on");
    }

    @Test
    void aLevelStartSurvivesTheRoundTrip() {
        LevelStart start = new LevelStart(987654321L, 4200, Difficulty.HARD,
                SaveSlot.forReplay(GameMode.COOP, Level.values()[3]),
                List.of("1,0,0", "1,0,0"));

        LevelStart back = LevelStart.decode(
                Packet.decode(Packet.start(1, start).encode()).text()).orElseThrow();

        assertEquals(start.seed(), back.seed());
        assertEquals(start.resumeTick(), back.resumeTick());
        assertEquals(start.difficulty(), back.difficulty());
        assertEquals(start.slot().level(), back.slot().level());
        assertEquals(start.loadouts(), back.loadouts());
    }

    /** Unreadable terms must read as no terms. A guess here is a desync wearing a bug's clothes. */
    @Test
    void anUnreadableLevelStartIsRefusedRatherThanGuessed() {
        assertTrue(LevelStart.decode("").isEmpty());
        assertTrue(LevelStart.decode("99;1;2;NORMAL;whatever").isEmpty(), "a future format version");
        assertTrue(LevelStart.decode("1;notanumber;0;NORMAL;x").isEmpty());
        assertTrue(LevelStart.decode("1;5;0;NO_SUCH_DIFFICULTY;x").isEmpty());
    }

    @Test
    void aReadySurvivesTheRoundTripCarryingItsLoadout() {
        Packet decoded = Packet.decode(Packet.ready(3, 7, "1,0,0|SALVO=2").encode());

        assertEquals(Packet.Kind.READY, decoded.kind());
        assertEquals(3, decoded.playerNumber());
        assertEquals(7, decoded.tick(), "READY carries the level it is for");
        assertEquals("1,0,0|SALVO=2", decoded.text(), "and what that pilot is flying");
    }

    /**
     * The garage is local; what it sells is not. An upgrade changes a ship's damage, hull and
     * speed, so a peer that did not hear about it simulates a different ship from the first shot.
     */
    @Test
    void everyPilotsLoadoutReachesTheHostInPlayerOrder() {
        Match match = new Match();

        // Announced out of order on purpose: the list travels positionally, so a race in the lobby
        // must not hand player two player three's ship.
        match.two.lockstep.ready(1, "player-two-ship");
        match.one.lockstep.ready(1, "player-one-ship");
        match.deliver();

        assertTrue(match.one.lockstep.everyoneReady());
        assertEquals(List.of("player-one-ship", "player-two-ship"),
                match.one.lockstep.readyLoadouts(),
                "loadouts must be ordered by player number, not by who spoke first");
    }

    /**
     * The handshake as the loop actually calls it, including the part that is easy to leave out.
     *
     * A pilot who spends ninety seconds in the garage has scrolled the terrain further than one who
     * pressed through the debrief at once, and the terrain pushes enemies around -- so re-entering
     * the level matters as much as realigning the clock. Both are checked here.
     */
    @Test
    void theHandshakePutsBothMachinesInTheAgreedState() {
        Match match = new Match();
        for (int tick = 0; tick < 120; tick++) {
            match.advanceOneTick(tick);
        }
        match.one.celebrateLocally(20);
        match.two.celebrateLocally(200);

        int nextLevel = 1;
        NetworkedLevel hostSide = match.one.handshake(true);
        NetworkedLevel guestSide = match.two.handshake(false);

        // The host cannot begin either: it is waiting to hear from the guest.
        assertFalse(hostSide.readyToFight(nextLevel, match.one.world),
                "the host must wait for the other machine too");
        match.deliver();
        assertFalse(guestSide.readyToFight(nextLevel, match.two.world),
                "the guest announces, but the terms have not been named yet");
        match.deliver();

        assertTrue(hostSide.readyToFight(nextLevel, match.one.world), "the host names the terms");
        match.deliver();
        assertTrue(guestSide.readyToFight(nextLevel, match.two.world), "and the guest takes them");

        assertEquals(match.one.world.tick(), match.two.world.tick(),
                "the clocks must agree after the handshake");
        assertEquals(match.one.world.terrain().getClass(), match.two.world.terrain().getClass());
        assertEquals(match.one.world.checksum(), match.two.world.checksum(),
                "and so must the worlds");
    }

    /** What the garage sold must be on the other machine's ship, or the two simulate different ships. */
    @Test
    void anUpgradeBoughtOnOneMachineReachesTheOther() {
        Match match = new Match();
        PlayerShip theirs = match.two.world.players().get(1);
        Loadout upgraded = Loadout.stock(2);
        for (int bought = 0; bought < 3; bought++) {
            upgraded.raise(Upgrade.FIREPOWER);
        }

        match.one.lockstep.ready(1, Loadout.stock(1).encode());
        match.two.lockstep.ready(1, upgraded.encode());
        match.deliver();

        NetworkedLevel hostSide = match.one.handshake(true);
        hostSide.readyToFight(1, match.one.world);
        match.deliver();

        PlayerShip onTheOtherMachine = match.one.world.players().get(1);
        assertEquals(3, onTheOtherMachine.loadout().level(Upgrade.FIREPOWER),
                "player two's upgrade must be fitted on player one's machine as well");
        assertEquals(theirs.playerNumber(), onTheOtherMachine.playerNumber(), "and on the right ship");
    }

    /** Two different tapes, so the peers cannot agree merely by flying identically. */
    private static void script(InputState input, int tick, int player) {
        input.clear();
        int beat = (tick + (player == 1 ? 0 : 90)) % 240;
        if (beat < 60) {
            input.press(KeyCode.A);
        } else if (beat < 120) {
            input.press(KeyCode.D);
        } else if (beat < 180) {
            input.press(KeyCode.W);
            input.press(KeyCode.A);
        } else {
            input.press(KeyCode.S);
            input.press(KeyCode.D);
        }
        if (tick % (player == 1 ? 5 : 7) != 0) {
            input.press(KeyCode.SPACE);
        }
    }
}
