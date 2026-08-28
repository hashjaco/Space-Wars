package com.hashimjacobs.spacecase.garage;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * The airframe itself: a silhouette, and the only thing in the garage that changes how a ship flies.
 *
 * The split with {@link Livery} is that <strong>a chassis is a shape and a livery is a hue</strong>.
 * Every chassis wears every paint, so the two are orthogonal and the catalogue is a grid rather than
 * a list. {@link Kit} stays orthogonal to both and stays cosmetic, for the reason written there.
 *
 * Called Chassis and not Hull deliberately. {@code Upgrade.HULL} is already a thing you buy, the
 * prose in this package already says "hull" for a livery, and {@code GarageOverlay} has a local
 * variable of that name — a fourth meaning would be a tax on whoever reads this next.
 *
 * <p><strong>{@link #STOCK} is ordinal zero and that is load-bearing.</strong> A saved loadout
 * written before this enum existed has no chassis field at all, and {@code Loadout.valueAt} reads a
 * missing field as 0. Anything else at the front would hand every existing pilot a ship they never
 * bought.
 *
 * STOCK delegates its frames to the livery rather than owning any, which is what keeps today's
 * behaviour exactly: the two free liveries are different sheet rows, so for the stock airframe the
 * paint really does pick the shape. Only a bought chassis has a shape of its own, and then the paint
 * is only a hue.
 *
 * Pure data -- nothing here loads an image -- so the whole catalogue is walkable in tests that never
 * start the JavaFX toolkit.
 */
public enum Chassis {

    /** What everybody flies until they buy something. Frames come from the livery. */
    STOCK("Stock", 0, null, 1.00, 1.00, 1.00),

    /**
     * Narrow and drawn out. Quick, and it cannot take a hit.
     *
     * The health factor is what makes this a decision rather than a free upgrade: eighty points is
     * three enemy shots fewer than the stock frame, and the speed does not help against a ram.
     */
    INTERCEPTOR("Interceptor", 900, "INTERCEPTOR", 0.80, 1.20, 1.00),

    /** Broad and slow, and the only frame that can stand still in a lane and survive it. */
    GUNSHIP("Gunship", 900, "GUNSHIP", 1.20, 0.85, 1.00),

    /**
     * Two booms and a spine. Fires faster than anything else and is made of paper.
     *
     * Its reload factor scales the fire-rate <em>floor</em> as well as the interval -- see
     * {@code entity.PlayerShip.startFireCooldown}. Without that the last two levels of the fire-rate
     * track would both land on the flat floor and the second of them would buy nothing.
     */
    TWIN_BOOM("Twin-Boom", 1100, "TWIN_BOOM", 0.85, 1.00, 0.80);

    /**
     * The name fragments {@link #table} builds its {@code Sprite} constants from.
     *
     * A nested holder rather than statics on the enum, for the reason {@code Sprite.Draw} is one: a
     * constant cannot forward-reference a static of its own class, and every constant here runs
     * {@code table} in its constructor -- long before a plain static array would have been assigned.
     * Written flat first, and every chassis threw a NullPointerException out of class
     * initialisation.
     */
    private static final class Names {

        /**
         * Poses in {@code entity.PlayerShip.Lean}'s declaration order, hardest left to hardest
         * right. The same order {@link Livery#pose} and {@link Kit#overlay} index by.
         */
        static final String[] POSES = {"BANK_LEFT", "LEFT", "STRAIGHT", "RIGHT", "BANK_RIGHT"};

        /** Paints in {@link Livery}'s declaration order, which is how the table is indexed. */
        static final String[] PAINTS = {"MILITIA", "CORSAIR", "AZURE", "AMBER", "VIOLET", "CHROME"};

        private Names() {
        }
    }

    private final String label;
    private final int cost;
    private final Sprite[][][] frames;
    private final double healthFactor;
    private final double speedFactor;
    private final double reloadFactor;

    Chassis(String label, int cost, String spritePrefix,
            double healthFactor, double speedFactor, double reloadFactor) {
        this.label = label;
        this.cost = cost;
        this.frames = spritePrefix == null ? null : table(spritePrefix);
        this.healthFactor = healthFactor;
        this.speedFactor = speedFactor;
        this.reloadFactor = reloadFactor;
    }

    /**
     * The sixty frames a bought chassis flies in, resolved by name once at class load.
     *
     * Derived rather than declared, for the reason {@code Sprite.sideCuts} is: sixty constants per
     * chassis written out by hand would only be a second place for the generator's file names to
     * fall out of step with them. A missing frame fails here, loudly, at class initialisation --
     * which is the whole point of doing it up front rather than at draw time.
     */
    private static Sprite[][][] table(String prefix) {
        String[] paints = Names.PAINTS;
        String[] poses = Names.POSES;
        Sprite[][][] byPaint = new Sprite[paints.length][2][poses.length];
        for (int paint = 0; paint < paints.length; paint++) {
            for (int hit = 0; hit < 2; hit++) {
                for (int pose = 0; pose < poses.length; pose++) {
                    byPaint[paint][hit][pose] = Sprite.valueOf(prefix + "_" + paints[paint] + "_"
                            + poses[pose] + (hit == 1 ? "_HIT" : ""));
                }
            }
        }
        return byPaint;
    }

    /**
     * The frame this airframe flies, in this paint, at this bank.
     *
     * @param paint      which livery is worn. Ignored by nothing: even {@link #STOCK} reads it,
     *                   because for the stock airframe the livery is the shape as well as the hue.
     * @param leanIndex  {@code entity.PlayerShip.Lean}'s ordinal
     * @param sideOn     picks the turned cut, so a side-on level costs no second table
     */
    public Sprite pose(Livery paint, int leanIndex, boolean hit, boolean sideOn) {
        if (frames == null) {
            return paint.pose(leanIndex, hit, sideOn);
        }
        Sprite chosen = frames[paint.ordinal()][hit ? 1 : 0][leanIndex];
        return sideOn ? chosen.sideOn() : chosen;
    }

    /**
     * Multiplier on {@code GameConfig.PLAYER_HEALTH}, and on the shield a pickup is worth.
     *
     * The shield scales with it deliberately. {@code UpgradeBalanceTest} holds that a full shield
     * never soaks more than a full hull, and that inequality is only scale-free if a smaller frame
     * carries a smaller shield -- otherwise an interceptor's shield would outlast its own hull.
     */
    public double healthFactor() {
        return healthFactor;
    }

    /**
     * Multiplier on base speed, boosted and unboosted alike.
     *
     * Applied to the base rather than to the whole figure so the thrusters ladder stays additive and
     * the speed pickup stays an upgrade on every frame: the inequality
     * {@code PLAYER_SPEED * f + maxThrusters < PLAYER_SPEED_BOOSTED * f} holds for any f above
     * about 0.47, which is far below anything here.
     */
    public double speedFactor() {
        return speedFactor;
    }

    /** Multiplier on a weapon's fire interval and on the interval floor. Below one is faster. */
    public double reloadFactor() {
        return reloadFactor;
    }

    public String label() {
        return label;
    }

    public int cost() {
        return cost;
    }

    /** The free airframe, which every pilot owns from the start. */
    public boolean isStock() {
        boolean free = cost == 0;
        return free;
    }
}
