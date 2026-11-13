package com.hashimjacobs.spacecase.scene;

import java.util.Objects;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.engine.Gamepad;
import com.hashimjacobs.spacecase.engine.GamepadMapping;
import com.hashimjacobs.spacecase.engine.PadState;
import com.hashimjacobs.spacecase.prefs.PadAction;
import com.hashimjacobs.spacecase.prefs.PadButton;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.ui.ControllerLayout;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Binding a gamepad, with the pad itself drawn beside the list.
 *
 * The pad's two bindable buttons used to be rows on Settings that cycled through a list of names,
 * and everything else on the pad was fixed. Here every action has a row, and a row is bound the way
 * {@link ControlsPanel} binds a key: choose it, then press the button -- or click it on the
 * drawing, for a pilot setting up a pad that is not in their hands.
 *
 * Choosing a row raises a prompt over the list: a countdown to pick a button, then a wheel that
 * fills while it is held. {@link HoldToBind} has the rules and the reasons.
 *
 * One player at a time, switched on the first row, because the drawing can only be one pad.
 */
final class ControllerPanel extends HBox {

    private static final int PLAYERS = 2;
    /** Rows above the first action: player and layout. */
    private static final int LEADING_ROWS = 2;

    private final Settings settings;
    /** Null where there is no reader to ask, which leaves the drawing unlit and clicks still working. */
    private final Gamepad gamepad;
    private final MenuButton playerRow;
    private final MenuButton layoutRow;
    private final MenuButton[] actionRow = new MenuButton[PadAction.values().length];
    private final MenuButton noticeRow;
    private final MenuPanel rows;
    private final ControllerView view = new ControllerView(this::picked);

    private static final double WHEEL = 150;
    private final Label promptTitle = promptLine(Tokens.SIZE_HEADING, Tokens.TEXT);
    private final Label promptHint = promptLine(Tokens.SIZE_LABEL, Tokens.TEXT_MUTED);
    private final Canvas wheel = new Canvas(WHEEL, WHEEL);
    private final VBox promptCard = new VBox(Tokens.GAP_L);
    /** The countdown and hold for the armed row, or null when nothing is armed. */
    private HoldToBind prompt;
    private MenuNavigator navigator;

    private int player = 1;
    /** The action waiting for a button, or null when nothing is armed. */
    private PadAction armed;
    private int focusedRow;
    private PadState lastState = PadState.disconnected();
    /** Whether the last prompt ran out, for the notice row to say why nothing changed. */
    private boolean timedOut;
    private String lastName;

    ControllerPanel(Settings settings, Gamepad gamepad, Runnable onBack) {
        this.settings = settings;
        this.gamepad = gamepad;

        playerRow = new MenuButton("", () -> switchPlayer(1));
        playerRow.setOnAdjust(this::switchPlayer);
        layoutRow = new MenuButton("", () -> cycleLayout(1));
        layoutRow.setOnAdjust(this::cycleLayout);
        for (PadAction action : PadAction.values()) {
            actionRow[action.ordinal()] = new MenuButton("", () -> arm(action));
        }
        MenuButton resetRow = new MenuButton("Reset to defaults", this::reset);
        // Not a control; see ControlsPanel, whose notice row this copies.
        noticeRow = new MenuButton("", () -> { });
        MenuButton backRow = new MenuButton("Back", () -> leave(onBack));

        MenuButton[] all = new MenuButton[LEADING_ROWS + actionRow.length + 3];
        all[0] = playerRow;
        all[1] = layoutRow;
        System.arraycopy(actionRow, 0, all, LEADING_ROWS, actionRow.length);
        all[all.length - 3] = resetRow;
        all[all.length - 2] = noticeRow;
        all[all.length - 1] = backRow;
        rows = new MenuPanel(all);

        Label promptFoot = promptLine(Tokens.SIZE_SMALL, Tokens.TEXT_FAINT);
        promptFoot.setText("Or click a button on the drawing. Esc cancels.");
        promptCard.getChildren().addAll(promptTitle, wheel, promptHint, promptFoot);
        promptCard.setAlignment(Pos.CENTER);
        promptCard.setPadding(new Insets(Tokens.GAP_XL));
        // Over the whole list and nearly opaque: the rows cannot be used while one is waiting, and
        // should not look as though they can. The drawing stays clear, since it is still live.
        promptCard.setBackground(new Background(new BackgroundFill(
                Tokens.SURFACE_0.deriveColor(0, 1, 1, 0.94), new CornerRadii(Tokens.RADIUS_L), null)));
        promptCard.setVisible(false);

        setSpacing(Tokens.GAP_L);
        setAlignment(Pos.CENTER);
        getChildren().addAll(new StackPane(rows, promptCard), view.node());
        refresh();
    }

    MenuNavigator navigator(Runnable onBack) {
        navigator = rows.navigator();
        navigator.setOnBack(() -> {
            // Escape backs out of an armed row first, as on the keyboard screen.
            if (armed != null) {
                disarm();
                refresh();
                return;
            }
            leave(onBack);
        });
        navigator.alsoOnFocusMoved(row -> {
            focusedRow = row;
            redraw();
        });
        return navigator;
    }

    /**
     * Holds the cursor still while a row waits for its button.
     *
     * The waiting pad sends no keys, but the keyboard and the other pad still do, and a row that
     * wandered off mid-bind would bind an action the pilot was no longer looking at. Escape and the
     * pad's cancel pass through, to the navigator, which disarms.
     *
     * @return true when the press was swallowed
     */
    boolean captureKey(KeyCode code) {
        return armed != null && code != KeyCode.ESCAPE && code != GamepadMapping.MENU_CANCEL;
    }

    /**
     * Once a frame: follows the pad as buttons go down and as pads come and go, and runs the prompt.
     *
     * @param now the frame's clock, in nanoseconds
     */
    void tick(long now) {
        PadState state = gamepad == null ? PadState.disconnected() : gamepad.latest(player - 1);
        String name = gamepad == null ? null : gamepad.padName(player - 1);
        if (prompt != null) {
            switch (prompt.tick(now, state::isPressed)) {
                case BOUND -> bind(prompt.candidate());
                case TIMED_OUT -> {
                    disarm();
                    timedOut = true;
                    refresh();
                }
                default -> drawPrompt();
            }
        }
        boolean renamed = !Objects.equals(name, lastName);
        if (renamed || !state.equals(lastState)) {
            lastState = state;
            lastName = name;
            if (renamed) {
                refresh();     // a pad switched on mid-screen may be a different shape
            } else {
                redraw();
            }
        }
    }

    private ControllerLayout shape() {
        ControllerLayout chosen = ControllerLayout.named(settings.padLayout(player));
        return chosen != null ? chosen : ControllerLayout.guess(lastName);
    }

    private void switchPlayer(int direction) {
        disarm();
        player = player % PLAYERS + 1;
        lastState = PadState.disconnected();
        lastName = gamepad == null ? null : gamepad.padName(player - 1);
        refresh();
    }

    /** Auto, then each layout in turn, then auto again; backwards on Left. */
    private void cycleLayout(int direction) {
        ControllerLayout[] all = ControllerLayout.values();
        ControllerLayout chosen = ControllerLayout.named(settings.padLayout(player));
        int at = chosen == null ? -1 : chosen.ordinal();
        int next = Math.floorMod(at + 1 + direction, all.length + 1) - 1;
        settings.setPadLayout(player, next < 0 ? "" : all[next].name());
        refresh();
    }

    private void arm(PadAction action) {
        armed = action;
        timedOut = false;
        HoldToBind fresh = new HoldToBind();
        prompt = fresh;
        if (gamepad != null) {
            // The pad goes quiet and reports each new press here; the prompt then watches whether
            // that button stays down.
            gamepad.setCapture(player - 1, fresh::pressed);
        }
        promptTitle.setText(action.label().toUpperCase(java.util.Locale.ROOT));
        promptCard.setVisible(true);
        drawPrompt();
        refresh();
    }

    private void disarm() {
        armed = null;
        prompt = null;
        promptCard.setVisible(false);
        if (gamepad != null) {
            for (int slot = 0; slot < PLAYERS; slot++) {
                gamepad.setCapture(slot, null);
            }
        }
    }

    /** At the end of a hold, or at once on a click: a click is already a deliberate act. */
    private void bind(PadButton button) {
        settings.setPadButton(player, armed, button);
        disarm();
        refresh();
    }

    /** A click on the drawing: binds the waiting row, or else finds the row that button belongs to. */
    private void picked(PadButton button) {
        if (armed != null) {
            bind(button);
            return;
        }
        PadAction owner = actionOn(button);
        if (owner != null && navigator != null) {
            navigator.focus(LEADING_ROWS + owner.ordinal());
        }
    }

    private void reset() {
        disarm();
        ControllerLayout layout = shape();
        for (PadAction action : PadAction.values()) {
            settings.setPadButton(player, action, layout.defaultButton(action));
        }
        refresh();
    }

    private void leave(Runnable onBack) {
        disarm();
        settings.save();
        onBack.run();
    }

    private PadAction actionOn(PadButton button) {
        for (PadAction action : PadAction.values()) {
            if (settings.padButton(player, action) == button) {
                return action;
            }
        }
        return null;
    }

    private void refresh() {
        ControllerLayout layout = shape();
        boolean auto = ControllerLayout.named(settings.padLayout(player)) == null;
        playerRow.setRow("Player", "P" + player);
        layoutRow.setRow("Layout", auto ? layout.label() + " (auto)" : layout.label());
        String missing = "";
        for (PadAction action : PadAction.values()) {
            PadButton button = settings.padButton(player, action);
            actionRow[action.ordinal()].setRow(action.label(),
                    action == armed ? "press a button" : layout.nameOf(button));
            if (layout.spot(button) == null && missing.isEmpty()) {
                missing = action.label() + " is on " + button.label() + ", which this pad lacks";
            }
        }
        noticeRow.setText(timedOut ? "No button held, so nothing changed" : missing);
        redraw();
    }

    /** The wheel: seconds left to pick a button, or the button being held and how far along. */
    private void drawPrompt() {
        PadButton held = prompt.candidate();
        promptHint.setText(held == null ? "Press and hold the button you want" : "Keep holding...");

        GraphicsContext gc = wheel.getGraphicsContext2D();
        double thick = 10;
        double size = WHEEL - thick;
        gc.clearRect(0, 0, WHEEL, WHEEL);
        gc.setLineWidth(thick);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setStroke(Tokens.TRACK);
        gc.strokeOval(thick / 2, thick / 2, size, size);
        if (held != null) {
            // From twelve o'clock, clockwise; JavaFX measures arcs anticlockwise from three.
            gc.setStroke(Tokens.BRAND);
            gc.strokeArc(thick / 2, thick / 2, size, size, 90, -360 * prompt.progress(), ArcType.OPEN);
        }
        String middle = held == null ? String.valueOf(prompt.secondsLeft()) : shape().nameOf(held);
        gc.setFill(held == null ? Tokens.TEXT : Tokens.BRAND);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        // A number is one glyph; a button can be "Right stick click" on a pad that lacks it.
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, middle.length() <= 3 ? 54 : 18));
        gc.fillText(middle, WHEEL / 2, WHEEL / 2, size - thick * 2);
    }

    private static Label promptLine(double size, javafx.scene.paint.Color color) {
        Label line = new Label();
        line.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, size));
        line.setTextFill(color);
        return line;
    }

    private void redraw() {
        int index = focusedRow - LEADING_ROWS;
        PadButton focused = index >= 0 && index < actionRow.length
                ? settings.padButton(player, PadAction.values()[index])
                : null;
        view.draw(shape(), lastState, focused, button -> {
            PadAction action = actionOn(button);
            return action == null ? null : action.label();
        });
    }
}
