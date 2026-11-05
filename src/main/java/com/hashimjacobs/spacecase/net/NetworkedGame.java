package com.hashimjacobs.spacecase.net;

import java.io.IOException;
import java.util.List;
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
    private final int localSeat;

    private NetworkedGame(RelayClient relay, Lockstep lockstep, NetworkedLevel level,
                          LevelStart opening, int localSeat) {
        this.relay = relay;
        this.lockstep = lockstep;
        this.level = level;
        this.opening = opening;
        this.localSeat = localSeat;
        this.host = relay.welcome().isHost();
    }

    /**
     * @param seats        the room's final roster, which fixes the lockstep's player numbers
     * @param sampleLocal  this machine's input for its own seat, from {@code GameLoop.sampleLocal}
     * @param checkpoint   where the run stands, for the host to state in each level's terms
     * @param localLoadout what this machine's pilot is flying, read after its garage closes
     */
    public static NetworkedGame begin(RelayClient relay, LevelStart opening, List<Integer> seats,
                                      Supplier<Intent> sampleLocal, Supplier<SaveSlot> checkpoint,
                                      Supplier<String> localLoadout) {
        int localSeat = relay.welcome().slot();
        boolean host = relay.welcome().isHost();

        int[] numbers = seats.stream().mapToInt(Integer::intValue).toArray();
        Lockstep lockstep = new Lockstep(localSeat, numbers, sampleLocal, relay::send);
        NetworkedLevel level = new NetworkedLevel(lockstep, host, opening.seed(),
                opening.difficulty(), checkpoint, localLoadout);
        return new NetworkedGame(relay, lockstep, level, opening, localSeat);
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
            relay.drainTo(lockstep::receive);
            if (!relay.isConnected()) {
                reconnectInBackground();
            }
            boolean ok = lockstep.canStep(tick);
            // Only the host decides a peer is gone, and only once the window has run out; every
            // machine then applies the same DROP from the same tick.
            if (!ok && host && lockstep.pastReconnectWindow()) {
                List<Integer> present = relay.peers();
                for (int player : lockstep.waitingFor()) {
                    if (!present.contains(player) && !lockstep.isDropped(player)) {
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
            names.append(names.isEmpty() ? "" : ", ").append("PILOT ").append(player);
        }
        return "WAITING FOR " + names + (lockstep.isDropped(waiting.get(0)) ? "" : "  " + secondsLeft + "s");
    }

    /** One attempt at a time, off the FX thread, with a short backoff between tries. */
    private void reconnectInBackground() {
        if (reconnecting) {
            return;
        }
        reconnecting = true;
        Thread.ofVirtual().start(() -> {
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
        });
    }

    public IntentSource intents() {
        return lockstep::intentFor;
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

    @Override
    public void close() {
        relay.close();
    }
}
