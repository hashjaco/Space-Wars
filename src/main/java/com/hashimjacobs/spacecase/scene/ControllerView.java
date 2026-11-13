package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.engine.PadState;
import com.hashimjacobs.spacecase.prefs.PadButton;
import com.hashimjacobs.spacecase.ui.ControllerLayout;
import com.hashimjacobs.spacecase.ui.ControllerLayout.Spot;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Draws a {@link ControllerLayout} and reports which button a click landed on.
 *
 * The picture answers the question the old text rows could not: which button is that? Buttons
 * light as they are pressed on the real pad, so a pilot finds one by pressing it; the button
 * carrying the row they are on is filled in the brand color; and every bound button wears the name
 * of what it does.
 */
final class ControllerView {

    /** Stage units per layout unit. As wide as fits beside a menu panel in the 996-wide stage. */
    private static final double SCALE = 1.1;

    /**
     * Canvas pixels per stage unit. A canvas is a bitmap, and the router scales the whole stage up
     * to fill the window, so one drawn at stage size goes soft on a big screen.
     * ponytail: fixed 2x, which is sharp up to a 1728-high window. Re-rasterize at the window's
     * real scale, the way GameScreen.fit() does, if 4K menus look soft.
     */
    private static final double RASTER = 2;

    private static final double STICK_RING = 17;

    private final Canvas canvas = new Canvas(
            ControllerLayout.WIDTH * SCALE * RASTER, ControllerLayout.HEIGHT * SCALE * RASTER);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();
    /** A Group reports its children's bounds after their transforms, so layout sees the real size. */
    private final Group node = new Group(canvas);
    private ControllerLayout layout = ControllerLayout.XBOX;

    ControllerView(Consumer<PadButton> onPicked) {
        canvas.setScaleX(1 / RASTER);
        canvas.setScaleY(1 / RASTER);
        // On release, like MenuButton, and with no hover paint: the highlight belongs to the
        // keyboard cursor and must not follow the mouse about.
        canvas.setOnMouseReleased(event -> {
            PadButton hit = layout.hit(event.getX() / (SCALE * RASTER), event.getY() / (SCALE * RASTER));
            if (hit != null) {
                onPicked.accept(hit);
            }
        });
    }

    Node node() {
        return node;
    }

    /**
     * @param state   the pad as it is right now, for the lit buttons and the stick
     * @param focused the button to fill in, or null
     * @param tags    what each button does, or null for one that does nothing
     */
    void draw(ControllerLayout layout, PadState state, PadButton focused,
              Function<PadButton, String> tags) {
        this.layout = layout;
        double k = SCALE * RASTER;
        gc.setTransform(k, 0, 0, k, 0, 0);
        gc.clearRect(0, 0, ControllerLayout.WIDTH, ControllerLayout.HEIGHT);

        // Bumpers and triggers first, so the body overlaps their lower edge and they read as
        // sitting on the far side of it.
        for (Spot spot : layout.spots()) {
            if (spot.shoulder()) {
                spot(spot, state, focused, tags);
            }
        }
        path(layout.body());
        gc.setFill(Tokens.SURFACE_1);
        gc.fill();
        gc.setStroke(Tokens.EDGE_STRONG);
        gc.setLineWidth(Tokens.STROKE_BOLD);
        gc.stroke();
        if (layout.detail() != null) {
            path(layout.detail());
            gc.setLineWidth(Tokens.STROKE_HAIR);
            gc.stroke();
        }

        dpad(state);
        if (layout.hasStick()) {
            stick(state);
        }
        for (Spot spot : layout.spots()) {
            if (!spot.shoulder()) {
                spot(spot, state, focused, tags);
            }
        }
        // Tags last and all together: one drawn with its button would be painted over by the next
        // button along, and each has to know where the others went.
        List<double[]> taken = new ArrayList<>();
        for (Spot spot : layout.spots()) {
            String tag = tags.apply(spot.button());
            if (tag != null) {
                tag(tag, spot, taken);
            }
        }
    }

    private void path(String svg) {
        gc.beginPath();
        gc.appendSVGPath(svg);
    }

    private void dpad(PadState state) {
        double x = layout.dpadX();
        double y = layout.dpadY();
        double arm = 20;
        double half = 6.5;
        gc.setFill(Tokens.SURFACE_2);
        gc.fillRoundRect(x - half, y - arm, half * 2, arm * 2, 4, 4);
        gc.fillRoundRect(x - arm, y - half, arm * 2, half * 2, 4, 4);
        gc.setFill(Tokens.TEXT);
        if (state.dpadUp()) {
            gc.fillRoundRect(x - half, y - arm, half * 2, arm - half, 4, 4);
        }
        if (state.dpadDown()) {
            gc.fillRoundRect(x - half, y + half, half * 2, arm - half, 4, 4);
        }
        if (state.dpadLeft()) {
            gc.fillRoundRect(x - arm, y - half, arm - half, half * 2, 4, 4);
        }
        if (state.dpadRight()) {
            gc.fillRoundRect(x + half, y - half, arm - half, half * 2, 4, 4);
        }
        caption("MOVE", x, y + arm + 9);
    }

    /** The steering stick: a ring, and a cap that leans the way the real one is pushed. */
    private void stick(PadState state) {
        double x = layout.stickX();
        double y = layout.stickY();
        gc.setStroke(Tokens.EDGE_STRONG);
        gc.setLineWidth(Tokens.STROKE);
        gc.strokeOval(x - STICK_RING, y - STICK_RING, STICK_RING * 2, STICK_RING * 2);
        // A pad whose stick does not click has no spot here, so the cap is drawn as plain metal.
        if (layout.spot(PadButton.LEFT_STICK) == null) {
            double lean = 5;
            double cx = x + state.leftStickX() * lean;
            double cy = y + state.leftStickY() * lean;
            gc.setFill(Tokens.SURFACE_2);
            gc.fillOval(cx - 11, cy - 11, 22, 22);
        }
        caption("MOVE", x, y + STICK_RING + 9);
    }

    private void spot(Spot spot, PadState state, PadButton focused, Function<PadButton, String> tags) {
        boolean pressed = state.isPressed(spot.button());
        boolean isFocused = spot.button() == focused;
        String tag = tags.apply(spot.button());
        double left = spot.x() - spot.width() / 2;
        double top = spot.y() - spot.height() / 2;
        double arc = Math.min(spot.width(), spot.height());

        Color fill = pressed ? Tokens.TEXT : isFocused ? Tokens.BRAND : Tokens.SURFACE_2;
        gc.setFill(fill);
        gc.fillRoundRect(left, top, spot.width(), spot.height(), arc, arc);
        gc.setStroke(tag != null || isFocused ? Tokens.BRAND : Tokens.EDGE_STRONG);
        gc.setLineWidth(tag != null ? Tokens.STROKE : Tokens.STROKE_HAIR);
        gc.strokeRoundRect(left, top, spot.width(), spot.height(), arc, arc);

        // A label too long for the button is printed above it instead.
        boolean inside = spot.label().length() <= 2;
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        // The PlayStation shapes and the arrows come from a fallback font, which draws them small.
        boolean symbol = !spot.label().isEmpty() && spot.label().charAt(0) > 0x7F;
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD,
                Math.min(symbol ? 13 : 9, spot.height() * (symbol ? 0.8 : 0.62))));
        if (inside) {
            gc.setFill(pressed || isFocused ? Tokens.SURFACE_0 : Tokens.TEXT_MUTED);
            gc.fillText(spot.label(), spot.x(), spot.y());
        } else {
            caption(spot.label(), spot.x(), top - 6);
        }
    }

    /**
     * The action a button carries, on a dark plate beside it.
     *
     * Whichever side is clear: under the button by choice, then over it, then out to either side.
     * The face buttons sit close enough together that no one side works for all four.
     */
    private void tag(String text, Spot spot, List<double[]> taken) {
        double height = 9;
        double width = text.length() * 4.6 + 6;
        double gap = 2;
        double dx = spot.width() / 2 + gap + width / 2;
        double dy = spot.height() / 2 + gap + height / 2;
        // A long label is already printed over its button, and a shoulder button's underside is
        // the body, so those start on the other side.
        boolean labelAbove = spot.label().length() > 2;
        double[][] offsets = spot.shoulder()
                ? new double[][] {{0, -dy}, {dx, 0}, {-dx, 0}, {0, dy}}
                : labelAbove ? new double[][] {{0, dy}, {dx, 0}, {-dx, 0}}
                : new double[][] {{0, dy}, {0, -dy}, {-dx, 0}, {dx, 0}};
        double[] box = null;
        for (double[] offset : offsets) {
            double[] candidate = {spot.x() + offset[0] - width / 2, spot.y() + offset[1] - height / 2,
                    width, height};
            if (box == null || clear(candidate, spot, taken)) {
                box = candidate;
                if (clear(candidate, spot, taken)) {
                    break;
                }
            }
        }
        taken.add(box);
        gc.setFill(Tokens.SURFACE_0.deriveColor(0, 1, 1, 0.85));
        gc.fillRoundRect(box[0], box[1], width, height, 4, 4);
        gc.setFill(Tokens.BRAND);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, 7));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(text.toUpperCase(java.util.Locale.ROOT), box[0] + width / 2, box[1] + height / 2);
    }

    /** Whether a tag here would sit on another button, another tag, or off the drawing. */
    private boolean clear(double[] box, Spot own, List<double[]> taken) {
        if (box[0] < 0 || box[0] + box[2] > ControllerLayout.WIDTH || box[1] < 0) {
            return false;
        }
        for (Spot other : layout.spots()) {
            double[] theirs = {other.x() - other.width() / 2, other.y() - other.height() / 2,
                    other.width(), other.height()};
            if (other != own && overlap(box, theirs)) {
                return false;
            }
        }
        for (double[] other : taken) {
            if (overlap(box, other)) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlap(double[] a, double[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    private void caption(String text, double x, double y) {
        gc.setFill(Tokens.TEXT_FAINT);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, 7));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(text, x, y);
    }
}
