package com.hashimjacobs.spacecase.entity;

/**
 * One bolted-on piece of {@link PilotedMech}: an arm pod, or the cockpit with the pilot in it.
 *
 * Deliberately not a {@link BossHead}. A head is on a neck, and its whole character is the sweeping
 * -- reusing it here would give the rig two flailing arms and a cockpit that wandered off the front
 * of the machine. A part of a walking rig is bolted where it is bolted, so its position is simply an
 * offset from the body.
 *
 * The guarded flag is the fight. An arm is shot the moment the rig arrives; the cockpit refuses
 * damage until both arms are gone, so the sequence is forced: break the guard, then shoot the man.
 * Without it a lucky opening volley could end a galaxy finale in four seconds.
 */
public final class MechPart extends EnemyShip {

    private final EnemyShip body;
    private final com.hashimjacobs.spacecase.asset.BossArt art;
    private final double acrossOffset;
    private final double alongOffset;
    private final boolean guarded;
    private final BossPhase pattern;

    /**
     * @param acrossOffset sideways from the body's centre, as a fraction of the body's breadth
     * @param alongOffset  down-arena from the body's centre, as a fraction of its depth
     * @param guarded      true for the cockpit: refuses damage while any unguarded part still lives
     */
    MechPart(EnemyShip body, com.hashimjacobs.spacecase.asset.BossArt art,
             double width, double height, int health, int scoreValue,
             double acrossOffset, double alongOffset, boolean guarded, BossPhase pattern) {
        super(body.boss(), body.centerX(), body.centerY(), body.scale(),
                width, height, health, scoreValue);
        this.body = body;
        this.art = art;
        this.acrossOffset = acrossOffset;
        this.alongOffset = alongOffset;
        this.guarded = guarded;
        this.pattern = pattern;
    }

    @Override
    public boolean isBossPart() {
        return true;
    }

    /**
     * Its own frames, not the body's head art.
     *
     * The inherited rule gives every part of a flagship the same art, which is right for a hydra --
     * three identical heads -- and wrong for a rig, whose pieces are an arm, an arm and a cockpit.
     * Left inherited, both arms rendered as the pilot's canopy.
     */
    @Override
    public com.hashimjacobs.spacecase.asset.BossArt bossArt() {
        return art;
    }

    /** Whether this piece is what has to be broken before the cockpit can be touched. */
    boolean isGuard() {
        return !guarded;
    }

    /** The rig walks; a bolted part goes where the rig goes. */
    @Override
    public void trackAcross(Entity target) {
    }

    /**
     * The cockpit is sealed while the arms are up.
     *
     * Silently, on purpose: the hit still lands, still flashes, still plays. A shot that vanished
     * with no feedback would read as the collision being broken rather than as armour.
     */
    @Override
    public void takeDamage(int amount) {
        if (guarded && body.hasLivingGuard()) {
            return;
        }
        super.takeDamage(amount);
    }

    @Override
    public BossPhase phase() {
        return pattern;
    }

    /**
     * Deliberately does not call {@code super.update()}.
     *
     * The inherited station-keeping would haul each part to the flagship's own holding depth, which
     * is the body's depth -- so every part would pile up in the middle of the rig.
     */
    @Override
    public void update() {
        if (!body.isAlive()) {
            // Nothing outlives the rig, whichever order the pieces happen to die in.
            kill();
            return;
        }
        Orientation facing = body.orientation();
        double across = facing.across(body.centerX(), body.centerY())
                + acrossOffset * facing.acrossExtent(body.width(), body.height());
        double along = facing.depth(body.x(), body.y(), body.width(), body.height())
                + facing.alongExtent(body.width(), body.height()) / 2
                + alongOffset * facing.alongExtent(body.width(), body.height());
        setPosition(facing.atX(along, across, width(), height()) - facing.vx(0, width() / 2),
                facing.atY(along, across, width(), height()) - facing.vy(0, height() / 2));
    }
}
