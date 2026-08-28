import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.mode.WorldTemplate;

/**
 * All fifty skies on one sheet, each composited far+mid+near the way the game stacks them.
 *
 * A companion to {@code FlagshipPlate}: same grid, same margins, same type, so the two hang
 * together. {@code tools/preview/Sheet} draws one galaxy for judging a change; this draws the whole
 * campaign for looking at, which is a different job and wants the level names and the marks.
 *
 * The marks under each tile are the things you cannot see in a still: which levels are flown
 * side-on, and which close in from both sides. Read from the enums, not typed.
 *
 * usage: java -cp target/classes:. SkyPlate <out.png>
 */
public final class SkyPlate {

    private static final int COLS = 10;
    private static final int CELL_W = 340;
    private static final int TILE_W = 300;
    private static final int TILE_H = 260;
    private static final int CELL_H = TILE_H + 96;
    private static final int MARGIN = 100;
    private static final int HEAD_H = 420;
    private static final int BAND_LABEL = 54;

    private static final Color SPACE = new Color(0x0a0e1a);
    private static final Color EDGE = new Color(0x1d2436);
    private static final Color TEXT = Color.WHITE;
    private static final Color DIM = new Color(0x8b98ad);
    private static final Color FAINT = new Color(0x6d7a90);

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "docs/press/posters/skies.png";

        int width = MARGIN * 2 + COLS * CELL_W;
        int bandH = BAND_LABEL + CELL_H;
        int height = HEAD_H + Galaxy.values().length * bandH + MARGIN;

        BufferedImage plate = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = plate.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(SPACE);
        g.fillRect(0, 0, width, height);

        drawHeader(g);

        int y = HEAD_H;
        for (Galaxy galaxy : Galaxy.values()) {
            Color accent = Color.decode(galaxy.accent());
            drawBandLabel(g, galaxy, accent, y, width);
            int row = 0;
            for (Level level : galaxy.levels()) {
                drawCell(g, level, accent, MARGIN + row * CELL_W, y + BAND_LABEL);
                row++;
            }
            y += bandH;
        }

        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 18));
        g.drawString("Each tile is that level's three parallax layers, composited as the game "
                + "stacks them · → flown side-on · ◇ rock closes in from both sides",
                MARGIN, height - 46);

        g.dispose();
        File file = new File(out);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(plate, "png", file);
        System.out.println(out + "  " + width + "x" + height);
    }

    private static void drawHeader(Graphics2D g) {
        g.setColor(TEXT);
        g.setFont(new Font("Impact", Font.PLAIN, 150));
        g.drawString("SPACE CASE", MARGIN, 190);

        g.setColor(new Color(0x0ec417));
        g.fillRect(MARGIN, 220, 420, 3);

        g.setColor(DIM);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 34));
        g.drawString("FIFTY SKIES", MARGIN, 285);

        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 20));
        g.drawString("Every place the campaign visits, in the order it is flown.", MARGIN, 330);
        g.drawString("Generated art, one seed per layer, reproducible byte for byte.",
                MARGIN, 360);
    }

    private static void drawBandLabel(Graphics2D g, Galaxy galaxy, Color accent, int y, int width) {
        g.setColor(accent);
        g.setFont(new Font("Impact", Font.PLAIN, 30));
        String label = galaxy.label().toUpperCase();
        g.drawString(label, MARGIN, y + 34);

        int labelEnd = MARGIN + g.getFontMetrics().stringWidth(label) + 20;
        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 19));
        String range = "LEVELS " + galaxy.first().number() + "–" + galaxy.last().number();
        g.drawString(range, labelEnd, y + 32);

        int rangeEnd = labelEnd + g.getFontMetrics().stringWidth(range) + 24;
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(1));
        g.drawLine(rangeEnd, y + 26, width - MARGIN, y + 26);
    }

    private static void drawCell(Graphics2D g, Level level, Color accent, int cx, int cy) {
        int tx = cx + (CELL_W - TILE_W) / 2;
        for (String layer : new String[] {"far", "mid", "near"}) {
            File file = new File("src/main/resources/sprites/level-" + level.number()
                    + "/" + layer + ".png");
            if (!file.exists()) {
                continue;
            }
            try {
                g.drawImage(ImageIO.read(file), tx, cy, TILE_W, TILE_H, null);
            } catch (Exception e) {
                System.err.println("could not read " + file + ": " + e.getMessage());
            }
        }
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(1));
        g.drawRect(tx, cy, TILE_W, TILE_H);

        int textY = cy + TILE_H + 32;
        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 17));
        g.drawString(String.valueOf(level.number()), tx, textY);

        g.setColor(TEXT);
        g.setFont(new Font("Helvetica Neue", Font.BOLD, 18));
        g.drawString(fit(g, level.label(), TILE_W - 40), tx + 30, textY);

        // The two things a still cannot show.
        StringBuilder marks = new StringBuilder();
        if (level.orientation() == Orientation.RIGHT_TO_LEFT) {
            marks.append("→ side-on   ");
        }
        if (level.template() == WorldTemplate.CAVE) {
            marks.append("◇ closes in");
        }
        if (marks.length() > 0) {
            g.setColor(accent);
            g.setFont(new Font("Helvetica Neue", Font.PLAIN, 15));
            g.drawString(marks.toString().trim(), tx + 30, textY + 24);
        }
    }

    private static String fit(Graphics2D g, String text, int available) {
        if (g.getFontMetrics().stringWidth(text) <= available) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 1
                && g.getFontMetrics().stringWidth(trimmed + "…") > available) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "…";
    }

    private SkyPlate() {
    }
}
