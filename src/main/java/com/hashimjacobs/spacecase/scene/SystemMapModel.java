package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.engine.GamepadMapping;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.HighScores;
import com.hashimjacobs.spacecase.prefs.SaveGames;

/**
 * A galaxy's ten levels laid out as a route, and a cursor moving along it.
 *
 * All of the map's rules and none of its pixels, for the same reason {@link MenuNavigator} and
 * {@code ui.MenuWindow} are split out from the things that draw them: a class that touches Font or
 * Canvas cannot be tested without starting the JavaFX toolkit, and most of this suite runs on a
 * headless runner. {@link SystemMapView} does the drawing and holds no rules.
 *
 * The route is two rows of five, and the cursor <strong>clamps at both ends rather than wrapping</strong>.
 * That is the one place this deliberately disagrees with {@link MenuNavigator}, which wraps: a list
 * of menu rows is a loop with no ends, but a route has a first level and a last one, and jumping
 * from the tenth back to the first reads as a glitch rather than as a shortcut.
 */
public final class SystemMapModel {

    /** What the player may do with a node, and what it therefore looks like. */
    public enum NodeState {
        /** Not reachable yet: an earlier level in this galaxy is unfinished. */
        LOCKED,
        /** Flyable, never yet cleared. */
        AVAILABLE,
        /** Finished at least once. Replayable. */
        CLEARED,
        /** The first unfinished level -- where the campaign is asking you to go next. */
        CURRENT
    }

    /**
     * One level on the route.
     *
     * @param x horizontal position, 0 to 1 across the map
     * @param y vertical position, 0 to 1 down the route band
     */
    public record Node(Level level, NodeState state, int best, double x, double y) {
    }

    /**
     * Where the ten sit, as fractions. Two rows of five, the lower one offset half a column so the
     * route reads as a path threading between them rather than as a grid.
     */
    private static final double[][] ROUTE = {
            {0.08, 0.24}, {0.28, 0.10}, {0.48, 0.26}, {0.68, 0.12}, {0.90, 0.28},
            {0.90, 0.74}, {0.68, 0.90}, {0.48, 0.72}, {0.28, 0.88}, {0.08, 0.70},
    };

    private final GameMode mode;
    private final Galaxy galaxy;
    private final List<Node> nodes;

    private int cursor;
    private String message = "";

    private Consumer<Level> onLaunch = level -> { };
    private Runnable onBack = () -> { };
    private Runnable onChanged = () -> { };

    public SystemMapModel(GameMode mode, Galaxy galaxy, SaveGames saves, HighScores scores) {
        this.mode = mode;
        this.galaxy = galaxy;
        this.nodes = buildNodes(saves, scores);
        // Open on the level the campaign wants next rather than on the first, so arriving and
        // pressing fire does the obvious thing however deep the player already is.
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).state() == NodeState.CURRENT) {
                cursor = i;
                break;
            }
        }
    }

    private List<Node> buildNodes(SaveGames saves, HighScores scores) {
        List<Level> levels = galaxy.levels();
        List<Node> built = new ArrayList<>();
        boolean currentTaken = false;
        for (int i = 0; i < levels.size(); i++) {
            Level level = levels.get(i);
            boolean cleared = saves != null && saves.isCleared(mode, level);
            boolean unlocked = saves == null || saves.isUnlocked(mode, level);
            NodeState state;
            if (!unlocked) {
                state = NodeState.LOCKED;
            } else if (cleared) {
                state = NodeState.CLEARED;
            } else if (!currentTaken) {
                state = NodeState.CURRENT;
                currentTaken = true;
            } else {
                state = NodeState.AVAILABLE;
            }
            int best = scores == null ? 0 : scores.best(mode, level);
            built.add(new Node(level, state, best, ROUTE[i][0], ROUTE[i][1]));
        }
        return List.copyOf(built);
    }

    /** Always all ten, whatever their state. A locked level is shown, not hidden. */
    public List<Node> nodes() {
        return nodes;
    }

    public int cursor() {
        return cursor;
    }

    public Node focused() {
        return nodes.get(cursor);
    }

    public Galaxy galaxy() {
        return galaxy;
    }

    public GameMode mode() {
        return mode;
    }

    /** Why the last keypress did nothing, or empty. Shown under the route. */
    public String message() {
        return message;
    }

    public void setOnLaunch(Consumer<Level> onLaunch) {
        this.onLaunch = onLaunch;
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    /** Called whenever something the view draws has changed. */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /**
     * Returns false for anything it does not own, which is what lets F11 reach the fullscreen
     * handler -- the same contract {@link MenuNavigator} keeps, and asserted the same way.
     */
    public boolean handleKey(KeyCode code) {
        // The pad's cancel button, which is B by default. See MenuNavigator for why this sits ahead
        // of the switch rather than in it.
        if (code == GamepadMapping.MENU_CANCEL) {
            onBack.run();
            return true;
        }
        switch (code) {
            case LEFT, A -> step(-1);
            case RIGHT, D -> step(1);
            case UP, W, DOWN, S -> swapRow();
            case ENTER, SPACE -> launch();
            case ESCAPE -> onBack.run();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Along the route, stopping at both ends. */
    private void step(int delta) {
        int moved = Math.max(0, Math.min(nodes.size() - 1, cursor + delta));
        if (moved == cursor) {
            return;
        }
        cursor = moved;
        message = "";
        onChanged.run();
    }

    /**
     * Between the two rows at the same column.
     *
     * With exactly two rows, up and down are the same move, so they share a branch rather than
     * pretending to be opposites. The route runs left to right along the top and then right to left
     * along the bottom, which means index {@code i} and index {@code 9 - i} sit in the same column
     * -- so the mirror is the whole calculation, in both directions.
     */
    private void swapRow() {
        cursor = nodes.size() - 1 - cursor;
        message = "";
        onChanged.run();
    }

    /**
     * Flies the focused level, or says why not.
     *
     * A locked node reports the level standing in the way rather than silently ignoring the press.
     * Doing nothing at all is indistinguishable from a dropped input.
     */
    private void launch() {
        Node node = focused();
        if (node.state() == NodeState.LOCKED) {
            message = "Clear " + previousLabel(node.level()) + " first";
            onChanged.run();
            return;
        }
        onLaunch.accept(node.level());
    }

    private String previousLabel(Level locked) {
        int at = locked.ordinal();
        return at == 0 ? "the level before it" : Level.values()[at - 1].label();
    }
}
