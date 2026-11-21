package com.hashimjacobs.spacecase.net;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import com.hashimjacobs.spacecase.engine.Intent;
import com.hashimjacobs.spacecase.engine.IntentSource;
import com.hashimjacobs.spacecase.engine.LevelHandshake;
import com.hashimjacobs.spacecase.engine.TickObserver;
import com.hashimjacobs.spacecase.prefs.SaveSlot;

/**
 * Everything a running game needs from the network, behind one object.
 *
 * The socket, the lockstep, and the between-levels handshake are three separate things for good
 * reasons, and wiring all three into a loop is four calls that must agree with each other about the
 * local seat and the roster. This is where they agree, so the router does not have to.
 *
 * Built by the lobby once the host has named the terms, and handed straight to the screen.
 */
public final class NetworkedGame implements AutoCloseable {

    private final RelayClient relay;
    private final Lockstep lockstep;
    private final NetworkedLevel level;
    private final LevelStart opening;
    /**
     * The room slots flying, in ship order: ship one is {@code seats.get(0)}.
     *
     * Two numberings meet here and nowhere else. The wire and the lockstep speak in room slots,
     * which have gaps the moment anybody leaves a lobby; the engine numbers its ships densely from
     * one. Handing the engine a slot, as this once did, flew ship two with nobody's input in a room
     * seated one and three.
     */
    private final List<Integer> seats;
    private final int localSeat;

    private final Events events;
    private boolean hostGoneRaised;

    private NetworkedGame(RelayClient relay, Lockstep lockstep, NetworkedLevel level,
                          LevelStart opening, List<Integer> seats, Events events) {
        this.events = events;
        this.relay = relay;
        this.lockstep = lockstep;
        this.level = level;
        this.opening = opening;
        this.seats = List.copyOf(seats);
        this.localSeat = shipOf(seats, relay.welcome().slot());
        this.host = relay.isHost();
    }

    /** The ship (1-based, dense) a room slot flies, or 0 if that slot is not in the match. */
    public static int shipOf(List<Integer> seats, int slot) {
        return seats.indexOf(slot) + 1;
    }

    /**
     * What the game tells whoever owns the screen. All three are called from inside the loop's own
     * frame, so the receiver must defer anything that tears the loop down.
     *
     * @param rosterChanged the next level seats different pilots: rebuild the game from these terms
     * @param removed       the next level does not include this machine's pilot
     * @param hostGone      the host has left and the group is closed
     */
    public record Events(Consumer<LevelStart> rosterChanged, Runnable removed, Runnable hostGone) {
    }

    /**
     * @param opening      the terms the fight begins on, which name who is flying
     * @param sampleLocal  this machine's input for its own seat, from {@code GameLoop.sampleLocal}
     * @param checkpoint   where the run stands, for the host to state in each level's terms
     * @param nextCheckpoint where it will stand as the next level opens, or null at a galaxy's end
     * @param localLoadout what this machine's pilot is flying, read after their garage closes
     * @param previous     the game these terms replace, when the roster changed mid-run; its
     *                     lockstep carries on, already holding whatever arrived for the new fight.
     *                     Null when a match opens.
     */
    public static NetworkedGame begin(RelayClient relay, LevelStart opening,
                                      Supplier<Intent> sampleLocal, Supplier<SaveSlot> checkpoint,
                                      Supplier<SaveSlot> nextCheckpoint,
                                      Supplier<String> localLoadout, NetworkedGame previous,
                                      Events events) {
        int localSlot = relay.welcome().slot();
        boolean host = relay.isHost();
        List<Integer> seats = opening.seats();

        Lockstep lockstep;
        if (previous == null) {
            int[] numbers = seats.stream().mapToInt(Integer::intValue).toArray();
            lockstep = new Lockstep(localSlot, numbers, sampleLocal, relay::send);
            lockstep.adopt(opening);
        } else {
            // Taking the terms already re-cut its rings for the new roster.
            lockstep = previous.lockstep;
            lockstep.setSampler(sampleLocal);
        }
        int capacity = relay.group().map(GroupInfo::maxPlayers).orElse(LobbyModel.MAX_PEERS);
        NetworkedLevel level = new NetworkedLevel(lockstep, host, opening.seed(),
                opening.difficulty(), checkpoint, localLoadout,
                new NetworkedLevel.Roster(seats, nextCheckpoint, relay::peers, capacity,
                        relay::sendControl, events.rosterChanged(), events.removed()));
        if (host) {
            // So the board shows the level being flown, and the relay knows whom to credit when
            // it is won. Said at every start: the opening one here, later ones at the barrier.
            relay.sendControl(NetworkedLevel.stateLine(opening.slot().level(), seats));
        }
        return new NetworkedGame(relay, lockstep, level, opening, seats, events);
    }

    /**
     * The gate the loop asks before each tick.
     *
     * The drain belongs here rather than anywhere else in the game: it is the one point that runs
     * on the loop's own thread immediately before the simulation needs the packets, which is the
     * whole of {@link RelayClient}'s threading contract. Putting it on a timer or a callback would
     * hand {@link Lockstep} to two threads at once.
     */
    private final boolean host;
    private volatile boolean reconnecting;
    private volatile int reconnectAttempts;
    private volatile String lastFailure;

    public IntPredicate stepGate() {
        return tick -> {
            // Still drained once this game has been told to stop: the lockstep outlives it, and what
            // arrives now is input for the fight the next game is about to be built for.
            relay.drainTo(lockstep::receive);
            if (level.handedOver()) {
                return false;
            }
            if (relay.closed()) {
                // The group was its host's. There is nobody to name the next level's terms and
                // no room to reconnect to.
                if (!hostGoneRaised) {
                    hostGoneRaised = true;
                    events.hostGone().run();
                }
                return false;
            }
            if (!relay.isConnected()) {
                reconnectInBackground();
            }
            boolean ok = lockstep.canStep(tick);
            // Only the host decides a peer is gone; every machine then applies the same DROP from
            // the same tick. A dropped connection gets the whole window to come back. A pilot the
            // relay says left on purpose gets none -- their own LEAVE normally lands first and
            // this never fires, but if it was lost with the socket nobody should wait thirty
            // seconds for somebody who said goodbye.
            if (!ok && host) {
                List<Integer> present = relay.peers();
                boolean windowOver = lockstep.pastReconnectWindow();
                for (int player : lockstep.waitingFor()) {
                    if (lockstep.isDropped(player)) {
                        continue;
                    }
                    if (relay.left().contains(player) || (windowOver && !present.contains(player))) {
                        lockstep.drop(player);
                    }
                }
            }
            return ok;
        };
    }

    /**
     * What the HUD says about the link, or null while the match is moving: reconnecting, or
     * waiting for named pilots with the seconds left in the window.
     */
    public String status() {
        if (!relay.isConnected()) {
            return "RECONNECTING" + ".".repeat(1 + reconnectAttempts % 3)
                    + (lastFailure == null ? "" : "  " + lastFailure);
        }
        List<Integer> waiting = lockstep.waitingFor();
        if (waiting.isEmpty()) {
            return null;
        }
        int secondsLeft = Math.max(0, (Lockstep.RECONNECT_WINDOW_CALLS - lockstep.stalledCalls()) / 60);
        StringBuilder names = new StringBuilder();
        for (int player : waiting) {
            names.append(names.isEmpty() ? "" : ", ")
                    .append(relay.names().getOrDefault(player, "PILOT " + player));
        }
        return "WAITING FOR " + names + (lockstep.isDropped(waiting.get(0)) ? "" : "  " + secondsLeft + "s");
    }

    /** One attempt at a time, off the FX thread, with a short backoff between tries. */
    private void reconnectInBackground() {
        if (reconnecting) {
            return;
        }
        reconnecting = true;
        // A daemon platform thread rather than a virtual one, which needs JDK 21: it sleeps a few
        // seconds and makes one connection, so there is nothing for a virtual thread to save.
        Thread retry = new Thread(() -> {
            try {
                Thread.sleep(Math.min(8000L, 1000L << Math.min(3, reconnectAttempts)));
                relay.reconnect();
                lastFailure = null;
                reconnectAttempts = 0;
                lockstep.resync();
            } catch (IOException | InterruptedException refused) {
                reconnectAttempts++;
                lastFailure = refused.getMessage();
            } finally {
                reconnecting = false;
            }
        }, "relay-reconnect");
        retry.setDaemon(true);
        retry.start();
    }

    public IntentSource intents() {
        return (ship, tick) -> ship < 1 || ship > seats.size()
                ? Intent.NEUTRAL
                : lockstep.intentFor(seats.get(ship - 1), tick);
    }

    /** Whether this machine names each level's terms. */
    public boolean isHost() {
        return host;
    }

    public LevelHandshake handshake() {
        return level;
    }

    public TickObserver tickObserver() {
        return lockstep::publishChecksum;
    }

    /** Which ship this machine's pilot flies, and therefore whose career it writes down. */
    public int localSeat() {
        return localSeat;
    }

    /** The terms the fight begins on: seed, difficulty, level and every pilot's ship. */
    public LevelStart opening() {
        return opening;
    }

    /** Where two machines stopped agreeing, or null while they still do. */
    public Lockstep.Divergence divergence() {
        return lockstep.divergence();
    }

    /** A packet that arrived somewhere other than the gate: the lobby's read of the socket. */
    public void receive(byte[] packet) {
        lockstep.receive(packet);
    }

    /**
     * Leaves the match on purpose. The others are told the tick this pilot's input stops at, so
     * they carry on without a pause, and the relay frees the seat at once.
     */
    public void leave() {
        if (!level.handedOver() && !relay.closed()) {
            lockstep.leave();
        }
        relay.leave();
    }

    @Override
    public void close() {
        relay.close();
    }
}
