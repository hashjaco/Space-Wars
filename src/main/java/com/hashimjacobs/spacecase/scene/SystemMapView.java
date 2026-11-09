package com.hashimjacobs.spacecase.scene;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.ChassisArt;
import com.hashimjacobs.spacecase.asset.MapArt;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.scene.SystemMapModel.Node;
import com.hashimjacobs.spacecase.scene.SystemMapModel.NodeState;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Draws a galaxy's route: ten nodes, the path between them, and a panel about the focused one.
 *
 * Canvas rather than nodes because this is drawing, not layout. Ten hand-placed positions, curved
 * connectors, four node states and a boss portrait have no layout manager that helps -- built from
 * Shapes it would be sixty positioned objects and the same coordinates written out anyway, plus the
 * scene-graph overhead. {@code Hud}, {@code GarageOverlay} and {@code DebriefOverlay} already
 * establish the idiom. A Canvas is still a Node, so it drops into {@link MenuScreen#build} and gets
 * the title and starfield for free.
 *
 * Holds no rules: {@link SystemMapModel} owns the cursor and the states, which is what keeps them
 * testable without a toolkit.
 *
 * <p><strong>No state is signalled by colour alone.</strong> Each of the four differs in stroke
 * pattern, in fill, and in the glyph at its centre, so the map reads without colour vision -- which
 * matters here more than most screens, since the whole thing is a green-and-red progress display.
 */
final class SystemMapView {

    private static final double WIDTH = GameConfig.WIDTH;
    private static final double HEIGHT = 620;

    /** The band the route occupies, leaving the panel below it. */
    private static final double ROUTE_TOP = 40;
    private static final double ROUTE_HEIGHT = 330;
    private static final double ROUTE_INSET = 70;

    private static final double NODE_RADIUS = 26;

    private static final double PANEL_TOP = 396;
    private static final double PANEL_HEIGHT = 208;
    private static final double PANEL_INSET = 60;

    /** Slowest pulse that still reads as alive. Suppressed entirely under reduced flash. */
    private static final double PULSE_TICKS = 90;

    /** The galaxy's planet, in the open middle of the route loop, behind everything on it. */
    private static final double PLANET_SIZE = 190;
    private static final double PLANET_X = 0.58;
    private static final double PLANET_Y = 0.5;

    /** The player's ship: parked above the focused node, gliding to the next one in this long. */
    private static final double TRAVEL_TICKS = 18;
    private static final double SHIP_W = 34;
    private static final double SHIP_H = 36;
    private static final double SHIP_LIFT = NODE_RADIUS + 44;

    private final Canvas canvas = new Canvas(WIDTH, HEIGHT);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();
    private final SystemMapModel model;
    private final Settings settings;
    private final Color accent;
    private final MapArt art;
    private final Loadout loadout;

    private int tick;
    /** Where the ship's glide started, the node it is flying to, and how far along it is. */
    private double fromX;
    private double fromY;
    private int shipTo;
    private double travel = TRAVEL_TICKS;

    SystemMapView(SystemMapModel model, Settings settings, Loadout loadout) {
        this.model = model;
        this.settings = settings;
        this.loadout = loadout;
        this.accent = Color.web(model.galaxy().accent());
        this.art = MapArt.valueOf(model.galaxy().name());
        this.shipTo = model.cursor();
        this.fromX = parkX(model.focused());
        this.fromY = parkY(model.focused());
    }

    Canvas canvas() {
        return canvas;
    }

    /**
     * Advances the idle animations and the ship's glide. Never called when the player has asked
     * for less motion, which is what holds the planet still and makes the ship jump instead.
     */
    void tick() {
        tick++;
        travel++;
    }

    void draw() {
        if (model.cursor() != shipTo) {
            // Leave from wherever the ship is drawn now, so a second press mid-glide does not jump.
            double t = glide();
            Node target = model.nodes().get(shipTo);
            fromX += (parkX(target) - fromX) * t;
            fromY += (parkY(target) - fromY) * t;
            shipTo = model.cursor();
            travel = settings.reducedFlash() ? TRAVEL_TICKS : 0;
        }
        gc.clearRect(0, 0, WIDTH, HEIGHT);
        drawPlanet();
        drawConnectors();
        for (Node node : model.nodes()) {
            drawNode(node, node == model.focused());
        }
        drawShip();
        drawPanel();
        drawMessage();
    }

    private void drawPlanet() {
        Image frame = Assets.planetFrames(art).get(MapArt.frameIndexAt(tick));
        double cx = ROUTE_INSET + PLANET_X * (WIDTH - ROUTE_INSET * 2);
        double cy = ROUTE_TOP + PLANET_Y * ROUTE_HEIGHT;
        gc.setGlobalAlpha(0.9);
        gc.drawImage(frame, cx - PLANET_SIZE / 2, cy - PLANET_SIZE / 2, PLANET_SIZE, PLANET_SIZE);
        gc.setGlobalAlpha(1);
    }

    /**
     * The equipped hull, parked above the focused node, gliding when the cursor moves.
     *
     * The reticle still marks focus; the ship is the same signal in motion, and turns toward where
     * it is going and back as it arrives. The hull call is GarageOverlay's, fallback and all.
     */
    private void drawShip() {
        Node to = model.nodes().get(shipTo);
        double t = glide();
        double fx = fromX;
        double fy = fromY;
        double tx = parkX(to);
        double ty = parkY(to);
        // Zero once parked: from and to coincide, and sin(pi) brings the nose back up anyway.
        double heading = Math.toDegrees(Math.atan2(tx - fx, -(ty - fy)));

        ChassisArt hull = loadout.chassisArt();
        Image image = hull == null ? Assets.image(loadout.chassisPose(2, false, false))
                : Assets.chassisFrames(hull, loadout.livery().hue(), loadout.livery().saturation(), 2, false, false)
                        .get(hull.frameIndexAt(tick));
        gc.save();
        gc.translate(fx + (tx - fx) * t, fy + (ty - fy) * t);
        gc.rotate(heading * Math.sin(Math.PI * t));
        gc.drawImage(image, -SHIP_W / 2, -SHIP_H / 2, SHIP_W, SHIP_H);
        gc.restore();
    }

    private double glide() {
        return smoothstep(Math.min(1, travel / TRAVEL_TICKS));
    }

    static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double parkX(Node node) {
        return x(node);
    }

    private static double parkY(Node node) {
        return y(node) - SHIP_LIFT;
    }

    /**
     * The path between consecutive levels, drawn solid where it has been flown.
     *
     * So the route itself carries progress: a player can see how far they have come without reading
     * a single node. Drawn first, so nodes sit on top of it.
     */
    private void drawConnectors() {
        var nodes = model.nodes();
        gc.setLineWidth(2);
        for (int i = 1; i < nodes.size(); i++) {
            Node from = nodes.get(i - 1);
            Node to = nodes.get(i);
            boolean flown = isDone(from) && (isDone(to) || to.state() == NodeState.CURRENT);
            gc.setStroke(accent.deriveColor(0, 1, 1, flown ? 0.55 : 0.22));
            gc.setLineDashes(flown ? null : new double[]{6, 8});
            if (to.state() == NodeState.CURRENT) {
                // The leg into the next fight marches toward it: the one segment that is a way
                // forward rather than a record. Still under reduced flash, since tick never moves.
                gc.setStroke(accent.deriveColor(0, 1, 1, 0.8));
                gc.setLineDashes(10, 8);
                gc.setLineDashOffset(-tick * 0.5);
            }
            gc.strokeLine(x(from), y(from), x(to), y(to));
            gc.setLineDashOffset(0);
        }
        gc.setLineDashes(null);
    }

    private static boolean isDone(Node node) {
        return node.state() == NodeState.CLEARED;
    }

    private void drawNode(Node node, boolean focused) {
        double cx = x(node);
        double cy = y(node);
        double r = NODE_RADIUS;

        switch (node.state()) {
            case LOCKED -> {
                gc.setStroke(Tokens.TEXT_FAINT);
                gc.setLineWidth(1.5);
                gc.setLineDashes(4, 5);
                gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
                gc.setLineDashes(null);
                drawPadlock(cx, cy);
            }
            case AVAILABLE -> {
                gc.setStroke(accent);
                gc.setLineWidth(2);
                gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
                label(String.valueOf(node.level().indexInGalaxy()), cx, cy, Tokens.TEXT,
                        Tokens.SIZE_ROW);
            }
            case CLEARED -> {
                gc.setFill(accent.deriveColor(0, 1, 1, 0.85));
                gc.fillOval(cx - r, cy - r, r * 2, r * 2);
                drawTick(cx, cy);
                if (node.best() > 0) {
                    label("best " + node.best(), cx, cy + r + 18, Tokens.TEXT_DIM,
                            Tokens.SIZE_SMALL);
                }
            }
            case CURRENT -> {
                double breathe = settings.reducedFlash()
                        ? 0.85
                        : 0.6 + 0.35 * Math.abs(Math.sin(tick / PULSE_TICKS * Math.PI));
                gc.setStroke(accent.deriveColor(0, 1, 1, breathe));
                gc.setLineWidth(3);
                gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
                gc.setLineWidth(1.5);
                gc.strokeOval(cx - r - 6, cy - r - 6, (r + 6) * 2, (r + 6) * 2);
                label(String.valueOf(node.level().indexInGalaxy()), cx, cy, Tokens.TEXT,
                        Tokens.SIZE_ROW);
                label("NEXT", cx, cy - r - 16, accent, Tokens.SIZE_CAPTION);
            }
        }

        if (focused) {
            drawReticle(cx, cy, r + 10);
        }
    }

    /**
     * Four corner brackets round the focused node.
     *
     * A bracket rather than a colour change, so focus is legible on top of any of the four states
     * and without colour vision. The panel below re-rendering to the focused level is the second,
     * redundant signal.
     */
    private void drawReticle(double cx, double cy, double r) {
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        double arm = 10;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                double px = cx + sx * r;
                double py = cy + sy * r;
                gc.strokeLine(px, py, px - sx * arm, py);
                gc.strokeLine(px, py, px, py - sy * arm);
            }
        }
    }

    /** A padlock, as shapes. Never a font glyph: the game bundles no font and cannot rely on one. */
    private void drawPadlock(double cx, double cy) {
        gc.setStroke(Tokens.TEXT_FAINT);
        gc.setLineWidth(2);
        gc.strokeArc(cx - 6, cy - 9, 12, 12, 0, 180, javafx.scene.shape.ArcType.OPEN);
        gc.setFill(Tokens.TEXT_FAINT);
        gc.fillRect(cx - 8, cy - 3, 16, 11);
    }

    private void drawTick(double cx, double cy) {
        gc.setStroke(Tokens.SURFACE_1);
        gc.setLineWidth(3);
        gc.strokeLine(cx - 7, cy, cx - 2, cy + 6);
        gc.strokeLine(cx - 2, cy + 6, cx + 8, cy - 6);
    }

    /**
     * What the focused level is, and what is waiting at the end of it.
     *
     * The boss portrait is the first frame of art the game already has, drawn under a veil and
     * unnamed when the level is locked -- so the map teases the fight without spoiling it, and costs
     * no new assets at all.
     */
    private void drawPanel() {
        Node node = model.focused();
        Level level = node.level();
        double x = PANEL_INSET;
        double w = WIDTH - PANEL_INSET * 2;

        gc.setFill(Tokens.SURFACE_1.deriveColor(0, 1, 1, 0.92));
        gc.fillRoundRect(x, PANEL_TOP, w, PANEL_HEIGHT, Tokens.RADIUS_L, Tokens.RADIUS_L);
        gc.setStroke(accent.deriveColor(0, 1, 1, 0.5));
        gc.setLineWidth(Tokens.STROKE);
        gc.strokeRoundRect(x, PANEL_TOP, w, PANEL_HEIGHT, Tokens.RADIUS_L, Tokens.RADIUS_L);

        boolean known = node.state() != NodeState.LOCKED;
        double textX = x + 26;
        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(Tokens.LABEL);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_CAPTION));
        gc.fillText("LEVEL " + level.indexInGalaxy() + " OF " + model.nodes().size(),
                textX, PANEL_TOP + 30);

        gc.setFill(Tokens.TEXT);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_HEADING));
        gc.fillText(known ? level.label() : "Uncharted", textX, PANEL_TOP + 62);

        gc.setFont(Font.font(Tokens.BODY, FontWeight.NORMAL, Tokens.SIZE_BODY));
        gc.setFill(Tokens.TEXT_SECONDARY);
        gc.fillText(level.wavesBeforeBoss() + " waves before the flagship", textX, PANEL_TOP + 92);
        gc.fillText(level.template().hasRock()
                        ? "Rock closes in -- mind the walls"
                        : "Open space", textX, PANEL_TOP + 114);

        gc.setFill(node.best() > 0 ? Tokens.BRAND : Tokens.TEXT_FAINT);
        gc.fillText(node.best() > 0 ? "Best  " + node.best() : "-- not yet flown --",
                textX, PANEL_TOP + 142);

        gc.setFill(Tokens.TEXT_GHOST);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.NORMAL, Tokens.SIZE_CAPTION));
        gc.fillText("< > choose    Enter fly    Esc back", textX, PANEL_TOP + 178);

        drawBossPortrait(level.boss(), x + w - 150, PANEL_TOP + PANEL_HEIGHT / 2, known);
    }

    private void drawBossPortrait(Boss boss, double cx, double cy, boolean known) {
        // Decodes this flagship's eight frames on first sight of its node, to use one of them. That
        // is a single dropped frame the first time the cursor lands on a level, and free every time
        // after -- Assets caches the list, and the fight needs the other seven anyway. Not worth a
        // decode-one-frame API to avoid.
        var frames = Assets.bossFrames(boss.art());
        if (frames == null || frames.isEmpty()) {
            return;
        }
        Image frame = frames.get(0);
        double scale = 0.55;
        double w = boss.art().width() * scale;
        double h = boss.art().height() * scale;
        gc.drawImage(frame, cx - w / 2, cy - h / 2 - 8, w, h);
        if (!known) {
            // A silhouette: the same art under a heavy veil, so the shape is a hint and nothing more.
            gc.setFill(Tokens.SURFACE_1.deriveColor(0, 1, 1, 0.86));
            gc.fillRect(cx - w / 2, cy - h / 2 - 8, w, h);
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(known ? Tokens.DANGER_SOFT : Tokens.TEXT_FAINT);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_SMALL));
        gc.fillText(known ? boss.label() : "??? flagship unknown", cx, cy + h / 2 + 14);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawMessage() {
        if (model.message().isEmpty()) {
            return;
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Tokens.DANGER_SOFT);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_BODY));
        gc.fillText(model.message(), WIDTH / 2, PANEL_TOP - 12);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void label(String text, double cx, double cy, Color colour, double size) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(colour);
        gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, size));
        gc.fillText(text, cx, cy + size * 0.36);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static double x(Node node) {
        return ROUTE_INSET + node.x() * (WIDTH - ROUTE_INSET * 2);
    }

    private static double y(Node node) {
        return ROUTE_TOP + node.y() * ROUTE_HEIGHT;
    }
}
