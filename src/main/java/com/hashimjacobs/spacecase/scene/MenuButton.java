package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * A menu entry that lights up on hover or keyboard focus.
 *
 * Ported from the original MenuItem, which was fully written but never wired to anything because
 * the start menu was never built.
 *
 * The row paints itself rather than stacking a Rectangle behind a Text. A Rectangle is a Shape and
 * so is not resizable: it stayed 320x42 whatever the label needed, and since a StackPane does not
 * clip, a long label -- the Continue row, every galaxy row, every save slot -- rendered straight out
 * over the starfield on both sides. A Region sizes to its content and a Label ellipsises, so the
 * two can no longer disagree.
 */
public final class MenuButton extends StackPane implements MenuNavigator.Item {

    private static final Color IDLE_BACKGROUND = Tokens.SURFACE_0;
    private static final Color IDLE_TEXT = Tokens.TEXT_DIM;
    private static final Color HIGHLIGHT = Tokens.BRAND;

    private static final CornerRadii CORNERS = new CornerRadii(Tokens.RADIUS);

    private final Label title;
    private final Label detail;
    private final Label value;
    private final Runnable action;
    private boolean focused;
    private boolean locked;

    public MenuButton(String text, Runnable action) {
        this(text, null, action);
    }

    /**
     * A row with a muted second line.
     *
     * Used where the action and the thing it acts on are different facts -- "Continue" over the run
     * being continued, "Slot 2" over what is in it. Before this the two were concatenated into one
     * string, which is how a menu row ended up 70 characters long.
     */
    public MenuButton(String text, String subtext, Runnable action) {
        this.action = action;

        title = line(text, Tokens.SIZE_BUTTON, FontWeight.BOLD);
        detail = subtext == null ? null : line(subtext, Tokens.SIZE_SMALL, FontWeight.NORMAL);
        value = line("", Tokens.SIZE_BUTTON, FontWeight.NORMAL);
        value.setMinWidth(Region.USE_PREF_SIZE);
        value.setManaged(false);
        value.setVisible(false);

        VBox lines = detail == null ? new VBox(title) : new VBox(Tokens.GAP_XS, title, detail);
        lines.setAlignment(Pos.CENTER_LEFT);

        // The label on the left, its value hard against the right edge. These used to be one
        // string padded apart with spaces, which in a proportional face lines nothing up -- the
        // column was visibly ragged, and only got away with it while the rows were centred.
        Region spacer = new Region();
        HBox row = new HBox(Tokens.GAP, lines, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Left-aligned rather than centred: in a vertical list, centring starts every row at a
        // different x and there is nothing for the eye to run down.
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(Tokens.GAP_S, Tokens.GAP, Tokens.GAP_S, Tokens.GAP));
        getChildren().add(row);

        // Width is fixed because MenuScreen's column sets fillWidth(false), so a row gets its
        // preferred width and nothing stretches it. Height is left computed so the two-line rows
        // grow on their own instead of being pinned to a pitch that only suits the one-line ones.
        setPrefWidth(Tokens.ROW_WIDTH);
        setMaxWidth(Tokens.ROW_WIDTH);
        setMinHeight(Tokens.ROW_MENU);

        paintForFocusState();

        // Hovering moves keyboard focus too, so the two input methods never disagree about which
        // entry is selected.
        setOnMouseEntered(event -> paintHighlighted());
        setOnMouseExited(event -> paintForFocusState());
        setOnMousePressed(event -> {
            if (!locked) {
                paint(Tokens.BRAND_DEEP, HIGHLIGHT, true);
            }
        });
        setOnMouseReleased(event -> {
            paintHighlighted();
            activate();
        });
    }

    private static Label line(String text, double size, FontWeight weight) {
        Label label = new Label(text);
        label.setFont(Font.font(Tokens.BODY, weight, size));
        // The backstop. Every caller is expected to keep its labels short enough to read, but a row
        // must never again be able to draw outside itself.
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    @Override
    public void setHighlighted(boolean highlighted) {
        this.focused = highlighted;
        paintForFocusState();
    }

    @Override
    public void activate() {
        if (locked) {
            return;
        }
        action.run();
    }

    /**
     * Marks a row as visible but not yet available.
     *
     * A locked row is shown rather than omitted, which is the point of a map: a player should be
     * able to see that a fourth galaxy exists and what it will take to reach it. It stays navigable
     * for the same reason the settings screen leaves its read-only rows navigable -- a pad user
     * scrolling past something they cannot read has not been told anything.
     *
     * The palette change is the weaker half of the signal on purpose: callers put the reason in the
     * label itself ("locked -- clear Ashen Verge"), so the row says why it cannot be used rather
     * than relying on a shade of grey that a colour-blind player may not distinguish.
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
        paintForFocusState();
    }

    public boolean isLocked() {
        return locked;
    }

    private void paintHighlighted() {
        if (locked) {
            // Focused but still unavailable: brightened enough to show the cursor is here, and
            // never given the brand colour, which everywhere else means "you may".
            paint(IDLE_BACKGROUND, Tokens.TEXT_FAINT, false);
            title.setTextFill(Tokens.TEXT_DIM);
            return;
        }
        paint(Tokens.SURFACE_1, Tokens.BRAND_DEEP, true);
        title.setTextFill(Color.WHITE);
    }

    private void paintForFocusState() {
        if (focused) {
            paintHighlighted();
            return;
        }
        paint(IDLE_BACKGROUND, Tokens.EDGE, false);
        title.setTextFill(locked ? Tokens.TEXT_FAINT : IDLE_TEXT);
    }

    /**
     * Fills the row and draws its edge.
     *
     * The focus cue is a thick left edge rather than a fill: a bar at the start of the row tracks
     * down the list as the cursor moves, where the old gradient put brand green in the outer eighth
     * at each end and read as a rendering fault. Four-paint BorderStroke, so it costs no extra node.
     */
    private void paint(Color fill, Color edge, boolean accent) {
        setBackground(new Background(new BackgroundFill(fill.deriveColor(0, 1, 1, 0.82), CORNERS, Insets.EMPTY)));
        Color left = accent ? HIGHLIGHT : edge;
        setBorder(new Border(new BorderStroke(edge, edge, edge, left,
                BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID,
                CORNERS,
                new BorderWidths(Tokens.STROKE_HAIR, Tokens.STROKE_HAIR, Tokens.STROKE_HAIR,
                        accent ? Tokens.STROKE_BOLD + Tokens.STROKE_HAIR : Tokens.STROKE_HAIR),
                Insets.EMPTY)));
        if (detail != null) {
            detail.setTextFill(locked ? Tokens.TEXT_GHOST : Tokens.TEXT_FAINT);
        }
        value.setTextFill(locked ? Tokens.TEXT_GHOST : Tokens.TEXT_MUTED);
    }

    /** Lets a caller update the text in place, which the settings rows use to show their value. */
    public void setText(String text) {
        title.setText(text);
    }

    /** Updates the muted second line. No-op on a row that was built without one. */
    public void setDetail(String text) {
        if (detail != null) {
            detail.setText(text);
        }
    }

    /**
     * A label and the setting it currently holds, the value right-aligned in the row.
     *
     * The settings and rebinding screens are the callers. Both used to build one padded string and
     * hope the spaces landed in the same place on every row.
     */
    public void setRow(String label, String rowValue) {
        title.setText(label);
        setValue(rowValue);
    }

    /** The right-hand half on its own, for rows whose label was set at construction. */
    public void setValue(String rowValue) {
        value.setText(rowValue);
        value.setManaged(true);
        value.setVisible(true);
    }
}
