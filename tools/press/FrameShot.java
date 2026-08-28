import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;

/**
 * Puts a native 996×864 capture into a 1920×1080 frame that is designed rather than padded.
 *
 * The arena is nearly square and every storefront wants sixteen by nine, so something has to fill
 * the 337px each side. Flat black bars would waste a third of the frame; these carry the level's
 * own sky, heavily darkened, plus the galaxy and the level it is -- so the surround belongs to the
 * picture instead of apologising for it.
 *
 * Scale is exactly 1.25 (996×864 → 1245×1080), which keeps the upscale clean.
 *
 * Pass {@code -} for the input to draw the surround alone, leaving the arena black. That is what
 * the trailer uses: framing five thousand PNGs through here would be slow and pointless when
 * ffmpeg can overlay the video onto one still plate instead.
 *
 * usage: java -cp target/classes:. FrameShot <level> <in.png|-> <out.png>
 */
public final class FrameShot {

    private static final int OUT_W = 1920;
    private static final int OUT_H = 1080;
    private static final int GAME_W = 1245;
    private static final int GAME_H = 1080;
    private static final int GUTTER = (OUT_W - GAME_W) / 2;

    private static final Color SPACE = new Color(0x0a0e1a);
    private static final Color TEXT = Color.WHITE;
    private static final Color DIM = new Color(0x8b98ad);
    private static final Color FAINT = new Color(0x6d7a90);

    public static void main(String[] args) throws Exception {
        Level level = Level.values()[Integer.parseInt(args[0]) - 1];
        BufferedImage game = "-".equals(args[1]) ? null : ImageIO.read(new File(args[1]));
        Galaxy galaxy = level.galaxy();
        Color accent = Color.decode(galaxy.accent());

        BufferedImage out = new BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g.setColor(SPACE);
        g.fillRect(0, 0, OUT_W, OUT_H);
        drawSurroundSky(g, level);

        // The arena, at a clean 1.25. Left black when only the plate was asked for.
        if (game != null) {
            g.drawImage(game, GUTTER, 0, GAME_W, GAME_H, null);
        } else {
            g.setColor(SPACE);
            g.fillRect(GUTTER, 0, GAME_W, GAME_H);
        }

        // Hairlines, so the arena reads as framed rather than cropped.
        g.setColor(accent);
        g.setStroke(new BasicStroke(1));
        g.drawLine(GUTTER, 0, GUTTER, OUT_H);
        g.drawLine(GUTTER + GAME_W, 0, GUTTER + GAME_W, OUT_H);

        drawLeft(g, galaxy, accent);
        drawRight(g, level, accent);

        g.dispose();
        File file = new File(args[2]);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(out, "png", file);
        System.out.println(args[2] + "  " + OUT_W + "x" + OUT_H);
    }

    /**
     * The level's own far layer, blurred and dimmed almost to nothing.
     *
     * Blurred by shrinking to a thumbnail and letting bilinear stretch it back, which costs one
     * scale and needs no convolution kernel. At this darkness nothing sharper would read anyway.
     */
    private static void drawSurroundSky(Graphics2D g, Level level) {
        File sky = new File("src/main/resources/sprites/level-" + level.number() + "/far.png");
        if (!sky.exists()) {
            return;
        }
        try {
            BufferedImage far = ImageIO.read(sky);
            BufferedImage tiny = new BufferedImage(48, 42, BufferedImage.TYPE_INT_RGB);
            Graphics2D tg = tiny.createGraphics();
            tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            tg.drawImage(far, 0, 0, 48, 42, null);
            tg.dispose();

            g.drawImage(tiny, 0, 0, OUT_W, OUT_H, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.86f));
            g.setColor(SPACE);
            g.fillRect(0, 0, OUT_W, OUT_H);
            g.setComposite(AlphaComposite.SrcOver);
        } catch (Exception e) {
            System.err.println("could not read " + sky + ": " + e.getMessage());
        }
    }

    private static void drawLeft(Graphics2D g, Galaxy galaxy, Color accent) {
        // The galaxy's number, huge and nearly out of sight: structure, not a label to read.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.13f));
        g.setColor(accent);
        g.setFont(new Font("Impact", Font.PLAIN, 300));
        String number = String.valueOf(galaxy.number());
        int nw = g.getFontMetrics().stringWidth(number);
        g.drawString(number, (GUTTER - nw) / 2, 700);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(accent);
        g.setFont(new Font("Impact", Font.PLAIN, 44));
        g.drawString(galaxy.label().toUpperCase(), 54, 190);

        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 17));
        g.drawString("GALAXY " + galaxy.number() + " OF " + Galaxy.values().length, 54, 222);
        g.drawString("LEVELS " + galaxy.first().number() + "–" + galaxy.last().number(), 54, 246);

        g.setColor(accent);
        g.fillRect(54, 130, 90, 3);
    }

    private static void drawRight(Graphics2D g, Level level, Color accent) {
        int x = GUTTER + GAME_W + 54;

        g.setColor(TEXT);
        g.setFont(new Font("Impact", Font.PLAIN, 40));
        g.drawString(String.valueOf(level.number()), x, 190);

        g.setColor(DIM);
        g.setFont(new Font("Helvetica Neue", Font.BOLD, 21));
        drawWrapped(g, level.label().toUpperCase(), x, 232, GUTTER - 90, 28);

        g.setColor(accent);
        g.fillRect(x, 130, 90, 3);

        g.setColor(FAINT);
        g.setFont(new Font("Helvetica Neue", Font.PLAIN, 17));
        g.drawString(level.boss().label(), x, OUT_H - 92);
        g.setColor(new Color(0x4c586c));
        g.drawString("FLAGSHIP", x, OUT_H - 116);
    }

    /** Level names run to two words; wrapping keeps the longest inside its gutter. */
    private static void drawWrapped(Graphics2D g, String text, int x, int y, int width, int line) {
        StringBuilder current = new StringBuilder();
        int at = y;
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > width && current.length() > 0) {
                g.drawString(current.toString(), x, at);
                at += line;
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) {
            g.drawString(current.toString(), x, at);
        }
    }

    private FrameShot() {
    }
}
