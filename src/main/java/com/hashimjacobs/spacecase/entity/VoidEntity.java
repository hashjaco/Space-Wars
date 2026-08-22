package com.hashimjacobs.spacecase.entity;

/**
 * A flagship that orbits the middle of the arena instead of holding a line at the top of it.
 *
 * One of them: Aeon, the Hollow Star, at the end of the campaign. Every other boss in the game
 * either keeps station and tracks sideways or charges and withdraws; this one is always moving and
 * never approaches, so the fight is about the arena rather than about the line the boss sits on.
 *
 * The orbit is a pure function of {@link #age} -- the discipline {@link BurrowingWorm} and
 * {@link PilotedMech} both keep. It costs no state beyond the tick count, it cannot drift out of
 * agreement with itself, and it is the reason this class needs no fixup when a fight is paused.
 *
 * Every distance is asked of {@link Orientation}, so the class holds no axis of its own. Level 50
 * runs top-down and nothing here knows that.
 *
 * <p>The eyes come free. {@link EnemyShip}'s constructor builds a {@link BossHead} per head and a
 * head re-reads its body's centre every tick, so four separately shootable eyes follow an orbiting
 * body with no code in this class at all.
 */
public final class VoidEntity extends EnemyShip {

    /**
     * Ticks for one lap. Slow on purpose: this is the last fight in the game and the arena is the
     * puzzle, so the orbit has to be readable far enough ahead to be flown around.
     */
    private static final double ORBIT_TICKS = 520;

    /**
     * Half the width of the orbit, across the lane.
     *
     * Nothing like the largest number that keeps the body on screen, which would be 348: what has
     * to stay inside the arena is the eyes, and their envelope is both much wider than the body and
     * <em>not centred on it</em>. {@link BossHead} spreads its sectors symmetrically but lengthens
     * every neck by {@code NECK_LENGTH_STEP} per index, so the outermost eye on the +across side is
     * also the one on the longest neck: measured, the eyes reach 298 to that side of the body's
     * centre and 221 to the other.
     *
     * So the binding constraint is {@code 498 + radius + 298 <= 996}, which caps this at 199. The
     * first pass estimated 201 of neck reach, put this at 240, and swung an eye 40 past the right
     * wall; AeonTest caught it, which is the entire reason that test steps the parts rather than
     * just the body. At 180 the eyes span 97 to 976 of 996 and the body 168 to 828.
     */
    private static final double ACROSS_RADIUS = 180;

    /**
     * The orbit along the arena, and the tightest number in this class.
     *
     * Shallow next to the width, so the orbit reads as a sweep with a lean in it rather than as a
     * circle, and shallow for a hard reason: <em>the eyes decide this, not the body.</em>
     *
     * A {@code BossArt}'s declared size is the collision box, so Aeon is a 300x300 box, and the
     * body alone would clear the player's spawn line at a far deeper orbit than this. But the eyes
     * hang off the leading edge. A neck roots at {@code (SOCKET_DEPTH - 0.5)} of the body's extent,
     * 84 ahead of centre, reaches {@code REACH_MIN + 3 * NECK_LENGTH_STEP} plus its full swing, 312,
     * and carries a 64 box whose far edge is 32 further: 428 ahead of the body's centre.
     *
     * The player spawns at {@code arenaDepth() - 130}, which is 734 in a top-down arena. So the
     * deepest the centre may orbit is 306, and 210 + 60 puts it at 270. Measured, the deepest any
     * eye actually reaches is 656 -- 78 short of the player's line, rather than the 36 the estimate
     * above predicts, because an eye is never at full stretch down-arena at the same moment the
     * body is at the bottom of its orbit. The estimate is the safe bound and the measurement is the
     * truth; both are comfortably the right side of fair.
     *
     * AeonTest holds all of it, because none of it is guarded by the arithmetic itself and every
     * constant it depends on lives in another class.
     */
    private static final double ALONG_RADIUS = 60;
    private static final double ALONG_CENTRE = 210;

    private int age;

    /**
     * The ordinary flagship constructor, unlike {@link BurrowingWorm}'s, and that is the whole
     * saving.
     *
     * That one takes the protected parts-less constructor because a worm is deliberately one
     * hitbox. This one wants what the inherited constructor already does: it sizes the box from
     * {@code boss.art()}, hands the body its {@code bodyShare} of the authored health rather than
     * all of it, and builds a {@link BossHead} per declared head. Aeon's four eyes cost this line.
     */
    public VoidEntity(Boss boss, double x, double y, double scale) {
        super(boss, x, y, scale);
    }

    /** The orbit owns where this thing is; there is nothing to track. */
    @Override
    public void trackAcross(Entity target) {
    }

    /**
     * Deliberately does not call {@code super.update()}: the inherited clamp is station-keeping for
     * a boss that holds a line, and this one is never on one.
     */
    @Override
    public void update() {
        age++;
        Orientation facing = orientation();
        double along = alongAt(age);
        double across = acrossAt(age);
        setPosition(facing.atX(along, across, width(), height()),
                facing.atY(along, across, width(), height()));
    }

    /** How far along the arena the body's leading edge is at a given age. */
    private double alongAt(double at) {
        return ALONG_CENTRE + Math.sin(angleAt(at)) * ALONG_RADIUS
                - orientation().alongExtent(width(), height()) / 2;
    }

    /** How far across the lane the body's near edge is at a given age. */
    private double acrossAt(double at) {
        return orientation().arenaBreadth() * 0.5 + Math.cos(angleAt(at)) * ACROSS_RADIUS
                - orientation().acrossExtent(width(), height()) / 2;
    }

    private static double angleAt(double at) {
        return 2 * Math.PI * at / ORBIT_TICKS;
    }
}
