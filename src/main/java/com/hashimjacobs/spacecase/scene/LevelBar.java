package com.hashimjacobs.spacecase.scene;

import java.util.function.DoubleConsumer;

import javafx.geometry.Insets;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * A draggable level bar for the settings rows: a track, a lit fill, and a glowing thumb.
 *
 * Drawn here rather than using JavaFX's Slider because that control takes keyboard focus on click
 * and then swallows the arrow keys, which would strand a player who used the mouse once. This one
 * is never focusable: the row's navigator keeps the keys, and Left/Right reach the value through
 * {@link MenuButton#adjust}.
 */
final class LevelBar extends Region {

    private static final double WIDTH = 150;
    private static final double HEIGHT = 22;
    private static final double TRACK_HEIGHT = 6;
    private static final double THUMB = 16;

    private final double min;
    private final double max;
    private final double step;
    private final DoubleConsumer onChange;
    private final Region fill = new Region();
    private final Region thumb = new Region();
    private double value;

    /** @param step what a drag snaps to; keyboard steps are the caller's business */
    LevelBar(double min, double max, double step, DoubleConsumer onChange) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.onChange = onChange;
        this.value = min;

        Region track = new Region();
        track.setBackground(paint(Tokens.TRACK));
        track.setManaged(false);
        track.resizeRelocate(0, (HEIGHT - TRACK_HEIGHT) / 2, WIDTH, TRACK_HEIGHT);

        fill.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Tokens.BRAND_DEEP), new Stop(1, Tokens.BRAND)),
                new CornerRadii(TRACK_HEIGHT / 2), Insets.EMPTY)));
        fill.setManaged(false);

        thumb.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(THUMB / 2), Insets.EMPTY)));
        thumb.setEffect(new DropShadow(10, Tokens.BRAND));
        thumb.setManaged(false);

        getChildren().addAll(track, fill, thumb);
        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setFocusTraversable(false);

        // Consumed so the row underneath does not also treat the gesture as a click.
        setOnMousePressed(this::drag);
        setOnMouseDragged(this::drag);
        setOnMouseReleased(MouseEvent::consume);
        setOnMouseClicked(MouseEvent::consume);
        layoutBar();
    }

    private void drag(MouseEvent event) {
        event.consume();
        double next = valueAt(event.getX(), WIDTH, min, max, step);
        if (next != value) {
            onChange.accept(next);
        }
    }

    /** Shows a value from the settings; does not call back. */
    void setValue(double value) {
        this.value = Math.max(min, Math.min(max, value));
        layoutBar();
    }

    private void layoutBar() {
        double x = (value - min) / (max - min) * WIDTH;
        fill.resizeRelocate(0, (HEIGHT - TRACK_HEIGHT) / 2, x, TRACK_HEIGHT);
        thumb.resizeRelocate(x - THUMB / 2, (HEIGHT - THUMB) / 2, THUMB, THUMB);
    }

    /** The value under a pointer at {@code x}, clamped to the range and snapped to {@code step}. */
    static double valueAt(double x, double width, double min, double max, double step) {
        double raw = min + Math.max(0, Math.min(1, x / width)) * (max - min);
        double snapped = min + Math.round((raw - min) / step) * step;
        // Rounded to kill float dust (0.30000000000000004), which would read as a change every drag.
        double tidy = Math.round(snapped * 1e6) / 1e6;
        return Math.max(min, Math.min(max, tidy));
    }

    private static Background paint(Color color) {
        return new Background(new BackgroundFill(color, new CornerRadii(TRACK_HEIGHT / 2), Insets.EMPTY));
    }
}
