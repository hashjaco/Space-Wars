package com.hashimjacobs.spacecase.entity;

/**
 * A hidden data core, one of three a level carries for its challenges.
 *
 * Collecting one changes nothing a ship or score reads, so cores are outside the checksum and a
 * replay re-scores the same whether or not anyone touched them. Drawn by the renderer from shapes.
 */
public final class Core extends Entity {

    public static final double SIZE = 30;

    private final int index;

    public Core(int index, double x, double y) {
        super(null, x, y, SIZE, SIZE);
        this.index = index;
    }

    /** Which of the level's three this is, from zero. */
    public int index() {
        return index;
    }
}
