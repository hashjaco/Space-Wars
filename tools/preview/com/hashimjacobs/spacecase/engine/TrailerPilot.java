package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;

/**
 * Flies well enough to be filmed.
 *
 * Holds station near its home edge, sidesteps whatever is about to hit it, and otherwise slides
 * under the nearest hostile with the trigger down.
 *
 * Written in {@link Orientation}'s lane axes rather than in screen x and y, so the same forty lines
 * fly a side-view level as well as a top-down one. Six of the fifty levels run right-to-left, and a
 * bot written in x and y would fly backwards into the wall on every one of them.
 *
 * Deterministic and stateless: given the same world and the same tick it answers the same thing,
 * which is what makes a capture reproducible from its seed. Nothing here is remembered between
 * ticks, so there is no way for the run to drift.
 *
 * ponytail: no threat scoring, no evasion planning, no aim leading. It is a shop window, not an
 * opponent -- and a bot that dodges perfectly reads as inhuman on camera anyway. If a level needs
 * better flying than this, film a shorter burst of it rather than teaching this to play.
 */
final class TrailerPilot implements IntentSource {

    /** How far up-arena a bullet still matters. Past this it has time to be ignored. */
    private static final double THREAT_LEAD = 340;

    /** How near the lane a bullet has to be before it is worth stepping around. */
    private static final double THREAT_LANE = 64;

    /** How near an enemy hull has to be before it is worth flinching from rather than shooting. */
    private static final double CONTACT_LEAD = 170;

    /** How far to step when something is coming. Wider than the ship, narrower than the arena. */
    private static final double SIDESTEP = 130;

    /** Distance from the home edge to hold. Just ahead of where World.placeSpawns puts a ship. */
    private static final double STANDOFF = 150;

    /** Lane margin, so the ship never parks against a wall where half of it is off-frame. */
    private static final double MARGIN = 60;

    /** Divisor turning a distance into a fraction of full speed. Smaller is twitchier. */
    private static final double APPROACH = 40;

    private final World world;

    TrailerPilot(World world) {
        this.world = world;
    }

    @Override
    public Intent intentFor(int playerNumber, int tick) {
        PlayerShip me = null;
        for (PlayerShip player : world.players()) {
            if (player.playerNumber() == playerNumber) {
                me = player;
            }
        }
        if (me == null || me.isOut()) {
            return Intent.NEUTRAL;
        }

        Orientation o = world.orientation();
        double breadth = o.arenaBreadth();
        double home = o.arenaDepth() - STANDOFF;
        double across = o.across(me.centerX(), me.centerY());
        double depth = o.depth(me.x(), me.y(), me.width(), me.height());

        // The single most urgent thing about to hit me, if there is one.
        //
        // Two earlier versions died here. The first scanned bullets only, so the pilot flew into
        // asteroids and into the enemies it was lining up on -- a threat is anything that can hit
        // the ship, so the scan is over everything that can. The second summed a vote per threat,
        // which reads fine until a bolt arrives on each side: the votes cancel, the sum is zero,
        // and the ship holds its lane and takes both. Nearest-first has no such tie to lose.
        Entity worst = null;
        double worstLead = Double.MAX_VALUE;
        for (Bullet bullet : world.bullets()) {
            if (bullet.firedByPlayer()) {
                continue;
            }
            double lead = threatLead(o, across, depth, bullet, THREAT_LEAD);
            if (lead < worstLead) {
                worstLead = lead;
                worst = bullet;
            }
        }
        for (Asteroid rock : world.asteroids()) {
            double lead = threatLead(o, across, depth, rock, THREAT_LEAD);
            if (lead < worstLead) {
                worstLead = lead;
                worst = rock;
            }
        }
        // Enemies only count as something to avoid once they are nearly on top of the ship.
        // Any wider and the pilot would flinch away from the very thing it is trying to shoot.
        for (EnemyShip enemy : world.enemies()) {
            double lead = threatLead(o, across, depth, enemy, CONTACT_LEAD);
            if (lead < worstLead) {
                worstLead = lead;
                worst = enemy;
            }
        }

        double want;
        if (worst != null) {
            double gap = across - o.across(worst.centerX(), worst.centerY());
            // Dead level with it: break the tie rather than standing still and being hit.
            double away = gap >= 0 ? 1 : -1;
            want = across + away * SIDESTEP;
        } else {
            EnemyShip target = world.nearestEnemy(me);
            want = target != null
                    ? o.across(target.centerX(), target.centerY())
                    // Empty sky: drift, so the ship is never a statue in the middle of the frame.
                    : breadth / 2 + Math.sin(tick / 45.0) * breadth * 0.3;
        }
        want = Math.max(MARGIN, Math.min(breadth - MARGIN, want));

        double alongStep = clampUnit((home - depth) / APPROACH);
        double acrossStep = clampUnit((want - across) / APPROACH);
        // Normalised, or a diagonal outruns a cardinal -- the same rule ShipController.sample keeps.
        double length = Math.hypot(alongStep, acrossStep);
        if (length > 1) {
            alongStep /= length;
            acrossStep /= length;
        }

        // Held rather than tapped: the weapon has its own cooldown, so this is already a rhythm.
        return new Intent(o.vx(alongStep, acrossStep), o.vy(alongStep, acrossStep), true);
    }

    /**
     * How far up-arena a threat is, or {@code MAX_VALUE} if it is not on course to hit.
     *
     * Clearance grows with the threat's own width, so a bolt is stepped around by a hair and an
     * asteroid by its whole radius. Without that the pilot dodges a bullet and clips the rock
     * behind it.
     */
    private static double threatLead(Orientation o, double across, double depth, Entity threat,
                                     double reach) {
        double lead = depth - o.depth(threat.x(), threat.y(), threat.width(), threat.height());
        if (lead < 0 || lead > reach) {
            return Double.MAX_VALUE;
        }
        double clearance = THREAT_LANE + o.acrossExtent(threat.width(), threat.height()) / 2;
        if (Math.abs(across - o.across(threat.centerX(), threat.centerY())) > clearance) {
            return Double.MAX_VALUE;
        }
        return lead;
    }

    private static double clampUnit(double value) {
        return Math.max(-1, Math.min(1, value));
    }
}
