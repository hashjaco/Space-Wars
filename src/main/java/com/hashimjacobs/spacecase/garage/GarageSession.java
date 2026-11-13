package com.hashimjacobs.spacecase.garage;

import com.hashimjacobs.spacecase.engine.GamepadMapping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javafx.scene.input.KeyCode;

/**
 * The between-levels garage, as rules rather than pixels.
 *
 * Holds one bay per pilot and turns keystrokes into purchases. Both pilots shop at the same time,
 * each driving their own cursor with the keys they fly with, so nobody waits their turn at a shared
 * keyboard. Drawing is {@code engine.GarageOverlay}'s job; this knows nothing about a canvas.
 *
 * Key sets arrive as plain {@code KeyCode} sets rather than as {@code engine.PlayerControls},
 * because {@code engine} already depends on this package and the reverse would close a cycle.
 * {@code KeyCode} itself is a bare enum, so this class still tests without a JavaFX toolkit.
 */
public final class GarageSession {

    /**
     * The sidebar's categories, cycled with left and right on the tab strip at its top.
     *
     * The looks get a tab of their own, so browsing a frame, a paint job or a kit sits right next to
     * the ship it changes; the upgrades are split by {@link Upgrade.Category}, which existed for
     * exactly this and was read by nothing. Mobility and utility share a tab: three rows between them
     * would make two thin tabs. LAUNCH ends every tab, so the way out is never a tab away.
     */
    public enum Tab {
        SHIP("Ship"), WEAPONS("Weapons"), DEFENSE("Defense"), SYSTEMS("Systems");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Whether this tab lists the upgrade. */
        public boolean holds(Upgrade upgrade) {
            return switch (this) {
                case SHIP -> false;
                case WEAPONS -> upgrade.category() == Upgrade.Category.WEAPONS;
                case DEFENSE -> upgrade.category() == Upgrade.Category.DEFENSE;
                case SYSTEMS -> upgrade.category() == Upgrade.Category.MOBILITY
                        || upgrade.category() == Upgrade.Category.UTILITY;
            };
        }
    }

    /** What a row is, so the cursor can be acted on without fixed row numbers. */
    private enum Slot { CHASSIS, LIVERY, KIT, UPGRADE, LAUNCH }

    private record Entry(Slot slot, Upgrade upgrade) {
    }

    /** The rows of a tab, in order, ending in LAUNCH. */
    private static List<Entry> entries(Tab tab) {
        List<Entry> entries = new ArrayList<>();
        if (tab == Tab.SHIP) {
            entries.add(new Entry(Slot.CHASSIS, null));
            entries.add(new Entry(Slot.LIVERY, null));
            entries.add(new Entry(Slot.KIT, null));
        }
        for (Upgrade upgrade : Upgrade.values()) {
            if (tab.holds(upgrade)) {
                entries.add(new Entry(Slot.UPGRADE, upgrade));
            }
        }
        entries.add(new Entry(Slot.LAUNCH, null));
        return entries;
    }

    /** One pilot's identity and controls. Fixed for the life of the session. */
    public record Seat(String pilotName, Set<KeyCode> up, Set<KeyCode> down, Set<KeyCode> left,
                       Set<KeyCode> right, Set<KeyCode> buy) {
    }

    /**
     * One drawable line in a bay.
     *
     * The first five components are what the row has always carried; the rest are for the drawn
     * segment meter that replaced the ASCII pip bar. {@code level} and {@code maxLevel} are 0 on the
     * cosmetic and launch rows, which have no ladder, and {@code detail} is the before-and-after
     * readout shown only on the focused row.
     */
    public record Row(String label, String value, int cost, boolean affordable, boolean maxed,
                      int level, int maxLevel, String detail, Upgrade upgrade) {

        /** The shape the cosmetic and launch rows use: a value, no ladder, no icon. */
        Row(String label, String value, int cost, boolean affordable, boolean maxed) {
            this(label, value, cost, affordable, maxed, 0, 0, "", null);
        }
    }

    /**
     * A pilot's mutable half: what they have, where they are looking, whether they are done.
     *
     * The two browse indices are the catalogue position, which is not the same as what the ship is
     * wearing. They have to be separate: you browse onto a locked paint job in order to buy it, and
     * a worn-only model would refuse to move there and leave nothing to buy.
     */
    private static final class Bay {
        private final Seat seat;
        private final Loadout loadout;
        private int credits;
        private Tab tab = Tab.SHIP;
        /** 0 is the tab strip; 1 onward are the tab's rows, LAUNCH last. */
        private int position;
        private int chassisBrowse;
        private int liveryBrowse;
        private int kitBrowse;
        private boolean done;
        private String message = "";

        private Bay(Seat seat, int credits, Loadout loadout) {
            this.seat = seat;
            this.credits = credits;
            this.loadout = loadout;
            this.chassisBrowse = loadout.chassis().ordinal();
            this.liveryBrowse = loadout.livery().ordinal();
            this.kitBrowse = loadout.kit().ordinal();
        }

        private Livery browsedLivery() {
            return Livery.values()[liveryBrowse];
        }

        private Kit browsedKit() {
            return Kit.values()[kitBrowse];
        }

        private Chassis browsedChassis() {
            return Chassis.values()[chassisBrowse];
        }

        /** The row under the cursor, or null on the tab strip. */
        private Entry entry() {
            return position == 0 ? null : entries(tab).get(position - 1);
        }

        private Slot slot() {
            Entry entry = entry();
            return entry == null ? null : entry.slot();
        }
    }

    private final List<Bay> bays = new ArrayList<>();

    public GarageSession(List<Seat> seats, List<Integer> credits, List<Loadout> loadouts) {
        for (int i = 0; i < seats.size(); i++) {
            bays.add(new Bay(seats.get(i), credits.get(i), loadouts.get(i)));
        }
    }

    /**
     * Routes a keystroke to whichever bay owns it.
     *
     * @return true when a bay consumed it, so the caller knows not to pass it on to the ships
     */
    public boolean handleKey(KeyCode code) {
        // Escape belongs to everyone: whatever the cursor is on, it means "I am finished". Without
        // it a pilot who cursors away from LAUNCH would have no way out of the garage.
        //
        // Enter is deliberately not a second hatch here, though every menu treats it as confirm. A
        // pad's fire button sends the player's fire key plus a menu confirm tap, and player two's
        // confirm is Enter -- so buying anything from a pad launched the whole crew out of the
        // garage on the same press. The bays are driven by each seat's own keys; the shared confirm
        // keys have no business in them.
        if (code == KeyCode.ESCAPE) {
            boolean any = false;
            for (Bay bay : bays) {
                any |= !bay.done;
                bay.done = true;
            }
            return any || !bays.isEmpty();
        }
        for (Bay bay : bays) {
            if (bay.done) {
                continue;
            }
            if (bay.seat.up().contains(code)) {
                move(bay, -1);
                return true;
            }
            if (bay.seat.down().contains(code)) {
                move(bay, 1);
                return true;
            }
            if (bay.seat.left().contains(code)) {
                sideways(bay, -1);
                return true;
            }
            if (bay.seat.right().contains(code)) {
                sideways(bay, 1);
                return true;
            }
            if (bay.seat.buy().contains(code)) {
                buy(bay);
                return true;
            }
        }
        // A pad direction nobody took -- its bay has launched -- still stops here. Unclaimed, it
        // would be offered again as an arrow key, and the arrows are the other seat's cursor.
        return GamepadMapping.menuKey(code) != code;
    }

    private void move(Bay bay, int delta) {
        bay.message = "";
        bay.position = Math.floorMod(bay.position + delta, entries(bay.tab).size() + 1);
    }

    /** On the tab strip left and right change tab; on a look they browse it. */
    private void sideways(Bay bay, int delta) {
        if (bay.position == 0) {
            bay.message = "";
            bay.tab = Tab.values()[Math.floorMod(bay.tab.ordinal() + delta, Tab.values().length)];
            return;
        }
        cycle(bay, delta);
    }

    /**
     * Left and right browse the catalogue, and only on the two cosmetic rows.
     *
     * Browsing onto something already owned wears it immediately, so a pilot flipping between paint
     * jobs they own sees each one on the spinning ship. Something unowned is previewed but not
     * worn -- it is not theirs yet -- and buying is what commits it.
     */
    private void cycle(Bay bay, int delta) {
        bay.message = "";
        if (bay.slot() == Slot.CHASSIS) {
            bay.chassisBrowse = Math.floorMod(bay.chassisBrowse + delta, Chassis.values().length);
            bay.loadout.select(bay.browsedChassis());
            return;
        }
        if (bay.slot() == Slot.LIVERY) {
            bay.liveryBrowse = Math.floorMod(bay.liveryBrowse + delta, Livery.values().length);
            bay.loadout.select(bay.browsedLivery());
            return;
        }
        if (bay.slot() == Slot.KIT) {
            bay.kitBrowse = Math.floorMod(bay.kitBrowse + delta, Kit.values().length);
            bay.loadout.select(bay.browsedKit());
        }
    }

    private void buy(Bay bay) {
        bay.message = "";
        Slot slot = bay.slot();
        if (slot == null) {
            return;   // fire on the tab strip buys nothing
        }
        if (slot == Slot.LAUNCH) {
            bay.done = true;
            return;
        }
        if (slot == Slot.UPGRADE) {
            buyUpgrade(bay, bay.entry().upgrade());
            return;
        }
        if (slot == Slot.CHASSIS) {
            buyChassis(bay);
            return;
        }
        if (slot == Slot.LIVERY) {
            buyLivery(bay);
            return;
        }
        // Spelled out rather than left as the fall-through it used to be. With three browsable rows
        // instead of two, "anything that is not launch, an upgrade or paint" stopped being a safe
        // way to say "the kit row", and a mis-ordered constant would have bought the wrong thing
        // with nothing to report it.
        if (slot == Slot.KIT) {
            buyKit(bay);
        }
    }

    private void buyUpgrade(Bay bay, Upgrade upgrade) {
        if (bay.loadout.isMaxed(upgrade)) {
            bay.message = upgrade.label() + " is maxed";
            return;
        }
        int cost = upgrade.costFor(bay.loadout.level(upgrade));
        if (!charge(bay, cost)) {
            return;
        }
        bay.loadout.raise(upgrade);
    }

    /**
     * On the cosmetic rows, buying means unlocking whatever is being previewed.
     *
     * Something already owned is free to put back on, which is why this checks ownership before
     * price: re-selecting a paint job you already bought must never charge for it twice.
     */
    private void buyLivery(Bay bay) {
        Livery chosen = bay.browsedLivery();
        if (bay.loadout.owns(chosen)) {
            bay.loadout.select(chosen);
            return;
        }
        if (!charge(bay, chosen.cost())) {
            return;
        }
        bay.loadout.unlock(chosen);
    }

    private void buyChassis(Bay bay) {
        Chassis chosen = bay.browsedChassis();
        if (bay.loadout.owns(chosen)) {
            bay.loadout.select(chosen);
            return;
        }
        if (!charge(bay, chosen.cost())) {
            return;
        }
        bay.loadout.unlock(chosen);
    }

    private void buyKit(Bay bay) {
        Kit chosen = bay.browsedKit();
        if (bay.loadout.owns(chosen)) {
            bay.loadout.select(chosen);
            return;
        }
        if (!charge(bay, chosen.cost())) {
            return;
        }
        bay.loadout.unlock(chosen);
    }

    private boolean charge(Bay bay, int cost) {
        if (cost > bay.credits) {
            bay.message = "Not enough credits";
            return false;
        }
        bay.credits -= cost;
        return true;
    }

    /** True once every pilot has launched. Trivially true when nobody is shopping. */
    public boolean everyoneDone() {
        for (Bay bay : bays) {
            if (!bay.done) {
                return false;
            }
        }
        return true;
    }

    public int bayCount() {
        return bays.size();
    }

    public String pilotName(int bay) {
        return bays.get(bay).seat.pilotName();
    }

    public int credits(int bay) {
        return bays.get(bay).credits;
    }

    public Loadout loadout(int bay) {
        return bays.get(bay).loadout;
    }

    /** What the ship on the stand wears: the worn loadout, with the row under the cursor swapped in. */
    public record Look(Chassis chassis, Livery livery, Kit kit, boolean owned) {
    }

    /**
     * The preview: whatever the pilot is browsing on the row the cursor is on, owned or not, over
     * what they are wearing. A paint job or a kit has to be seen on the hull before it is bought,
     * which is the whole point of a garage; moving off the row puts the worn one back.
     */
    public Look look(int index) {
        Bay bay = bays.get(index);
        Loadout worn = bay.loadout;
        Chassis chassis = bay.slot() == Slot.CHASSIS ? bay.browsedChassis() : worn.chassis();
        Livery livery = bay.slot() == Slot.LIVERY ? bay.browsedLivery() : worn.livery();
        Kit kit = bay.slot() == Slot.KIT ? bay.browsedKit() : worn.kit();
        boolean owned = worn.owns(chassis) && worn.owns(livery) && worn.owns(kit);
        return new Look(chassis, livery, kit, owned);
    }

    /** Index into {@link #rows} of the row under the cursor, or -1 while it is on the tab strip. */
    public int cursor(int bay) {
        return bays.get(bay).position - 1;
    }

    public Tab tab(int bay) {
        return bays.get(bay).tab;
    }

    public boolean done(int bay) {
        return bays.get(bay).done;
    }

    public String message(int bay) {
        return bays.get(bay).message;
    }

    /** The current tab's rows, top to bottom, LAUNCH last, priced against what the pilot can afford. */
    public List<Row> rows(int index) {
        Bay bay = bays.get(index);
        List<Row> rows = new ArrayList<>();
        // The browsed item, not the worn one: an unowned paint job has to show its price to be
        // worth buying, and it is never worn until it is paid for.
        for (Entry entry : entries(bay.tab)) {
            rows.add(switch (entry.slot()) {
                case CHASSIS -> {
                    Chassis chassis = bay.browsedChassis();
                    boolean owned = bay.loadout.owns(chassis);
                    // The airframe is the one look that changes how the ship flies, so it says how.
                    String frame = String.format("Hull %d%%  Speed %d%%  Rate %d%%  Special: %s",
                            Math.round(chassis.healthFactor() * 100), Math.round(chassis.speedFactor() * 100),
                            Math.round(100 / chassis.reloadFactor()), chassis.specialLabel());
                    yield new Row("Airframe", chassis.label(), owned ? 0 : chassis.cost(),
                            owned || chassis.cost() <= bay.credits, owned, 0, 0, frame, null);
                }
                case LIVERY -> {
                    Livery livery = bay.browsedLivery();
                    boolean owned = bay.loadout.owns(livery);
                    yield new Row("Paint", livery.label(), owned ? 0 : livery.cost(),
                            owned || livery.cost() <= bay.credits, owned);
                }
                case KIT -> {
                    Kit kit = bay.browsedKit();
                    boolean owned = bay.loadout.owns(kit);
                    yield new Row("Body kit", kit.label(), owned ? 0 : kit.cost(),
                            owned || kit.cost() <= bay.credits, owned);
                }
                case UPGRADE -> {
                    Upgrade upgrade = entry.upgrade();
                    int level = bay.loadout.level(upgrade);
                    boolean maxed = bay.loadout.isMaxed(upgrade);
                    int cost = upgrade.costFor(level);
                    String detail = maxed
                            ? upgrade.effectAt(level, bay.loadout.chassis())
                            : upgrade.effectAt(level, bay.loadout.chassis()) + "  ->  "
                                    + upgrade.effectAt(level + 1, bay.loadout.chassis());
                    yield new Row(upgrade.label(), upgrade.description(), cost,
                            !maxed && cost <= bay.credits, maxed,
                            level, upgrade.maxLevel(), detail, upgrade);
                }
                case LAUNCH -> new Row("LAUNCH", "", 0, true, false);
            });
        }
        return Collections.unmodifiableList(rows);
    }
}
