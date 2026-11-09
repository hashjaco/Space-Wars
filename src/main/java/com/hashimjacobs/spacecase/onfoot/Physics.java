package com.hashimjacobs.spacecase.onfoot;

/**
 * Every number that decides how being on foot feels, in pixels and ticks at 60 steps a second.
 *
 * One record so a feel pass edits one place. These are the calibration knobs: they were set by
 * playing, not derived, and they should keep being set by playing.
 *
 * @param gravity        downward acceleration while airborne
 * @param juggleGravity  lighter gravity for a fighter launched by a hit, so a combo can follow it up
 * @param maxFall        terminal fall speed
 * @param walkSpeed      top speed for the first {@code runAfter} ticks of holding a direction
 * @param runSpeed       top speed after that
 * @param runAfter       ticks of holding a direction before a walk becomes a run
 * @param groundAccel    how fast a grounded fighter reaches top speed
 * @param friction       velocity kept per tick on the ground with no input (and while sliding from a hit)
 * @param airAccel       steering in the air, much less than on the ground
 * @param airDrag        velocity kept per tick in the air
 * @param jumpSpeed      launch speed of a full jump
 * @param jumpCut        how much upward speed is kept when jump is released early (variable height)
 * @param coyoteTicks    a jump still works this long after walking off a ledge
 * @param bufferTicks    a press made this early still counts once the fighter can act
 * @param wallBounce     fraction of speed kept off an arena wall, above {@code bounceSpeed}
 * @param groundBounce   the same off the floor, for a fighter slammed down
 * @param bounceSpeed    below this a fighter stops at a wall or the floor instead of bouncing
 * @param juggleDecay    each extra air hit multiplies the next launch by this, so juggles end
 */
public record Physics(double gravity, double juggleGravity, double maxFall,
                      double walkSpeed, double runSpeed, int runAfter, double groundAccel,
                      double friction, double airAccel, double airDrag,
                      double jumpSpeed, double jumpCut, int coyoteTicks, int bufferTicks,
                      double wallBounce, double groundBounce, double bounceSpeed, double juggleDecay) {

    public static final Physics DEFAULT = new Physics(0.66, 0.46, 17,
            3.6, 6.4, 22, 0.75,
            0.80, 0.42, 0.985,
            14.2, 0.45, 6, 8,
            0.45, 0.35, 8, 0.78);
}
