package com.hashimjacobs.spacecase.garage;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.hashimjacobs.spacecase.GameConfig;

/**
 * Everything a pilot has bought for their ship: upgrade levels, the paint job and kit they are
 * flying, and which of the locked ones they own.
 *
 * Encodes to a short comma-separated string so {@code prefs.Pilots} can keep it next to a career
 * score without knowing anything about the garage.
 *
 * {@link #decode} never throws. A preferences file is user-writable, survives upgrades of the game,
 * and is exactly the sort of thing that ends up half-written after a crash -- so anything it cannot
 * make sense of yields a stock ship rather than an exception on the way into a level. That
 * tolerance is also what lets a later version add a field without bricking today's saves.
 */
public final class Loadout {

    /**
     * Bumped only if the field order below changes incompatibly.
     *
     * Appending is not a breaking change: a shorter old record simply leaves the new trailing
     * fields at their defaults.
     */
    private static final int FORMAT_VERSION = 1;

    private static final String SEPARATOR = ",";

    private final Map<Upgrade, Integer> levels = new EnumMap<>(Upgrade.class);
    private final Set<Livery> unlockedLiveries = EnumSet.noneOf(Livery.class);
    private final Set<Kit> unlockedKits = EnumSet.noneOf(Kit.class);
    private Livery livery;
    private Kit kit = Kit.STOCK;

    private Loadout(Livery stock) {
        this.livery = stock;
        for (Upgrade upgrade : Upgrade.values()) {
            levels.put(upgrade, 0);
        }
        // Both stock hulls are owned by everyone: a pilot swapping seats should not have to re-buy
        // the ship they were already flying.
        for (Livery candidate : Livery.values()) {
            if (candidate.isStock()) {
                unlockedLiveries.add(candidate);
            }
        }
        unlockedKits.add(Kit.STOCK);
    }

    /** A brand-new ship for the given seat: no upgrades, stock paint, no kit. */
    public static Loadout stock(int playerNumber) {
        Loadout fresh = new Loadout(Livery.stockFor(playerNumber));
        return fresh;
    }

    /**
     * Reads back an {@link #encode}d loadout, falling back to stock on anything unrecognisable.
     *
     * @param code          what {@code prefs.Pilots} had stored; null and empty are both fine
     * @param playerNumber  which seat, for the stock livery when the code is unusable
     */
    public static Loadout decode(String code, int playerNumber) {
        Loadout loadout = stock(playerNumber);
        if (code == null || code.isBlank()) {
            return loadout;
        }
        String[] fields = code.split(SEPARATOR);
        try {
            if (fields.length < 2 || Integer.parseInt(fields[0].trim()) != FORMAT_VERSION) {
                return loadout;
            }
            Upgrade[] upgrades = Upgrade.values();
            for (int i = 0; i < upgrades.length; i++) {
                int level = valueAt(fields, 1 + i);
                loadout.levels.put(upgrades[i], clampLevel(level));
            }
            int liveryOrdinal = valueAt(fields, 1 + upgrades.length);
            int kitOrdinal = valueAt(fields, 2 + upgrades.length);
            loadout.unlockFromMask(valueAt(fields, 3 + upgrades.length),
                    valueAt(fields, 4 + upgrades.length));
            loadout.select(liveryFor(liveryOrdinal, playerNumber));
            loadout.select(kitFor(kitOrdinal));
        } catch (NumberFormatException malformed) {
            // A hand-edited or truncated record. Stock is always a valid ship; refusing to start
            // the level would be a worse answer than losing the paint job.
            return stock(playerNumber);
        }
        return loadout;
    }

    /** Fields beyond the end of a shorter, older record read as zero rather than failing. */
    private static int valueAt(String[] fields, int index) {
        if (index >= fields.length) {
            return 0;
        }
        return Integer.parseInt(fields[index].trim());
    }

    private static int clampLevel(int level) {
        int clamped = Math.max(0, Math.min(GameConfig.UPGRADE_MAX_LEVEL, level));
        return clamped;
    }

    private static Livery liveryFor(int ordinal, int playerNumber) {
        Livery[] all = Livery.values();
        if (ordinal < 0 || ordinal >= all.length) {
            return Livery.stockFor(playerNumber);
        }
        return all[ordinal];
    }

    private static Kit kitFor(int ordinal) {
        Kit[] all = Kit.values();
        if (ordinal < 0 || ordinal >= all.length) {
            return Kit.STOCK;
        }
        return all[ordinal];
    }

    private void unlockFromMask(int liveryMask, int kitMask) {
        Livery[] liveries = Livery.values();
        for (int i = 0; i < liveries.length; i++) {
            if ((liveryMask & (1 << i)) != 0) {
                unlockedLiveries.add(liveries[i]);
            }
        }
        Kit[] kits = Kit.values();
        for (int i = 0; i < kits.length; i++) {
            if ((kitMask & (1 << i)) != 0) {
                unlockedKits.add(kits[i]);
            }
        }
    }

    /** Round-trips through {@link #decode}. Kept short: it lives in a preferences value. */
    public String encode() {
        StringBuilder code = new StringBuilder().append(FORMAT_VERSION);
        for (Upgrade upgrade : Upgrade.values()) {
            code.append(SEPARATOR).append(level(upgrade));
        }
        code.append(SEPARATOR).append(livery.ordinal());
        code.append(SEPARATOR).append(kit.ordinal());
        code.append(SEPARATOR).append(maskOf(unlockedLiveries, Livery.values().length));
        code.append(SEPARATOR).append(maskOf(unlockedKits, Kit.values().length));
        return code.toString();
    }

    private static <E extends Enum<E>> int maskOf(Set<E> owned, int count) {
        int mask = 0;
        for (E member : owned) {
            if (member.ordinal() < count) {
                mask |= 1 << member.ordinal();
            }
        }
        return mask;
    }

    public int level(Upgrade upgrade) {
        int level = levels.getOrDefault(upgrade, 0);
        return level;
    }

    public boolean isMaxed(Upgrade upgrade) {
        boolean maxed = level(upgrade) >= GameConfig.UPGRADE_MAX_LEVEL;
        return maxed;
    }

    /** Buys one level. Charging for it is the caller's job; this only records the purchase. */
    public void raise(Upgrade upgrade) {
        if (isMaxed(upgrade)) {
            return;
        }
        levels.put(upgrade, level(upgrade) + 1);
    }

    public Livery livery() {
        return livery;
    }

    public Kit kit() {
        return kit;
    }

    public boolean owns(Livery candidate) {
        boolean owned = unlockedLiveries.contains(candidate);
        return owned;
    }

    public boolean owns(Kit candidate) {
        boolean owned = unlockedKits.contains(candidate);
        return owned;
    }

    /** Records a purchase and wears it straight away, which is what a buyer expects to see. */
    public void unlock(Livery bought) {
        unlockedLiveries.add(bought);
        livery = bought;
    }

    public void unlock(Kit bought) {
        unlockedKits.add(bought);
        kit = bought;
    }

    /** Wears something already owned. Ignores anything that is not, so it cannot grant by accident. */
    public void select(Livery chosen) {
        if (owns(chosen)) {
            livery = chosen;
        }
    }

    public void select(Kit chosen) {
        if (owns(chosen)) {
            kit = chosen;
        }
    }
}
