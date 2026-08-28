package com.hashimjacobs.spacecase.net;

import java.util.Optional;
import java.util.function.Supplier;

import com.hashimjacobs.spacecase.engine.LevelHandshake;
import com.hashimjacobs.spacecase.engine.World;
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
 * The seed is sent but never changes within a run, because the shared {@code Random} is drawn from
 * by exactly two things -- the spawn director and the collision system -- and both run only inside a
 * lockstepped tick. Nothing between two fights touches it: the music picks its track from
 * {@code SoundBank}'s own generator. So the stream cannot drift while the machines are apart, and
 * the director never has to be rebuilt.
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

    private final Lockstep lockstep;
    private final boolean host;
    private final long seed;
    private final Difficulty difficulty;
    private final Supplier<SaveSlot> checkpoint;
    private final Supplier<String> localLoadout;

    private boolean announced;
    private boolean termsSent;

    /**
     * @param host         whether this machine names the terms; exactly one peer in a match may
     * @param checkpoint   where this run stands, for the terms to state authoritatively
     * @param localLoadout what this machine's pilot is flying, read after their garage closes
     */
    public NetworkedLevel(Lockstep lockstep, boolean host, long seed, Difficulty difficulty,
                          Supplier<SaveSlot> checkpoint, Supplier<String> localLoadout) {
        this.lockstep = lockstep;
        this.host = host;
        this.seed = seed;
        this.difficulty = difficulty;
        this.checkpoint = checkpoint;
        this.localLoadout = localLoadout;
    }

    @Override
    public boolean readyToFight(int level, World world) {
        if (!announced) {
            lockstep.ready(level, localLoadout.get());
            announced = true;
        }
        if (host && !termsSent && lockstep.everyoneReady()) {
            lockstep.start(new LevelStart(seed, world.tick() + LEAD_TICKS, difficulty,
                    checkpoint.get(), lockstep.readyLoadouts()));
            termsSent = true;
        }

        Optional<LevelStart> terms = lockstep.takeStart();
        if (terms.isEmpty()) {
            return false;
        }
        apply(terms.get(), world);
        // Reset for the next level rather than in some later callback, so a barrier that is never
        // reached again leaves nothing behind and one that is starts from a clean sheet.
        announced = false;
        termsSent = false;
        return true;
    }

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
