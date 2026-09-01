package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * The keyboard that pops up when a name or a code is being changed.
 *
 * A grid of characters the cursor walks, so a controller can enter one. The rules live in
 * {@link KeyGridModel}; this draws them and does nothing else. See that class for why free typing
 * had to go.
 *
 * ponytail: a held direction crosses this grid at {@code MenuRepeat}'s pace, about three cells a
 * second, which was tuned for a list of a dozen rows rather than a grid of forty. Wrapping in both
 * axes keeps any cell within about four columns and two rows, so the worst case is a couple of
 * seconds -- and a keyboard can tap past the gate where a d-pad cannot. If it drags in the hand,
 * give {@code MenuRepeat.gate} a rate and pass a faster one from the screens that open this.
 *
 * Mounted into the screen's root {@code StackPane} rather than into the panel that opened it. The
 * panels are all {@code VBox}es, so a card added to one would land under the rows instead of over
 * them; and {@code MenuScreen} lays its column out at preferred size, so growing a panel mid-screen
 * would slide the title and the captions. Going into the root also puts the scrim over the whole
 * screen, which is what a modal is for.
 */
final class OnScreenKeyboard extends VBox {

    /** Wide enough for a two-character label, and eight of them fit inside {@code ROW_WIDTH}. */
    private static final double CELL = 46;

    private static final double CELL_HEIGHT = 34;

    /** Eight cells and the gaps between them: what the preview line and the card are sized to. */
    private static final double COLUMNS_WIDTH =
            KeyGridModel.COLUMNS * CELL + (KeyGridModel.COLUMNS - 1) * Tokens.GAP_XS;

    private final KeyGridModel model;
    private final Consumer<String> onDone;
    private final Label preview;
    private final List<List<Key>> keys = new ArrayList<>();

    private final StackPane host;
    private final Region scrim;

    /**
     * Builds the card and mounts it over {@code host}, which is the screen root from
     * {@code MenuScreen.build}.
     *
     * @param onDone handed the finished value when the player presses DONE, Escape or the pad's
     *               cancel button. There is no separate cancel: the field is edited in place and
     *               what is on the card is what the player meant.
     */
    OnScreenKeyboard(StackPane host, String title, String alphabet, int maxLength, String initial,
                     Consumer<String> onDone) {
        super(Tokens.GAP_S);
        this.host = host;
        this.onDone = onDone;
        this.model = new KeyGridModel(alphabet, maxLength, initial);

        setAlignment(Pos.CENTER);
        setPadding(new Insets(Tokens.GAP_L));
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        CornerRadii corners = new CornerRadii(Tokens.RADIUS_L);
        setBackground(new Background(new BackgroundFill(Tokens.SURFACE_1, corners, Insets.EMPTY)));
        setBorder(new Border(new BorderStroke(Tokens.EDGE_STRONG, BorderStrokeStyle.SOLID, corners,
                new BorderWidths(Tokens.STROKE))));

        Label heading = new Label(title);
        heading.setFont(Font.font(Tokens.BODY, FontWeight.NORMAL, Tokens.SIZE_SMALL));
        heading.setTextFill(Tokens.TEXT_FAINT);

        preview = new Label();
        preview.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_HEADING));
        preview.setTextFill(Color.WHITE);
        // Fixed, so the card does not jump a pixel wider with every character entered.
        preview.setMinWidth(COLUMNS_WIDTH);
        preview.setAlignment(Pos.CENTER);

        getChildren().addAll(heading, preview, grid());
        getChildren().add(MenuScreen.caption(
                "Arrows or the d-pad to move    Enter or A to press    Escape or B to finish",
                Tokens.SIZE_CAPTION, Tokens.TEXT_GHOST));

        scrim = new Region();
        scrim.setBackground(new Background(new BackgroundFill(Tokens.veil(0.72), null, null)));
        host.getChildren().addAll(scrim, this);
        refresh();
    }

    private VBox grid() {
        VBox lines = new VBox(Tokens.GAP_XS);
        lines.setAlignment(Pos.CENTER);
        List<List<String>> cells = model.rows();
        for (int row = 0; row < cells.size(); row++) {
            HBox line = new HBox(Tokens.GAP_XS);
            // Every row is the full width and packs from the left, so the last one -- five cells of
            // eight, for the name alphabet -- keeps its columns under the rows above rather than
            // centring itself and putting 6 under a gap.
            line.setMinWidth(COLUMNS_WIDTH);
            line.setPrefWidth(COLUMNS_WIDTH);
            line.setAlignment(Pos.CENTER_LEFT);
            List<Key> built = new ArrayList<>();
            for (int column = 0; column < cells.get(row).size(); column++) {
                // The action row's three labels are words, so its cells are wide enough to hold one.
                boolean action = row == cells.size() - 1;
                Key key = new Key(cells.get(row).get(column), row, column,
                        action ? COLUMNS_WIDTH / 3 - Tokens.GAP_XS : CELL);
                built.add(key);
                line.getChildren().add(key);
            }
            keys.add(built);
            lines.getChildren().add(line);
        }
        return lines;
    }

    /**
     * @return true when the key was used here and must not reach the menu underneath
     */
    boolean handleKey(KeyCode code) {
        boolean handled = model.handleKey(code);
        if (!handled) {
            return false;
        }
        if (model.isDone()) {
            close();
            return true;
        }
        refresh();
        return true;
    }

    private void close() {
        host.getChildren().removeAll(scrim, this);
        onDone.accept(model.typed());
    }

    private void refresh() {
        String value = model.typed();
        // A placeholder rather than an empty line, so the card keeps its height and the player can
        // see the field is empty rather than wonder whether the keyboard is working.
        preview.setText(value.isEmpty() ? "—" : value);
        for (int row = 0; row < keys.size(); row++) {
            for (int column = 0; column < keys.get(row).size(); column++) {
                keys.get(row).get(column)
                        .setHighlighted(row == model.row() && column == model.column());
            }
        }
    }

    /**
     * One cell.
     *
     * Not a {@link MenuButton}: that row is 460px wide with its label pinned left by an HBox spacer,
     * and the pieces that would have to move to centre one character are locals rather than fields.
     * Bending a widget ten screens share into a 46px square was more code than drawing the square,
     * and its hover paint would have fought the cursor this grid keeps in the model.
     */
    private final class Key extends StackPane {

        private static final CornerRadii CORNERS = new CornerRadii(Tokens.RADIUS_S);

        private final Label label;

        Key(String text, int row, int column, double width) {
            // A space is a character a name may contain and a cell nobody can see, so it is drawn
            // as its name. The value pressed is still the space itself.
            String shown = " ".equals(text) ? "SPC" : text;
            label = new Label(shown);
            label.setFont(Font.font(Tokens.BODY, FontWeight.BOLD,
                    shown.length() > 1 ? Tokens.SIZE_CAPTION : Tokens.SIZE_ROW));
            setAlignment(Pos.CENTER);
            getChildren().add(label);
            setPrefSize(width, CELL_HEIGHT);
            setMinSize(width, CELL_HEIGHT);
            setMaxSize(width, CELL_HEIGHT);
            setHighlighted(false);

            // The cursor moves before the cell fires, so a click and the arrows cannot disagree
            // about where the cursor is. Hovering deliberately does not paint: the model owns the
            // highlight, and a second bright cell under the mouse would only say something false.
            setOnMouseReleased(event -> {
                model.moveTo(row, column);
                model.press(text);
                if (model.isDone()) {
                    close();
                    return;
                }
                refresh();
            });
        }

        void setHighlighted(boolean highlighted) {
            label.setTextFill(highlighted ? Color.WHITE : Tokens.TEXT_DIM);
            setBackground(new Background(new BackgroundFill(
                    highlighted ? Tokens.SURFACE_2 : Tokens.SURFACE_0, CORNERS, Insets.EMPTY)));
            setBorder(new Border(new BorderStroke(highlighted ? Tokens.BRAND : Tokens.EDGE,
                    BorderStrokeStyle.SOLID, CORNERS,
                    new BorderWidths(highlighted ? Tokens.STROKE : Tokens.STROKE_HAIR))));
        }
    }
}
