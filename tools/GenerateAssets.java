import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Generates every sprite and sound that is not original hand-drawn work, so the repository contains
 * no third-party art or audio and can be published under a single licence.
 *
 * Run with:  java tools/GenerateAssets.java
 *
 * Output is deterministic: every random draw comes from a fixed seed, so re-running reproduces the
 * same assets byte for byte, which is what CI checks.
 *
 * Deliberately NOT generated, because they are the author's own work and better than anything here
 * would produce: the projectiles, the explosion frame sequences, and the music. The player ships are
 * the author's own too, but arrive as one spritesheet in {@code tools/art} and are cut up here.
 */
public final class GenerateAssets {

    private static final Path SPRITES = Path.of("src/main/resources/sprites");
    private static final Path SOUNDS = Path.of("src/main/resources/sounds");
    private static final Path ART = Path.of("tools/art");

    private static final int SAMPLE_RATE = 44100;

    // Palette shared across the generated sprites so they read as one set.
    private static final Color HULL_DARK = new Color(0x1b2030);
    private static final Color HULL_MID = new Color(0x39415c);
    private static final Color HULL_LIGHT = new Color(0x6d7899);
    private static final Color HOSTILE = new Color(0xc2384a);
    private static final Color HOSTILE_GLOW = new Color(0xff6b5a);
    private static final Color BRAND = new Color(0x0ec417);

    public static void main(String[] args) throws IOException {
        Files.createDirectories(SPRITES);
        Files.createDirectories(SOUNDS);

        players();
        asteroids();
        enemies();
        bosses();
        monsters();
        pickups();
        backgrounds();

        laser();
        explosionSound();
        thud();
        gameOverSting();
        levelClearSting();
        lowHealthAlarm();

        System.out.println("done");
    }

    // ---------------------------------------------------------- player ships

    /**
     * Bank poses in the spritesheet's column order, which runs hardest *right* to hardest left.
     *
     * The sheet's leftmost column is a ship rolled to its right, not to the left of the frame. Naming
     * the columns the other way round is what made holding D bank the ship left.
     */
    private static final String[] POSES =
            {"bank-right", "right", "straight", "left", "bank-left"};

    /** Rows in the spritesheet, top to bottom. */
    private static final int SHEET_ROWS = 5;

    /**
     * Which spritesheet row supplies each player's hull, player one first.
     *
     * Row 3 is green and row 1 red: green reads as friendly against the red hostile palette, so
     * player one flies it. ponytail: rows 0 (engines off), 2 (rolled) and 4 (second green) go
     * unused -- wire them up if the ships ever need an idle or a barrel roll.
     */
    private static final int[] PLAYER_ROWS = {3, 1};

    /**
     * Common canvas every cut frame is padded to.
     *
     * The sheet's cells differ in size by up to 30px. Decoding at a fixed on-screen size preserves
     * each image's own aspect ratio, so unpadded frames would draw at visibly different sizes and
     * the ship would appear to grow as it banked. Padding to one canvas makes every pose scale
     * identically.
     */
    private static final int PLAYER_FRAME_WIDTH = 180;
    private static final int PLAYER_FRAME_HEIGHT = 192;

    /** Ignores stray antialiasing specks when hunting for ships; the smallest real one is ~12,700px. */
    private static final int MIN_SPRITE_PIXELS = 800;

    /** The checkerboard is two near-white greys; every ship pixel is tinted or much darker. */
    private static final int CHECKER_MIN_BRIGHTNESS = 238;

    /**
     * Garage paint jobs, as a hue applied to player one's cut frames.
     *
     * A hue rotation rather than a repaint: the sheet's shading and its black outlines survive
     * untouched, since outlines are unsaturated and a hue swap cannot move them. Chrome is the odd
     * one out -- it desaturates rather than recolours, so its hue is arbitrary.
     */
    private enum Paintwork {
        AZURE(0.55f, 1.0f),
        AMBER(0.10f, 1.0f),
        VIOLET(0.76f, 1.0f),
        CHROME(0.00f, 0.15f);

        private final float hue;
        private final float saturationScale;

        Paintwork(float hue, float saturationScale) {
            this.hue = hue;
            this.saturationScale = saturationScale;
        }
    }

    /** Body kits, drawn as transparent decals so one set serves every paint job. */
    private static final String[] KITS = {"fins", "armour", "lance"};

    private static void players() throws IOException {
        BufferedImage sheet = ImageIO.read(ART.resolve("spritesheet.png").toFile());
        boolean[] background = keyOutCheckerboard(sheet);
        List<Rectangle> cells = findShips(sheet, background);

        for (int player = 0; player < PLAYER_ROWS.length; player++) {
            for (int pose = 0; pose < POSES.length; pose++) {
                Rectangle cell = cells.get(PLAYER_ROWS[player] * POSES.length + pose);
                BufferedImage frame = cut(sheet, background, cell);
                String name = "player/p" + (player + 1) + "-" + POSES[pose];
                write(frame, SPRITES.resolve(name + ".png"));
                write(hitFlash(frame), SPRITES.resolve(name + "-hit.png"));
            }
        }

        // Everything below is bought in the garage. Both sets derive from player one's row, so the
        // whole cosmetic catalogue costs one extra cut per pose rather than new hand-drawn art.
        for (int pose = 0; pose < POSES.length; pose++) {
            Rectangle cell = cells.get(PLAYER_ROWS[0] * POSES.length + pose);
            BufferedImage hull = cut(sheet, background, cell);

            for (Paintwork paint : Paintwork.values()) {
                BufferedImage repainted = recolour(hull, paint);
                String name = "player/" + paint.name().toLowerCase() + "-" + POSES[pose];
                write(repainted, SPRITES.resolve(name + ".png"));
                write(hitFlash(repainted), SPRITES.resolve(name + "-hit.png"));
            }
            for (int kit = 0; kit < KITS.length; kit++) {
                BufferedImage decal = kitOverlay(hull, kit);
                write(decal, SPRITES.resolve("player/kit-" + KITS[kit] + "-" + POSES[pose] + ".png"));
            }
        }
    }

    /** Rotates every opaque pixel's hue, preserving its brightness, shading and alpha. */
    private static BufferedImage recolour(BufferedImage frame, Paintwork paint) {
        BufferedImage painted = blank(frame.getWidth(), frame.getHeight());
        float[] hsb = new float[3];
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int argb = frame.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                Color.RGBtoHSB((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, hsb);
                float saturation = Math.min(1f, hsb[1] * paint.saturationScale);
                int rgb = Color.HSBtoRGB(paint.hue, saturation, hsb[2]);
                painted.setRGB(x, y, (alpha << 24) | (rgb & 0xffffff));
            }
        }
        return painted;
    }

    /**
     * A body kit decal for one pose, sized and placed from the hull it will sit on.
     *
     * Measured off the hull's alpha bounding box rather than drawn at fixed canvas coordinates: the
     * bank poses are narrower and offset, so a decal at fixed coordinates would float off the wing
     * on everything except the straight-ahead frame.
     */
    private static BufferedImage kitOverlay(BufferedImage hull, int kit) {
        Rectangle box = opaqueBounds(hull);
        BufferedImage decal = blank(hull.getWidth(), hull.getHeight());
        if (box == null) {
            return decal;
        }
        Graphics2D g = paint(decal);
        switch (kit) {
            case 0 -> {
                // Delta fins: swept blades off the trailing corners.
                g.setColor(HULL_LIGHT);
                g.fill(finAt(box, -1));
                g.fill(finAt(box, 1));
                g.setColor(BRAND);
                g.fill(new Ellipse2D.Double(box.getCenterX() - box.width * 0.03,
                        box.getMaxY() - box.height * 0.10, box.width * 0.06, box.height * 0.05));
            }
            case 1 -> {
                // Ablative plates: slabs bolted along both flanks.
                g.setColor(HULL_MID);
                double plateW = box.width * 0.13;
                double plateH = box.height * 0.30;
                double plateY = box.getCenterY() - plateH / 2;
                g.fill(new Rectangle2D.Double(box.x + box.width * 0.04, plateY, plateW, plateH));
                g.fill(new Rectangle2D.Double(box.getMaxX() - box.width * 0.04 - plateW, plateY,
                        plateW, plateH));
                g.setColor(HULL_LIGHT);
                g.setStroke(new BasicStroke((float) Math.max(1, box.width * 0.012)));
                g.draw(new Rectangle2D.Double(box.x + box.width * 0.04, plateY, plateW, plateH));
                g.draw(new Rectangle2D.Double(box.getMaxX() - box.width * 0.04 - plateW, plateY,
                        plateW, plateH));
            }
            default -> {
                // Nose lance: a spike off the prow with a hot tip.
                g.setColor(HULL_LIGHT);
                g.fill(path(1, 1, new double[][]{
                        {box.getCenterX() - box.width * 0.045, box.y + box.height * 0.12},
                        {box.getCenterX() + box.width * 0.045, box.y + box.height * 0.12},
                        {box.getCenterX(), box.y - box.height * 0.10}}));
                g.setColor(BRAND);
                g.fill(new Ellipse2D.Double(box.getCenterX() - box.width * 0.022,
                        box.y - box.height * 0.09, box.width * 0.044, box.height * 0.045));
            }
        }
        g.dispose();
        return decal;
    }

    /** One swept fin off the hull's trailing edge; side is -1 for left, 1 for right. */
    private static Path2D finAt(Rectangle box, int side) {
        double rootX = side < 0 ? box.x + box.width * 0.16 : box.getMaxX() - box.width * 0.16;
        double tipX = rootX + side * box.width * 0.20;
        return path(1, 1, new double[][]{
                {rootX, box.getMaxY() - box.height * 0.30},
                {rootX, box.getMaxY() - box.height * 0.06},
                {tipX, box.getMaxY() + box.height * 0.02}});
    }

    /** Bounding box of everything non-transparent, or null for an empty frame. */
    private static Rectangle opaqueBounds(BufferedImage frame) {
        int minX = frame.getWidth();
        int minY = frame.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                if ((frame.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            return null;
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * Marks the sheet's transparent pixels.
     *
     * The supplied sheet has no alpha channel -- its transparency is a baked-in near-white
     * checkerboard. Flooding inward from the border keys that out without punching holes in the
     * ships' own white highlights, which a plain colour test would; the black outlines stop the fill.
     */
    private static boolean[] keyOutCheckerboard(BufferedImage sheet) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        boolean[] background = new boolean[w * h];
        Deque<int[]> pending = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            spreadInto(sheet, background, pending, x, 0);
            spreadInto(sheet, background, pending, x, h - 1);
        }
        for (int y = 0; y < h; y++) {
            spreadInto(sheet, background, pending, 0, y);
            spreadInto(sheet, background, pending, w - 1, y);
        }
        while (!pending.isEmpty()) {
            int[] at = pending.remove();
            spreadInto(sheet, background, pending, at[0] + 1, at[1]);
            spreadInto(sheet, background, pending, at[0] - 1, at[1]);
            spreadInto(sheet, background, pending, at[0], at[1] + 1);
            spreadInto(sheet, background, pending, at[0], at[1] - 1);
        }
        return background;
    }

    private static void spreadInto(BufferedImage sheet, boolean[] background, Deque<int[]> pending,
                                   int x, int y) {
        if (x < 0 || y < 0 || x >= sheet.getWidth() || y >= sheet.getHeight()) {
            return;
        }
        int index = y * sheet.getWidth() + x;
        if (background[index] || !isChecker(sheet.getRGB(x, y))) {
            return;
        }
        background[index] = true;
        pending.add(new int[]{x, y});
    }

    private static boolean isChecker(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        boolean grey = Math.abs(r - g) < 4 && Math.abs(g - b) < 4;
        boolean checker = grey && r >= CHECKER_MIN_BRIGHTNESS;
        return checker;
    }

    /**
     * Bounding boxes of the sheet's ships in reading order.
     *
     * Once the checkerboard is keyed out the ships are the only islands left, so labelling connected
     * regions finds them without needing the grid pitch -- which is just as well, since the cells are
     * neither evenly spaced nor a whole number of pixels apart.
     */
    private static List<Rectangle> findShips(BufferedImage sheet, boolean[] background) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        boolean[] visited = new boolean[w * h];
        List<Rectangle> ships = new ArrayList<>();
        Deque<int[]> pending = new ArrayDeque<>();

        for (int startY = 0; startY < h; startY++) {
            for (int startX = 0; startX < w; startX++) {
                if (visited[startY * w + startX] || background[startY * w + startX]) {
                    continue;
                }
                visited[startY * w + startX] = true;
                pending.add(new int[]{startX, startY});
                int minX = startX;
                int maxX = startX;
                int minY = startY;
                int maxY = startY;
                int pixels = 0;

                while (!pending.isEmpty()) {
                    int[] at = pending.remove();
                    pixels++;
                    minX = Math.min(minX, at[0]);
                    maxX = Math.max(maxX, at[0]);
                    minY = Math.min(minY, at[1]);
                    maxY = Math.max(maxY, at[1]);
                    // Eight-connected: antialiased outlines meet only diagonally in places.
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int nx = at[0] + dx;
                            int ny = at[1] + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                                continue;
                            }
                            int index = ny * w + nx;
                            if (visited[index] || background[index]) {
                                continue;
                            }
                            visited[index] = true;
                            pending.add(new int[]{nx, ny});
                        }
                    }
                }
                if (pixels >= MIN_SPRITE_PIXELS) {
                    ships.add(new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1));
                }
            }
        }

        int expected = SHEET_ROWS * POSES.length;
        if (ships.size() != expected) {
            throw new IllegalStateException(
                    "expected " + expected + " ships in the spritesheet, found " + ships.size());
        }
        // Rows are far enough apart to sort on centre, then each row left to right.
        ships.sort(Comparator.comparingInt(ship -> ship.y + ship.height / 2));
        for (int row = 0; row < ships.size(); row += POSES.length) {
            ships.subList(row, row + POSES.length).sort(Comparator.comparingInt(ship -> ship.x));
        }
        return ships;
    }

    /** Cuts one ship out of the sheet, centred on the common canvas, checkerboard keyed to alpha 0. */
    private static BufferedImage cut(BufferedImage sheet, boolean[] background, Rectangle cell) {
        if (cell.width > PLAYER_FRAME_WIDTH || cell.height > PLAYER_FRAME_HEIGHT) {
            throw new IllegalStateException("ship " + cell + " does not fit the padded frame");
        }
        BufferedImage frame = blank(PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT);
        int offsetX = (PLAYER_FRAME_WIDTH - cell.width) / 2;
        int offsetY = (PLAYER_FRAME_HEIGHT - cell.height) / 2;

        for (int y = 0; y < cell.height; y++) {
            for (int x = 0; x < cell.width; x++) {
                if (background[(cell.y + y) * sheet.getWidth() + cell.x + x]) {
                    continue;
                }
                int rgb = sheet.getRGB(cell.x + x, cell.y + y);
                frame.setRGB(offsetX + x, offsetY + y, 0xff000000 | rgb);
            }
        }
        return frame;
    }

    /**
     * The damage frame for a pose.
     *
     * Baked here rather than blended at runtime because a JavaFX Canvas has no per-draw tint, so the
     * renderer would otherwise need an effect pass per ship per frame.
     */
    private static BufferedImage hitFlash(BufferedImage frame) {
        BufferedImage flashed = blank(frame.getWidth(), frame.getHeight());
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int argb = frame.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                int r = mixToward((argb >> 16) & 0xff, HOSTILE_GLOW.getRed());
                int g = mixToward((argb >> 8) & 0xff, HOSTILE_GLOW.getGreen());
                int b = mixToward(argb & 0xff, HOSTILE_GLOW.getBlue());
                flashed.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return flashed;
    }

    private static int mixToward(int value, int target) {
        int mixed = channel((int) Math.round(value * 0.45 + target * 0.55));
        return mixed;
    }

    // ---------------------------------------------------------------- sprites

    /** Procedural cratered rock, lit from the upper left. Replaces a watermarked download. */
    private static void asteroids() throws IOException {
        int[] sizes = {128, 128, 128};
        String[] names = {"asteroid-small", "asteroid-big", "asteroid-huge"};
        for (int variant = 0; variant < names.length; variant++) {
            int size = sizes[variant];
            BufferedImage image = blank(size, size);
            Graphics2D g = paint(image);
            Random random = new Random(1000 + variant);

            // Irregular silhouette: a circle perturbed per vertex.
            Path2D outline = new Path2D.Double();
            int points = 15 + variant * 3;
            double radius = size * 0.42;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double wobble = radius * (0.76 + random.nextDouble() * 0.3);
                double x = size / 2.0 + Math.cos(angle) * wobble;
                double y = size / 2.0 + Math.sin(angle) * wobble;
                if (i == 0) {
                    outline.moveTo(x, y);
                } else {
                    outline.lineTo(x, y);
                }
            }
            outline.closePath();

            g.setClip(outline);
            g.setPaint(new RadialGradientPaint(
                    (float) (size * 0.34), (float) (size * 0.3), (float) (size * 0.72),
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{new Color(0xb9c0cc), new Color(0x6f7783), new Color(0x24272e)}));
            g.fillRect(0, 0, size, size);

            // Craters, darker with a lit lower rim.
            int craters = 9 + variant * 4;
            for (int i = 0; i < craters; i++) {
                double cx = size * (0.2 + random.nextDouble() * 0.6);
                double cy = size * (0.2 + random.nextDouble() * 0.6);
                double cr = size * (0.035 + random.nextDouble() * 0.075);
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(new Ellipse2D.Double(cx - cr, cy - cr, cr * 2, cr * 2));
                g.setColor(new Color(255, 255, 255, 34));
                g.fill(new Ellipse2D.Double(cx - cr * 0.8, cy - cr * 0.55, cr * 1.6, cr * 1.3));
            }
            g.setClip(null);

            g.setColor(new Color(0x15171c));
            g.setStroke(new BasicStroke(2f));
            g.draw(outline);
            g.dispose();
            write(image, SPRITES.resolve(names[variant] + ".png"));
        }
    }

    /**
     * A level's inhabitants: the palette and build of the ships that defend it.
     *
     * Every level fields its own faction, so arriving somewhere new looks like meeting a different
     * enemy rather than the same three ships under a new sky.
     *
     * @param directory resource directory, one per level
     * @param hull      structural colour: wings and plating
     * @param accent    fuselage colour, the ship's dominant read
     * @param glow      cockpit and core colour
     * @param style     mechanical navies get barrels and spines, organic ones chitin and segments
     */
    private record Faction(String directory, Color hull, Color accent, Color glow, HullStyle style,
                           boolean sideways) {

        /** A faction whose level runs top-down, which is all of them but the side-view leg. */
        Faction(String directory, Color hull, Color accent, Color glow, HullStyle style) {
            this(directory, hull, accent, glow, style, false);
        }
    }

    private enum HullStyle { MECHANICAL, ORGANIC }

    private static final Faction[] FACTIONS = {
            // Level 1 keeps the original palette exactly, so the opening minutes stay tuned.
            new Faction("level-1", HULL_MID, HOSTILE, HOSTILE_GLOW, HullStyle.MECHANICAL),
            new Faction("level-2", new Color(0x2f5a33), new Color(0x7fae2a), new Color(0xffd24a),
                    HullStyle.ORGANIC),
            new Faction("level-3", new Color(0x2a4a44), new Color(0x4fae7a), new Color(0xc07aff),
                    HullStyle.ORGANIC),
            new Faction("level-4", new Color(0x4a3a2c), new Color(0xb0692a), new Color(0xffcb5a),
                    HullStyle.MECHANICAL),
            new Faction("level-5", new Color(0x2c3f4a), new Color(0x4fa8c9), new Color(0xe8faff),
                    HullStyle.MECHANICAL),
            new Faction("level-6", new Color(0x33294a), new Color(0x7a3fc9), new Color(0xd9a6ff),
                    HullStyle.MECHANICAL),
            new Faction("level-7", new Color(0x4a2c22), new Color(0xd6321a), new Color(0xffb04a),
                    HullStyle.MECHANICAL),
            new Faction("level-8", new Color(0x34383f), new Color(0x8d94a0), new Color(0x9fd0ff),
                    HullStyle.MECHANICAL),
            // Level 9 runs sideways, so its hulls are cut pointing left. Organic, to sit with the
            // burrowing thing that ends the level.
            new Faction("level-9", new Color(0x3b2f22), new Color(0xc08a2e), new Color(0xffe07a),
                    HullStyle.ORGANIC, true),
            new Faction("level-10", new Color(0x241a2e), new Color(0x8a2f5a), new Color(0xff5ea8),
                    HullStyle.ORGANIC),
    };

    /** Three hostile silhouettes per level, angular and pointing down the arena at the player. */
    private static void enemies() throws IOException {
        for (Faction faction : FACTIONS) {
            Path directory = SPRITES.resolve(faction.directory());
            write(pointed(faction, scout(faction)), directory.resolve("enemy-scout.png"));
            write(pointed(faction, fighter(faction)), directory.resolve("enemy-fighter.png"));
            write(pointed(faction, cruiser(faction)), directory.resolve("enemy-cruiser.png"));
        }
    }

    /**
     * Turns a hull to point the way its level's hostiles travel.
     *
     * Baked into the art rather than rotated at draw time, so the sprite and the collision box
     * agree to the pixel -- the engine has no facing for enemies and does not need one.
     */
    private static BufferedImage pointed(Faction faction, BufferedImage noseDown) {
        return faction.sideways() ? quarterTurnLeft(noseDown) : noseDown;
    }

    /**
     * Nose-down becomes nose-left.
     *
     * A pixel copy rather than a rotated draw: no interpolation and no dependence on rendering
     * hints, so the output is byte-identical on every JDK the build might run on. That matters
     * because CI regenerates these and fails if anything moved.
     */
    private static BufferedImage quarterTurnLeft(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage turned = blank(h, w);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                turned.setRGB(y, w - 1 - x, source.getRGB(x, y));
            }
        }
        return turned;
    }

    private static final double[][] SCOUT_MECHANICAL = {
            {0.50, 0.96}, {0.34, 0.60}, {0.05, 0.28}, {0.17, 0.17}, {0.40, 0.32},
            {0.44, 0.05}, {0.56, 0.05}, {0.60, 0.32}, {0.83, 0.17}, {0.95, 0.28},
            {0.66, 0.60}};

    /** Forward-swept mandibles where the mechanical dart has wingtips. */
    private static final double[][] SCOUT_ORGANIC = {
            {0.50, 0.97}, {0.38, 0.68}, {0.10, 0.46}, {0.03, 0.20}, {0.19, 0.31},
            {0.42, 0.27}, {0.46, 0.04}, {0.54, 0.04}, {0.58, 0.27}, {0.81, 0.31},
            {0.97, 0.20}, {0.90, 0.46}, {0.62, 0.68}};

    /** A dart with swept-back wings, so the fastest enemy reads as an interceptor. */
    private static BufferedImage scout(Faction faction) {
        int w = 104;
        int h = 92;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        boolean organic = faction.style() == HullStyle.ORGANIC;

        Path2D body = path(w, h, organic ? SCOUT_ORGANIC : SCOUT_MECHANICAL);
        g.setColor(faction.accent());
        g.fill(body);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(body);

        g.setColor(HULL_DARK);
        g.fill(new Ellipse2D.Double(w * 0.43, h * 0.40, w * 0.14, h * 0.20));
        g.setColor(faction.glow());
        if (organic) {
            // An eye cluster rather than a canopy.
            g.fill(new Ellipse2D.Double(w * 0.445, h * 0.43, w * 0.045, h * 0.06));
            g.fill(new Ellipse2D.Double(w * 0.510, h * 0.43, w * 0.045, h * 0.06));
            g.fill(new Ellipse2D.Double(w * 0.478, h * 0.51, w * 0.045, h * 0.06));
        } else {
            g.fill(new Ellipse2D.Double(w * 0.455, h * 0.44, w * 0.09, h * 0.12));
        }
        g.dispose();
        return image;
    }

    private static final double[][] FIGHTER_WINGS_MECHANICAL = {
            {0.50, 0.20}, {0.04, 0.62}, {0.24, 0.66}, {0.50, 0.44}, {0.76, 0.66}, {0.96, 0.62}};

    /** Broad moth wings: more area, none of the hard sweep. */
    private static final double[][] FIGHTER_WINGS_ORGANIC = {
            {0.50, 0.24}, {0.06, 0.42}, {0.02, 0.70}, {0.22, 0.82}, {0.50, 0.54},
            {0.78, 0.82}, {0.98, 0.70}, {0.94, 0.42}};

    private static final double[][] FIGHTER_BODY = {
            {0.50, 0.96}, {0.36, 0.42}, {0.44, 0.08}, {0.56, 0.08}, {0.64, 0.42}};

    private static BufferedImage fighter(Faction faction) {
        int w = 128;
        int h = 120;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        boolean organic = faction.style() == HullStyle.ORGANIC;

        Path2D wings = path(w, h, organic ? FIGHTER_WINGS_ORGANIC : FIGHTER_WINGS_MECHANICAL);
        g.setColor(faction.hull());
        g.fill(wings);

        Path2D fuselage = path(w, h, FIGHTER_BODY);
        g.setColor(faction.accent());
        g.fill(fuselage);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(fuselage);
        g.draw(wings);

        if (organic) {
            // Veins, so the broad panels do not read as flat plate.
            g.setColor(HULL_DARK);
            g.setStroke(new BasicStroke(1.6f));
            for (int side = -1; side <= 1; side += 2) {
                for (double reach = 0.16; reach <= 0.45; reach += 0.14) {
                    g.draw(new java.awt.geom.Line2D.Double(
                            w * 0.5, h * 0.34, w * (0.5 + side * reach), h * 0.76));
                }
            }
        }

        g.setColor(faction.glow());
        g.fill(new Ellipse2D.Double(w * 0.44, h * 0.6, w * 0.12, h * 0.12));
        g.dispose();
        return image;
    }

    private static final double[][] CRUISER_HULL_MECHANICAL = {
            {0.50, 0.98}, {0.33, 0.80}, {0.15, 0.70}, {0.11, 0.44}, {0.24, 0.36},
            {0.30, 0.10}, {0.43, 0.03}, {0.57, 0.03}, {0.70, 0.10}, {0.76, 0.36},
            {0.89, 0.44}, {0.85, 0.70}, {0.67, 0.80}};

    /** A rounder carapace, with none of the mechanical hull's steps. */
    private static final double[][] CRUISER_HULL_ORGANIC = {
            {0.50, 0.99}, {0.30, 0.84}, {0.13, 0.66}, {0.09, 0.42}, {0.20, 0.28},
            {0.34, 0.08}, {0.50, 0.02}, {0.66, 0.08}, {0.80, 0.28},
            {0.91, 0.42}, {0.87, 0.66}, {0.70, 0.84}};

    /** The heavy archetype: a stepped hull with outboard guns, or a segmented body with claws. */
    private static BufferedImage cruiser(Faction faction) {
        int w = 150;
        int h = 168;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        boolean organic = faction.style() == HullStyle.ORGANIC;

        Path2D hull = path(w, h, organic ? CRUISER_HULL_ORGANIC : CRUISER_HULL_MECHANICAL);
        g.setColor(faction.hull());
        g.fill(hull);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(3f));
        g.draw(hull);

        if (organic) {
            for (int segment = 0; segment < 3; segment++) {
                var plate = new Ellipse2D.Double(w * 0.35, h * (0.16 + segment * 0.17),
                        w * 0.30, h * 0.15);
                g.setColor(faction.accent());
                g.fill(plate);
                g.setColor(HULL_DARK);
                g.setStroke(new BasicStroke(2f));
                g.draw(plate);
            }
            // Claws where the mechanical hull carries barrels.
            for (int side = -1; side <= 1; side += 2) {
                Path2D claw = new Path2D.Double();
                claw.moveTo(w * (0.5 + side * 0.17), h * 0.66);
                claw.quadTo(w * (0.5 + side * 0.36), h * 0.72,
                        w * (0.5 + side * 0.28), h * 0.94);
                claw.quadTo(w * (0.5 + side * 0.20), h * 0.80,
                        w * (0.5 + side * 0.17), h * 0.66);
                claw.closePath();
                g.setColor(faction.accent());
                g.fill(claw);
                g.setColor(HULL_DARK);
                g.setStroke(new BasicStroke(2f));
                g.draw(claw);
            }
        } else {
            // Raised spine.
            g.setColor(HULL_LIGHT);
            g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.42, h * 0.14, w * 0.16, h * 0.52));
            g.setColor(HULL_DARK);
            g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.455, h * 0.18, w * 0.09, h * 0.42));

            // Outboard gun barrels reaching past the hull toward the player.
            for (int side = -1; side <= 1; side += 2) {
                double bx = w * (0.5 + side * 0.30);
                var barrel = new java.awt.geom.Rectangle2D.Double(
                        bx - w * 0.045, h * 0.56, w * 0.09, h * 0.30);
                g.setColor(faction.accent());
                g.fill(barrel);
                g.setColor(HULL_DARK);
                g.setStroke(new BasicStroke(2f));
                g.draw(barrel);
            }
        }

        // Prow and core.
        Path2D prow = path(w, h, new double[][]{{0.50, 0.99}, {0.43, 0.82}, {0.57, 0.82}});
        g.setColor(faction.glow());
        g.fill(prow);
        g.fill(new Ellipse2D.Double(w * 0.445, h * 0.66, w * 0.11, h * 0.08));
        g.dispose();
        return image;
    }

    /** Builds a closed path from fractional coordinates. */
    private static Path2D path(int w, int h, double[][] points) {
        Path2D shape = new Path2D.Double();
        for (int i = 0; i < points.length; i++) {
            double x = w * points[i][0];
            double y = h * points[i][1];
            if (i == 0) {
                shape.moveTo(x, y);
            } else {
                shape.lineTo(x, y);
            }
        }
        shape.closePath();
        return shape;
    }

    /** Frames in every boss animation. One full throb cycle, so the loop is seamless. */
    private static final int BOSS_FRAMES = 8;

    /**
     * One level boss.
     *
     * All eight share a construction — swept wings ending in turret pods, a central hull over an
     * armoured spine, a hot core, an engine bank behind, and a prow blade at the bottom because
     * enemies face down — and differ by silhouette, palette and armament.
     *
     * @param directory resource directory holding the numbered frames
     * @param width     source canvas width; the on-screen size lives in asset/BossArt
     * @param accent    hull accent, used for the spine and the turret barrels
     * @param glow      hot colour, used for the core, the prow and the turret eyes
     * @param engines   thrusters across the rear
     * @param coreScale core radius as a fraction of the width
     * @param hull      fractional outline of the central hull
     * @param wing      fractional outline of one wing, mirrored for the other side
     * @param turrets   one {distance from centre, y, radius} triple per mirrored pod pair
     */
    private record BossProfile(String directory, int width, int height, Color accent, Color glow,
                               int engines, double coreScale, double[][] hull, double[][] wing,
                               double[][] turrets) {
    }

    private static final BossProfile[] BOSSES = {
            // Sentinel: the level 1 fight, unchanged, so the opening minutes stay tuned.
            new BossProfile("boss-sentinel", 440, 340, HOSTILE, HOSTILE_GLOW, 3, 0.11,
                    new double[][]{{0.50, 0.99}, {0.40, 0.72}, {0.385, 0.30},
                            {0.44, 0.05}, {0.56, 0.05}, {0.615, 0.30}, {0.60, 0.72}},
                    new double[][]{{0.41, 0.60}, {0.10, 0.40}, {0.03, 0.50},
                            {0.09, 0.72}, {0.26, 0.90}, {0.38, 0.80}},
                    new double[][]{{0.29, 0.735, 0.042}, {0.15, 0.80, 0.032}}),

            // Hive Matriarch: wide and low, membranous wings, a swollen abdomen full of core.
            new BossProfile("boss-hive-matriarch", 470, 330,
                    new Color(0x7fae2a), new Color(0xffd24a), 3, 0.14,
                    new double[][]{{0.50, 0.98}, {0.33, 0.88}, {0.26, 0.62}, {0.31, 0.28},
                            {0.43, 0.04}, {0.57, 0.04}, {0.69, 0.28}, {0.74, 0.62}, {0.67, 0.88}},
                    new double[][]{{0.38, 0.46}, {0.18, 0.20}, {0.02, 0.30}, {0.00, 0.56},
                            {0.14, 0.78}, {0.32, 0.86}, {0.37, 0.66}},
                    new double[][]{{0.30, 0.66, 0.042}, {0.18, 0.44, 0.032}, {0.24, 0.80, 0.030}}),

            // Bloom Colossus: tall rather than wide, wings opening like petals around the core.
            new BossProfile("boss-bloom-colossus", 460, 390,
                    new Color(0x4fae7a), new Color(0xc07aff), 3, 0.16,
                    new double[][]{{0.50, 0.98}, {0.36, 0.86}, {0.30, 0.58}, {0.34, 0.24},
                            {0.44, 0.03}, {0.56, 0.03}, {0.66, 0.24}, {0.70, 0.58}, {0.64, 0.86}},
                    new double[][]{{0.36, 0.50}, {0.20, 0.20}, {0.04, 0.30}, {0.00, 0.56},
                            {0.10, 0.80}, {0.28, 0.90}, {0.35, 0.70}},
                    new double[][]{{0.30, 0.72, 0.044}, {0.19, 0.48, 0.034},
                            {0.23, 0.86, 0.030}, {0.12, 0.66, 0.026}}),

            // Scrap Hive: a bulbous carrier that vents fighters, so the pods read as bays.
            new BossProfile("boss-scrap-hive", 470, 350,
                    new Color(0x9a7b2a), new Color(0xffd66b), 4, 0.13,
                    new double[][]{{0.50, 0.98}, {0.36, 0.84}, {0.30, 0.58}, {0.34, 0.28},
                            {0.44, 0.06}, {0.56, 0.06}, {0.66, 0.28}, {0.70, 0.58}, {0.64, 0.84}},
                    new double[][]{{0.38, 0.56}, {0.18, 0.44}, {0.06, 0.56}, {0.10, 0.74},
                            {0.24, 0.84}, {0.36, 0.74}},
                    new double[][]{{0.32, 0.70, 0.046}, {0.20, 0.82, 0.038}, {0.26, 0.64, 0.030}}),

            // Foundry Warden: slab-sided industrial plant, the widest engine bank in the run.
            new BossProfile("boss-foundry-warden", 480, 340,
                    new Color(0x4fa8c9), new Color(0xe8faff), 6, 0.11,
                    new double[][]{{0.50, 0.97}, {0.32, 0.88}, {0.28, 0.62}, {0.30, 0.30},
                            {0.40, 0.05}, {0.60, 0.05}, {0.70, 0.30}, {0.72, 0.62}, {0.68, 0.88}},
                    new double[][]{{0.38, 0.42}, {0.14, 0.36}, {0.02, 0.46}, {0.02, 0.70},
                            {0.16, 0.82}, {0.36, 0.76}},
                    new double[][]{{0.32, 0.56, 0.042}, {0.32, 0.76, 0.042}, {0.16, 0.62, 0.030}}),

            // Void Weaver: narrow and long-limbed, a spider of a ship.
            new BossProfile("boss-void-weaver", 430, 360,
                    new Color(0x7a3fc9), new Color(0xd9a6ff), 2, 0.09,
                    new double[][]{{0.50, 0.99}, {0.43, 0.76}, {0.41, 0.40}, {0.45, 0.06},
                            {0.55, 0.06}, {0.59, 0.40}, {0.57, 0.76}},
                    new double[][]{{0.44, 0.62}, {0.20, 0.28}, {0.04, 0.34}, {0.02, 0.52},
                            {0.16, 0.86}, {0.34, 0.94}, {0.42, 0.80}},
                    new double[][]{{0.36, 0.86, 0.034}, {0.26, 0.52, 0.028}}),

            // Core Tyrant: the heaviest hull and the most guns, for the last level.
            new BossProfile("boss-core-tyrant", 500, 370,
                    new Color(0xd6321a), new Color(0xffb04a), 5, 0.14,
                    new double[][]{{0.50, 0.98}, {0.34, 0.80}, {0.28, 0.52}, {0.32, 0.24},
                            {0.42, 0.03}, {0.58, 0.03}, {0.68, 0.24}, {0.72, 0.52}, {0.66, 0.80}},
                    new double[][]{{0.36, 0.58}, {0.14, 0.36}, {0.01, 0.48}, {0.04, 0.68},
                            {0.16, 0.84}, {0.30, 0.90}, {0.34, 0.76}},
                    new double[][]{{0.38, 0.66, 0.048}, {0.28, 0.82, 0.040},
                            {0.17, 0.90, 0.032}, {0.23, 0.74, 0.030}}),

            // Exodus Dreadnought: the longest hull and the most pods, guarding the way out.
            new BossProfile("boss-exodus-dreadnought", 520, 390,
                    new Color(0x8d94a0), new Color(0x9fd0ff), 6, 0.12,
                    new double[][]{{0.50, 0.99}, {0.38, 0.84}, {0.33, 0.54}, {0.36, 0.22},
                            {0.44, 0.02}, {0.56, 0.02}, {0.64, 0.22}, {0.67, 0.54}, {0.62, 0.84}},
                    new double[][]{{0.38, 0.56}, {0.16, 0.30}, {0.02, 0.40}, {0.01, 0.60},
                            {0.12, 0.80}, {0.30, 0.92}, {0.36, 0.74}},
                    new double[][]{{0.36, 0.78, 0.046}, {0.26, 0.58, 0.038},
                            {0.16, 0.86, 0.032}, {0.30, 0.90, 0.028}}),
    };

    private static void bosses() throws IOException {
        for (BossProfile profile : BOSSES) {
            for (int frame = 1; frame <= BOSS_FRAMES; frame++) {
                BufferedImage image = bossFrame(profile, frame);
                write(image, SPRITES.resolve(profile.directory()).resolve(frame + ".png"));
            }
        }
    }

    // ------------------------------------------------------------------ monsters

    /** Diseased hide, bone and bile: the palette the two monsters share. */
    private static final Color HIDE = new Color(0x24361f);
    private static final Color HIDE_DARK = new Color(0x131c10);
    private static final Color BONE = new Color(0xcfc7a8);
    private static final Color BILE = new Color(0xb6e24a);
    private static final Color CHITIN = new Color(0x6a5334);
    private static final Color CHITIN_DARK = new Color(0x3a2c1a);

    /**
     * The two organic bosses, which share nothing with the warships above.
     *
     * Written as their own methods rather than as another {@code BossProfile}: that record is all
     * wings, turrets, spine, engines and core, and a monster has none of the five. A style flag
     * would leave half the record dead and branch {@code bossFrame} through its whole draw order.
     *
     * The hydra's necks are deliberately absent from these frames. They are drawn at runtime from
     * the live head positions, so they move at sixty steps a second instead of eight, and the
     * eight frames here only have to carry a breath.
     */
    private static void monsters() throws IOException {
        for (int frame = 1; frame <= BOSS_FRAMES; frame++) {
            write(hydraTorsoFrame(frame), SPRITES.resolve("boss-hydra").resolve(frame + ".png"));
            write(hydraHeadFrame(frame), SPRITES.resolve("boss-hydra-head").resolve(frame + ".png"));
            write(wormMawFrame(frame),
                    SPRITES.resolve("boss-dune-leviathan").resolve(frame + ".png"));
        }
        write(wormSegment(), SPRITES.resolve("worm-segment.png"));
        write(acidBall(), SPRITES.resolve("acid-ball.png"));
    }

    /**
     * Where each neck leaves the torso, as fractions of its width and height.
     *
     * Must match {@code entity.BossHead}, which roots its necks at the same fractions. If one
     * moves, the necks detach from their sockets. Low on the body rather than high: the heads
     * reach down-arena toward the player, so the sockets belong on that edge.
     */
    private static final double[] NECK_SOCKETS = {0.30, 0.50, 0.70};
    private static final double NECK_SOCKET_DEPTH = 0.78;

    /** The hydra's body: a squat sac of a thing on stubby legs, necks rising from three sockets. */
    private static BufferedImage hydraTorsoFrame(int oneBasedFrame) {
        int w = 460;
        int h = 300;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = Math.sin(2 * Math.PI * phase);

        // Legs first, so the mass sits over them.
        g.setColor(darken(HIDE, 30));
        for (int side = -1; side <= 1; side += 2) {
            for (int leg = 0; leg < 3; leg++) {
                double rootX = 0.5 + side * (0.20 + leg * 0.09);
                double rootY = 0.46 + leg * 0.10;
                g.fill(path(w, h, new double[][]{
                        {rootX, rootY}, {rootX + side * 0.10, rootY + 0.30},
                        {rootX + side * 0.05, rootY + 0.34}, {rootX, rootY + 0.14}}));
            }
        }

        // Body. Many vertices rather than curves: an irregular outline reads as organic where a
        // smooth one reads as moulded plastic.
        g.setColor(HIDE);
        java.awt.geom.Path2D body = path(w, h, new double[][]{
                {0.50, 0.94}, {0.31, 0.87}, {0.19, 0.71}, {0.14, 0.55}, {0.18, 0.40},
                {0.26, 0.31}, {0.34, 0.26}, {0.42, 0.30}, {0.50, 0.27}, {0.58, 0.30},
                {0.66, 0.26}, {0.74, 0.31}, {0.82, 0.40}, {0.86, 0.55}, {0.81, 0.71},
                {0.69, 0.87}});
        g.fill(body);
        g.setColor(HIDE_DARK);
        g.setStroke(new BasicStroke(5f));
        g.draw(body);

        // Gut sac, breathing. The organic answer to the warships' reactor core.
        softBlob(g, w * 0.5, h * 0.62, w * 0.20 * (1 + 0.12 * pulse),
                h * 0.20 * (1 + 0.12 * pulse), BILE, 150);

        // Ribs pushing through the hide.
        g.setColor(BONE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int rib = 0; rib < 5; rib++) {
            double y = 0.44 + rib * 0.10;
            java.awt.geom.Path2D arc = new java.awt.geom.Path2D.Double();
            arc.moveTo(w * 0.26, h * y);
            arc.quadTo(w * 0.50, h * (y + 0.07), w * 0.74, h * y);
            g.draw(arc);
        }

        // Neck sockets: a dark ring each, so a neck emerges from the body rather than off it.
        for (double socket : NECK_SOCKETS) {
            double cx = w * socket;
            double cy = h * NECK_SOCKET_DEPTH;
            g.setColor(HIDE_DARK);
            g.fill(new Ellipse2D.Double(cx - 30, cy - 22, 60, 44));
            g.setColor(darken(HIDE, 10));
            g.fill(new Ellipse2D.Double(cx - 22, cy - 16, 44, 32));
        }
        g.dispose();
        return image;
    }

    /**
     * One head, pointing down the arena, jaw working on the breath.
     *
     * The eyes are the whole trick: black sockets with a pinprick of fire in them. A large glowing
     * eye reads as a machine, and this is not supposed to be a machine.
     */
    private static BufferedImage hydraHeadFrame(int oneBasedFrame) {
        int size = 150;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = Math.sin(2 * Math.PI * phase);
        double gape = 0.30 * (0.5 + 0.5 * pulse);

        // Throat glow behind the jaws, brightest at full gape: the tell before it spits.
        softBlob(g, size * 0.5, size * 0.72, size * 0.20, size * 0.18, BILE,
                (int) (60 + 150 * (0.5 + 0.5 * pulse)));

        // Lower jaw, swung open about the hinge. Same path as drawn, then transformed -- the
        // cheapest possible articulation and it never drifts out of register.
        java.awt.geom.Path2D jaw = path(size, size, new double[][]{
                {0.30, 0.58}, {0.50, 0.94}, {0.70, 0.58}, {0.58, 0.66}, {0.42, 0.66}});
        jaw.transform(java.awt.geom.AffineTransform.getRotateInstance(
                gape, size * 0.5, size * 0.58));
        g.setColor(darken(HIDE, 20));
        g.fill(jaw);
        g.setColor(HIDE_DARK);
        g.setStroke(new BasicStroke(4f));
        g.draw(jaw);

        // Skull.
        java.awt.geom.Path2D skull = path(size, size, new double[][]{
                {0.50, 0.72}, {0.26, 0.58}, {0.18, 0.36}, {0.28, 0.14}, {0.42, 0.06},
                {0.58, 0.06}, {0.72, 0.14}, {0.82, 0.36}, {0.74, 0.58}});
        g.setColor(HIDE);
        g.fill(skull);
        g.setColor(HIDE_DARK);
        g.setStroke(new BasicStroke(4.5f));
        g.draw(skull);

        // Teeth along the upper jaw.
        g.setColor(BONE);
        for (int tooth = 0; tooth < 5; tooth++) {
            double tx = 0.32 + tooth * 0.09;
            g.fill(path(size, size, new double[][]{
                    {tx, 0.60}, {tx + 0.045, 0.60}, {tx + 0.022, 0.70}}));
        }

        // Eyes: holes first, then a small hot pupil inside each.
        for (int side = -1; side <= 1; side += 2) {
            double cx = size * (0.5 + side * 0.14);
            double cy = size * 0.30;
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(cx - 15, cy - 13, 30, 26));
            softBlob(g, cx, cy, 7 * (0.6 + 0.4 * (0.5 + 0.5 * pulse)),
                    6 * (0.6 + 0.4 * (0.5 + 0.5 * pulse)), new Color(0xffb020), 230);
        }
        g.dispose();
        return image;
    }

    /** The worm's maw, seen from the side and opening left, into the player. */
    private static BufferedImage wormMawFrame(int oneBasedFrame) {
        int w = 420;
        int h = 300;
        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = Math.sin(2 * Math.PI * phase);
        double flare = 0.34 + 0.20 * (0.5 + 0.5 * pulse);

        // Body plates behind the head, receding to the right.
        g.setColor(CHITIN);
        for (int plate = 0; plate < 4; plate++) {
            double x = 0.52 + plate * 0.12;
            java.awt.geom.Path2D arc = new java.awt.geom.Path2D.Double();
            arc.moveTo(w * x, h * 0.18);
            arc.quadTo(w * (x + 0.10), h * 0.50, w * x, h * 0.82);
            arc.quadTo(w * (x + 0.02), h * 0.50, w * x, h * 0.18);
            g.fill(arc);
            g.setColor(CHITIN_DARK);
            g.setStroke(new BasicStroke(3f));
            g.draw(arc);
            g.setColor(CHITIN);
        }

        // Throat: a black hole the petals open around.
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(w * 0.24, h * 0.30, w * 0.30, h * 0.40));

        // Four mandible petals, flowering open on the breath.
        for (int petal = 0; petal < 4; petal++) {
            double angle = -flare + petal * (2 * flare / 3);
            java.awt.geom.Path2D blade = path(w, h, new double[][]{
                    {0.44, 0.50}, {0.06, 0.42}, {0.02, 0.50}, {0.06, 0.58}});
            blade.transform(java.awt.geom.AffineTransform.getRotateInstance(
                    angle, w * 0.44, h * 0.50));
            g.setColor(CHITIN);
            g.fill(blade);
            g.setColor(CHITIN_DARK);
            g.setStroke(new BasicStroke(3.5f));
            g.draw(blade);
        }

        // Two rings of teeth inside the throat.
        g.setColor(BONE);
        for (int ring = 0; ring < 2; ring++) {
            double radius = 0.10 - ring * 0.035;
            for (int tooth = 0; tooth < 12; tooth++) {
                double angle = tooth * 2 * Math.PI / 12;
                double tx = 0.39 + Math.cos(angle) * radius * 0.7;
                double ty = 0.50 + Math.sin(angle) * radius;
                g.fill(new Ellipse2D.Double(w * tx - 4, h * ty - 4, 8, 8));
            }
        }
        g.dispose();
        return image;
    }

    /** One armoured ring of the worm's body. The renderer trails several of these behind the maw. */
    private static BufferedImage wormSegment() {
        int size = 120;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);

        g.setColor(CHITIN);
        g.fill(new Ellipse2D.Double(size * 0.10, size * 0.16, size * 0.80, size * 0.68));
        g.setColor(CHITIN_DARK);
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(size * 0.10, size * 0.16, size * 0.80, size * 0.68));
        g.setColor(brighten(CHITIN, 30));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(size * 0.24, size * 0.28, size * 0.52, size * 0.44));

        // Bristles around the underside, so a ring reads as something that grips.
        g.setColor(BONE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int bristle = 0; bristle < 8; bristle++) {
            double angle = Math.PI * (0.15 + bristle * 0.10);
            double cx = size * 0.5 + Math.cos(angle) * size * 0.40;
            double cy = size * 0.5 + Math.sin(angle) * size * 0.34;
            g.draw(new java.awt.geom.Line2D.Double(cx, cy,
                    cx + Math.cos(angle) * 12, cy + Math.sin(angle) * 12));
        }
        g.dispose();
        return image;
    }

    /**
     * A ball of acid.
     *
     * Deliberately not the bright green of an ordinary enemy bolt, which is what it would collide
     * with visually -- that one is a hot white core inside a green glow. This is the opposite
     * read: matte, murky yellow-green with a dark olive rim and no highlight, so it looks like a
     * thrown liquid rather than an energy shot, and a player can tell at a glance which is which.
     */
    private static BufferedImage acidBall() {
        int size = 68;
        BufferedImage image = blank(size, size);
        Graphics2D g = paint(image);

        Color rim = new Color(0x3f4a10);
        Color body = new Color(0x9aa81c);
        Color sheen = new Color(0xc8d24a);

        softBlob(g, size * 0.5, size * 0.5, size * 0.48, size * 0.48, new Color(0x8a9a18), 110);
        g.setColor(rim);
        g.fill(new Ellipse2D.Double(size * 0.12, size * 0.12, size * 0.76, size * 0.76));
        g.setColor(body);
        g.fill(new Ellipse2D.Double(size * 0.19, size * 0.19, size * 0.62, size * 0.62));
        // Off-centre sheen rather than a centred hotspot: a wet surface, not a light source.
        g.setColor(sheen);
        g.fill(new Ellipse2D.Double(size * 0.30, size * 0.26, size * 0.24, size * 0.20));

        // A couple of drips coming off it, which no bolt in the game has.
        g.setColor(body);
        g.fill(new Ellipse2D.Double(size * 0.70, size * 0.62, size * 0.14, size * 0.18));
        g.fill(new Ellipse2D.Double(size * 0.22, size * 0.68, size * 0.11, size * 0.14));
        g.dispose();
        return image;
    }

    /**
     * One frame of a boss's idle animation.
     *
     * The whole sequence is pure geometry with no random draw: the frame index only shifts a phase,
     * so the frames reproduce byte for byte and the last one leads back into the first.
     */
    private static BufferedImage bossFrame(BossProfile profile, int oneBasedFrame) {
        int w = profile.width();
        int h = profile.height();
        double phase = (oneBasedFrame - 1) / (double) BOSS_FRAMES;
        double pulse = Math.sin(2 * Math.PI * phase);

        BufferedImage image = blank(w, h);
        Graphics2D g = paint(image);

        // Wings first, so the central hull overlaps them.
        for (int side = -1; side <= 1; side += 2) {
            Path2D wing = mirrored(side, w, h, profile.wing());
            g.setColor(HULL_MID.darker());
            g.fill(wing);
            g.setColor(HULL_DARK);
            g.setStroke(new BasicStroke(4f));
            g.draw(wing);

            // The sign matches `mirrored`, which flips for side -1, so pods land on the wing just
            // drawn rather than the opposite one.
            for (double[] pod : profile.turrets()) {
                double cx = w * (0.5 - side * pod[0]);
                turret(g, cx, h * pod[1], w * pod[2], profile.glow(), 1 + 0.22 * pulse);
            }
        }

        Path2D hull = path(w, h, profile.hull());
        g.setColor(HULL_MID);
        g.fill(hull);
        g.setColor(HULL_DARK);
        g.setStroke(new BasicStroke(4.5f));
        g.draw(hull);

        // Armoured spine with a hot core.
        g.setColor(HULL_LIGHT);
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.455, h * 0.12, w * 0.09, h * 0.56));
        g.setColor(profile.accent());
        g.fill(new java.awt.geom.Rectangle2D.Double(w * 0.472, h * 0.16, w * 0.056, h * 0.46));

        double coreRadius = w * profile.coreScale() * (1 + 0.18 * pulse);
        Color glow = profile.glow();
        g.setPaint(new RadialGradientPaint(
                (float) (w * 0.5), (float) (h * 0.44), (float) coreRadius,
                new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(255, 240, 200, 255),
                        new Color(glow.getRed(), glow.getGreen(), glow.getBlue(),
                                channel((int) (170 + 60 * pulse))),
                        new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(w * 0.5 - coreRadius, h * 0.44 - coreRadius,
                coreRadius * 2, coreRadius * 2));

        // Engine bank at the rear, flaring on the same cycle as the core.
        double spacing = 0.055;
        double first = -(profile.engines() - 1) / 2.0;
        double flare = w * 0.035 * (1 + 0.28 * pulse);
        for (int i = 0; i < profile.engines(); i++) {
            double ex = w * (0.5 + (first + i) * spacing);
            g.setColor(HULL_DARK);
            g.fill(new java.awt.geom.Rectangle2D.Double(ex - w * 0.018, h * 0.02, w * 0.036, h * 0.07));
            g.setPaint(new RadialGradientPaint(
                    (float) ex, (float) (h * 0.045), (float) flare,
                    new float[]{0f, 1f},
                    new Color[]{new Color(140, 210, 255, 220), new Color(140, 210, 255, 0)}));
            g.fill(new Ellipse2D.Double(ex - flare, h * 0.01, flare * 2, flare * 2));
        }

        // Prow blade.
        Path2D prow = path(w, h, new double[][]{{0.50, 0.99}, {0.455, 0.80}, {0.545, 0.80}});
        g.setColor(profile.glow());
        g.fill(prow);
        g.dispose();
        return image;
    }

    private static void turret(Graphics2D g, double cx, double cy, double r, Color glow,
                               double glowScale) {
        g.setColor(HULL_DARK);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(HULL_LIGHT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        double eye = r * 0.42 * glowScale;
        g.setColor(glow);
        g.fill(new Ellipse2D.Double(cx - eye, cy - eye, eye * 2, eye * 2));
    }

    /** Builds a path from fractional points, mirrored about the vertical centre when side is -1. */
    private static Path2D mirrored(int side, int w, int h, double[][] points) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < points.length; i++) {
            double fx = 0.5 + side * (points[i][0] - 0.5);
            double x = w * fx;
            double y = h * points[i][1];
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    /** Pickup icons: readable at 36px, and none of them a trademark. */
    private static void pickups() throws IOException {
        write(icon(g -> {
            // Three chevrons: speed.
            g.setColor(BRAND);
            for (int i = 0; i < 3; i++) {
                Path2D chevron = new Path2D.Double();
                double y = 26 + i * 26;
                chevron.moveTo(28, y + 18);
                chevron.lineTo(64, y);
                chevron.lineTo(100, y + 18);
                g.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(chevron);
            }
        }), SPRITES.resolve("pickup-speed.png"));

        write(icon(g -> {
            // Medkit cross.
            g.setColor(new Color(0xe8f1f2));
            g.fillRoundRect(18, 18, 92, 92, 22, 22);
            g.setColor(new Color(0xd6263c));
            g.fillRect(56, 34, 16, 60);
            g.fillRect(34, 56, 60, 16);
        }), SPRITES.resolve("pickup-health.png"));

        write(icon(g -> {
            // Shield outline.
            Path2D shield = new Path2D.Double();
            shield.moveTo(64, 14);
            shield.lineTo(110, 34);
            shield.curveTo(110, 88, 90, 106, 64, 116);
            shield.curveTo(38, 106, 18, 88, 18, 34);
            shield.closePath();
            g.setColor(new Color(0x2a4f8f));
            g.fill(shield);
            g.setColor(new Color(0x9fd0ff));
            g.setStroke(new BasicStroke(7f));
            g.draw(shield);
        }), SPRITES.resolve("pickup-shield.png"));

        write(icon(g -> {
            // Three bolts fanning upward, with heads, so it reads as outgoing fire rather than a
            // download arrow.
            g.setColor(new Color(0x7ce8ff));
            double[][] tips = {{34, 34}, {64, 20}, {94, 34}};
            for (double[] tip : tips) {
                g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Double(64, 108, tip[0], tip[1] + 16));
                Path2D head = new Path2D.Double();
                head.moveTo(tip[0], tip[1]);
                head.lineTo(tip[0] - 11, tip[1] + 20);
                head.lineTo(tip[0] + 11, tip[1] + 20);
                head.closePath();
                g.fill(head);
            }
        }), SPRITES.resolve("pickup-tri-shot.png"));

        write(icon(g -> {
            // Thick beam with a flared muzzle: mega laser.
            g.setColor(new Color(0xff8ae0));
            g.fillRoundRect(50, 20, 28, 78, 12, 12);
            g.setColor(new Color(0xffffff));
            g.fillRoundRect(58, 28, 12, 54, 6, 6);
            g.setColor(new Color(0xff8ae0));
            Path2D flare = new Path2D.Double();
            flare.moveTo(36, 104);
            flare.lineTo(64, 88);
            flare.lineTo(92, 104);
            flare.closePath();
            g.fill(flare);
        }), SPRITES.resolve("pickup-mega-laser.png"));

        write(icon(g -> {
            // A small ship outline plus a plus sign: an extra life.
            g.setColor(BRAND);
            Path2D ship = new Path2D.Double();
            ship.moveTo(52, 24);
            ship.lineTo(78, 70);
            ship.lineTo(64, 62);
            ship.lineTo(50, 70);
            ship.closePath();
            g.fill(ship);
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(64, 82, 64, 112);
            g.drawLine(49, 97, 79, 97);
        }), SPRITES.resolve("pickup-extra-life.png"));
    }

    private static BufferedImage icon(java.util.function.Consumer<Graphics2D> body) {
        BufferedImage image = blank(128, 128);
        Graphics2D g = paint(image);
        body.accept(g);
        g.dispose();
        return image;
    }

    /**
     * A level's sky: what makes one place look unlike the next.
     *
     * @param directory resource directory, one per level
     * @param kind      which backdrop recipe draws the three layers
     * @param seed      base seed; each layer adds its index, so seeds must be at least 3 apart
     * @param tintA     dominant tint -- nebula wash, high sky, soil or rock, depending on kind
     * @param tintB     secondary tint, alternated or blended with the first
     * @param blobs     large features: nebula washes, cloud decks, hills, light strips
     * @param density   count multiplier for the small scattered detail, whatever that detail is
     * @param starRed   added to each star's red channel, and likewise green and blue
     */
    private record Theme(String directory, Backdrop kind, long seed, Color tintA, Color tintB,
                         int blobs, double density, int starRed, int starGreen, int starBlue,
                         boolean sideways) {

        /** A level that scrolls top to bottom, which is all of them but the side-view leg. */
        Theme(String directory, Backdrop kind, long seed, Color tintA, Color tintB,
              int blobs, double density, int starRed, int starGreen, int starBlue) {
            this(directory, kind, seed, tintA, tintB, blobs, density,
                    starRed, starGreen, starBlue, false);
        }
    }

    /**
     * How a level's three layers are drawn.
     *
     * STARFIELD is open space. PLANET_RISE adds a world to look at. ATMOSPHERE is inside the air of
     * one, SURFACE is low over its ground, and CAVERN is enclosed by rock on both sides.
     */
    private enum Backdrop { STARFIELD, PLANET_RISE, ATMOSPHERE, SURFACE, CAVERN }

    private static final int BACKDROP_WIDTH = 996;
    private static final int BACKDROP_HEIGHT = 864;
    private static final String[] LAYER_NAMES = {"far", "mid", "near"};

    private static final Theme[] THEMES = {
            // Orbital Approach: open space with the target world hanging in it.
            new Theme("level-1", Backdrop.PLANET_RISE, 4200,
                    new Color(0x35, 0x2a, 0x6a, 26), new Color(0x10, 0x3a, 0x4a, 22),
                    5, 1.0, 0, 0, 18),
            // Verdant Airspace: inside the atmosphere, cloud decks lit from above.
            new Theme("level-2", Backdrop.ATMOSPHERE, 4210,
                    new Color(0x24, 0x54, 0x6b), new Color(0x9a, 0xc9, 0xa4),
                    7, 1.2, 0, 0, 0),
            // Canopy Descent: low over jungle, treetops crowding the frame.
            new Theme("level-3", Backdrop.SURFACE, 4220,
                    new Color(0x18, 0x33, 0x20), new Color(0x36, 0x6b, 0x38),
                    9, 1.7, 0, 0, 0),
            // Rust Canyon: oxide rock and dust, barer and bigger-boned than the jungle.
            new Theme("level-4", Backdrop.SURFACE, 4230,
                    new Color(0x36, 0x20, 0x16), new Color(0x7a, 0x44, 0x24),
                    5, 0.6, 0, 0, 0),
            // Undercity: a foundry tunnel, walls closing in on both sides.
            new Theme("level-5", Backdrop.CAVERN, 4240,
                    new Color(0x22, 0x28, 0x30), new Color(0x4f, 0xa8, 0xc9),
                    7, 1.0, 0, 0, 0),
            // Void Rift: the emptiest sky in the run.
            new Theme("level-6", Backdrop.STARFIELD, 4250,
                    new Color(0x2a, 0x10, 0x50, 34), new Color(0x12, 0x07, 0x30, 28),
                    3, 0.6, 8, -10, 20),
            // Star Core: too close to a star, everything washed red.
            new Theme("level-7", Backdrop.STARFIELD, 4260,
                    new Color(0x6b, 0x1c, 0x10, 34), new Color(0x7a, 0x2a, 0x0a, 26),
                    7, 1.0, 28, -4, -22),
            // Escape Vector: the world falling away astern.
            new Theme("level-8", Backdrop.PLANET_RISE, 4270,
                    new Color(0x25, 0x1c, 0x2c, 30), new Color(0x2f, 0x1a, 0x14, 24),
                    4, 0.8, 10, 4, 10),
            // Dust Reach: the side-view leg, flown along a dead world's terminator.
            // Open space on purpose -- ATMOSPHERE, SURFACE and CAVERN all have a built-in up
            // (lit sky bands, ground along the bottom edge, walls left and right), and scrolled
            // sideways they read as nonsense. Stars look the same lying on their side.
            new Theme("level-9", Backdrop.STARFIELD, 4280,
                    new Color(0x4a, 0x33, 0x14, 32), new Color(0x2a, 0x1d, 0x0c, 24),
                    5, 0.9, 22, 8, -14, true),
            // Hollow Womb: where the thing with three heads lives.
            new Theme("level-10", Backdrop.CAVERN, 4290,
                    new Color(0x28, 0x14, 0x1e), new Color(0x8a, 0x2f, 0x5a),
                    8, 1.3, 0, 0, 0),
    };

    /** Three parallax layers per level, scrolled at different rates by the renderer. */
    private static void backgrounds() throws IOException {
        for (Theme theme : THEMES) {
            for (int layer = 0; layer < LAYER_NAMES.length; layer++) {
                BufferedImage image = blank(BACKDROP_WIDTH, BACKDROP_HEIGHT);
                Graphics2D g = paint(image);
                // One Random per image, so adding a shape to one layer cannot shift any other file.
                Random random = new Random(theme.seed() + layer);

                switch (theme.kind()) {
                    case STARFIELD -> stars(g, theme, layer, random);
                    case PLANET_RISE -> {
                        stars(g, theme, layer, random);
                        if (layer == 0) {
                            planet(g, theme, random);
                        }
                    }
                    case ATMOSPHERE -> sky(g, theme, layer, random);
                    case SURFACE -> ground(g, theme, layer, random);
                    case CAVERN -> tunnel(g, theme, layer, random);
                }
                g.dispose();
                write(image, SPRITES.resolve(theme.directory())
                        .resolve(LAYER_NAMES[layer] + ".png"));
            }
        }
    }

    private static final int[] STAR_COUNTS = {520, 240, 90};
    private static final float[] STAR_MAX_RADIUS = {0.9f, 1.5f, 2.4f};

    private static void stars(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;

        if (layer == 0) {
            // Nebula wash, only on the deepest layer.
            for (int i = 0; i < theme.blobs(); i++) {
                double cx = random.nextDouble() * w;
                double cy = random.nextDouble() * h;
                double r = 180 + random.nextDouble() * 260;
                Color tint = i % 2 == 0 ? theme.tintA() : theme.tintB();
                g.setPaint(new RadialGradientPaint(
                        (float) cx, (float) cy, (float) r,
                        new float[]{0f, 1f},
                        new Color[]{tint, new Color(0, 0, 0, 0)}));
                g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
        }

        int count = (int) Math.round(STAR_COUNTS[layer] * theme.density());
        for (int i = 0; i < count; i++) {
            double x = random.nextDouble() * w;
            double y = random.nextDouble() * h;
            double r = 0.35 + random.nextDouble() * STAR_MAX_RADIUS[layer];
            int brightness = 150 + random.nextInt(106);
            int starAlpha = layer == 0 ? 150 : 210 + random.nextInt(46);
            g.setColor(new Color(
                    channel(brightness + theme.starRed()),
                    channel(brightness + theme.starGreen()),
                    channel(brightness + theme.starBlue()),
                    starAlpha));
            g.fill(new Ellipse2D.Double(x, y, r * 2, r * 2));
        }
    }

    /**
     * A world hanging in the far layer.
     *
     * Kept smaller than the canvas on purpose: layers tile by wrapping at the image height, and a
     * feature taller than that cannot repeat without overlapping itself.
     */
    private static void planet(Graphics2D g, Theme theme, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        double radius = h * 0.26;
        // Kept clear of the left and right edges too: only the vertical axis wraps, so a limb cut
        // flat by a side edge just looks like a mistake.
        double cx = radius + random.nextDouble() * (w - radius * 2);
        double cy = h * (0.2 + random.nextDouble() * 0.6);
        Color body = opaque(theme.tintA());
        Color rim = opaque(theme.tintB());

        wrapped(g, theme, copy -> {
            // Lit from the upper left, so the terminator reads as a sphere. Kept dim on purpose: the
            // planet covers a third of the arena, and at full brightness bullets crossing it stop
            // reading.
            copy.setPaint(new RadialGradientPaint(
                    (float) (cx - radius * 0.45), (float) (cy - radius * 0.5), (float) (radius * 1.7),
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{brighten(body, 26), darken(body, 6), darken(body, 40)}));
            copy.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));

            // Atmosphere: one gradient hugging the limb. Concentric strokes banded visibly instead.
            double halo = radius * 1.22;
            float limb = (float) (radius / halo);
            copy.setPaint(new RadialGradientPaint(
                    (float) cx, (float) cy, (float) halo,
                    new float[]{0f, limb * 0.98f, limb, 1f},
                    new Color[]{alpha(rim, 0), alpha(rim, 0), alpha(rim, 96), alpha(rim, 0)}));
            copy.fill(new Ellipse2D.Double(cx - halo, cy - halo, halo * 2, halo * 2));
        });
    }

    private static final int[] DECK_COUNTS = {7, 12, 17};
    private static final double[] DECK_REACH = {0.34, 0.23, 0.15};
    private static final double[] DECK_FLATTEN = {0.22, 0.16, 0.09};
    private static final int[] DECK_ALPHAS = {64, 98, 126};

    /** Inside a planet's air: a banded sky, broad decks behind, thin wisps rushing past in front. */
    private static void sky(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        Color high = opaque(theme.tintA());
        Color low = opaque(theme.tintB());

        if (layer == 0) {
            // Symmetric gradient: the top and bottom edges match, which is what lets it tile.
            g.setPaint(new java.awt.LinearGradientPaint(
                    0, 0, 0, h,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{high, low, high}));
            g.fillRect(0, 0, w, h);
        }

        int decks = (int) Math.round(DECK_COUNTS[layer] * theme.density());
        for (int i = 0; i < decks; i++) {
            double cx = random.nextDouble() * w;
            double cy = random.nextDouble() * h;
            double rx = w * DECK_REACH[layer] * (0.55 + random.nextDouble() * 0.7);
            double ry = rx * DECK_FLATTEN[layer] * (0.7 + random.nextDouble() * 0.7);
            Color tint = i % 2 == 0 ? brighten(low, 78) : brighten(high, 58);
            int deckAlpha = DECK_ALPHAS[layer];
            wrapped(g, theme, copy -> {
                softBlob(copy, cx, cy, rx, ry, tint, deckAlpha);
                // A brighter, tighter top so the deck has a lit crown rather than one flat wash.
                softBlob(copy, cx - rx * 0.15, cy - ry * 0.45, rx * 0.6, ry * 0.55,
                        brighten(tint, 40), deckAlpha);
            });
        }
    }

    private static final double[] MOUND_REACH = {0.018, 0.027, 0.038};
    private static final int[] MOUND_COUNTS = {40, 24, 12};
    private static final int[] MOUND_ALPHAS = {255, 225, 200};

    /** Low over a world's surface: ground, then hills, then whatever nearly brushes the hull. */
    private static void ground(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        Color soil = opaque(theme.tintA());
        Color growth = opaque(theme.tintB());

        if (layer == 0) {
            g.setColor(soil);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < theme.blobs(); i++) {
                double cx = random.nextDouble() * w;
                double cy = random.nextDouble() * h;
                double rx = w * (0.16 + random.nextDouble() * 0.26);
                wrapped(g, theme, copy -> softBlob(copy, cx, cy, rx, rx * 0.7, growth, 96));
            }
        }

        // Mounds: treetops on a jungle, boulders on a canyon. Density decides which it reads as.
        int count = (int) Math.round(MOUND_COUNTS[layer] * theme.density());
        int tintAlpha = MOUND_ALPHAS[layer];
        for (int i = 0; i < count; i++) {
            double cx = random.nextDouble() * w;
            double cy = random.nextDouble() * h;
            double r = w * MOUND_REACH[layer] * (0.6 + random.nextDouble() * 0.8);
            Color tint = random.nextBoolean() ? growth : brighten(soil, 34);
            // Lobe offsets are drawn before wrapping, not inside it: consuming randoms per copy
            // would give the three copies different shapes and break the tile.
            double[][] lobes = new double[3][3];
            for (double[] lobe : lobes) {
                lobe[0] = (random.nextDouble() - 0.5) * r * 0.9;
                lobe[1] = (random.nextDouble() - 0.5) * r * 0.7;
                lobe[2] = 0.62 + random.nextDouble() * 0.5;
            }

            wrapped(g, theme, copy -> {
                copy.setColor(alpha(darken(tint, 48), tintAlpha));
                for (double[] lobe : lobes) {
                    double lr = r * lobe[2];
                    copy.fill(new Ellipse2D.Double(cx + lobe[0] - lr,
                            cy + lobe[1] - lr + r * 0.2, lr * 2, lr * 2));
                }
                copy.setColor(alpha(tint, tintAlpha));
                for (double[] lobe : lobes) {
                    double lr = r * lobe[2] * 0.88;
                    copy.fill(new Ellipse2D.Double(cx + lobe[0] - lr, cy + lobe[1] - lr,
                            lr * 2, lr * 2));
                }
                copy.setColor(alpha(brighten(tint, 48), tintAlpha));
                double lit = r * 0.32;
                copy.fill(new Ellipse2D.Double(cx - lit - r * 0.22, cy - lit - r * 0.3,
                        lit * 2, lit * 2));
            });
        }
    }

    /** A foundry tunnel: rock walls on both sides, light strips down them, dust in between. */
    private static void tunnel(Graphics2D g, Theme theme, int layer, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        Color rock = opaque(theme.tintA());
        Color lamp = opaque(theme.tintB());

        if (layer == 0) {
            g.setColor(darken(rock, 14));
            g.fillRect(0, 0, w, h);
        }

        // Walls thicken on nearer layers, so the tunnel closes in as the parallax pulls past. Kept
        // narrow on purpose: enemies still spawn across the full arena, and ships flying over solid
        // rock reads as a bug rather than as depth.
        double reach = 0.07 + layer * 0.03;
        for (int side = -1; side <= 1; side += 2) {
            Path2D wall = tunnelWall(side, reach, random);
            g.setColor(layer == 0 ? rock : darken(rock, 18 + layer * 12));
            g.fill(wall);
            g.setColor(darken(rock, 60));
            g.setStroke(new BasicStroke(3f));
            g.draw(wall);
        }

        int lamps = (int) Math.round(theme.blobs() * theme.density());
        for (int i = 0; i < lamps; i++) {
            double lx = w * (i % 2 == 0 ? reach * 0.5 : 1 - reach * 0.5);
            double ly = random.nextDouble() * h;
            double r = w * 0.007;
            wrapped(g, theme, copy -> {
                softBlob(copy, lx, ly, r * 9, r * 9, lamp, 110);
                copy.setColor(brighten(lamp, 80));
                copy.fill(new java.awt.geom.RoundRectangle2D.Double(
                        lx - r, ly - r * 5, r * 2, r * 10, r * 2, r * 2));
            });
        }

        if (layer == LAYER_NAMES.length - 1) {
            // Dust down the middle, so the nearest layer has something moving between the walls.
            int motes = (int) Math.round(140 * theme.density());
            for (int i = 0; i < motes; i++) {
                double x = w * (reach + random.nextDouble() * (1 - reach * 2));
                double y = random.nextDouble() * h;
                double r = 0.6 + random.nextDouble() * 1.8;
                g.setColor(alpha(brighten(rock, 96), 90 + random.nextInt(80)));
                g.fill(new Ellipse2D.Double(x, y, r * 2, r * 2));
            }
        }
    }

    /** Harmonic weights for the tunnel edge, coarsest first. */
    private static final double[] WALL_HARMONICS = {0.5, 0.3, 0.2};

    /**
     * One side of the tunnel.
     *
     * The edge is a sum of harmonics with random phases rather than an independent sample per step.
     * Being periodic in the layer height it closes on itself exactly, which is what lets the wall
     * tile when the layer wraps; being continuous it reads as rock, where uncorrelated samples read
     * as a sawtooth however many of them there are.
     */
    private static Path2D tunnelWall(int side, double reach, Random random) {
        int w = BACKDROP_WIDTH;
        int h = BACKDROP_HEIGHT;
        int steps = 48;

        double[] phase = new double[WALL_HARMONICS.length];
        for (int i = 0; i < phase.length; i++) {
            phase[i] = random.nextDouble() * 2 * Math.PI;
        }

        Path2D wall = new Path2D.Double();
        double edge = side < 0 ? 0 : w;
        wall.moveTo(edge, 0);
        for (int i = 0; i <= steps; i++) {
            double along = i / (double) steps;
            double wobble = 0;
            for (int harmonic = 0; harmonic < WALL_HARMONICS.length; harmonic++) {
                wobble += WALL_HARMONICS[harmonic]
                        * Math.sin(2 * Math.PI * (harmonic + 1) * along + phase[harmonic]);
            }
            double inset = reach * (1 + 0.42 * wobble);
            double x = side < 0 ? w * inset : w * (1 - inset);
            wall.lineTo(x, h * along);
        }
        wall.lineTo(edge, h);
        wall.closePath();
        return wall;
    }

    /**
     * Draws the same body three times, one canvas height apart.
     *
     * The renderer scrolls each layer by wrapping at the image height, so a shape clipped by the top
     * or bottom edge has to reappear on the other side or the join shows as a hard line once per
     * cycle. Stars get away without this because they are barely a pixel across; nothing larger does.
     */
    private static void wrapped(Graphics2D g, Theme theme,
                                java.util.function.Consumer<Graphics2D> body) {
        // Along whichever axis the renderer wraps this level on. A top-down level shifts by the
        // image height exactly as it always did, so every existing backdrop is unchanged.
        int dx = theme.sideways() ? BACKDROP_WIDTH : 0;
        int dy = theme.sideways() ? 0 : BACKDROP_HEIGHT;
        for (int copy = -1; copy <= 1; copy++) {
            Graphics2D shifted = (Graphics2D) g.create();
            shifted.translate(copy * dx, copy * dy);
            body.accept(shifted);
            shifted.dispose();
        }
    }

    /**
     * A soft elliptical wash.
     *
     * Squashes a circular gradient rather than filling an ellipse with one, so the tint fades out on
     * every edge instead of being cut off flat above and below.
     */
    private static void softBlob(Graphics2D g, double cx, double cy, double rx, double ry,
                                 Color tint, int centreAlpha) {
        Graphics2D squashed = (Graphics2D) g.create();
        squashed.translate(cx, cy);
        squashed.scale(1, ry / rx);
        squashed.translate(-cx, -cy);
        squashed.setPaint(new RadialGradientPaint(
                (float) cx, (float) cy, (float) rx,
                new float[]{0f, 1f},
                new Color[]{alpha(tint, centreAlpha), alpha(tint, 0)}));
        squashed.fill(new Ellipse2D.Double(cx - rx, cy - rx, rx * 2, rx * 2));
        squashed.dispose();
    }

    private static int channel(int value) {
        int clamped = Math.max(0, Math.min(255, value));
        return clamped;
    }

    /** Drops a tint's alpha, for the places a theme colour is used as a solid fill. */
    private static Color opaque(Color tint) {
        Color solid = new Color(tint.getRed(), tint.getGreen(), tint.getBlue());
        return solid;
    }

    private static Color alpha(Color tint, int value) {
        Color tinted = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), channel(value));
        return tinted;
    }

    /** Even per-channel lightening. Color.brighter() scales, which washes out dark tints unevenly. */
    private static Color brighten(Color base, int amount) {
        Color shifted = new Color(channel(base.getRed() + amount), channel(base.getGreen() + amount),
                channel(base.getBlue() + amount));
        return shifted;
    }

    private static Color darken(Color base, int amount) {
        Color shifted = brighten(base, -amount);
        return shifted;
    }

    // ----------------------------------------------------------------- audio

    private static void laser() throws IOException {
        double duration = 0.2;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        // Downward pitch sweep: the classic shot.
        for (int i = 0; i < mix.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = t / duration;
            double freq = 1500 - 1050 * progress;
            double envelope = Math.exp(-5.5 * progress);
            mix[i] = square(freq, t) * 0.35 * envelope;
        }
        writeWav(mix, SOUNDS.resolve("laser.wav"));
    }

    private static void explosionSound() throws IOException {
        double duration = 0.75;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        Random random = new Random(77);
        double lowpass = 0;
        for (int i = 0; i < mix.length; i++) {
            double progress = i / (double) mix.length;
            double envelope = Math.exp(-4.2 * progress);
            double white = random.nextDouble() * 2 - 1;
            // One-pole lowpass, opening then closing, so it reads as a boom not a hiss.
            double cutoff = 0.36 - 0.26 * progress;
            lowpass += cutoff * (white - lowpass);
            double rumble = Math.sin(2 * Math.PI * (70 - 30 * progress) * i / SAMPLE_RATE);
            mix[i] = (lowpass * 0.8 + rumble * 0.35) * envelope * 0.75;
        }
        writeWav(mix, SOUNDS.resolve("explosion.wav"));
    }

    private static void thud() throws IOException {
        double duration = 0.3;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        Random random = new Random(21);
        for (int i = 0; i < mix.length; i++) {
            double progress = i / (double) mix.length;
            double envelope = Math.exp(-11 * progress);
            double body = Math.sin(2 * Math.PI * (150 - 90 * progress) * i / SAMPLE_RATE);
            double grit = (random.nextDouble() * 2 - 1) * 0.3;
            mix[i] = (body + grit) * envelope * 0.55;
        }
        writeWav(mix, SOUNDS.resolve("collision.wav"));
    }

    /** A short descending arpeggio. Replaces a 172-second clip ripped from a video. */
    private static void gameOverSting() throws IOException {
        double duration = 1.9;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        int[] notes = {57, 53, 50, 45};
        for (int i = 0; i < notes.length; i++) {
            addTone(mix, i * 0.34, 0.5, pitch(notes[i]), 0.3, Wave.SQUARE);
            addTone(mix, i * 0.34, 0.5, pitch(notes[i] - 12), 0.18, Wave.TRIANGLE);
        }
        writeWav(mix, SOUNDS.resolve("game-over.wav"));
    }

    /** A short ascending arpeggio over a held top note: the level is cleared. */
    private static void levelClearSting() throws IOException {
        double duration = 1.6;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        int[] notes = {57, 62, 66, 69};
        for (int i = 0; i < notes.length; i++) {
            addTone(mix, i * 0.2, 0.45, pitch(notes[i]), 0.3, Wave.SQUARE);
            addTone(mix, i * 0.2, 0.45, pitch(notes[i] + 12), 0.14, Wave.TRIANGLE);
        }
        addTone(mix, 0.8, 0.7, pitch(74), 0.26, Wave.SQUARE);
        addTone(mix, 0.8, 0.7, pitch(62), 0.16, Wave.TRIANGLE);
        writeWav(mix, SOUNDS.resolve("level-clear.wav"));
    }

    /**
     * A two-note warble for a nearly-dead ship.
     *
     * Deliberately short and unlooped: the game retriggers it on a tick interval while health is
     * low, which needs no stop call and stops on its own the moment health recovers. A falling
     * interval rather than a rising one, so it reads as an alarm and not as a pickup.
     */
    private static void lowHealthAlarm() throws IOException {
        double duration = 0.5;
        double[] mix = new double[(int) (duration * SAMPLE_RATE)];
        addTone(mix, 0.00, 0.16, pitch(81), 0.28, Wave.SQUARE);
        addTone(mix, 0.20, 0.16, pitch(76), 0.28, Wave.SQUARE);
        addTone(mix, 0.20, 0.16, pitch(64), 0.12, Wave.TRIANGLE);
        writeWav(mix, SOUNDS.resolve("low-health.wav"));
    }

    private enum Wave { SQUARE, TRIANGLE, SINE }

    private static double pitch(int midiNote) {
        double frequency = 440 * Math.pow(2, (midiNote - 69) / 12.0);
        return frequency;
    }

    private static void addTone(double[] mix, double startSeconds, double durationSeconds,
                                double frequency, double amplitude, Wave wave) {
        int start = (int) (startSeconds * SAMPLE_RATE);
        int length = (int) (durationSeconds * SAMPLE_RATE);
        int attack = Math.max(1, length / 30);
        int release = Math.max(1, length / 3);

        for (int i = 0; i < length; i++) {
            int index = start + i;
            if (index < 0 || index >= mix.length) {
                continue;
            }
            double t = index / (double) SAMPLE_RATE;
            double sample = switch (wave) {
                case SQUARE -> square(frequency, t);
                case TRIANGLE -> triangle(frequency, t);
                case SINE -> Math.sin(2 * Math.PI * frequency * t);
            };
            // Short attack, long release, so notes do not click.
            double envelope = 1;
            if (i < attack) {
                envelope = i / (double) attack;
            } else if (i > length - release) {
                envelope = (length - i) / (double) release;
            }
            mix[index] += sample * amplitude * envelope;
        }
    }

    private static double square(double frequency, double t) {
        double phase = (t * frequency) % 1.0;
        return phase < 0.5 ? 1 : -1;
    }

    private static double triangle(double frequency, double t) {
        double phase = (t * frequency) % 1.0;
        return 4 * Math.abs(phase - 0.5) - 1;
    }

    /** 16-bit mono PCM WAV, written by hand so the tool needs no audio libraries. */
    private static void writeWav(double[] samples, Path target) throws IOException {
        // Normalise to just under full scale rather than clipping the sum of layered voices.
        double peak = 0;
        for (double sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        double gain = peak > 0 ? 0.89 / peak : 1;

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (double sample : samples) {
            int value = (int) Math.round(Math.max(-1, Math.min(1, sample * gain)) * 32767);
            pcm.write(value & 0xff);
            pcm.write((value >> 8) & 0xff);
        }
        byte[] data = pcm.toByteArray();

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + data.length);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes());
        header.putInt(data.length);

        try (var out = Files.newOutputStream(target)) {
            out.write(header.array());
            out.write(data);
        }
        System.out.println("wrote " + target);
    }

    // ----------------------------------------------------------------- shared

    private static BufferedImage blank(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        return image;
    }

    private static Graphics2D paint(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void write(BufferedImage image, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        ImageIO.write(image, "png", target.toFile());
        System.out.println("wrote " + target);
    }

    private GenerateAssets() {
    }
}
