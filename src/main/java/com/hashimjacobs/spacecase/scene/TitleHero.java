package com.hashimjacobs.spacecase.scene;

import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.ChassisArt;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * What moves on the start screen: the pilot's own ship turning on a lit pad.
 *
 * Only the ship moves. A drifting spore layer was tried and removed: anything falling across the
 * whole screen reads as the menu itself scrolling.
 *
 * The ship is the garage's turntable render (the same Tripo model, livery and kit), so what the
 * title shows is what the player will fly. A drawn hull with no turntable falls back to the plain
 * pad, which is better than a flat sprite spinning like a coin.
 */
final class TitleHero {

    private static final double SIZE = 400;

    private final Canvas canvas = new Canvas(SIZE, SIZE);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();
    private final Loadout look;
    private int tick;

    TitleHero(Loadout look) {
        this.look = look;
        draw();
    }

    Node canvas() {
        return canvas;
    }

    void tick() {
        tick++;
        draw();
    }

    private void draw() {
        double c = SIZE / 2;
        double ship = SIZE * 0.8;
        gc.clearRect(0, 0, SIZE, SIZE);

        // The pad, as GarageOverlay.drawShowcase lights it, minus the hangar's gantry cone.
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setGlobalAlpha(0.30);
        gc.setFill(Tokens.BRAND);
        gc.fillOval(c - ship * 0.62, c + ship * 0.12, ship * 1.24, ship * 0.22);
        gc.restore();
        gc.setStroke(Tokens.BRAND);
        gc.setLineWidth(2);
        gc.setGlobalAlpha(0.7);
        gc.strokeOval(c - ship * 0.55, c + ship * 0.14, ship * 1.1, ship * 0.18);
        gc.setGlobalAlpha(1);

        ChassisArt art = look.chassisArt();
        if (art == null) {
            return;
        }
        Image frame = Assets.chassisFrames(art, look.kit().slug(), ChassisArt.View.TURN,
                look.livery().hue(), look.livery().saturation(), 2, false)
                .get(ChassisArt.View.TURN.frameIndexAt(tick));
        gc.drawImage(frame, c - ship / 2, c - ship / 2, ship, ship);
    }
}
