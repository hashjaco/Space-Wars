package com.hashimjacobs.spacecase.garage;

import java.util.List;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * A hull paint job: the twenty frames a ship flies in, and what they cost.
 *
 * This replaces the private skin enum that used to live inside {@code entity.PlayerShip}. Same
 * shape -- two parallel pose lists, one clean and one scorched -- with a price and four more
 * choices bolted on. The two stock liveries are free and start unlocked, one per seat, so a pilot
 * who never visits the garage flies exactly what they always did.
 *
 * Pure data: nothing here loads an image, so the enum is usable in tests that never start JavaFX.
 */
public enum Livery {

    /** Player one's stock hull. */
    MILITIA_GREEN("Militia Green", 0,
            List.of(Sprite.P1_BANK_LEFT, Sprite.P1_LEFT, Sprite.P1_STRAIGHT,
                    Sprite.P1_RIGHT, Sprite.P1_BANK_RIGHT),
            List.of(Sprite.P1_BANK_LEFT_HIT, Sprite.P1_LEFT_HIT, Sprite.P1_STRAIGHT_HIT,
                    Sprite.P1_RIGHT_HIT, Sprite.P1_BANK_RIGHT_HIT)),

    /** Player two's stock hull. */
    CORSAIR_RED("Corsair Red", 0,
            List.of(Sprite.P2_BANK_LEFT, Sprite.P2_LEFT, Sprite.P2_STRAIGHT,
                    Sprite.P2_RIGHT, Sprite.P2_BANK_RIGHT),
            List.of(Sprite.P2_BANK_LEFT_HIT, Sprite.P2_LEFT_HIT, Sprite.P2_STRAIGHT_HIT,
                    Sprite.P2_RIGHT_HIT, Sprite.P2_BANK_RIGHT_HIT)),

    ION_BLUE("Ion Blue", 150,
            List.of(Sprite.AZURE_BANK_LEFT, Sprite.AZURE_LEFT, Sprite.AZURE_STRAIGHT,
                    Sprite.AZURE_RIGHT, Sprite.AZURE_BANK_RIGHT),
            List.of(Sprite.AZURE_BANK_LEFT_HIT, Sprite.AZURE_LEFT_HIT, Sprite.AZURE_STRAIGHT_HIT,
                    Sprite.AZURE_RIGHT_HIT, Sprite.AZURE_BANK_RIGHT_HIT)),

    SOLAR_AMBER("Solar Amber", 150,
            List.of(Sprite.AMBER_BANK_LEFT, Sprite.AMBER_LEFT, Sprite.AMBER_STRAIGHT,
                    Sprite.AMBER_RIGHT, Sprite.AMBER_BANK_RIGHT),
            List.of(Sprite.AMBER_BANK_LEFT_HIT, Sprite.AMBER_LEFT_HIT, Sprite.AMBER_STRAIGHT_HIT,
                    Sprite.AMBER_RIGHT_HIT, Sprite.AMBER_BANK_RIGHT_HIT)),

    VOID_VIOLET("Void Violet", 200,
            List.of(Sprite.VIOLET_BANK_LEFT, Sprite.VIOLET_LEFT, Sprite.VIOLET_STRAIGHT,
                    Sprite.VIOLET_RIGHT, Sprite.VIOLET_BANK_RIGHT),
            List.of(Sprite.VIOLET_BANK_LEFT_HIT, Sprite.VIOLET_LEFT_HIT, Sprite.VIOLET_STRAIGHT_HIT,
                    Sprite.VIOLET_RIGHT_HIT, Sprite.VIOLET_BANK_RIGHT_HIT)),

    CHROME("Chrome", 250,
            List.of(Sprite.CHROME_BANK_LEFT, Sprite.CHROME_LEFT, Sprite.CHROME_STRAIGHT,
                    Sprite.CHROME_RIGHT, Sprite.CHROME_BANK_RIGHT),
            List.of(Sprite.CHROME_BANK_LEFT_HIT, Sprite.CHROME_LEFT_HIT, Sprite.CHROME_STRAIGHT_HIT,
                    Sprite.CHROME_RIGHT_HIT, Sprite.CHROME_BANK_RIGHT_HIT));

    private final String label;
    private final int cost;
    private final List<Sprite> poses;
    private final List<Sprite> scorched;

    Livery(String label, int cost, List<Sprite> poses, List<Sprite> scorched) {
        this.label = label;
        this.cost = cost;
        this.poses = poses;
        this.scorched = scorched;
    }

    /**
     * The frame for a bank pose.
     *
     * Takes the ordinal rather than {@code entity.PlayerShip.Lean} so this package stays clear of
     * {@code entity} -- the dependency runs the other way. Both lists are therefore in that enum's
     * declaration order, hardest left to hardest right, and must stay that way.
     */
    public Sprite pose(int leanIndex, boolean hit) {
        List<Sprite> set = hit ? scorched : poses;
        Sprite chosen = set.get(leanIndex);
        return chosen;
    }

    public String label() {
        return label;
    }

    public int cost() {
        return cost;
    }

    /** Free liveries are the two stock hulls, which every pilot already owns. */
    public boolean isStock() {
        boolean free = cost == 0;
        return free;
    }

    /** What a pilot in this seat flies before buying anything. */
    public static Livery stockFor(int playerNumber) {
        Livery stock = playerNumber == 1 ? MILITIA_GREEN : CORSAIR_RED;
        return stock;
    }
}
