package com.hashimjacobs.spacecase.engine;

/**
 * What one player is asking their ship to do on one tick, after their own settings have been read.
 *
 * The boundary between what is local and what is simulated. Deadzone, analog on or off, stick
 * sensitivity and key bindings are all per-machine preferences, and every one of them sits upstream
 * of this record -- {@link ShipController#sample} applies them and produces the vector below. So two
 * players on different sensitivities push the stick differently and their ships still move
 * identically, which is the property networked play rests on. Put any of those settings downstream
 * of here and the two machines diverge on the first frame somebody moves.
 *
 * Toolkit- and native-free on purpose, the same reason {@link PadState} is: it has to survive being
 * written to a socket and read back on a machine with a different keyboard, a different pad, and no
 * idea what {@code KeyCode} the sender pressed.
 *
 * ponytail: raw doubles on the wire, ~21 bytes per player per tick, ~5 KB/s for four players.
 * Packing them into bytes was the original design and is wrong: at 8 bits the worst velocity error
 * is 2.8e-2 against AnalogStickTest's 1e-4 tolerance, and even 16 bits misses at 1.1e-4. Anything
 * narrower than 32-bit fixed point changes how the ship handles. Bandwidth here is not scarce;
 * quantise only if that ever stops being true, and never below 32 bits.
 *
 * @param moveX fraction of full speed across the screen, already normalised so a diagonal is not
 *              faster than a cardinal
 * @param moveY same, down the screen; positive is downward, following the screen and the stick
 */
public record Intent(double moveX, double moveY, boolean firing) {

    /** A player who is not touching anything, and what a dropped peer's ship is given. */
    public static final Intent NEUTRAL = new Intent(0, 0, false);
}
