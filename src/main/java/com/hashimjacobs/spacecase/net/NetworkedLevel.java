package com.hashimjacobs.spacecase.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.engine.LevelHandshake;
import com.hashimjacobs.spacecase.engine.World;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.SaveSlot;

/**
 * Settles the two things machines disagree about by the time a level ends: the clock, and the ships.
 *
 * Both disagreements are earned rather than accidental. Everything between two fights runs at each
 * machine's own pace on purpose -- see {@link LevelHandshake} -- so by the time the warp finishes,
 * one pilot has spent ninety seconds in the garage and another pressed through the debrief at once.
 * Their tick counters have drifted, and {@code world.tick()} is simulation input: enemy fire
 * patterns derive from it and the terrain scrolls by it. Meanwhile the garage has changed what each
 * ship can do, and a peer that did not hear about an upgrade simulates a different ship.
 *
 * So: everyone announces, carrying what they are flying; the host names the terms; everyone applies
 * them and begins together.
 *
 * <h2>What is not in the terms</h2>
 *
 * While the same pilots fly, the seed is sent but never changes, because the shared {@code Random}
 * is drawn from by exactly two things -- the spawn director and the collision system -- and both
 * run only inside a lockstepped tick. Nothing between two fights touches it: the music picks its
 * track from {@code SoundBank}'s own generator. So the stream cannot drift while the machines are
 * apart, and the director never has to be rebuilt.
 *
 * <h2>When the pilots change</h2>
 *
 * That stops being true the moment somebody joins. A newcomer has no world to carry on from and
 * cannot be sent one, so the host's terms then say everything -- a fresh seed, every ship's
 * counters, who is flying -- and every machine, not just the newcomer's, builds the next level
 * from them. A pilot who left is taken out the same way. See {@link Roster}.
 */
public final class NetworkedLevel implements LevelHandshake {

    /**
     * How far past the host's clock the next fight begins.
     *
     * Any agreed number would do; the simulation cares that the machines match, not what they
     * match on. A little ahead of the host rather than exactly on it so the number is one no peer
     * has just been using, which keeps a stray late packet from a tick that has already run from
     * landing in a ring slot the new level wants.
     */
    private static final int LEAD_TICKS = 60;

    /**
     * What the host tells the relay as a level begins: which one, and which seats are flying it.
     *
     * The relay repeats the level on the board and remembers the seats, which is how it knows whom
     * to credit when the host later says the level is won.
     */
    public static String stateLine(Level level, List<Integer> seats) {
        StringBuilder line = new StringBuilder("state,PLAYING,").append(level.name());
        for (int seat : seats) {
            line.append(',').append(seat);
        }
        return line.toString();
    }

    private final Lockstep lockstep;
    private final boolean host;
    private final long seed;
    private final Difficulty difficulty;
    private final Supplier<SaveSlot> checkpoint;
    private final Supplier<String> localLoadout;
    private final Roster roster;

    private boolean announced;
    private boolean termsSent;
    private boolean handedOver;

    /**
     * What the barrier needs to change who is flying. A match that never does -- two machines
     * wired together in a test, a room nobody joins or leaves -- passes {@link #fixed}.
     *
     * @param seats          the room slots flying now, in ship order
     * @param nextCheckpoint where the run will stand as the next level opens, each ship's
     *                       counters in ship order; null when this level ends the run
     * @param present        the slots connected to the room right now
     * @param maxPlayers     how many the group seats
     * @param control        says a line to the relay; see {@link RelayClient#sendControl}
     * @param onRosterChange handed the terms when they seat different pilots than are flying;
     *                       the caller rebuilds the game from them
     * @param onRemoved      run when the terms leave this machine's pilot out
     */
    public record Roster(List<Integer> seats, Supplier<SaveSlot> nextCheckpoint,
                         Supplier<List<Integer>> present, int maxPlayers, Consumer<String> control,
                         Consumer<LevelStart> onRosterChange, Runnable onRemoved) {

        public Roster {
            seats = List.copyOf(seats);
        }

        /** The same pilots from the first level to the last. */
        public static Roster fixed(List<Integer> seats) {
            return new Roster(seats, () -> null, () -> seats, seats.size(), line -> { },
                    terms -> { }, () -> { });
        }
    }

    /**
     * @param host         whether this machine names the terms; exactly one peer in a match may
     * @param checkpoint   where this run stands, for the terms to state authoritatively
     * @param localLoadout what this machine's pilot is flying, read after their garage closes
     */
    public NetworkedLevel(Lockstep lockstep, boolean host, long seed, Difficulty difficulty,
                          Supplier<SaveSlot> checkpoint, Supplier<String> localLoadout) {
        this(lockstep, host, seed, difficulty, checkpoint, localLoadout,
                Roster.fixed(lockstep.players()));
    }

    public NetworkedLevel(Lockstep lockstep, boolean host, long seed, Difficulty difficulty,
                          Supplier<SaveSlot> checkpoint, Supplier<String> localLoadout,
                          Roster roster) {
        this.lockstep = lockstep;
        this.host = host;
        this.seed = seed;
        this.difficulty = difficulty;
        this.checkpoint = checkpoint;
        this.localLoadout = localLoadout;
        this.roster = roster;
    }

    /** Whether this game has been told to stop: rebuilt around new terms, or left out of them. */
    public boolean handedOver() {
        return handedOver;
    }

    @Override
    public void cleared() {
        if (host) {
            roster.control().accept("cleared");
        }
    }

    @Override
    public boolean readyToFight(int level, World world) {
        if (handedOver) {
            return false;
        }
        if (!announced) {
            lockstep.ready(level, localLoadout.get());
            announced = true;
        }
        if (host && !termsSent && lockstep.everyoneReady()) {
            lockstep.start(terms(world));
            termsSent = true;
        }

        Optional<LevelStart> taken = lockstep.takeStart();
        if (lockstep.removed()) {
            handedOver = true;
            roster.onRemoved().run();
            return false;
        }
        if (taken.isEmpty()) {
            return false;
        }
        LevelStart terms = taken.get();
        if (!terms.seats().isEmpty() && !terms.seats().equals(roster.seats())) {
            // Somebody is joining or has gone. This game cannot become that one in place -- a ship
            // cannot be added to a world mid-run on four machines and be the same ship on each --
            // so every machine builds the next level from the terms alone, the way a match opens.
            // Answering false leaves this loop in its warp until the new one replaces it.
            handedOver = true;
            roster.onRosterChange().accept(terms);
            return false;
        }
        apply(terms, world);
        if (host) {
            SaveSlot next = roster.nextCheckpoint().get();
            if (next != null) {
                roster.control().accept(stateLine(next.level(), roster.seats()));
            }
        }
        // Reset for the next level rather than in some later callback, so a barrier that is never
        // reached again leaves nothing behind and one that is starts from a clean sheet.
        announced = false;
        termsSent = false;
        return true;
    }

    /**
     * The host's terms for the next level: the same pilots carrying on in place, or -- if anyone
     * has left, been dropped, or is waiting to join -- a new roster and everything a machine
     * needs to build the level from nothing.
     */
    private LevelStart terms(World world) {
        List<Integer> flying = roster.seats();
        Set<Integer> gone = lockstep.gone();
        List<Integer> present = roster.present().get();
        SaveSlot next = roster.nextCheckpoint().get();

        List<Integer> staying = new ArrayList<>();
        for (int seat : flying) {
            if (!gone.contains(seat)) {
                staying.add(seat);
            }
        }
        Map<Integer, String> asking = lockstep.joiners();
        List<Integer> joining = new ArrayList<>();
        for (int seat : asking.keySet().stream().sorted().toList()) {
            // Present, because a pilot who asked and then left the room is not coming.
            if (present.contains(seat) && !flying.contains(seat)
                    && staying.size() + joining.size() < roster.maxPlayers()) {
                joining.add(seat);
            }
        }
        // No next level means the run ends at this barrier, and there is nothing to rebuild for.
        if (next == null || (joining.isEmpty() && staying.equals(flying))) {
            return new LevelStart(seed, world.tick() + LEAD_TICKS, difficulty,
                    checkpoint.get(), flying, lockstep.readyLoadouts());
        }

        List<Integer> seats = new ArrayList<>(staying);
        seats.addAll(joining);
        seats.sort(null);
        List<PlayerShip.Progress> progress = new ArrayList<>();
        List<String> loadouts = new ArrayList<>();
        for (int seat : seats) {
            int ship = flying.indexOf(seat);
            boolean carried = ship >= 0 && ship < next.players().size();
            progress.add(carried ? next.players().get(ship) : NEWCOMER);
            loadouts.add(ship >= 0 ? lockstep.readyLoadout(seat) : asking.get(seat));
        }
        // A new seed with the new roster: the old generator's position was never sent anywhere
        // and a newcomer could not reach it, so every machine starts a fresh one together.
        return new LevelStart(new Random().nextLong(), world.tick() + LEAD_TICKS, difficulty,
                new SaveSlot(next.mode(), next.level(), next.wavesSurvived(), next.loop(), progress),
                seats, loadouts);
    }

    /**
     * A pilot joining mid-run: a fresh ship with a full set of lives and nothing on the scoreboard.
     * The health is more than any hull holds and is clamped to the fitted one when it is restored.
     */
    private static final PlayerShip.Progress NEWCOMER = new PlayerShip.Progress(
            Integer.MAX_VALUE, GameConfig.PLAYER_LIVES, 0, 0, 0, 0, 0, 0);

    /**
     * Puts this machine into the agreed state.
     *
     * Both halves matter and the second is the one that gets forgotten: realigning the clock is not
     * enough on its own, because a pilot who lingered in the garage has scrolled the terrain further
     * than one who did not, and the terrain pushes enemies around. Re-entering the level rebuilds it
     * from the level's own ordinal, which is the same on every machine.
     */
    private void apply(LevelStart terms, World world) {
        world.resumeAt(terms.resumeTick());
        world.enterLevel(terms.slot().level());

        world.fitLoadouts(terms.loadouts());
    }
}
