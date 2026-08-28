import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;

/**
 * Every flagship in the campaign on one sheet, drawn at true relative scale.
 *
 * {@code tools/preview/BossSheet} exists and is not this: it scales each boss to fill its cell,
 * which is right for asking "do these six read as different ships" and wrong for the only question
 * a plate like this answers, which is how much bigger the last one is than the first. So the scale
 * here is one number shared by all fifty, and the growth from Sentinel to Aeon is the picture.
 *
 * Names, health and sizes are read from the {@link Boss} and {@link Level} enums rather than typed
 * out, so this cannot drift from the game the way a hand-written table would.
 *
 * Note {@code BossArt.width()/height()} are the on-screen size, not the PNG's natural size -- the
 * source frames are drawn larger and decoded down. Drawing the file at its own dimensions would
 * make the plate a comparison of source art rather than of ships.
 *
 * usage: java -cp target/classes:. FlagshipPlate <out.png>
 */
public final class FlagshipPlate {

    private static final int COLS = 10;
    private static final int CELL_W = 340;
    private static final int CELL_H = 360;
    private static final int ART_H = 210;
    private static final int MARGIN = 100;
    private static final int HEAD_H = 420;
    private static final int BAND_LABEL = 54;

    private static final Color SPACE = new Color(0x0a0e1a);
    private static final Color EDGE = new Color(0x1d2436);
    private static final Color TEXT = Color.WHITE;
    private static final Color DIM = new Color(0x8b98ad);
    private static final Color FAINT = new Color(0x6d7a90);

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "docs/press/posters/flagships.png";

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

        // One scale for all fifty, set by whichever ship is largest in either axis.
        double widest = 0;
        double tallest = 0;
        for (Level level : Level.values()) {
            widest = Math.max(widest, level.boss().art().width());
            tallest = Math.max(tallest, level.boss().art().height());
        }
        double scale = Math.min((CELL_W - 60) / widest, ART_H / tallest);

        drawHeader(g, width);

        int y = HEAD_H;
        for (Galaxy galaxy : Galaxy.values()) {
            Color accent = Color.decode(galaxy.accent());
            drawBandLabel(g, galaxy, accent, y, width);
            int row = 0;
            for (Level level : galaxy.levels()) {
                int cx = MARGIN + row * CELL_W;
                int cy = y + BAND_LABEL;
                drawCell(g, level, accent, scale, cx, cy);
                row++;
            }
            y += bandH;
        }

        drawFooter(g, scale, height, width);

        g.dispose();
        File file = new File(out);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(plate, "png", file);
        System.out.println(out + "  " + width + "x" + height);
    }

    private static void drawHeader(Graphics2D g, int width) {
        g.setColor(TEXT);
        g.setFont(new Font("Impact", Font.PLAIN, 150));
        g.drawString("SPACE CASE", MARGIN, 190);

        g.setColor(new Color(0x0ec417));
        g.fillRect(MARGIN, 220, 420, 3);

        g.setColor(DIM);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 34));
        g.drawString("FIFTY FLAGSHIPS, AT TRUE RELATIVE SCALE", MARGIN, 285);

        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 20));
        g.drawString("One flagship closes each of the fifty levels. Five galaxies, ten apiece.",
                MARGIN, 330);
        g.drawString("Hull sizes and health are the shipped values, read from the game.",
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

    private static void drawCell(Graphics2D g, Level level, Color accent, double scale,
                                 int cx, int cy) {
        Boss boss = level.boss();
        int drawW = (int) Math.round(boss.art().width() * scale);
        int drawH = (int) Math.round(boss.art().height() * scale);

        File file = new File("src/main/resources" + boss.art().framePath(1));
        if (file.exists()) {
            try {
                BufferedImage art = ImageIO.read(file);
                // Bottom-aligned on a shared baseline, so a taller ship reads as taller rather
                // than as merely differently centred.
                int ax = cx + (CELL_W - drawW) / 2;
                int ay = cy + ART_H - drawH;
                g.drawImage(art, ax, ay, drawW, drawH, null);
            } catch (Exception e) {
                System.err.println("could not read " + file + ": " + e.getMessage());
            }
        }

        int textY = cy + ART_H + 34;
        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 17));
        g.drawString(String.valueOf(level.number()), cx + 20, textY);

        g.setColor(TEXT);
        g.setFont(new Font("Helvetica Neue", Font.BOLD, 18));
        g.drawString(fit(g, boss.label(), CELL_W - 70), cx + 48, textY);

        g.setColor(accent);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 16));
        g.drawString(String.format("%,d HP", boss.health()), cx + 48, textY + 26);

        g.setColor(new Color(0x4c586c));
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 14));
        g.drawString(fit(g, level.label(), CELL_W - 70), cx + 48, textY + 50);
    }

    private static void drawFooter(Graphics2D g, double scale, int height, int width) {
        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 18));
        String note = String.format(
                "Drawn at %.2f× on-screen size · health rises from %,d to %,d across the "
                + "campaign · generated from the source, not transcribed",
                scale, Level.values()[0].boss().health(),
                Level.values()[Level.values().length - 1].boss().health());
        g.drawString(note, MARGIN, height - 46);
    }

    /** Trims a label to the width available, so nothing overruns its cell. */
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

    private FlagshipPlate() {
    }
}
