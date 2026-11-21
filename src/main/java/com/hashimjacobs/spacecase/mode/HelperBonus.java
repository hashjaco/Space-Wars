package com.hashimjacobs.spacecase.mode;

/**
 * What a pilot earns, on top of the level's own pay, for clearing a level in somebody else's group.
 *
 * Credits and career only, never score. The score on a ship is simulation state that every machine
 * must agree on and that the leaderboard's replay check re-derives, so anything added to it outside
 * the simulation would turn an honest run into one the verifier rejects. Career sets rank and
 * credits buy upgrades; both are this machine's own records, and both are safe to be generous with.
 *
 * @param credits    extra garage credits
 * @param career     extra career score, which is what a pilot's rank is read from
 * @param helpPoints what the level adds to the pilot's standing as a helper
 */
public record HelperBonus(int credits, int career, int helpPoints) {

    /** A helper is paid half the level's credits again, */
    private static final int CREDIT_SHARE = 2;
    /** and never less than this, so helping on an early level is still worth turning up for. */
    private static final int LEAST_CREDITS = 25;
    /** A quarter of the level's bonus again toward rank. */
    private static final int CAREER_SHARE = 4;

    /**
     * One level cleared for somebody else, whatever the level. Flat, and the same number the relay
     * adds to the helpers' table, so a pilot's own total and their row on the board move together.
     */
    public static final int HELP_POINTS = 100;

    /** The bonus for the level a debrief describes. Scales with how well the helper flew it. */
    public static HelperBonus of(Debrief debrief) {
        return new HelperBonus(
                Math.max(LEAST_CREDITS, debrief.credits() / CREDIT_SHARE),
                Math.max(0, debrief.totalBonus() / CAREER_SHARE),
                HELP_POINTS);
    }
}
