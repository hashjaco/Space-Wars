package com.hashimjacobs.spacecase.scene;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.MapArt;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * The focused galaxy's planet, turning beside the universe list.
 *
 * A locked galaxy shows its planet as a silhouette under a "?", the same tease the route's boss
 * portraits use -- the shape is a hint and nothing more. Without ticks (reduced flash) it holds on
 * the first frame and only redraws when focus moves.
 */
final class GalaxyPreview {

    private static final double SIZE = 240;

    private final Canvas canvas = new Canvas(SIZE, SIZE);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();
    private MapArt art;
    private boolean open;
    private int tick;

    Canvas canvas() {
        return canvas;
    }

    void show(MapArt art, boolean open) {
        this.art = art;
        this.open = open;
        draw();
    }

    void tick() {
        tick++;
        draw();
    }

    private void draw() {
        gc.clearRect(0, 0, SIZE, SIZE);
        if (art == null) {
            return;
        }
        gc.drawImage(Assets.planetFrames(art).get(MapArt.frameIndexAt(tick)), 0, 0, SIZE, SIZE);
        if (!open) {
            gc.setFill(Tokens.SURFACE_1.deriveColor(0, 1, 1, 0.86));
            gc.fillOval(4, 4, SIZE - 8, SIZE - 8);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Tokens.TEXT_FAINT);
            gc.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, Tokens.SIZE_DISPLAY));
            gc.fillText("?", SIZE / 2, SIZE / 2 + Tokens.SIZE_DISPLAY * 0.36);
        }
    }
}
