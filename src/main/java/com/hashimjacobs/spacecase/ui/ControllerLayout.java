package com.hashimjacobs.spacecase.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hashimjacobs.spacecase.prefs.PadAction;
import com.hashimjacobs.spacecase.prefs.PadButton;

/**
 * A controller as a picture: its outline, and where each button sits on it.
 *
 * SDL reports every pad in Xbox terms, so a layout changes nothing about what a button does or how
 * a binding is stored. It changes what the controller screen draws and what it calls each button --
 * {@link PadButton#A} is "A" here, a cross there and sits on the other side of a Nintendo pad -- so
 * a pilot can find the button in their hand on the screen.
 *
 * Plain data in a {@link #WIDTH} by {@link #HEIGHT} box, with no toolkit types, so the geometry is
 * unit-tested without JavaFX. Adding a pad is a new constant: an outline and a list of spots.
 */
public enum ControllerLayout {

    XBOX("Xbox", Shapes.XBOX, null, 150, 150, 105, 100,
            face(295, 100, false, "A", "B", "X", "Y"),
            shoulders("LB", "RB", "LT", "RT"),
            small(PadButton.BACK, 175, 100, "Back"), small(PadButton.START, 225, 100, "Start"),
            click(PadButton.LEFT_STICK, 105, 100, "LS"), rightStick(250, 150, "RS")),

    PS4("PlayStation 4", Shapes.PS4, Shapes.TOUCHPAD, 95, 112, 150, 158,
            face(305, 112, false, "\u2715", "\u25CB", "\u25A1", "\u25B3"),
            shoulders("L1", "R1", "L2", "R2"),
            small(PadButton.BACK, 140, 82, "Share"), small(PadButton.START, 260, 82, "Options"),
            click(PadButton.LEFT_STICK, 150, 158, "L3"), rightStick(250, 158, "R3")),

    PS5("PlayStation 5", Shapes.PS5, Shapes.TOUCHPAD, 95, 112, 150, 158,
            face(305, 112, false, "\u2715", "\u25CB", "\u25A1", "\u25B3"),
            shoulders("L1", "R1", "L2", "R2"),
            small(PadButton.BACK, 140, 82, "Create"), small(PadButton.START, 260, 82, "Options"),
            click(PadButton.LEFT_STICK, 150, 158, "L3"), rightStick(250, 158, "R3")),

    PS3("PlayStation 3", Shapes.PS3, null, 95, 112, 150, 158,
            face(305, 112, false, "\u2715", "\u25CB", "\u25A1", "\u25B3"),
            shoulders("L1", "R1", "L2", "R2"),
            small(PadButton.BACK, 175, 112, "Select"), small(PadButton.START, 225, 112, "Start"),
            click(PadButton.LEFT_STICK, 150, 158, "L3"), rightStick(250, 158, "R3")),

    // SDL goes by what is printed on a Nintendo pad, so A is the right-hand button and B the lower.
    SWITCH_PRO("Switch Pro", Shapes.SWITCH, null, 150, 150, 105, 100,
            face(295, 100, true, "A", "B", "X", "Y"),
            shoulders("L", "R", "ZL", "ZR"),
            small(PadButton.BACK, 172, 88, "\u2212"), small(PadButton.START, 228, 88, "+"),
            click(PadButton.LEFT_STICK, 105, 100, "LS"), rightStick(250, 150, "RS")),

    // No X or Y and no stick clicks. The C-buttons arrive as the right stick, Z as the left trigger.
    N64("Nintendo 64", Shapes.N64, null, 82, 112, 200, 182,
            List.of(dot(PadButton.A, 292, 137, 10, "A"), dot(PadButton.B, 270, 114, 10, "B"),
                    dot(PadButton.C_UP, 328, 76, 8, "\u25B2", "C \u25B2"),
                    dot(PadButton.C_DOWN, 328, 112, 8, "\u25BC", "C \u25BC"),
                    dot(PadButton.C_LEFT, 310, 94, 8, "\u25C0", "C \u25C0"),
                    dot(PadButton.C_RIGHT, 346, 94, 8, "\u25B6", "C \u25B6"),
                    bar(PadButton.LEFT_BUMPER, 95, 54, 70, 14, "L", true),
                    bar(PadButton.RIGHT_BUMPER, 305, 54, 70, 14, "R", true),
                    bar(PadButton.LEFT_TRIGGER, 200, 228, 30, 14, "Z", false)),
            small(PadButton.START, 200, 108, "Start")),

    // Z is the right bumper; there is no left one, no Select and no stick clicks.
    GAMECUBE("GameCube", Shapes.GAMECUBE, null, 150, 160, 95, 105,
            List.of(dot(PadButton.A, 300, 108, 15, "A"), dot(PadButton.B, 272, 127, 9, "B"),
                    bar(PadButton.X, 329, 100, 15, 27, "X", false),
                    bar(PadButton.Y, 296, 77, 27, 15, "Y", false),
                    bar(PadButton.RIGHT_BUMPER, 300, 50, 70, 14, "Z", true),
                    bar(PadButton.LEFT_TRIGGER, 100, 28, 46, 22, "L", true),
                    bar(PadButton.RIGHT_TRIGGER, 300, 28, 46, 22, "R", true)),
            small(PadButton.START, 200, 108, "Start"),
            directions(250, 160, "C")),

    // A flat pad: no sticks, no triggers.
    SNES("SNES style", Shapes.SNES, null, 85, 140, -1, -1,
            face(315, 140, true, "A", "B", "X", "Y"),
            List.of(bar(PadButton.LEFT_BUMPER, 85, 64, 70, 14, "L", true),
                    bar(PadButton.RIGHT_BUMPER, 315, 64, 70, 14, "R", true)),
            small(PadButton.BACK, 178, 150, "Select"), small(PadButton.START, 222, 150, "Start"));

    /** The box every layout is drawn in. */
    public static final double WIDTH = 400;
    public static final double HEIGHT = 265;

    /** How far outside a button a click still counts, in the same units. */
    private static final double SLOP = 2;

    /**
     * One button on the drawing.
     *
     * @param x        its center
     * @param label    what is printed on it, drawn on the picture
     * @param name     what to call it in a sentence, where the label alone would not say enough
     * @param shoulder whether it sits on the top edge and is drawn tucked behind the body
     */
    public record Spot(PadButton button, double x, double y, double width, double height,
                       String label, String name, boolean shoulder) {

        public boolean contains(double px, double py, double slop) {
            return Math.abs(px - x) <= width / 2 + slop && Math.abs(py - y) <= height / 2 + slop;
        }

        boolean overlaps(Spot other) {
            return Math.abs(x - other.x) < (width + other.width) / 2
                    && Math.abs(y - other.y) < (height + other.height) / 2;
        }
    }

    private final String label;
    private final String body;
    private final String detail;
    private final double dpadX;
    private final double dpadY;
    private final double stickX;
    private final double stickY;
    private final Map<PadButton, Spot> spots = new EnumMap<>(PadButton.class);

    @SafeVarargs
    ControllerLayout(String label, String body, String detail, double dpadX, double dpadY,
                     double stickX, double stickY, List<Spot>... groups) {
        this.label = label;
        this.body = body;
        this.detail = detail;
        this.dpadX = dpadX;
        this.dpadY = dpadY;
        this.stickX = stickX;
        this.stickY = stickY;
        for (List<Spot> group : groups) {
            for (Spot spot : group) {
                spots.put(spot.button(), spot);
            }
        }
    }

    public String label() {
        return label;
    }

    /** The outline, as SVG path data. */
    public String body() {
        return body;
    }

    /** A line or two of surface detail in the same form, or null. Decoration only. */
    public String detail() {
        return detail;
    }

    public double dpadX() {
        return dpadX;
    }

    public double dpadY() {
        return dpadY;
    }

    /** Whether this pad has a stick to steer with; the flat pads have only the d-pad. */
    public boolean hasStick() {
        return stickX >= 0;
    }

    public double stickX() {
        return stickX;
    }

    public double stickY() {
        return stickY;
    }

    public Iterable<Spot> spots() {
        return spots.values();
    }

    /** Where a button is on this pad, or null when this pad has no such button. */
    public Spot spot(PadButton button) {
        return spots.get(button);
    }

    /** What this pad calls a button, falling back to the general name for one it lacks. */
    public String nameOf(PadButton button) {
        Spot spot = spots.get(button);
        return spot == null ? button.label() : spot.name();
    }

    /** The button under a point, or null. The smallest wins where two are close. */
    public PadButton hit(double x, double y) {
        Spot best = null;
        for (Spot spot : spots.values()) {
            boolean smaller = best == null || spot.width() * spot.height() < best.width() * best.height();
            if (spot.contains(x, y, SLOP) && smaller) {
                best = spot;
            }
        }
        return best == null ? null : best.button();
    }

    /**
     * The button an action starts on for this pad: the usual one wherever the pad has it.
     *
     * The N64 pad has no X or Y, so punch and kick would sit on buttons that are not there.
     */
    public PadButton defaultButton(PadAction action) {
        if (this == N64 && action == PadAction.PUNCH) {
            return PadButton.C_LEFT;
        }
        if (this == N64 && action == PadAction.KICK) {
            return PadButton.C_UP;
        }
        return action.defaultButton();
    }

    /** The layout stored under this name, or null for an empty or unknown one. */
    public static ControllerLayout named(String name) {
        for (ControllerLayout layout : values()) {
            if (layout.name().equals(name)) {
                return layout;
            }
        }
        return null;
    }

    /**
     * A best guess from what a pad calls itself, Xbox when it says nothing recognizable.
     *
     * The name is all there is to go on: Jamepad exposes no vendor or product id. It is SDL's name
     * for the pad, which is usually the model, and the pilot can always pick another.
     */
    public static ControllerLayout guess(String padName) {
        String name = padName == null ? "" : padName.toLowerCase(Locale.ROOT);
        if (has(name, "n64", "nintendo 64")) {
            return N64;
        }
        if (has(name, "gamecube", "wavebird")) {
            return GAMECUBE;
        }
        if (has(name, "snes", "sn30", "sf30", "sfc", "super nintendo", "super famicom")) {
            return SNES;
        }
        if (has(name, "ps5", "dualsense")) {
            return PS5;
        }
        if (has(name, "ps3", "dualshock 3", "sixaxis", "playstation 3")) {
            return PS3;
        }
        if (has(name, "ps4", "dualshock", "playstation", "sony")) {
            return PS4;
        }
        if (has(name, "switch", "pro controller", "joy-con", "nintendo")) {
            return SWITCH_PRO;
        }
        return XBOX;
    }

    private static boolean has(String name, String... words) {
        for (String word : words) {
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // ---- spot builders -------------------------------------------------------------------------

    private static Spot dot(PadButton button, double x, double y, double radius, String label) {
        return dot(button, x, y, radius, label, label);
    }

    private static Spot dot(PadButton button, double x, double y, double radius, String label,
                            String name) {
        return new Spot(button, x, y, radius * 2, radius * 2, label, name, false);
    }

    private static Spot bar(PadButton button, double x, double y, double width, double height,
                            String label, boolean shoulder) {
        return new Spot(button, x, y, width, height, label, label, shoulder);
    }

    /**
     * The four face buttons in a diamond, each with what this pad prints on it.
     *
     * @param nintendo whether A and B, and X and Y, sit the Nintendo way round. An Xbox or
     *                 PlayStation pad has A at the bottom; a Nintendo pad prints A on the right, and
     *                 SDL follows the print, so there {@link PadButton#A} belongs on the right.
     */
    private static List<Spot> face(double x, double y, boolean nintendo, String a, String b,
                                   String xLabel, String yLabel) {
        double reach = 22;
        return nintendo
                ? List.of(dot(PadButton.A, x + reach, y, 10, a), dot(PadButton.B, x, y + reach, 10, b),
                        dot(PadButton.X, x, y - reach, 10, xLabel), dot(PadButton.Y, x - reach, y, 10, yLabel))
                : List.of(dot(PadButton.A, x, y + reach, 10, a), dot(PadButton.B, x + reach, y, 10, b),
                        dot(PadButton.X, x - reach, y, 10, xLabel), dot(PadButton.Y, x, y - reach, 10, yLabel));
    }

    private static List<Spot> shoulders(String leftBumper, String rightBumper, String leftTrigger,
                                        String rightTrigger) {
        return List.of(bar(PadButton.LEFT_BUMPER, 100, 50, 70, 14, leftBumper, true),
                bar(PadButton.RIGHT_BUMPER, 300, 50, 70, 14, rightBumper, true),
                bar(PadButton.LEFT_TRIGGER, 100, 28, 46, 22, leftTrigger, true),
                bar(PadButton.RIGHT_TRIGGER, 300, 28, 46, 22, rightTrigger, true));
    }

    private static List<Spot> small(PadButton button, double x, double y, String label) {
        return List.of(dot(button, x, y, 6, label));
    }

    private static List<Spot> click(PadButton button, double x, double y, String label) {
        return List.of(dot(button, x, y, 11, label));
    }

    /** A stick read as four buttons, one per direction it can be pushed. */
    private static List<Spot> directions(double x, double y, String stick) {
        double reach = 19;
        return List.of(dot(PadButton.C_UP, x, y - reach, 6, "\u25B2", stick + " \u25B2"),
                dot(PadButton.C_DOWN, x, y + reach, 6, "\u25BC", stick + " \u25BC"),
                dot(PadButton.C_LEFT, x - reach, y, 6, "\u25C0", stick + " \u25C0"),
                dot(PadButton.C_RIGHT, x + reach, y, 6, "\u25B6", stick + " \u25B6"));
    }

    private static List<Spot> rightStick(double x, double y, String label) {
        List<Spot> all = new ArrayList<>(directions(x, y, label));
        all.addAll(click(PadButton.RIGHT_STICK, x, y, label));
        return all;
    }

    /**
     * The outlines. In a holder of their own because an enum constant cannot name a static field of
     * its own enum that is declared after it.
     */
    private static final class Shapes {
        static final String XBOX = "M 95 60 C 130 50 270 50 305 60 C 345 68 372 150 378 205"
                + " C 382 240 350 252 330 225 C 312 200 300 180 270 178 L 130 178"
                + " C 100 180 88 200 70 225 C 50 252 18 240 22 205 C 28 150 55 68 95 60 Z";
        static final String SWITCH = "M 90 60 C 130 52 270 52 310 60 C 350 66 372 120 376 185"
                + " C 378 235 350 250 330 232 C 315 218 300 196 275 190 L 125 190"
                + " C 100 196 85 218 70 232 C 50 250 22 235 24 185 C 28 120 50 66 90 60 Z";
        static final String PS4 = "M 80 62 L 320 62 C 350 62 368 90 376 150"
                + " C 384 215 372 250 348 248 C 325 246 318 200 290 190 L 110 190"
                + " C 82 200 75 246 52 248 C 28 250 16 215 24 150 C 32 90 50 62 80 62 Z";
        static final String PS5 = "M 70 66 C 100 54 140 60 160 64 L 240 64 C 260 60 300 54 330 66"
                + " C 362 80 380 150 386 200 C 390 240 366 256 346 244 C 326 232 312 200 286 192"
                + " L 114 192 C 88 200 74 232 54 244 C 34 256 10 240 14 200"
                + " C 20 150 38 80 70 66 Z";
        static final String PS3 = "M 95 62 C 120 56 280 56 305 62 C 345 66 370 105 378 165"
                + " C 384 215 370 250 345 248 C 322 246 312 210 285 192 C 265 180 135 180 115 192"
                + " C 88 210 78 246 55 248 C 30 250 16 215 22 165 C 30 105 55 66 95 62 Z";
        static final String TOUCHPAD = "M 160 66 L 240 66 L 236 106 C 236 112 164 112 164 106 Z";
        static final String N64 = "M 60 70 C 120 50 280 50 340 70 C 370 80 385 130 370 190"
                + " C 362 225 335 250 318 235 C 300 215 300 170 280 150 L 235 150"
                + " C 230 190 228 230 218 248 C 210 262 190 262 182 248 C 172 230 170 190 165 150"
                + " L 120 150 C 100 170 100 215 82 235 C 65 250 38 225 30 190"
                + " C 15 130 30 80 60 70 Z";
        static final String GAMECUBE = "M 85 62 C 115 52 150 60 175 70 L 225 70"
                + " C 250 60 285 52 315 62 C 355 72 378 130 380 190 C 382 240 352 254 334 232"
                + " C 318 212 312 196 290 192 L 110 192 C 88 196 82 212 66 232"
                + " C 48 254 18 240 20 190 C 22 130 45 72 85 62 Z";
        static final String SNES = "M 85 75 L 315 75 C 365 75 385 110 385 140"
                + " C 385 175 360 205 315 205 C 285 205 265 190 250 180 L 150 180"
                + " C 135 190 115 205 85 205 C 40 205 15 175 15 140 C 15 110 35 75 85 75 Z";

        private Shapes() {
        }
    }
}
