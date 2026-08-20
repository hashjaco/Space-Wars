import java.awt.*; import java.awt.image.BufferedImage; import java.io.File;
import javax.imageio.ImageIO;

/** Frame 1 of each named boss, side by side at its on-screen size. */
public class BossSheet {
    public static void main(String[] a) throws Exception {
        String[] dirs = a[0].split(",");
        int tw = 250, th = 250;
        BufferedImage sheet = new BufferedImage(tw * Math.min(dirs.length, 4),
                th * ((dirs.length + 3) / 4), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(new Color(0x0a0e1a)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < dirs.length; i++) {
            File f = new File("src/main/resources/sprites/" + dirs[i] + "/1.png");
            int cx = (i % 4) * tw, cy = (i / 4) * th;
            if (f.exists()) {
                BufferedImage img = ImageIO.read(f);
                double scale = Math.min((tw - 20.0) / img.getWidth(), (th - 40.0) / img.getHeight());
                int dw = (int)(img.getWidth() * scale), dh = (int)(img.getHeight() * scale);
                g.drawImage(img, cx + (tw - dw) / 2, cy + 26 + (th - 40 - dh) / 2, dw, dh, null);
            }
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(dirs[i].replace("boss-", ""), cx + 8, cy + 18);
        }
        g.dispose(); ImageIO.write(sheet, "png", new File(a[1]));
        System.out.println("wrote " + a[1]);
    }
}
