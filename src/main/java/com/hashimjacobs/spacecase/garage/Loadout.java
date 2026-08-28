package com.hashimjacobs.spacecase.garage;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.hashimjacobs.spacecase.asset.Sprite;

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
     * Version 2 moved upgrades out of fixed positions and into a keyed block, because the old
     * layout could not survive the catalogue growing. Version 1 wrote one field per upgrade in enum
     * order, so the paint job sat at whatever index came after the last upgrade -- append a sixth
     * upgrade and every existing record starts reading its paint out of an upgrade slot. Keying by
     * name means adding, removing or reordering upgrades never moves another field again.
     */
    private static final int FORMAT_VERSION = 2;

    private static final String SEPARATOR = ",";

    /** Separates upgrades inside their one field. Cannot be the comma; that is taken. */
    private static final String UPGRADE_SEPARATOR = "|";
    private static final String UPGRADE_ASSIGN = "=";

    /**
     * The five upgrades a version 1 record holds, in the order it holds them.
     *
     * Frozen history, deliberately not {@code Upgrade.values()}. What a v1 record means was decided
     * when it was written; deriving this from the live enum would re-interpret every old save every
     * time the catalogue changes, which is the exact bug version 2 exists to end.
     */
    private static final Upgrade[] V1_UPGRADE_ORDER = {
            Upgrade.FIREPOWER, Upgrade.FIRE_RATE, Upgrade.SPEED, Upgrade.SHIELDING, Upgrade.HULL,
    };

    private final Map<Upgrade, Integer> levels = new EnumMap<>(Upgrade.class);
    private final Set<Livery> unlockedLiveries = EnumSet.noneOf(Livery.class);
    private final Set<Kit> unlockedKits = EnumSet.noneOf(Kit.class);
    private final Set<Chassis> unlockedChassis = EnumSet.noneOf(Chassis.class);
    private Livery livery;
    private Kit kit = Kit.STOCK;
    private Chassis chassis = Chassis.STOCK;

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
        // Granted here rather than only through the mask, because a record written before the
        // chassis field existed carries no bits for it and still has to produce a flyable ship.
        unlockedChassis.add(Chassis.STOCK);
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
        String[] fields = code.split(SEPARATOR, -1);
        try {
            if (fields.length < 2) {
                return loadout;
            }
            int version = Integer.parseInt(fields[0].trim());
            int cosmeticsAt = switch (version) {
                case 1 -> readVersionOneUpgrades(loadout, fields);
                case FORMAT_VERSION -> readUpgrades(loadout, fields);
                default -> -1;
            };
            if (cosmeticsAt < 0) {
                return loadout;
            }
            int liveryOrdinal = valueAt(fields, cosmeticsAt);
            int kitOrdinal = valueAt(fields, cosmeticsAt + 1);
            loadout.unlockFromMask(valueAt(fields, cosmeticsAt + 2),
                    valueAt(fields, cosmeticsAt + 3), valueAt(fields, cosmeticsAt + 5));
            loadout.select(liveryFor(liveryOrdinal, playerNumber));
            loadout.select(kitFor(kitOrdinal));
            loadout.select(chassisFor(valueAt(fields, cosmeticsAt + 4)));
        } catch (NumberFormatException malformed) {
            // A hand-edited or truncated record. Stock is always a valid ship; refusing to start
            // the level would be a worse answer than losing the paint job.
            return stock(playerNumber);
        }
        return loadout;
    }

    /**
     * Reads the keyed upgrade block. Returns the index the cosmetics start at.
     *
     * An upgrade this build does not have is skipped rather than rejected, so a record written by a
     * newer version still yields everything else in it. One missing from the block reads as zero,
     * which is what lets encode omit the ones nobody has bought.
     */
    private static int readUpgrades(Loadout loadout, String[] fields) {
        String block = fields[1].trim();
        if (!block.isEmpty()) {
            for (String entry : block.split(java.util.regex.Pattern.quote(UPGRADE_SEPARATOR))) {
                int split = entry.indexOf(UPGRADE_ASSIGN);
                if (split <= 0) {
                    continue;
                }
                Upgrade upgrade = upgradeNamed(entry.substring(0, split).trim());
                if (upgrade == null) {
                    continue;
                }
                loadout.levels.put(upgrade, clampLevel(upgrade,
                        Integer.parseInt(entry.substring(split + 1).trim())));
            }
        }
        return 2;
    }

    /** Reads the five positional upgrade fields a version 1 record holds. */
    private static int readVersionOneUpgrades(Loadout loadout, String[] fields) {
        for (int i = 0; i < V1_UPGRADE_ORDER.length; i++) {
            loadout.levels.put(V1_UPGRADE_ORDER[i],
                    clampLevel(V1_UPGRADE_ORDER[i], valueAt(fields, 1 + i)));
        }
        return 1 + V1_UPGRADE_ORDER.length;
    }

    private static Upgrade upgradeNamed(String name) {
        for (Upgrade upgrade : Upgrade.values()) {
            if (upgrade.name().equals(name)) {
                return upgrade;
            }
        }
        return null;
    }

    /** Fields beyond the end of a shorter, older record read as zero rather than failing. */
    private static int valueAt(String[] fields, int index) {
        if (index >= fields.length) {
            return 0;
        }
        return Integer.parseInt(fields[index].trim());
    }

    /**
     * Holds a stored level inside the track's own ceiling.
     *
     * Per-upgrade rather than one global maximum: the tracks are different lengths now, so a record
     * naming level four of a two-level track -- hand-edited, or written when the ceilings differed --
     * must come back as two rather than as four.
     */
    private static int clampLevel(Upgrade upgrade, int level) {
        int clamped = Math.max(0, Math.min(upgrade.maxLevel(), level));
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

    /** Zero is the stock airframe, which is what a record written before the field had reads as. */
    private static Chassis chassisFor(int ordinal) {
        Chassis[] all = Chassis.values();
        if (ordinal < 0 || ordinal >= all.length) {
            return Chassis.STOCK;
        }
        return all[ordinal];
    }

    private void unlockFromMask(int liveryMask, int kitMask, int chassisMask) {
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
        Chassis[] frames = Chassis.values();
        for (int i = 0; i < frames.length; i++) {
            if ((chassisMask & (1 << i)) != 0) {
                unlockedChassis.add(frames[i]);
            }
        }
    }

    /** Round-trips through {@link #decode}. Kept short: it lives in a preferences value. */
    public String encode() {
        StringBuilder code = new StringBuilder().append(FORMAT_VERSION).append(SEPARATOR);
        // Only what has actually been bought. An absent upgrade reads as zero, so a pilot who has
        // spent nothing costs six characters rather than one per entry in the catalogue.
        boolean first = true;
        for (Upgrade upgrade : Upgrade.values()) {
            int level = level(upgrade);
            if (level <= 0) {
                continue;
            }
            if (!first) {
                code.append(UPGRADE_SEPARATOR);
            }
            code.append(upgrade.name()).append(UPGRADE_ASSIGN).append(level);
            first = false;
        }
        code.append(SEPARATOR).append(livery.ordinal());
        code.append(SEPARATOR).append(kit.ordinal());
        code.append(SEPARATOR).append(maskOf(unlockedLiveries, Livery.values().length));
        code.append(SEPARATOR).append(maskOf(unlockedKits, Kit.values().length));
        // Appended rather than versioned. decode's `default -> -1` throws a whole record away on an
        // unknown version, so bumping to 3 would make an older build lose a pilot's upgrades and
        // paint as well as their airframe -- where two extra fields are simply not read.
        code.append(SEPARATOR).append(chassis.ordinal());
        code.append(SEPARATOR).append(maskOf(unlockedChassis, Chassis.values().length));
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
        boolean maxed = level(upgrade) >= upgrade.maxLevel();
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

    public Chassis chassis() {
        return chassis;
    }

    /**
     * The frame this ship draws: the airframe, wearing the paint.
     *
     * The one place the stock-or-bought branch lives. Three callers need it -- the ship itself, the
     * garage turntable and the garage's browse -- and a second copy of that branch is how one of
     * them ends up drawing a hull the pilot is not flying.
     */
    public Sprite chassisPose(int leanIndex, boolean hit, boolean sideOn) {
        return chassis.pose(livery, leanIndex, hit, sideOn);
    }

    public boolean owns(Livery candidate) {
        boolean owned = unlockedLiveries.contains(candidate);
        return owned;
    }

    public boolean owns(Kit candidate) {
        boolean owned = unlockedKits.contains(candidate);
        return owned;
    }

    public boolean owns(Chassis candidate) {
        boolean owned = unlockedChassis.contains(candidate);
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

    public void unlock(Chassis bought) {
        unlockedChassis.add(bought);
        chassis = bought;
    }

    /** Wears something already owned. Ignores anything that is not, so it cannot grant by accident. */
    public void select(Livery chosen) {
        if (owns(chosen)) {
            livery = chosen;
        }
    }

    public void select(Chassis chosen) {
        if (owns(chosen)) {
            chassis = chosen;
        }
    }

    public void select(Kit chosen) {
        if (owns(chosen)) {
            kit = chosen;
        }
    }
}
