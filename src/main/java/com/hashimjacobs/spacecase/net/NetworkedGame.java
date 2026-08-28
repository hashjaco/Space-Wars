package com.hashimjacobs.spacecase.net;

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
    public IntPredicate stepGate() {
        return tick -> {
            relay.drainTo(lockstep::receive);
            return lockstep.canStep(tick);
        };
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
