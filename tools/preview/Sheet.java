import java.awt.*; import java.awt.image.BufferedImage; import java.io.File;
import javax.imageio.ImageIO;

/** A contact sheet of one galaxy's backdrops, composited far+mid+near as the game stacks them. */
public class Sheet {
    public static void main(String[] a) throws Exception {
        int from = Integer.parseInt(a[0]), to = Integer.parseInt(a[1]);
        int n = to - from + 1, cols = 5, rows = (n + cols - 1) / cols;
        int tw = 249, th = 216;   // quarter scale
        BufferedImage sheet = new BufferedImage(cols * tw, rows * (th + 20), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(new Color(0x0a0e1a)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < n; i++) {
            int lvl = from + i, cx = (i % cols) * tw, cy = (i / cols) * (th + 20);
            for (String layer : new String[]{"far", "mid", "near"}) {
                File f = new File("src/main/resources/sprites/level-" + lvl + "/" + layer + ".png");
                if (f.exists()) g.drawImage(ImageIO.read(f), cx, cy + 20, tw, th, null);
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString("level-" + lvl, cx + 6, cy + 15);
        }
        g.dispose(); ImageIO.write(sheet, "png", new File(a[2]));
        System.out.println("wrote " + a[2]);
    }
}
