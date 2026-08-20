import com.hashimjacobs.spacecase.engine.Terrain;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.mode.WorldTemplate;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Draws what the cave actually looks like, using the same numbers collision uses. */
public class TerrainPreview {
    public static void main(String[] args) throws Exception {
        int w = 996, h = 864;
        // Three moments: arrival, mid-level, and the boss chamber opening.
        int[] ticks = {0, 240, 0};
        boolean[] boss = {false, false, true};
        String[] label = {"tick 0", "tick 240", "boss chamber"};

        BufferedImage sheet = new BufferedImage(w * 3 + 40, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x0a0e1a));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        for (int panel = 0; panel < 3; panel++) {
            Terrain t = new Terrain(WorldTemplate.CAVE, Orientation.TOP_DOWN, 4);
            for (int i = 0; i < ticks[panel]; i++) t.tick(false);
            if (boss[panel]) for (int i = 0; i < 400; i++) t.tick(true);

            int ox = panel * (w + 20);
            double pitch = Terrain.PITCH;
            g.setColor(new Color(0x3a2f27));
            java.awt.geom.Path2D.Double left = new java.awt.geom.Path2D.Double();
            java.awt.geom.Path2D.Double right = new java.awt.geom.Path2D.Double();
            left.moveTo(ox, 0); right.moveTo(ox + w, 0);
            for (double d = 0; d <= h; d += pitch) {
                left.lineTo(ox + t.laneLow(d), d);
                right.lineTo(ox + t.laneHigh(d), d);
            }
            left.lineTo(ox, h); left.closePath();
            right.lineTo(ox + w, h); right.closePath();
            g.fill(left); g.fill(right);
            g.setColor(new Color(0x7a6250));
            g.setStroke(new BasicStroke(3));
            g.draw(left); g.draw(right);

            // Narrowest lane in this panel, and a player-sized box for scale.
            double min = Double.MAX_VALUE; double minAt = 0;
            for (double d = 0; d <= h; d += pitch) {
                double lane = t.laneHigh(d) - t.laneLow(d);
                if (lane < min) { min = lane; minAt = d; }
            }
            g.setColor(new Color(0x0ec417));
            g.drawLine((int)(ox + t.laneLow(minAt)), (int) minAt,
                       (int)(ox + t.laneHigh(minAt)), (int) minAt);
            g.fillRect((int)(ox + t.laneLow(minAt) + 4), (int) minAt - 30, 64, 60);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.drawString(label[panel] + "   narrowest lane " + Math.round(min) + "px", ox + 16, 34);
            g.drawString("(green box = a 64px ship, to scale)", ox + 16, 62);
            System.out.printf("%-14s narrowest lane %.0fpx of %d  (%.0f%%)%n",
                    label[panel], min, w, 100.0 * min / w);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }
}
