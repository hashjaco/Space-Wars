import com.hashimjacobs.spacecase.engine.Terrain;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.mode.WorldTemplate;
import java.awt.*; import java.awt.geom.Path2D; import java.awt.image.BufferedImage;
import java.io.File; import javax.imageio.ImageIO;

/** What a side-on cave looks like: rock on the ceiling and the floor. */
public class SideCave {
    public static void main(String[] a) throws Exception {
        int w = 996, h = 864;
        Orientation f = Orientation.RIGHT_TO_LEFT;
        BufferedImage img = new BufferedImage(w, h * 2 + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x0a0e1a)); g.fillRect(0,0,img.getWidth(),img.getHeight());
        int[] ticks = {0, 300};
        for (int panel = 0; panel < 2; panel++) {
            Terrain t = new Terrain(WorldTemplate.CAVE, f, 9);
            for (int i = 0; i < ticks[panel]; i++) t.tick(false);
            int oy = panel * (h + 20);
            // across is vertical here, depth runs right-to-left
            Path2D.Double ceil = new Path2D.Double(), floor = new Path2D.Double();
            ceil.moveTo(0, oy); floor.moveTo(0, oy + h);
            double min = 1e9, minAtX = 0, minLo = 0;
            for (int px = 0; px <= w; px += 3) {
                double depth = w - px;              // screen x -> depth
                double lo = t.laneLow(depth), hi = t.laneHigh(depth);
                ceil.lineTo(px, oy + lo);
                floor.lineTo(px, oy + hi);
                if (hi - lo < min) { min = hi - lo; minAtX = px; minLo = lo; }
            }
            ceil.lineTo(w, oy); ceil.closePath();
            floor.lineTo(w, oy + h); floor.closePath();
            g.setColor(new Color(0x3a2f27)); g.fill(ceil); g.fill(floor);
            g.setColor(new Color(0x7a6250)); g.setStroke(new BasicStroke(3)); g.draw(ceil); g.draw(floor);
            g.setColor(new Color(0x0ec417));
            g.fillRect((int) minAtX - 32, (int)(oy + minLo + 6), 64, 40);   // side-on ship, to scale
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.drawString("side-on cave, tick " + ticks[panel]
                    + "   narrowest gap " + Math.round(min) + "px of " + h, 16, oy + 34);
            System.out.printf("tick %-4d narrowest ceiling-to-floor gap %.0fpx of %d (%.0f%%)%n",
                    ticks[panel], min, h, 100.0 * min / h);
        }
        g.dispose(); ImageIO.write(img, "png", new File(a[0])); System.out.println("wrote " + a[0]);
    }
}
