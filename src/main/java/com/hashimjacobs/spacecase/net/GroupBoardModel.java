package com.hashimjacobs.spacecase.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * The game board: which groups are open, which of them the pilot wants to see, and what each row
 * of the screen says about it.
 *
 * No JavaFX here, for the reason {@link LobbyModel} gives. The screen has a fixed number of rows
 * and this decides what is written on each, so the view never changes shape as groups come and go
 * and a pilot's cursor is not thrown about by somebody else opening a room.
 */
public final class GroupBoardModel {

    /** How many groups the screen shows at once. With the filters and actions it fills the panel. */
    public static final int ROWS = 6;

    /** How often the board asks again. Listings change when people join, which is not often. */
    private static final long REFRESH_NANOS = 5_000_000_000L;

    public enum State {
        /** Nothing has come back yet. */
        LOADING,
        READY,
        /** The relay did not answer. The last list, if any, is still shown. */
        FAILED
    }

    private State state = State.LOADING;
    private List<GroupListing> all = List.of();
    private Galaxy galaxy;
    private Difficulty difficulty;
    private boolean openOnly;
    private long lastAsked = Long.MIN_VALUE;
    private boolean asking;

    public State state() {
        return state;
    }

    /**
     * Whether it is time to ask the relay again. False while a request is still out, so a slow
     * relay is asked once rather than once per frame.
     */
    public boolean refreshDue(long nowNanos) {
        return !asking && (lastAsked == Long.MIN_VALUE || nowNanos - lastAsked >= REFRESH_NANOS);
    }

    public void asked(long nowNanos) {
        asking = true;
        lastAsked = nowNanos;
    }

    /** Asks again at the next opportunity, for the Refresh row and for a changed filter. */
    public void refreshNow() {
        lastAsked = Long.MIN_VALUE;
    }

    public void loaded(List<GroupListing> listings) {
        all = List.copyOf(listings);
        state = State.READY;
        asking = false;
    }

    public void failed() {
        state = State.FAILED;
        asking = false;
    }

    /** Null, meaning any, then each galaxy in turn. */
    public void cycleGalaxy(int direction) {
        galaxy = cycle(Galaxy.values(), galaxy, direction);
    }

    public void cycleDifficulty(int direction) {
        difficulty = cycle(Difficulty.values(), difficulty, direction);
    }

    public void toggleOpenOnly() {
        openOnly = !openOnly;
    }

    /** One step through "any" followed by every value, wrapping at both ends. */
    private static <T extends Enum<T>> T cycle(T[] values, T current, int direction) {
        int at = current == null ? 0 : current.ordinal() + 1;
        int next = Math.floorMod(at + (direction < 0 ? -1 : 1), values.length + 1);
        return next == 0 ? null : values[next - 1];
    }

    public String galaxyLabel() {
        return galaxy == null ? "ANY" : galaxy.label().toUpperCase();
    }

    public String difficultyLabel() {
        return difficulty == null ? "ANY" : difficulty.label().toUpperCase();
    }

    public String openOnlyLabel() {
        return openOnly ? "OPEN SEATS ONLY" : "ALL GROUPS";
    }

    /** The relay's galaxy filter for the next request, or "" for any. */
    public String galaxyQuery() {
        return galaxy == null ? "" : galaxy.name();
    }

    public String difficultyQuery() {
        return difficulty == null ? "" : difficulty.name();
    }

    /**
     * The groups that pass the filters, in the relay's order: those with a seat first.
     *
     * Filtered here as well as by the relay, so changing a filter redraws at once from the list in
     * hand rather than showing the old answer until the new one arrives.
     */
    public List<GroupListing> visible() {
        List<GroupListing> shown = new ArrayList<>();
        for (GroupListing listing : all) {
            if ((galaxy == null || listing.galaxy() == galaxy)
                    && (difficulty == null || listing.difficulty() == difficulty)
                    && (!openOnly || listing.isOpen())) {
                shown.add(listing);
            }
        }
        return shown;
    }

    /** The group on a row of the screen, or nothing for a row past the end of the list. */
    public Optional<GroupListing> at(int row) {
        List<GroupListing> shown = visible();
        return row >= 0 && row < ROWS && row < shown.size()
                ? Optional.of(shown.get(row)) : Optional.empty();
    }

    /** Whether pressing a row would do anything: there is a group on it with a seat free. */
    public boolean canJoin(int row) {
        return at(row).map(GroupListing::isOpen).orElse(false);
    }

    /** One line under the list saying what the pilot is looking at. */
    public String status() {
        int shown = visible().size();
        if (state == State.LOADING) {
            return "Looking for groups...";
        }
        if (state == State.FAILED && all.isEmpty()) {
            return "The relay did not answer. Trying again.";
        }
        if (shown == 0) {
            return all.isEmpty() ? "Nobody is asking for help right now. Open a group of your own."
                    : "No groups match. Loosen a filter.";
        }
        if (shown > ROWS) {
            return "Showing " + ROWS + " of " + shown + ". Narrow the filters to see the rest.";
        }
        return shown == 1 ? "1 group open" : shown + " groups open";
    }
}
