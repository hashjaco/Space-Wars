package com.hashimjacobs.spacecase.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     * Reconnect: a missing peer pauses the match (canStep), a returning one asks for a re-feed
 * (resync), and after RECONNECT_WINDOW_CALLS the host may drop them (drop). Formerly: ponytail:
 * no vote to continue, and no reconnect. Reconnecting means shipping a whole world to
     * the returning peer, which is the state-transfer system this design exists to avoid.
     */
    /**
     * Render-rate calls a match waits for a missing peer before the host may drop them: about
     * thirty seconds. Until then everyone holds still; see {@link #canStep}.
     */
    public static final int RECONNECT_WINDOW_CALLS = 1800;
    /** After a DROP, how long peers wait for forwarded copies of the dropped player's last intents. */
    private static final int DROP_GRACE_CALLS = 120;

    /** How often each peer publishes its world fingerprint. */
    private static final int CHECKSUM_EVERY_TICKS = 60;

    private final int localPlayer;
    /** Insertion-ordered so the missing-peer message names players the way the lobby did. */
    private final Map<Integer, IntentRing> rings = new LinkedHashMap<>();
    private final Consumer<byte[]> send;
    private Supplier<Intent> sampler;

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
    private int stuckTick = -1;
    private int steppedThrough = -1;
    private List<Integer> waitingFor = List.of();
    private final Map<Integer, Integer> droppedFrom = new LinkedHashMap<>();
    /** The dropped who said so themselves, and so need no grace: see {@link Packet.Kind#LEAVE}. */
    private final Set<Integer> leftSelf = new HashSet<>();
    private int dropCalls;
    /** Pilots waiting in the room who have asked to fly from the next level, with their loadouts. */
    private final Map<Integer, String> joiners = new LinkedHashMap<>();
    /**
     * Input for the next fight from a pilot this machine does not have a seat for yet.
     *
     * The host's terms and a newcomer's first input can arrive in one read of the socket, terms
     * first. Until the terms are taken there is no ring for the newcomer, and dropping their input
     * would leave this machine waiting for ticks that will never be sent again.
     */
    private final List<Packet> early = new ArrayList<>();
    private static final int EARLY_HELD = 1024;
    private boolean removed;
    private LevelStart lastStart;
    private int lastStartTick = -1;

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
        // A dropped player's silence becomes neutral input, but only after a grace in which the
        // other peers forward whatever real intents of theirs they still hold (see DROP).
        if (!droppedFrom.isEmpty()) {
            dropCalls++;
        }
        List<Integer> missing = new ArrayList<>();
        for (Map.Entry<Integer, IntentRing> entry : rings.entrySet()) {
            if (entry.getValue().get(tick) != null) {
                continue;
            }
            Integer from = droppedFrom.get(entry.getKey());
            if (from != null && tick >= from
                    && (dropCalls >= DROP_GRACE_CALLS || leftSelf.contains(entry.getKey()))) {
                entry.getValue().put(tick, Intent.NEUTRAL);
                continue;
            }
            missing.add(entry.getKey());
        }
        if (missing.isEmpty()) {
            stalledCalls = 0;
            waitingFor = List.of();
            steppedThrough = tick;
            return true;
        }
        // Somebody's input has not arrived. Everyone holds still: a peer who dropped can come back
        // inside the window and be re-fed, and nothing is invented that the others would not also
        // invent. Flying the absentee neutral, as this once did, let peers disagree about which
        // tick the silence began on.
        stalledCalls++;
        stuckTick = tick;
        waitingFor = List.copyOf(missing);
        return false;
    }

    /** Players whose input the current tick is waiting on, or empty while the match is moving. */
    public List<Integer> waitingFor() {
        return waitingFor;
    }

    /** Render-rate calls the match has been held for the same tick. */
    public int stalledCalls() {
        return stalledCalls;
    }

    public boolean pastReconnectWindow() {
        return stalledCalls >= RECONNECT_WINDOW_CALLS;
    }

    public boolean isDropped(int playerNumber) {
        return droppedFrom.containsKey(playerNumber);
    }

    /** The first tick this machine lacks input for: where a re-feed has to start. */
    private int firstMissingTick() {
        return waitingFor.isEmpty() ? steppedThrough + 1 : stuckTick;
    }

    /**
     * After reconnecting: asks every peer for their input from the tick we are stuck on, and for
     * the level start we may have missed. Their replies carry their own stuck ticks, which is
     * what tells us how far back to resend ours.
     */
    public void resync() {
        send.accept(Packet.resync(localPlayer, firstMissingTick(), false).encode());
    }

    /**
     * The host's decision that a peer is not coming back. Everyone, host included, forwards what
     * they hold of that player's input from the tick on, then fills the rest with neutral after a
     * grace, so every machine flies the same ghost.
     */
    public void drop(int playerNumber) {
        int from = firstMissingTick();
        send.accept(Packet.drop(localPlayer, playerNumber, from).encode());
        acceptDrop(playerNumber, from);
    }

    private void acceptDrop(int playerNumber, int from) {
        IntentRing ring = rings.get(playerNumber);
        if (ring == null || playerNumber == localPlayer) {
            return;
        }
        for (int tick = from; tick <= ring.newest(); tick++) {
            Intent held = ring.get(tick);
            if (held != null) {
                send.accept(Packet.intent(playerNumber, tick, held).encode());
            }
        }
        if (!droppedFrom.containsKey(playerNumber)) {
            droppedFrom.put(playerNumber, from);
            dropCalls = 0;
        }
    }

    /**
     * This machine's pilot is leaving. Tells everyone the first tick there will be no input for,
     * so they fly the ship neutral from that tick and carry on without a pause.
     */
    public void leave() {
        send.accept(Packet.leave(localPlayer, lastSent + 1).encode());
    }

    /** Everyone flying in this match, by room slot, lowest first: the order ships are numbered in. */
    public List<Integer> players() {
        return rings.keySet().stream().sorted().toList();
    }

    /** Players who left or were dropped. They fly neutral now and have no seat in the next level. */
    public Set<Integer> gone() {
        return Set.copyOf(droppedFrom.keySet());
    }

    /** Pilots who have asked to join, by slot, with what each is flying. */
    public Map<Integer, String> joiners() {
        return Map.copyOf(joiners);
    }

    /** Whether the host's terms for the next level left this machine's pilot out. */
    public boolean removed() {
        return removed;
    }

    /**
     * Hands this lockstep to a rebuilt game. The rings, the clock and everything already heard for
     * the next fight stay; only where this machine's own input comes from changes, because the
     * controls belong to the loop and the loop is new.
     */
    public void setSampler(Supplier<Intent> sampler) {
        this.sampler = sampler;
    }

    /**
     * Starts a fresh lockstep on terms that were taken somewhere else -- in the lobby, by the
     * router -- so its clock begins where the fight does and a returning peer can be re-sent them.
     */
    public void adopt(LevelStart start) {
        lastStart = start;
        lastStartTick = start.resumeTick();
        lastSent = start.resumeTick() - 1;
        steppedThrough = start.resumeTick() - 1;
    }

    private void resendFrom(int fromTick) {
        IntentRing mine = rings.get(localPlayer);
        for (int tick = Math.max(0, fromTick); tick <= lastSent; tick++) {
            Intent held = mine.get(tick);
            if (held != null) {
                send.accept(Packet.intent(localPlayer, tick, held).encode());
            }
        }
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
        Packet packet;
        try {
            packet = Packet.decode(bytes);
        } catch (IllegalArgumentException malformed) {
            return; // one bad frame from a peer is not a reason to crash the game thread
        }
        switch (packet.kind()) {
            case INTENT -> {
                IntentRing ring = rings.get(packet.playerNumber());
                // A packet for somebody not in this match is a stray from another room, not a
                // reason to stop playing.
                // One for a tick already simulated is late, and overwriting what ran would be a
                // divergence by another name.
                if (ring != null && packet.playerNumber() != localPlayer
                        && packet.tick() > steppedThrough) {
                    ring.put(packet.tick(), packet.intent());
                } else if (ring == null && pendingStart != null
                        && pendingStart.seats().contains(packet.playerNumber())
                        && early.size() < EARLY_HELD) {
                    early.add(packet);
                }
            }
            case RESYNC -> {
                resendFrom(packet.tick());
                if (packet.flag() == 0) {
                    if (lastStart != null) {
                        send.accept(Packet.start(localPlayer, lastStart).encode());
                    }
                    send.accept(Packet.resync(localPlayer, firstMissingTick(), true).encode());
                }
            }
            case DROP -> acceptDrop(packet.flag(), packet.tick());
            case LEAVE -> {
                if (rings.containsKey(packet.playerNumber()) && packet.playerNumber() != localPlayer) {
                    droppedFrom.putIfAbsent(packet.playerNumber(), packet.tick());
                    leftSelf.add(packet.playerNumber());
                }
            }
            // Only from somebody not already flying: a seated pilot has nothing to ask for.
            case JOIN -> {
                if (!rings.containsKey(packet.playerNumber())) {
                    joiners.put(packet.playerNumber(), packet.text());
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
            // A START re-sent to a returning peer reaches everyone; one already taken is ignored.
            case START -> LevelStart.decode(packet.text())
                    .filter(start -> start.resumeTick() > lastStartTick)
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
        if (readyLevel < 0) {
            return false;
        }
        for (int player : rings.keySet()) {
            // A dropped player will never announce, and waiting for them would hold everyone at
            // the barrier for the rest of the run.
            if (!readyLoadouts.containsKey(player) && !droppedFrom.containsKey(player)) {
                return false;
            }
        }
        return true;
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

    /** What one announced pilot is flying, or a stock ship for one who never said. */
    public String readyLoadout(int player) {
        return readyLoadouts.getOrDefault(player, "");
    }

    /**
     * The host's terms for the next fight, sent once everyone has announced.
     *
     * Only the host calls this. Every field in it is something the peers would otherwise each read
     * from their own machine and each read differently.
     */
    public void start(LevelStart start) {
        pendingStart = start;
        lastStart = start;
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
        if (!start.seats().isEmpty() && !start.seats().contains(localPlayer)) {
            // The fight goes on without this pilot. Nothing is re-based, because nothing here
            // will run again; the caller is told and takes the pilot back to the board.
            removed = true;
            early.clear();
            return Optional.empty();
        }
        lastStart = start;
        lastStartTick = start.resumeTick();
        readyLoadouts.clear();
        readyLevel = -1;
        joiners.clear();
        reseat(start);
        lastSent = start.resumeTick() - 1;
        steppedThrough = start.resumeTick() - 1;
        stalledCalls = 0;
        waitingFor = List.of();
        return Optional.of(start);
    }

    /**
     * Empties the rings for the fight the terms describe, and re-cuts them if the terms seat
     * different pilots: a ring for each newcomer, none for anyone who has gone.
     *
     * Not quite everything is thrown away. A peer who took the same terms a moment sooner is
     * already sending input for the new fight, and that can arrive in the same read of the socket
     * as the terms themselves -- so it is in a ring, or in {@link #early}, before this runs. It is
     * kept. Emptying it, as this once did, left this machine waiting for ticks its peers had
     * already sent and would never send again.
     */
    private void reseat(LevelStart start) {
        Map<Integer, Map<Integer, Intent>> ahead = new LinkedHashMap<>();
        for (Map.Entry<Integer, IntentRing> entry : rings.entrySet()) {
            IntentRing ring = entry.getValue();
            if (entry.getKey() == localPlayer) {
                continue;
            }
            Map<Integer, Intent> kept = new LinkedHashMap<>();
            int from = Math.max(start.resumeTick(), ring.newest() - IntentRing.capacity() + 1);
            for (int tick = from; tick <= ring.newest(); tick++) {
                Intent held = ring.get(tick);
                if (held != null) {
                    kept.put(tick, held);
                }
            }
            ahead.put(entry.getKey(), kept);
        }
        for (Packet packet : early) {
            if (packet.tick() >= start.resumeTick()) {
                ahead.computeIfAbsent(packet.playerNumber(), player -> new LinkedHashMap<>())
                        .put(packet.tick(), packet.intent());
            }
        }
        early.clear();

        boolean sameSeats = start.seats().isEmpty() || players().equals(sorted(start.seats()));
        if (sameSeats) {
            for (IntentRing ring : rings.values()) {
                ring.clear();
            }
        } else {
            rings.clear();
            for (int seat : start.seats()) {
                rings.put(seat, new IntentRing());
            }
            // Whoever was dropped is either not in the new terms or, named in them, is back.
            droppedFrom.clear();
            leftSelf.clear();
            dropCalls = 0;
            theirChecksums.clear();
        }
        for (Map.Entry<Integer, Map<Integer, Intent>> entry : ahead.entrySet()) {
            IntentRing ring = rings.get(entry.getKey());
            if (ring != null) {
                entry.getValue().forEach(ring::put);
            }
        }
    }

    private static List<Integer> sorted(List<Integer> seats) {
        return seats.stream().sorted().toList();
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
