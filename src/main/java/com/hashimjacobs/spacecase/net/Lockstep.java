package com.hashimjacobs.spacecase.net;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.hashimjacobs.spacecase.engine.Intent;
import com.hashimjacobs.spacecase.engine.IntentSource;

/**
 * Holds a machine's simulation in step with its peers by exchanging nothing but input.
 *
 * Every peer runs the whole game. What crosses the wire is one {@link Intent} per player per tick,
 * so the traffic is a few kilobytes a second and the server can be a relay that knows nothing about
 * the game. What that buys has to be paid for in discipline: two peers running the same ticks from
 * the same seed must reach the same world, which is what {@code DeterminismTest} and the checksums
 * below are for.
 *
 * Deliberately knows nothing about sockets. It is handed something to send bytes with and is fed
 * the bytes that arrive, which is what lets the whole scheme be tested with two instances wired to
 * each other in one JVM and no network at all.
 *
 * <h2>Where the delay comes from</h2>
 *
 * A tick is not run until every peer's input for it has arrived, so a peer that stops talking stops
 * everyone. To give a packet time to travel, each machine samples and sends its input for tick
 * {@code T + INPUT_DELAY_TICKS} at the moment it is about to run tick {@code T}. The cost is that a
 * player's own ship answers their hands three ticks late, always, on every machine. That floor
 * cannot be lowered without predicting inputs and rolling back when the prediction is wrong, which
 * is several times this much code.
 */
public final class Lockstep implements IntentSource {

    /**
     * How far ahead of the running tick input is sampled. Three ticks is fifty milliseconds.
     *
     * ponytail: fixed. A delay that tracked the worst peer's round trip would stall less on a bad
     * connection and feel better on a good one; do that only once real players on real networks
     * say this is not enough.
     */
    public static final int INPUT_DELAY_TICKS = 3;

    /**
     * How many refused gate calls before a silent peer is flown as {@link Intent#NEUTRAL}.
     *
     * Counted in calls, which arrive at render rate rather than tick rate, so this is roughly three
     * seconds at sixty frames a second and less on a slower display. Precision is not the point:
     * the point is that one person closing their laptop must not leave everyone else looking at a
     * frozen fight forever.
     *
     * The dropped ship then stops and drifts. In co-op it runs out of lives and the round ends
     * through {@code checkRoundOver}; in battle, last-player-standing ends it almost at once.
     * ponytail: no vote to continue, and no reconnect. Reconnecting means shipping a whole world to
     * the returning peer, which is the state-transfer system this design exists to avoid.
     */
    private static final int DROPOUT_CALLS = 180;

    /** How often each peer publishes its world fingerprint. */
    private static final int CHECKSUM_EVERY_TICKS = 60;

    private final int localPlayer;
    /** Insertion-ordered so the missing-peer message names players the way the lobby did. */
    private final Map<Integer, IntentRing> rings = new LinkedHashMap<>();
    private final Consumer<byte[]> send;
    private final Supplier<Intent> sampler;

    /** Each peer's most recent fingerprint. Checksums are far enough apart that only the last matters. */
    private final Map<Integer, Packet> theirChecksums = new LinkedHashMap<>();

    /**
     * Our own fingerprints, by tick, so a peer's can be matched against the same tick rather than
     * against whatever we happened to compute last.
     *
     * Bounded, because nothing ever removes an entry on the happy path: a peer that goes quiet
     * would otherwise leave its unanswered ticks here for the rest of the match.
     */
    private final Map<Integer, Long> ourChecksums = new LinkedHashMap<>();
    private static final int CHECKSUMS_REMEMBERED = 16;

    private Divergence divergence;

    private int lastSent = -1;
    private int stalledCalls;

    /**
     * Who has announced they are done with their own between-levels business, and what they are
     * flying. Insertion order is irrelevant here -- the host reads it by player number.
     */
    private final Map<Integer, String> readyLoadouts = new LinkedHashMap<>();
    private int readyLevel = -1;
    private LevelStart pendingStart;

    /**
     * @param localPlayer   the player number this machine flies
     * @param playerNumbers every player in the match, this one included
     * @param sampler       this machine's input right now, from {@code ShipController.sample}
     * @param send          hands one encoded packet to the transport
     */
    public Lockstep(int localPlayer, int[] playerNumbers, Supplier<Intent> sampler,
                    Consumer<byte[]> send) {
        this.localPlayer = localPlayer;
        this.sampler = sampler;
        this.send = send;
        for (int number : playerNumbers) {
            rings.put(number, new IntentRing());
        }
        if (!rings.containsKey(localPlayer)) {
            throw new IllegalArgumentException("local player " + localPlayer + " is not in the match");
        }
    }

    /**
     * Whether the simulation may run this tick. Wire this to {@code GameLoop.setStepGate}.
     *
     * Two jobs, in this order. Produce and send this machine's input for every tick up to
     * {@code tick + INPUT_DELAY_TICKS} -- per tick rather than per call, because a frame that earns
     * two steps must produce two intents or the peers find a hole where the second one should be.
     * Then say whether everybody's input for the tick about to run has arrived.
     */
    public boolean canStep(int tick) {
        produceThrough(tick + INPUT_DELAY_TICKS);

        if (divergence != null) {
            return false;
        }
        if (haveEveryIntentFor(tick)) {
            stalledCalls = 0;
            return true;
        }
        if (++stalledCalls < DROPOUT_CALLS) {
            return false;
        }
        // Whoever is missing has gone. Fly them neutral so the round can reach its own ending
        // rather than hanging; this is the one place an intent is invented rather than received.
        for (Map.Entry<Integer, IntentRing> entry : rings.entrySet()) {
            if (entry.getValue().get(tick) == null) {
                entry.getValue().put(tick, Intent.NEUTRAL);
            }
        }
        return true;
    }

    @Override
    public Intent intentFor(int playerNumber, int tick) {
        IntentRing ring = rings.get(playerNumber);
        Intent intent = ring == null ? null : ring.get(tick);
        // Never null: canStep is what guarantees this, and a null here would mean it was bypassed.
        return intent == null ? Intent.NEUTRAL : intent;
    }

    /** Feed every packet the transport delivers straight in here. */
    public void receive(byte[] bytes) {
        Packet packet = Packet.decode(bytes);
        switch (packet.kind()) {
            case INTENT -> {
                IntentRing ring = rings.get(packet.playerNumber());
                // A packet for somebody not in this match is a stray from another room, not a
                // reason to stop playing.
                if (ring != null && packet.playerNumber() != localPlayer) {
                    ring.put(packet.tick(), packet.intent());
                }
            }
            case CHECKSUM -> {
                theirChecksums.put(packet.playerNumber(), packet);
                // Compared here as well as on publish because either machine can reach a tick
                // first: whichever of the two fingerprints arrives second is the one that compares.
                compare(packet);
            }
            case READY -> {
                // Keyed by level, so a peer that raced ahead and announced the next one cannot
                // free the barrier everybody else is still standing at.
                if (packet.tick() == readyLevel) {
                    readyLoadouts.put(packet.playerNumber(), packet.text());
                }
            }
            case START -> LevelStart.decode(packet.text())
                    .ifPresent(start -> pendingStart = start);
        }
    }

    /**
     * Publishes this machine's fingerprint every {@link #CHECKSUM_EVERY_TICKS} ticks and compares
     * it with whatever the peers last sent. Call once per tick, after the tick has run.
     *
     * A mismatch is a bug in the simulation, not a network condition, so it stops the match and
     * says where. It deliberately does not restart the level: restarting hides the defect and
     * charges the player for it.
     *
     * ponytail: compares against each peer's most recent checksum rather than the one for this
     * exact tick, so it can miss by up to a second. Set {@code -Dlockstep.checksum.everyTick} to
     * compare every tick while chasing one, which is when the exact tick actually matters.
     */
    public void publishChecksum(int tick, long checksum) {
        int every = System.getProperty("lockstep.checksum.everyTick") != null
                ? 1 : CHECKSUM_EVERY_TICKS;
        if (tick % every != 0) {
            return;
        }
        ourChecksums.put(tick, checksum);
        while (ourChecksums.size() > CHECKSUMS_REMEMBERED) {
            Integer eldest = ourChecksums.keySet().iterator().next();
            ourChecksums.remove(eldest);
        }
        for (Packet theirs : theirChecksums.values()) {
            compare(theirs);
        }
        send.accept(Packet.checksum(localPlayer, tick, checksum).encode());
    }

    /**
     * Compares one peer's fingerprint against our own for the same tick, and only the same tick.
     *
     * The tick guard is the whole point. Comparing a peer's latest against our latest looks like it
     * works and is worse than useless: the two are rarely for the same tick, so a healthy match
     * reports a divergence at the second checksum and stops itself. A false alarm here costs a
     * player their game, so this stays conservative -- no tick in common means no opinion.
     */
    private void compare(Packet theirs) {
        if (divergence != null) {
            return;
        }
        Long ours = ourChecksums.get(theirs.tick());
        // longValue() rather than !=, which works here only by unboxing: box both sides in
        // some later edit and the same line silently becomes a reference comparison.
        if (ours != null && ours.longValue() != theirs.checksum()) {
            divergence = new Divergence(theirs.tick(), localPlayer, ours,
                    theirs.playerNumber(), theirs.checksum());
        }
    }

    /** Where and how two machines stopped agreeing, or null while they still do. */
    public Divergence divergence() {
        return divergence;
    }

    /**
     * Announces that this machine has finished its own between-levels business.
     *
     * The fight is lockstepped; everything between two fights is not. The victory lap, the debrief,
     * the garage and the warp all end when something purely local happens -- a pilot pressing a key,
     * a pilot finishing their shopping, this machine's disk finishing a decode -- so lockstepping
     * them would mean the slowest disk in the match sets everyone's pace, and would drag local disk
     * reads into the simulation. They run at each machine's own speed instead, and meet here.
     *
     * @param level which level this peer is ready for; an announcement for any other is ignored
     */
    public void ready(int level, String loadout) {
        if (level != readyLevel) {
            readyLevel = level;
            readyLoadouts.clear();
        }
        readyLoadouts.put(localPlayer, loadout == null ? "" : loadout);
        send.accept(Packet.ready(localPlayer, level, loadout).encode());
    }

    /** Whether everyone still in the match has announced for the level passed to {@link #ready}. */
    public boolean everyoneReady() {
        return readyLevel >= 0 && readyLoadouts.keySet().containsAll(rings.keySet());
    }

    /**
     * What every announced pilot is flying, by player number, for the host to put in the start.
     *
     * Ordered by player number rather than by who spoke first, because the list travels positionally
     * in {@link LevelStart} and a race in the lobby must not hand player two player three's ship.
     */
    public List<String> readyLoadouts() {
        return rings.keySet().stream()
                .sorted()
                .map(player -> readyLoadouts.getOrDefault(player, ""))
                .toList();
    }

    /**
     * The host's terms for the next fight, sent once everyone has announced.
     *
     * Only the host calls this. Every field in it is something the peers would otherwise each read
     * from their own machine and each read differently.
     */
    public void start(LevelStart start) {
        pendingStart = start;
        send.accept(Packet.start(localPlayer, start).encode());
    }

    /**
     * The terms to begin the next fight on, once they have arrived. Empty until the host speaks.
     *
     * Taking it clears it and re-bases the clock: the rings are emptied and the send counter is set
     * from the agreed tick, so intents produced for the level just finished cannot be read as
     * intents for the one beginning. Without that, a ring slot still holding tick 300 from the last
     * level answers a request for tick 300 of this one, and the two machines quietly fly different
     * ships.
     */
    public Optional<LevelStart> takeStart() {
        if (pendingStart == null) {
            return Optional.empty();
        }
        LevelStart start = pendingStart;
        pendingStart = null;
        readyLoadouts.clear();
        readyLevel = -1;
        for (IntentRing ring : rings.values()) {
            ring.clear();
        }
        lastSent = start.resumeTick() - 1;
        stalledCalls = 0;
        return Optional.of(start);
    }

    /**
     * @param tick the first tick the two were seen to disagree on, which is not necessarily the
     *             tick they began to
     */
    public record Divergence(int tick, int localPlayer, long localChecksum,
                             int peerPlayer, long peerChecksum) {

        @Override
        public String toString() {
            return "diverged at tick " + tick + ": player " + localPlayer + " has " + localChecksum
                    + ", player " + peerPlayer + " has " + peerChecksum;
        }
    }

    private void produceThrough(int tick) {
        while (lastSent < tick) {
            lastSent++;
            Intent mine = sampler.get();
            rings.get(localPlayer).put(lastSent, mine);
            send.accept(Packet.intent(localPlayer, lastSent, mine).encode());
        }
    }

    private boolean haveEveryIntentFor(int tick) {
        for (IntentRing ring : rings.values()) {
            if (ring.get(tick) == null) {
                return false;
            }
        }
        return true;
    }
}
