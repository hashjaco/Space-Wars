package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.EnemyShip.EnemyKind;

/**
 * One hostile as ordered: an archetype, where it comes in, and how it differs from that archetype.
 *
 * Every difference is a multiplier rather than an absolute, so an authored ship can never be wildly
 * off -- {@code .health(1.3)} is a third tougher than whatever a cruiser is this month, and stays a
 * third tougher when {@link EnemyKind} is retuned. The hull it wears is never named here: that comes
 * from {@code mode.Level.enemySprite}, which is what keeps a wave themed without a wave having to
 * know which level it is in.
 *
 * In {@code entity} rather than {@code mode} for the reason {@link Boss} is: it is authored data
 * that {@code entity} itself consumes -- {@link EnemyShip} takes one -- and pointing {@code entity}
 * at {@code mode} would close a package cycle.
 *
 * Bands worth staying inside, none of them enforced by the type: size 0.8-1.5, health 0.6-1.6,
 * speed 0.6-1.5, fireGap 0.5-2.0. Past those a row stops being a variation on an archetype and
 * becomes a boss nobody authored a health bar or a bounty for. {@code WavesTest} holds much looser
 * bounds than these -- it is a typo guard, not a taste guard.
 *
 * Pure data, no asset loading, so the wave tables are readable in tests that never start the
 * JavaFX toolkit.
 */
public record WaveShip(EnemyKind kind, double across, double setback,
                       double size, double health, double speed, double fireGap,
                       int shots, MoveStyle move) {

    /** A ship exactly as its archetype flies, arriving at the entry edge. */
    public static WaveShip at(EnemyKind kind, double across) {
        return at(kind, across, 0);
    }

    /**
     * @param across  position across the lane as a fraction of {@code Orientation.arenaBreadth()},
     *                0 to 1. A fraction and never pixels, because the breadth is 996 top-down and
     *                864 side-on, so a pixel written for one level is in the wrong place in the
     *                other -- the same reason {@code BurrowingWorm}'s reach is a fraction.
     * @param setback pixels further back than the entry edge; zero or negative. This is how a
     *                formation gets its depth -- a wedge is six ships at three setbacks -- and it
     *                needs no timer, because depth already is one.
     */
    public static WaveShip at(EnemyKind kind, double across, double setback) {
        return new WaveShip(kind, across, setback, 1, 1, 1, 1, 1, MoveStyle.HUNT);
    }

    /** Hull scale. Moves the drawn sprite and the collision box together; they are one number. */
    public WaveShip size(double factor) {
        return new WaveShip(kind, across, setback, factor, health, speed, fireGap, shots, move);
    }

    /** Hull points, multiplying with the difficulty preset's own scaling. */
    public WaveShip health(double factor) {
        return new WaveShip(kind, across, setback, size, factor, speed, fireGap, shots, move);
    }

    /**
     * How fast it comes down the lane and slides across it, together.
     *
     * Slowing is always honoured; speeding up is capped with the preset's, in
     * {@code EnemyShip.ENEMY_SPEED_SCALE_CAP}, for the reason written there -- a hostile that closes
     * faster than the player can leave has no answer at all, and that is as true of an authored one
     * as of a preset-scaled one.
     */
    public WaveShip speed(double factor) {
        return new WaveShip(kind, across, setback, size, health, factor, fireGap, shots, move);
    }

    /**
     * Multiplier on the gap between its shots. <strong>Below one fires faster</strong>, as
     * {@code EnemyKind.fireFactor} does, and for the same reason: the preset names the gap and
     * everything else is read as a share of it.
     */
    public WaveShip fireGap(double factor) {
        return new WaveShip(kind, across, setback, size, health, speed, factor, shots, move);
    }

    /** How many shots go out at once, in a narrow fan. Capped in {@code engine.EnemyWeapons}. */
    public WaveShip shots(int count) {
        return new WaveShip(kind, across, setback, size, health, speed, fireGap, count, move);
    }

    /**
     * The same ship somewhere else, keeping every tuning it was authored with.
     *
     * For {@code mode.Waves.group}, which builds a wave's second and third groups by densifying the
     * authored row and pushing it deeper. Everything but the position is carried, so an infill ship
     * is the archetype its neighbour was rather than a stock one.
     */
    public WaveShip movedTo(double across, double setback) {
        return new WaveShip(kind, across, setback, size, health, speed, fireGap, shots, move);
    }

    /** How it flies. The default is {@link MoveStyle#HUNT}, which is what every enemy always did. */
    public WaveShip move(MoveStyle style) {
        return new WaveShip(kind, across, setback, size, health, speed, fireGap, shots, style);
    }

    /**
     * Shorthands for the four styles a wave row reaches for most.
     *
     * Sugar, and deliberately shallow -- {@code .move(MoveStyle.STRAIGHT)} says the same thing.
     * Twelve hundred authored ships is enough that the noise in a row is worth removing, and a row
     * that reads as a sentence is a row somebody will check.
     */
    public WaveShip straight() {
        return move(MoveStyle.STRAIGHT);
    }

    public WaveShip weaving() {
        return move(MoveStyle.WEAVE);
    }

    public WaveShip diving() {
        return move(MoveStyle.DIVE);
    }

    public WaveShip holding() {
        return move(MoveStyle.HOLD);
    }

    public WaveShip drifting() {
        return move(MoveStyle.DRIFT);
    }

    /**
     * This ship's box, from the hull the level supplies.
     *
     * Here rather than at each call site because two places need it and they have to agree: the
     * constructor, which sets the box, and whatever places the ship, which has to leave room for
     * it. Placing from {@code art.width()} while the ship is drawn at 1.4 of that is how a wave
     * spawns half a cruiser inside a wall.
     */
    public double width(Sprite art) {
        return art.width() * size;
    }

    public double height(Sprite art) {
        return art.height() * size;
    }
}
