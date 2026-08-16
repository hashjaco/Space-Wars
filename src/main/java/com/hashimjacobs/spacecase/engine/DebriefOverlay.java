package com.hashimjacobs.spacecase.engine;

import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.GameConfig;
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

    private static final Color BRAND = Color.web("#0ec417");
    private static final Color LABEL = Color.web("#b388ff");
    private static final Color MUTED = Color.web("#9fb0c9");
    private static final Color PANEL = Color.web("#0c1120");
    private static final Color PANEL_EDGE = Color.web("#3b4560");

    private static final double PANEL_WIDTH = 396;
    private static final double PANEL_TOP = 150;
    /** Tall enough for six bonus rows plus the credits line; the ladders can field two at once. */
    private static final double PANEL_HEIGHT = 560;

    private final GraphicsContext gc;
    private final Font titleFont = Font.font("Verdana", FontWeight.BOLD, 30);
    private final Font headingFont = Font.font("Verdana", FontWeight.BOLD, 17);
    private final Font rowFont = Font.font("Verdana", FontWeight.NORMAL, 13);
    private final Font valueFont = Font.font("Verdana", FontWeight.BOLD, 15);
    private final Font promptFont = Font.font("Verdana", FontWeight.BOLD, 16);

    DebriefOverlay(GraphicsContext gc) {
        this.gc = gc;
    }

    /**
     * @param armed whether a fresh key press will now dismiss the screen; the prompt stays hidden
     *              until it is, so it never invites a press that is already being held
     */
    void draw(Level level, List<Debrief> debriefs, List<Standing> standings, boolean armed) {
        gc.setFill(Color.color(0, 0, 0, 0.78));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        gc.setTextBaseline(VPos.TOP);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(titleFont);
        gc.setFill(BRAND);
        gc.fillText("LEVEL " + level.number() + " CLEARED", GameConfig.WIDTH / 2, 74);
        gc.setFont(headingFont);
        gc.setFill(MUTED);
        gc.fillText(level.label().toUpperCase(), GameConfig.WIDTH / 2, 112);

        // One column per pilot, centred as a group so a solo run does not sit off to one side.
        double totalWidth = debriefs.size() * PANEL_WIDTH + (debriefs.size() - 1) * 24;
        double x = (GameConfig.WIDTH - totalWidth) / 2;
        for (int i = 0; i < debriefs.size(); i++) {
            Standing standing = i < standings.size() ? standings.get(i) : null;
            drawColumn(x, debriefs.get(i), standing);
            x += PANEL_WIDTH + 24;
        }

        if (!armed) {
            return;
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(promptFont);
        gc.setFill(Color.WHITE);
        gc.fillText("PRESS ANY BUTTON TO CONTINUE", GameConfig.WIDTH / 2, PANEL_TOP + PANEL_HEIGHT + 22);
    }

    private void drawColumn(double x, Debrief debrief, Standing standing) {
        gc.setFill(PANEL);
        gc.fillRoundRect(x, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT, 12, 12);
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT, 12, 12);

        double left = x + 22;
        double right = x + PANEL_WIDTH - 22;
        double y = PANEL_TOP + 20;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(headingFont);
        gc.setFill(Color.WHITE);
        gc.fillText(debrief.pilotName(), left, y);
        y += 34;

        y = drawStat(left, right, y, "Enemies downed", String.valueOf(debrief.enemiesKilled()));
        y = drawStat(left, right, y, "Asteroids cleared", String.valueOf(debrief.asteroidsDestroyed()));
        y = drawStat(left, right, y, "Accuracy", debrief.accuracyPercent() + "%");
        y = drawStat(left, right, y, "Damage taken", String.valueOf(debrief.damageTaken()));
        y = drawStat(left, right, y, "Lives lost", String.valueOf(debrief.livesLost()));
        y = drawStat(left, right, y, "Clear time", debrief.clearSeconds() + "s");

        y += 10;
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(left, y, right, y);
        y += 14;

        gc.setFont(rowFont);
        gc.setFill(LABEL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("BONUSES", left, y);
        y += 22;
        for (Debrief.Bonus bonus : debrief.bonuses()) {
            y = drawStat(left, right, y, bonus.label(), "+" + bonus.points());
        }

        y += 6;
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(valueFont);
        gc.setFill(BRAND);
        gc.fillText("TOTAL", left, y);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("+" + debrief.totalBonus(), right, y);
        y += 26;

        // What the level paid into the garage. A summary line under the rule rather than another
        // stat row, because it is the one number here that buys something.
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(rowFont);
        gc.setFill(LABEL);
        gc.fillText("CREDITS EARNED", left, y);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFill(Color.WHITE);
        gc.fillText("+" + debrief.credits() + " CR", right, y);
        y += 32;

        if (standing != null) {
            drawStanding(left, right, y, standing);
        }
    }

    private double drawStat(double left, double right, double y, String label, String value) {
        gc.setFont(rowFont);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(MUTED);
        gc.fillText(label, left, y);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFill(Color.WHITE);
        gc.fillText(value, right, y);
        return y + 21;
    }

    private void drawStanding(double left, double right, double y, Standing standing) {
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
        gc.fillText(rank.label(), left, y);
        drawInsignia(right - 52, y + 2, rank);
        y += 26;

        gc.setFont(rowFont);
        gc.setFill(MUTED);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("Career  " + standing.careerScore(), left, y);
        y += 20;

        // Progress toward the next rank, or a full bar at the top of the ladder.
        double width = right - left;
        gc.setFill(Color.web("#22283a"));
        gc.fillRoundRect(left, y, width, 8, 4, 4);
        gc.setFill(BRAND);
        gc.fillRoundRect(left, y, width * standing.progress(), 8, 4, 4);
        y += 16;

        gc.setFill(MUTED);
        gc.setTextAlign(TextAlignment.LEFT);
        String toward = rank.isHighest()
                ? "Top of the ladder"
                : "Next  " + rank.next().label() + "  at " + rank.next().careerScoreRequired();
        gc.fillText(toward, left, y);
    }

    /**
     * Rank insignia, drawn from the rank's tier rather than loaded as art.
     *
     * ponytail: twenty-six PNGs for something this small is not worth generating. Replace with real
     * insignia if the drawn marks ever read as placeholder.
     */
    private void drawInsignia(double x, double y, Rank rank) {
        int marks = Math.min(4, rank.insigniaCount());
        gc.setStroke(BRAND);
        gc.setFill(BRAND);
        gc.setLineWidth(2);
        gc.setLineCap(StrokeLineCap.ROUND);

        for (int mark = 0; mark < marks; mark++) {
            double offset = mark * 5;
            switch (rank.insignia()) {
                case CHEVRONS -> {
                    gc.strokeLine(x, y + 10 - offset, x + 8, y + 3 - offset);
                    gc.strokeLine(x + 8, y + 3 - offset, x + 16, y + 10 - offset);
                }
                case RODS -> gc.fillRoundRect(x + mark * 7, y, 4, 13, 2, 2);
                case BARS -> gc.fillRect(x + mark * 7, y + 2, 5, 11);
                case STARS -> drawStar(x + mark * 11 + 5, y + 7, 5);
            }
        }
    }

    private void drawStar(double cx, double cy, double radius) {
        double[] xs = new double[10];
        double[] ys = new double[10];
        for (int point = 0; point < 10; point++) {
            // Alternate outer and inner radius, starting at the top.
            double reach = point % 2 == 0 ? radius : radius * 0.42;
            double angle = -Math.PI / 2 + point * Math.PI / 5;
            xs[point] = cx + Math.cos(angle) * reach;
            ys[point] = cy + Math.sin(angle) * reach;
        }
        gc.fillPolygon(xs, ys, 10);
    }
}
