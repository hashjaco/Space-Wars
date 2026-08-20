package com.hashimjacobs.spacecase.garage;

import java.util.List;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * A body kit: bolt-on greebles drawn over the hull, whatever colour that hull happens to be.
 *
 * Purely cosmetic, deliberately. Naming one of them "Ablative Plates" and then having it not reduce
 * damage would be a lie, but the alternative -- cosmetics that also carry stats -- turns every
 * paint choice into a balance decision and stops anybody picking the one they like. The upgrades in
 * {@link Upgrade} are where stats are bought.
 *
 * Overlays rather than whole hulls, so one set of five frames serves all six liveries.
 */
public enum Kit {

    STOCK("Stock", 0, null),

    FINS("Delta Fins", 250,
            List.of(Sprite.KIT_FINS_BANK_LEFT, Sprite.KIT_FINS_LEFT, Sprite.KIT_FINS_STRAIGHT,
                    Sprite.KIT_FINS_RIGHT, Sprite.KIT_FINS_BANK_RIGHT)),

    ARMOUR("Ablative Plates", 300,
            List.of(Sprite.KIT_ARMOUR_BANK_LEFT, Sprite.KIT_ARMOUR_LEFT, Sprite.KIT_ARMOUR_STRAIGHT,
                    Sprite.KIT_ARMOUR_RIGHT, Sprite.KIT_ARMOUR_BANK_RIGHT)),

    LANCE("Nose Lance", 350,
            List.of(Sprite.KIT_LANCE_BANK_LEFT, Sprite.KIT_LANCE_LEFT, Sprite.KIT_LANCE_STRAIGHT,
                    Sprite.KIT_LANCE_RIGHT, Sprite.KIT_LANCE_BANK_RIGHT));

    private final String label;
    private final int cost;
    private final List<Sprite> overlays;

    Kit(String label, int cost, List<Sprite> overlays) {
        this.label = label;
        this.cost = cost;
        this.overlays = overlays;
    }

    /**
     * The decal for a bank pose, or null for {@link #STOCK}, which is the absence of a kit.
     *
     * Indexed by {@code entity.PlayerShip.Lean}'s ordinal, same as {@link Livery#pose}.
     */
    public Sprite overlay(int leanIndex, boolean sideOn) {
        if (overlays == null) {
            return null;
        }
        Sprite decal = overlays.get(leanIndex);
        return sideOn ? decal.sideOn() : decal;
    }

    public String label() {
        return label;
    }

    public int cost() {
        return cost;
    }

    public boolean isStock() {
        boolean free = cost == 0;
        return free;
    }
}
