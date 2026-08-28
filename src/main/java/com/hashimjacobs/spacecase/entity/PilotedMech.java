package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.BossArt;

/**
 * Vaunt's rig: a walking machine with a man in the front of it.
 *
 * The galaxy's finale, and the first fight in the game against a person rather than a thing. The
 * shape of it is three pieces and one rule. Two arm pods hang off the body and are shot first --
 * that much the multi-part machinery already did, since a flagship refuses damage while any part
 * lives. The new rule is the cockpit: a third part that refuses damage of its own while either arm
 * survives, so the fight cannot be short-circuited by shooting the pilot through the guard.
 *
 * It walks rather than flies. Position is a pure function of age, the same discipline
 * {@link BurrowingWorm} keeps and for the same two reasons: no state to restore when a run is
 * resumed, and no draw from the spawn director's generator, which would shift every subsequent
 * spawn and break the fixed-seed run its tests are pinned against.
 */
public final class PilotedMech extends EnemyShip {

    /** How far across the arena the rig strides, as a fraction of the lane. */
    private static final double STRIDE_REACH = 0.30;

    /**
     * Ticks for one full traverse and back. Still heavy, but no longer ponderous.
     *
     * Was 420 -- seven seconds to cross and seven back, which was slow enough that a player could
     * pick a corner and reload in it. STRIDE_REACH is untouched, so the rig covers exactly the same
     * ground; it just stops giving you as long to stand still in.
     */
    private static final double STRIDE_TICKS = 300;

    /** Where the rig holds station, as a fraction of the arena's depth. */
    private static final double HOLD_DEPTH = 0.20;

    /** Arm pods take this much of the flagship's health between them, the cockpit the rest. */
    private static final double ARM_SHARE = 0.34;
    private static final double COCKPIT_SHARE = 0.11;

    private int age;

    public PilotedMech(Boss boss, double x, double y, double scale) {
        super(boss, x, y, scale);

        // The inherited constructor fits every multi-part flagship with heads on necks, which is
        // the wrong shape for a rig. Swapped here rather than branched on inside EnemyShip: the
        // superclass has no business knowing which of its subclasses wants what.
        parts().clear();

        int total = (int) Math.round(boss.health() * scale);
        int armHealth = (int) Math.round(total * ARM_SHARE / 2);
        int cockpitHealth = (int) Math.round(total * COCKPIT_SHARE);
        BossArt glass = boss.headArt();
        // Data, not a constant. This was BossArt.FORGE_RIG_ARM outright, which meant a second rig
        // of any size wore Ashfall's 78-pixel pods on a body scaled past them.
        BossArt arm = boss.armArt();

        for (int side = -1; side <= 1; side += 2) {
            parts().add(new MechPart(this, arm, arm.width(), arm.height(), armHealth,
                    Math.max(1, boss.scoreValue() / 6),
                    side * 0.30, -0.05, false, BossPhase.AIMED_BURST));
        }
        // Last, so it draws over the arms rather than under them.
        parts().add(new MechPart(this, glass, glass.width(), glass.height(), cockpitHealth,
                Math.max(1, boss.scoreValue() / 4),
                0, 0.18, true, BossPhase.SPREAD));
    }

    /** The stride owns where this thing is; there is nothing to track. */
    @Override
    public void trackAcross(Entity target) {
    }

    /**
     * Deliberately does not call {@code super.update()}: the inherited clamp is station-keeping for
     * a flagship that holds a line, and this one paces along one.
     */
    @Override
    public void update() {
        age++;
        Orientation facing = orientation();
        double breadth = facing.arenaBreadth();
        double lane = breadth * 0.5
                + StrictMath.sin(2 * Math.PI * age / STRIDE_TICKS) * breadth * STRIDE_REACH;
        double across = lane - facing.acrossExtent(width(), height()) / 2;
        double depth = facing.arenaDepth() * HOLD_DEPTH;
        setPosition(facing.atX(depth, across, width(), height()),
                facing.atY(depth, across, width(), height()));
    }
}
