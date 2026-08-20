package com.hashimjacobs.spacecase.engine;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import javafx.scene.input.KeyCode;

/**
 * Gives a held direction the auto-repeat of a text cursor: one move, a pause, then a steady walk.
 *
 * The gamepad re-sends every held key several times a second so that a ship keeps flying after the
 * held set is cleared on unpause. A menu reads each of those as another press and scrolls away under
 * a stick that is barely leaning, which makes landing on a row a matter of luck. Rather than slow the
 * pad down and make the ship sluggish to recover, the repeat is throttled here -- on the way into a
 * menu, and nowhere else, so gameplay is untouched by construction.
 *
 * Wrapped around the two predicates every menu keystroke passes through, {@link
 * InputState#setMenuRouter} and the scene router's key handler, so the menus, the garage and name
 * entry are all covered without any of them knowing this exists.
 *
 * Only the direction keys are throttled, and only while one is genuinely held down -- releases are
 * reported, not guessed at, so tapping as fast as you like always lands. Enter, Space, Escape and
 * letters pass through untouched: activation and typing are one-shot already.
 */
public final class MenuRepeat implements Predicate<KeyCode> {

    /** The wait after the first move, long enough to notice and let go. */
    private static final long FIRST_NANOS = 500_000_000L;

    /** The steady rate afterwards: about three rows a second. */
    private static final long NEXT_NANOS = 350_000_000L;

    private static final Set<KeyCode> REPEATABLE = EnumSet.of(
            KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT,
            KeyCode.W, KeyCode.A, KeyCode.S, KeyCode.D);

    private final Predicate<KeyCode> inner;
    private final LongSupplier clock;

    // Per key, not one "currently held" field: both players shop in the garage at once, and player
    // one's S must not make player two's DOWN look like a fresh press.
    private final Set<KeyCode> down = EnumSet.noneOf(KeyCode.class);
    private final Map<KeyCode, Long> openAt = new EnumMap<>(KeyCode.class);
    private final Map<KeyCode, Boolean> consumed = new EnumMap<>(KeyCode.class);

    /** Wraps a menu router, or returns null for the null that means "no menu is up". */
    public static MenuRepeat gate(Predicate<KeyCode> inner) {
        return inner == null ? null : new MenuRepeat(inner, System::nanoTime);
    }

    /** Package-private clock so the timing can be tested exactly, without sleeping. */
    MenuRepeat(Predicate<KeyCode> inner, LongSupplier clock) {
        this.inner = inner;
        this.clock = clock;
    }

    @Override
    public boolean test(KeyCode code) {
        if (!REPEATABLE.contains(code)) {
            return inner.test(code);
        }

        long now = clock.getAsLong();
        // add returns true the first time, which is exactly "this key was not already down".
        boolean fresh = down.add(code);

        if (!fresh && now < openAt.getOrDefault(code, 0L)) {
            // Swallowed -- but answer as the menu did when it last saw this key. Reporting a
            // direction as unconsumed would drop it into InputState's held set, where it would fly
            // the ship around underneath the pause overlay.
            return consumed.getOrDefault(code, Boolean.FALSE);
        }

        openAt.put(code, nextOpening(code, now, fresh));
        boolean result = inner.test(code);
        consumed.put(code, result);
        return result;
    }

    /**
     * Tells the gate a key genuinely came up, so the next press counts as a new one.
     *
     * This is what keeps quick tapping honest. Guessing at releases from the gap between presses
     * cannot tell a fast double-tap from a held key, and swallows the second tap; the real event
     * says so outright. The gamepad only reports a release when a direction actually leaves the
     * deadzone, so held sticks never produce one by accident.
     */
    public void release(KeyCode code) {
        down.remove(code);
        openAt.remove(code);
    }

    /** Forgets every held key, for a window losing focus mid-press with no release to follow. */
    public void clear() {
        down.clear();
        openAt.clear();
    }

    /**
     * When this key may move the cursor again.
     *
     * Repeats are timed from the previous opening rather than from this press, because the pad only
     * reports every 133ms or so: measuring from arrival would round every wait up by most of a
     * report and quietly turn three moves a second into two. The clamp catches a key held across a
     * stall long enough for the schedule to have gone by.
     */
    private long nextOpening(KeyCode code, long now, boolean fresh) {
        if (fresh) {
            return now + FIRST_NANOS;
        }
        long scheduled = openAt.getOrDefault(code, now) + NEXT_NANOS;
        return Math.max(scheduled, now);
    }
}
