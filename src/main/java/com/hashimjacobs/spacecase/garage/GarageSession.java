package com.hashimjacobs.spacecase.garage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javafx.scene.input.KeyCode;
import com.hashimjacobs.spacecase.ui.MenuWindow;

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

    /** The fixed rows in every bay, in the order they are drawn and cursored through. */
    private static final int UPGRADE_ROWS = Upgrade.values().length;
    private static final int CHASSIS_ROW = UPGRADE_ROWS;
    private static final int LIVERY_ROW = UPGRADE_ROWS + 1;
    private static final int KIT_ROW = UPGRADE_ROWS + 2;
    private static final int LAUNCH_ROW = UPGRADE_ROWS + 3;
    private static final int ROW_COUNT = UPGRADE_ROWS + 4;

    /**
     * Rows a bay shows at once.
     *
     * Fourteen is every row the catalogue currently has -- eleven upgrades plus paint, kit and
     * launch -- and the taller panel has room for all of them, so nothing scrolls today. The window
     * is here as the safety net rather than as machinery in use: the first upgrade past this number
     * starts a bay scrolling on its own instead of quietly pushing LAUNCH off the bottom, which is
     * how a player would lose the way out of the garage.
     */
    private static final int VISIBLE_ROWS = 14;

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
                      int level, int maxLevel, String detail) {

        /** The shape the cosmetic and launch rows use: a value, no ladder. */
        Row(String label, String value, int cost, boolean affordable, boolean maxed) {
            this(label, value, cost, affordable, maxed, 0, 0, "");
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
        private int cursor;
        private final MenuWindow window = new MenuWindow(ROW_COUNT, VISIBLE_ROWS);
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
                cycle(bay, -1);
                return true;
            }
            if (bay.seat.right().contains(code)) {
                cycle(bay, 1);
                return true;
            }
            if (bay.seat.buy().contains(code)) {
                buy(bay);
                return true;
            }
        }
        return false;
    }

    private void move(Bay bay, int delta) {
        bay.message = "";
        bay.cursor = Math.floorMod(bay.cursor + delta, ROW_COUNT);
        bay.window.follow(bay.cursor);
    }

    /** First row on screen in this bay. {@link #rows} still returns all of them. */
    public int firstVisibleRow(int bay) {
        return bays.get(bay).window.firstVisible();
    }

    public int visibleRows(int bay) {
        return bays.get(bay).window.visibleRows();
    }

    /** Whether this bay has more rows than it can show, so the overlay draws a scroll track. */
    public boolean scrolls(int bay) {
        return bays.get(bay).window.scrolls();
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
        if (bay.cursor == CHASSIS_ROW) {
            bay.chassisBrowse = Math.floorMod(bay.chassisBrowse + delta, Chassis.values().length);
            bay.loadout.select(bay.browsedChassis());
            return;
        }
        if (bay.cursor == LIVERY_ROW) {
            bay.liveryBrowse = Math.floorMod(bay.liveryBrowse + delta, Livery.values().length);
            bay.loadout.select(bay.browsedLivery());
            return;
        }
        if (bay.cursor == KIT_ROW) {
            bay.kitBrowse = Math.floorMod(bay.kitBrowse + delta, Kit.values().length);
            bay.loadout.select(bay.browsedKit());
        }
    }

    private void buy(Bay bay) {
        bay.message = "";
        if (bay.cursor == LAUNCH_ROW) {
            bay.done = true;
            return;
        }
        if (bay.cursor < UPGRADE_ROWS) {
            buyUpgrade(bay, Upgrade.values()[bay.cursor]);
            return;
        }
        if (bay.cursor == CHASSIS_ROW) {
            buyChassis(bay);
            return;
        }
        if (bay.cursor == LIVERY_ROW) {
            buyLivery(bay);
            return;
        }
        // Spelled out rather than left as the fall-through it used to be. With three browsable rows
        // instead of two, "anything that is not launch, an upgrade or paint" stopped being a safe
        // way to say "the kit row", and a mis-ordered constant would have bought the wrong thing
        // with nothing to report it.
        if (bay.cursor == KIT_ROW) {
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

    public int cursor(int bay) {
        return bays.get(bay).cursor;
    }

    public boolean done(int bay) {
        return bays.get(bay).done;
    }

    public String message(int bay) {
        return bays.get(bay).message;
    }

    public static int launchRow() {
        return LAUNCH_ROW;
    }

    /** Everything one bay draws, top to bottom, already priced against what the pilot can afford. */
    public List<Row> rows(int index) {
        Bay bay = bays.get(index);
        List<Row> rows = new ArrayList<>(ROW_COUNT);
        for (Upgrade upgrade : Upgrade.values()) {
            int level = bay.loadout.level(upgrade);
            boolean maxed = bay.loadout.isMaxed(upgrade);
            int cost = upgrade.costFor(level);
            String detail = maxed
                    ? upgrade.effectAt(level, bay.loadout.chassis())
                    : upgrade.effectAt(level, bay.loadout.chassis()) + "  ->  "
                            + upgrade.effectAt(level + 1, bay.loadout.chassis());
            rows.add(new Row(upgrade.label(), upgrade.description(), cost,
                    !maxed && cost <= bay.credits, maxed,
                    level, upgrade.maxLevel(), detail));
        }

        // The browsed item, not the worn one: an unowned paint job has to show its price to be
        // worth buying, and it is never worn until it is paid for.
        //
        // This block's order has to match the row constants above, and nothing enforces that.
        Chassis chassis = bay.browsedChassis();
        boolean chassisOwned = bay.loadout.owns(chassis);
        rows.add(new Row("Airframe", chassis.label(), chassisOwned ? 0 : chassis.cost(),
                chassisOwned || chassis.cost() <= bay.credits, chassisOwned));

        Livery livery = bay.browsedLivery();
        boolean liveryOwned = bay.loadout.owns(livery);
        rows.add(new Row("Paint", livery.label(), liveryOwned ? 0 : livery.cost(),
                liveryOwned || livery.cost() <= bay.credits, liveryOwned));

        Kit kit = bay.browsedKit();
        boolean kitOwned = bay.loadout.owns(kit);
        rows.add(new Row("Body kit", kit.label(), kitOwned ? 0 : kit.cost(),
                kitOwned || kit.cost() <= bay.credits, kitOwned));

        rows.add(new Row("LAUNCH", "", 0, true, false));
        return Collections.unmodifiableList(rows);
    }

}
