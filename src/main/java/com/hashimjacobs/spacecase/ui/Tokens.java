package com.hashimjacobs.spacecase.ui;

import javafx.scene.paint.Color;

/**
 * The look of the interface, in one place.
 *
 * Before this existed the brand green was written out as {@code Color.web("#0ec417")} in seven
 * files and {@code "Verdana"} in fourteen call sites, so restyling anything meant finding every
 * copy and hoping. These are the same values those literals held -- this was a move, not a redesign.
 *
 * Two rules about what belongs here:
 *
 * Interface chrome only. Colours that describe a thing in the world rather than a thing in the
 * interface -- the halo round a bullet, a boss's hide, the deep-space fill behind the parallax --
 * stay next to the code that draws them. They are art, and naming them SURFACE_3 would not make
 * them easier to change, only harder to find.
 *
 * No {@link javafx.scene.text.Font} constants, ever. Building a Font starts the JavaFX toolkit,
 * which would make every test that touches a class importing this fail on a headless runner -- and
 * that is most of the suite. So this holds a family name and a scale of sizes, and each call site
 * still writes {@code Font.font(Tokens.BODY, Tokens.SIZE_ROW)}. {@link Color#web} is safe; it does
 * no toolkit initialisation, which {@code TokensTest} exists to keep true.
 *
 * Every family named here is bundled under {@code /fonts/} and registered by {@code Assets.load()},
 * which must run before the first Font is built. Display headings go through
 * {@code Assets.displayFontFamily()} and level titles through {@code Galaxy.titleFace()}.
 */
public final class Tokens {

    // ---- Brand ----------------------------------------------------------------------------

    /** The one accent. Focus, totals, health above half, anything the player owns. */
    public static final Color BRAND = Color.web("#0ec417");
    /** The brand's dark end: a focused row's border and pressed fill, the start of a slider's fill. */
    public static final Color BRAND_DEEP = Color.web("#0a5d12");

    // ---- Surfaces, back to front ----------------------------------------------------------

    /**
     * Empty space, behind every parallax layer.
     *
     * The backdrop art is transparent where there is nothing in it, so both the arena and the
     * menus need something opaque underneath or the previous frame shows through.
     */
    public static final Color SPACE = Color.web("#0a0e1a");
    /** Menu button fill, drawn under its own opacity. */
    public static final Color SURFACE_0 = Color.web("#05070c");
    /** Panels: garage bays, debrief columns, map detail. */
    public static final Color SURFACE_1 = Color.web("#0c1120");
    /** A focused row inside a panel. */
    public static final Color SURFACE_2 = Color.web("#1b2440");
    /** The empty part of a meter or progress bar. */
    public static final Color TRACK = Color.web("#22283a");
    /** As TRACK, but under a red fill, so it is tinted to match. */
    public static final Color TRACK_DANGER = Color.web("#2a1620");

    // ---- Edges ----------------------------------------------------------------------------

    /** Menu button outline at rest. */
    public static final Color EDGE = Color.web("#1d2436");
    /** Hairline between menu sections. */
    public static final Color EDGE_SOFT = Color.web("#232b3d");
    /** Panel borders and bar outlines -- the only edge meant to be noticed. */
    public static final Color EDGE_STRONG = Color.web("#3b4560");

    // ---- Text, lightest to faintest -------------------------------------------------------

    /** Focused labels, values, headings. */
    public static final Color TEXT = Color.WHITE;
    /** Body copy on the help screen. */
    public static final Color TEXT_MUTED = Color.web("#a9b6c9");
    /** Stat labels, pilot names, lives at a safe count. */
    public static final Color TEXT_SECONDARY = Color.web("#9fb0c9");
    /** Unfocused menu labels and secondary captions. */
    public static final Color TEXT_DIM = Color.web("#8b98ad");
    /** Anything unavailable: locked rows, unaffordable prices, tertiary captions. */
    public static final Color TEXT_FAINT = Color.web("#6d7a90");
    /** The keyboard-hint line, and nothing louder than it. */
    public static final Color TEXT_GHOST = Color.web("#4c586c");

    /** Section headings and the level/wave line. Violet, so it reads as a label not a value. */
    public static final Color LABEL = Color.web("#b388ff");

    // ---- Danger -----------------------------------------------------------------------------
    //
    // Four reds because they sit on four different backgrounds, not because the game has four
    // kinds of danger. Never the only signal for anything: the health bar carries a number, the
    // locked map node carries a padlock, the unaffordable row carries a dash.

    /** Full-screen damage wash, and the frame meter over budget. */
    public static final Color DANGER = Color.web("#ff2b2b");
    /** Boss health fill and the hit halo. */
    public static final Color DANGER_BAR = Color.web("#ff3b3b");
    /** Player health below a quarter, and lives running out. */
    public static final Color DANGER_LOW = Color.web("#ff4d4d");
    /** Boss names, inbound warnings, garage errors -- red on a dark panel, so lighter. */
    public static final Color DANGER_SOFT = Color.web("#ff6b6b");
    /** Health between a quarter and a half. */
    public static final Color WARN = Color.GOLD;

    /** What a bar has just lost, drawn behind its fill for a beat: see {@code Meter.Lag}. */
    public static final Color GHOST = Color.web("#f2f5ff", 0.85);
    /** The plate behind a HUD panel, so its text reads on the brightest sky in the campaign. */
    public static final Color PLATE = Color.web("#0c1120", 0.6);

    /** Health between a quarter and a half, on the bars: brighter than the brand green. */
    public static final Color HEALTH_MID = Color.web("#ffd166");
    /** Health under a quarter, on the bars: much darker than either, so the ramp reads by luminance. */
    public static final Color HEALTH_LOW = Color.web("#b3122b");

    /**
     * The fill for a health bar at a given fraction.
     *
     * The old green, gold, red ramp was a hue ramp, and green against red is the pair a deutan eye
     * cannot separate. These three stops differ in luminance as much as in hue, and under a quarter
     * the bars also hatch (see {@code Meter.stripes}), which no colour vision can miss.
     */
    public static Color healthColor(double fraction) {
        return fraction > 0.5 ? BRAND : fraction > 0.25 ? HEALTH_MID : HEALTH_LOW;
    }

    /** Relative luminance, the sRGB weighting; what the health ramp is tuned by. */
    public static double luminance(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    /** Black at a given opacity, for dimming the world behind an overlay. */
    public static Color veil(double opacity) {
        return Color.color(0, 0, 0, opacity);
    }

    // ---- Type -------------------------------------------------------------------------------

    /** Body face, Exo 2: bundled with Regular and Bold, so it is the same on every platform. */
    public static final String BODY = "Exo 2";

    public static final double SIZE_CAPTION = 11;
    public static final double SIZE_SMALL = 12;
    public static final double SIZE_BODY = 13;
    public static final double SIZE_LABEL = 14;
    public static final double SIZE_ROW = 15;
    public static final double SIZE_BUTTON = 17;
    public static final double SIZE_HEADING = 22;
    public static final double SIZE_TITLE = 30;
    public static final double SIZE_OVERLAY_TITLE = 38;
    public static final double SIZE_DISPLAY = 46;

    // ---- Space, corners, strokes -----------------------------------------------------------

    public static final double GAP_XS = 4;
    public static final double GAP_S = 8;
    public static final double GAP = 12;
    public static final double GAP_L = 18;
    public static final double GAP_XL = 24;

    public static final double RADIUS_S = 4;
    public static final double RADIUS = 7;
    public static final double RADIUS_L = 12;

    public static final double STROKE_HAIR = 1;
    public static final double STROKE = 1.5;
    public static final double STROKE_BOLD = 2;

    /**
     * Menu row width.
     *
     * One number for every menu row, because the button and the rule that used to sit under it were
     * two independent constants in two files with an implied relationship, and they drifted.
     *
     * Sized so a two-line save row's detail fits without truncating: the worst case is
     * "Single Player - Lv50 Mag-Storm Caverns - loop 9 - 12,345,678", about 60 characters, which at
     * Verdana 12 comes to roughly 410px inside 460 less the row padding.
     */
    public static final double ROW_WIDTH = 460;

    /** Row pitches. A menu row is a button; the others are lines of text in a panel. */
    public static final double ROW_MENU = 42;
    public static final double ROW_GARAGE = 28;
    public static final double ROW_DEBRIEF = 22;

    private Tokens() {
    }
}
