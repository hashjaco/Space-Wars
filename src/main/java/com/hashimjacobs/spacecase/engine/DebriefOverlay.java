package com.hashimjacobs.spacecase.engine;

import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.ui.Meter;
import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.mode.Challenge;
import com.hashimjacobs.spacecase.mode.Debrief;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Rank;
import com.hashimjacobs.spacecase.prefs.Standing;

/**
 * The between-levels report: what each pilot did, what it paid, and where it leaves their rank.
 *
 * Canvas-drawn beside {@link Hud} rather than built as JavaFX nodes, so it can sit over a frozen world
 * without the router swapping screens and tearing the game down.
 */
final class DebriefOverlay {

    private static final Color BRAND = Tokens.BRAND;

    /** Marks a badge can carry. Tiers run longer than this; the extra ranks share the top badge. */
    private static final int MOST_MARKS = 4;
    private static final Color LABEL = Tokens.LABEL;
    private static final Color MUTED = Tokens.TEXT_SECONDARY;
    private static final Color PANEL = Tokens.SURFACE_1;
    private static final Color PANEL_EDGE = Tokens.EDGE_STRONG;

    private static final double PANEL_WIDTH = 396;
    private static final double PANEL_TOP = 150;
    /** Tall enough for six bonus rows plus the credits line; the ladders can field two at once. */
    private static final double PANEL_HEIGHT = 560;
    /** Below the challenge strip, which sits between the columns and this. */
    private static final double PROMPT_Y = PANEL_TOP + PANEL_HEIGHT + 74;

    // ---- The reveal ------------------------------------------------------------------------
    // Rows land one at a time and count up, so the screen is watched rather than scanned: stats,
    // then each bonus, then the total, the grade and the rank. All of it is a function of one
    // tick count, so skipping is just handing in a large one.

    /** Ticks before the first row, while the title settles. */
    private static final int REVEAL_START = 24;
    /** Ticks between one row landing and the next. */
    private static final int REVEAL_STEP = 7;
    /** Ticks a number takes to count up to its value. */
    private static final int COUNT_TICKS = 16;
    private static final int STAT_ROWS = 6;

    private final GraphicsContext gc;
    private final Font titleFont = Font.font(Assets.displayFontFamily(), Tokens.SIZE_DISPLAY);
    private final Font headingFont = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_BUTTON);
    private final Font rowFont = Font.font(Tokens.BODY, FontWeight.NORMAL, Tokens.SIZE_BODY);
    private final Font valueFont = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_ROW);
    private final Font promptFont = Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_ROW);
    private final Font gradeFont = Font.font(Assets.displayFontFamily(), Tokens.SIZE_DISPLAY);

    /** The reveal clock for the column being drawn; set once per {@link #draw}. */
    private int clock;

    DebriefOverlay(GraphicsContext gc) {
        this.gc = gc;
    }

    /** The tick a row lands on, counting stat rows, then bonuses, then the summary lines. */
    private static int landsAt(int row) {
        return REVEAL_START + row * REVEAL_STEP;
    }

    private static int mostBonuses(List<Debrief> debriefs) {
        int most = 0;
        for (Debrief debrief : debriefs) {
            most = Math.max(most, debrief.bonuses().size());
        }
        return most;
    }

    /** The tick by which everything is on screen and counted. */
    static int revealEnd(List<Debrief> debriefs) {
        return landsAt(STAT_ROWS + mostBonuses(debriefs) + 4) + COUNT_TICKS;
    }

    /** Whether a bonus row lands on exactly this tick, which is when it should be heard. */
    static boolean bonusLandsOn(int tick, List<Debrief> debriefs) {
        int row = (tick - REVEAL_START) / REVEAL_STEP - STAT_ROWS;
        return tick >= REVEAL_START && (tick - REVEAL_START) % REVEAL_STEP == 0
                && row >= 0 && row < mostBonuses(debriefs);
    }

    /**
     * @param tick  ticks since the debrief opened, or anything past {@link #revealEnd} to show it all
     * @param armed whether a fresh key press will now act; the prompt stays hidden until it will,
     *              so it never invites a press that is already being held
     */
    void draw(Level level, List<Debrief> debriefs, List<Standing> standings, int tick, boolean armed) {
        clock = tick;
        gc.setFill(Tokens.veil(0.78));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        // The title drops into place over the first third of a second.
        double settle = Math.min(1, tick / 18.0);
        double ease = 1 - (1 - settle) * (1 - settle);
        gc.setTextBaseline(VPos.TOP);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setGlobalAlpha(ease);
        gc.setFont(titleFont);
        gc.setFill(BRAND);
        gc.fillText("LEVEL " + level.number() + " CLEARED", GameConfig.WIDTH / 2, 56 - (1 - ease) * 24);
        gc.setGlobalAlpha(1);
        gc.setFont(headingFont);
        gc.setFill(MUTED);
        gc.fillText(level.galaxy().label().toUpperCase() + "   \u00b7   " + level.label().toUpperCase(),
                GameConfig.WIDTH / 2, 112);

        // One column per pilot, centred as a group so a solo run does not sit off to one side, and
        // narrowed to fit: four at full width ran 600 pixels off the stage.
        int count = debriefs.size();
        double gap = count > 2 ? 12 : 24;
        double width = Math.min(PANEL_WIDTH, (GameConfig.WIDTH - 40 - (count - 1) * gap) / count);
        double x = (GameConfig.WIDTH - (count * width + (count - 1) * gap)) / 2;
        for (int i = 0; i < count; i++) {
            Standing standing = i < standings.size() ? standings.get(i) : null;
            drawColumn(x, width, debriefs.get(i), standing);
            x += width + gap;
        }

        if (!armed) {
            return;
        }
        boolean done = tick >= revealEnd(debriefs);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(promptFont);
        gc.setFill(done ? Color.WHITE : Tokens.TEXT_FAINT);
        gc.fillText(done ? "PRESS ANY BUTTON TO CONTINUE" : "PRESS ANY BUTTON TO SKIP",
                GameConfig.WIDTH / 2, PROMPT_Y);
    }

    /**
     * The level's challenges in one strip under the columns, landing with the last row: a filled
     * mark for met this run, a hollow brand one for met on an earlier run, a faint ring for still
     * open, and "NEW +n" for the ones paid out just now.
     */
    void drawChallenges(int tick, List<Debrief> debriefs, int met, int fresh, int held) {
        if (tick < landsAt(STAT_ROWS + mostBonuses(debriefs) + 4)) {
            return;
        }
        double top = PANEL_TOP + PANEL_HEIGHT + 16;
        String[] labels = {"EVERY ENEMY", "PAR TIME", "CORES", "S RANK"};
        Challenge[][] groups = {{Challenge.ALL_DOWN}, {Challenge.CLOCK},
                {Challenge.CORE_1, Challenge.CORE_2, Challenge.CORE_3}, {Challenge.S_RANK}};
        double chip = 200;
        double gap = 12;
        double x = (GameConfig.WIDTH - (4 * chip + 3 * gap)) / 2;
        gc.setTextBaseline(VPos.CENTER);
        for (int i = 0; i < groups.length; i++) {
            int paid = 0;
            gc.setFill(PANEL);
            gc.fillRoundRect(x, top, chip, 40, Tokens.RADIUS, Tokens.RADIUS);
            double markX = x + 18;
            for (Challenge challenge : groups[i]) {
                drawMark(markX, top + 20, challenge.in(met), challenge.in(held));
                markX += 16;
                if (challenge.in(fresh)) {
                    paid += challenge.credits();
                }
            }
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(rowFont);
            gc.setFill(groups[i][0].in(held) || groups[i][0].in(met) ? LABEL : MUTED);
            gc.fillText(labels[i], markX + 2, top + 20);
            if (paid > 0) {
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.setFont(rowFont);
                gc.setFill(BRAND);
                gc.fillText("+" + paid, x + chip - 12, top + 20);
            }
            x += chip + gap;
        }
        gc.setTextBaseline(VPos.TOP);
    }

    private void drawMark(double cx, double cy, boolean now, boolean before) {
        double r = 6;
        if (now) {
            gc.setFill(BRAND);
            gc.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            return;
        }
        gc.setStroke(before ? BRAND : Tokens.TEXT_FAINT);
        gc.setLineWidth(2);
        gc.strokeOval(cx - r, cy - r, 2 * r, 2 * r);
    }

    private void drawColumn(double x, double width, Debrief debrief, Standing standing) {
        boolean narrow = width < 300;
        gc.setFill(PANEL);
        gc.fillRoundRect(x, PANEL_TOP, width, PANEL_HEIGHT, Tokens.RADIUS_L, Tokens.RADIUS_L);
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(Tokens.STROKE);
        gc.strokeRoundRect(x, PANEL_TOP, width, PANEL_HEIGHT, Tokens.RADIUS_L, Tokens.RADIUS_L);
        // The brand rule across the top that every card in the game carries.
        gc.setFill(BRAND);
        gc.fillRoundRect(x + 14, PANEL_TOP, width - 28, 3, 3, 3);

        double pad = narrow ? 12 : 22;
        double left = x + pad;
        double right = x + width - pad;
        double y = PANEL_TOP + 20;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(headingFont);
        gc.setFill(Color.WHITE);
        // Cut to leave the corner for the grade, which is the louder of the two.
        String name = debrief.pilotName();
        int fits = (int) ((right - left - 48) / 11);
        gc.fillText(name.length() > fits ? name.substring(0, Math.max(1, fits - 1)) + "\u2026" : name, left, y);
        y += 44;

        int row = 0;
        y = drawStat(left, right, y, narrow ? "Enemies" : "Enemies downed", debrief.enemiesKilled(), "", "", row++);
        y = drawStat(left, right, y, narrow ? "Asteroids" : "Asteroids cleared", debrief.asteroidsDestroyed(), "", "", row++);
        y = drawStat(left, right, y, "Accuracy", debrief.accuracyPercent(), "", "%", row++);
        y = drawStat(left, right, y, narrow ? "Damage" : "Damage taken", debrief.damageTaken(), "", "", row++);
        y = drawStat(left, right, y, "Lives lost", debrief.livesLost(), "", "", row++);
        y = drawStat(left, right, y, narrow ? "Time" : "Clear time", debrief.clearSeconds(), "", "s", row++);

        y += 10;
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(left, y, right, y);
        y += 14;

        gc.setFont(rowFont);
        gc.setFill(LABEL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("BONUSES", left, y);
        y += Tokens.ROW_DEBRIEF;
        for (Debrief.Bonus bonus : debrief.bonuses()) {
            y = drawStat(left, right, y, bonus.label(), bonus.points(), "+", "", row++);
        }
        // Every column's summary starts together, after the longest bonus list on screen.
        row = Math.max(row, STAT_ROWS + debrief.bonuses().size());

        y += 6;
        double shown = shown(row);
        gc.setGlobalAlpha(shown > 0 ? 1 : 0);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(valueFont);
        gc.setFill(BRAND);
        gc.fillText("TOTAL", left, y);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("+" + String.format("%,d", Math.round(debrief.totalBonus() * shown)), right, y);
        y += 26;
        row++;

        // What the level paid into the garage. A summary line under the rule rather than another
        // stat row, because it is the one number here that buys something.
        shown = shown(row);
        gc.setGlobalAlpha(shown > 0 ? 1 : 0);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(rowFont);
        gc.setFill(LABEL);
        gc.fillText(narrow ? "CREDITS" : "CREDITS EARNED", left, y);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFill(Color.WHITE);
        gc.fillText("+" + Math.round(debrief.credits() * shown) + " CR", right, y);
        y += 32;
        row++;
        gc.setGlobalAlpha(1);

        drawGrade(right, PANEL_TOP + 4, debrief.grade(), row++);

        if (standing != null && clock >= landsAt(row)) {
            drawStanding(left, right, y, standing, shown(row), narrow);
        }
    }

    /** How far a row's number has counted, 0 before it lands and 1 once it has settled. */
    private double shown(int row) {
        double t = (clock - landsAt(row)) / (double) COUNT_TICKS;
        return t <= 0 ? 0 : t >= 1 ? 1 : 1 - (1 - t) * (1 - t);
    }

    private double drawStat(double left, double right, double y, String label, int value,
                            String prefix, String suffix, int row) {
        double shown = shown(row);
        if (shown > 0) {
            // Slides in the last few pixels as it lands.
            double slide = (1 - Math.min(1, (clock - landsAt(row)) / 6.0)) * 10;
            gc.setFont(rowFont);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFill(MUTED);
            gc.fillText(label, left - slide, y);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setFill(Color.WHITE);
            gc.fillText(prefix + String.format("%,d", Math.round(value * shown)) + suffix, right, y);
        }
        return y + Tokens.ROW_DEBRIEF;
    }

    /** The run's letter, stamped into the column's corner once the numbers are in. */
    private void drawGrade(double right, double y, char grade, int row) {
        double t = (clock - landsAt(row)) / 10.0;
        if (t <= 0) {
            return;
        }
        double settle = Math.min(1, t);
        double scale = 1 + (1 - settle) * 0.9;
        Color color = switch (grade) {
            case 'S' -> Tokens.WARN;
            case 'A' -> BRAND;
            case 'B' -> Color.WHITE;
            default -> Tokens.TEXT_DIM;
        };
        gc.save();
        gc.translate(right - 16, y + 28);
        gc.scale(scale, scale);
        gc.setGlobalAlpha(settle);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(gradeFont);
        gc.setFill(color);
        gc.fillText(String.valueOf(grade), 0, 0);
        gc.restore();
        gc.setTextBaseline(VPos.TOP);
    }

    private void drawStanding(double left, double right, double y, Standing standing, double shown,
                              boolean narrow) {
        Rank rank = standing.rank();

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(rowFont);
        gc.setFill(LABEL);
        gc.fillText(standing.promoted() ? "PROMOTED" : "RANK", left, y);
        if (standing.promoted()) {
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setFill(BRAND);
            gc.fillText("NEW", right, y);
        }
        y += 22;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(valueFont);
        gc.setFill(Color.WHITE);
        gc.fillText(narrow ? rank.abbreviation() : rank.label(), left, y);
        drawInsignia(right - 52, y + 2, rank);
        y += 26;

        gc.setFont(rowFont);
        gc.setFill(MUTED);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("Career  " + String.format("%,d", standing.careerScore()), left, y);
        y += 20;

        // Progress toward the next rank, or a full bar at the top of the ladder; it fills as it lands.
        Meter.draw(gc, left, y, right - left, 8, standing.progress() * shown, 0, BRAND, Tokens.TRACK, 0, false);
        y += 16;

        gc.setFont(rowFont);
        gc.setFill(MUTED);
        gc.setTextAlign(TextAlignment.LEFT);
        String toward = rank.isHighest()
                ? "Top of the ladder"
                : "Next  " + (narrow ? rank.next().abbreviation() : rank.next().label())
                        + "  at " + String.format("%,d", rank.next().careerScoreRequired());
        gc.fillText(toward, left, y);
    }

    /**
     * Every insignia frame, addressed by tier and mark count.
     *
     * Looked up by name rather than listed in a switch: the generator writes one file per cell of
     * exactly this grid, so a hand-written mapping would only be somewhere for the two to drift.
     */
    private static final Sprite[][] INSIGNIA = insigniaFrames();

    private static Sprite[][] insigniaFrames() {
        Rank.Insignia[] tiers = Rank.Insignia.values();
        Sprite[][] frames = new Sprite[tiers.length][MOST_MARKS + 1];
        for (Rank.Insignia tier : tiers) {
            for (int marks = 1; marks <= MOST_MARKS; marks++) {
                frames[tier.ordinal()][marks] =
                        Sprite.valueOf("INSIGNIA_" + tier.name() + "_" + marks);
            }
        }
        return frames;
    }

    /**
     * Rank insignia.
     *
     * The marks used to be strokes drawn here, which is what the art replaced. The clamp stays:
     * a tier runs longer than four ranks, and past the fourth the badge stops gaining marks and
     * the rank's own name carries the difference.
     */
    private void drawInsignia(double x, double y, Rank rank) {
        int marks = Math.min(MOST_MARKS, rank.insigniaCount());
        Sprite badge = INSIGNIA[rank.insignia().ordinal()][marks];
        gc.drawImage(Assets.image(badge), x, y - 4, badge.width(), badge.height());
    }
}
