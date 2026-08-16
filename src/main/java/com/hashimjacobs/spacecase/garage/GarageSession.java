package com.hashimjacobs.spacecase.garage;

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

    /** The fixed rows in every bay, in the order they are drawn and cursored through. */
    private static final int UPGRADE_ROWS = Upgrade.values().length;
    private static final int LIVERY_ROW = UPGRADE_ROWS;
    private static final int KIT_ROW = UPGRADE_ROWS + 1;
    private static final int LAUNCH_ROW = UPGRADE_ROWS + 2;
    private static final int ROW_COUNT = UPGRADE_ROWS + 3;

    /** One pilot's identity and controls. Fixed for the life of the session. */
    public record Seat(String pilotName, Set<KeyCode> up, Set<KeyCode> down, Set<KeyCode> left,
                       Set<KeyCode> right, Set<KeyCode> buy) {
    }

    /** One drawable line in a bay. */
    public record Row(String label, String value, int cost, boolean affordable, boolean maxed) {
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
        private int liveryBrowse;
        private int kitBrowse;
        private boolean done;
        private String message = "";

        private Bay(Seat seat, int credits, Loadout loadout) {
            this.seat = seat;
            this.credits = credits;
            this.loadout = loadout;
            this.liveryBrowse = loadout.livery().ordinal();
            this.kitBrowse = loadout.kit().ordinal();
        }

        private Livery browsedLivery() {
            return Livery.values()[liveryBrowse];
        }

        private Kit browsedKit() {
            return Kit.values()[kitBrowse];
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
        // Escape and Enter belong to everyone: whatever the cursor is on, they mean "I am finished".
        // Without them a pilot who cursors away from LAUNCH would have no way out of the garage.
        if (code == KeyCode.ESCAPE || code == KeyCode.ENTER) {
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
        if (bay.cursor == LIVERY_ROW) {
            buyLivery(bay);
            return;
        }
        buyKit(bay);
    }

    private void buyUpgrade(Bay bay, Upgrade upgrade) {
        if (bay.loadout.isMaxed(upgrade)) {
            bay.message = upgrade.label() + " is maxed";
            return;
        }
        int cost = Upgrade.costFor(bay.loadout.level(upgrade));
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
            int cost = Upgrade.costFor(level);
            rows.add(new Row(upgrade.label(), pips(level), cost,
                    !maxed && cost <= bay.credits, maxed));
        }

        // The browsed item, not the worn one: an unowned paint job has to show its price to be
        // worth buying, and it is never worn until it is paid for.
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

    /** A filled-pip bar, which reads as a level at a glance where a bare number does not. */
    private static String pips(int level) {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < com.hashimjacobs.spacecase.GameConfig.UPGRADE_MAX_LEVEL; i++) {
            bar.append(i < level ? '#' : '.');
        }
        return bar.toString();
    }
}
