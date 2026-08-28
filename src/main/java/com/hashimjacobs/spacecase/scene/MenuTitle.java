package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.asset.Assets;

/**
 * The heading at the top of each menu.
 *
 * Two looks. The boxed one is the original and is what the submenus still use. The plate one is
 * the start screen's: no box, a gradient fill and a rule under it, because the box was the thing
 * making the front page read as a form rather than as a title.
 */
public final class MenuTitle extends StackPane {

    /** Where the gradient ends, as a fraction of the cap height. Below this it is all brand. */
    private static final double GRADIENT_END = 0.95;

    public MenuTitle(String text) {
        this(text, Tokens.SIZE_DISPLAY, 470, 74);
    }

    public MenuTitle(String text, double fontSize, double width, double height) {
        Rectangle border = new Rectangle(width, height);
        border.setArcWidth(Tokens.RADIUS_L * 2);
        border.setArcHeight(Tokens.RADIUS_L * 2);
        border.setStroke(Tokens.BRAND);
        border.setStrokeWidth(Tokens.STROKE);
        border.setFill(Tokens.veil(0.35));

        Text heading = new Text(text);
        heading.setFill(Color.WHITE);
        heading.setFont(Font.font(Assets.displayFontFamily(), fontSize));

        setAlignment(Pos.CENTER);
        getChildren().addAll(border, heading);
    }

    /**
     * The unboxed treatment, for the start screen.
     *
     * A white-to-brand gradient rather than flat white, a dark stroke so the letterforms keep
     * their weight against a lit backdrop, and a glow doing the job the box used to do -- holding
     * the title away from what is behind it without drawing a rectangle round it.
     *
     * @param fontSize cap height; the rule under the title is sized from it
     */
    public static MenuTitle plate(String text, double fontSize) {
        return new MenuTitle(text, fontSize);
    }

    private MenuTitle(String text, double fontSize) {
        Text heading = new Text(text);
        heading.setFont(Font.font(Assets.displayFontFamily(), fontSize));
        heading.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE),
                new Stop(GRADIENT_END, Tokens.BRAND)));
        // Stroked as well as filled: on a lit backdrop a gradient alone loses the bottom of every
        // letter, since the brand green and the sky behind it are close in value.
        heading.setStroke(Tokens.SURFACE_0);
        // Thin. At 2.5 the outline was thicker than the strokes of the face it was tracing, which
        // is what made the title read as a sticker rather than as type.
        heading.setStrokeWidth(Tokens.STROKE);
        heading.setEffect(new DropShadow(14, Tokens.BRAND.deriveColor(0, 1, 1, 0.55)));

        // Full heading width with the ends faded out, rather than a hard bar at an arbitrary 40%.
        // Derived from the measured heading because Assets.displayFontFamily() resolves to whatever
        // is installed -- Impact here, DejaVu Sans Condensed elsewhere -- so the width is not known
        // ahead of time. Text layout bounds are available before the node joins a scene.
        Rectangle rule = new Rectangle(heading.getLayoutBounds().getWidth(), Tokens.STROKE);
        rule.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.5, Tokens.BRAND),
                new Stop(1, Color.TRANSPARENT)));

        VBox stack = new VBox(Tokens.GAP, heading, rule);
        stack.setAlignment(Pos.CENTER);

        setAlignment(Pos.CENTER);
        getChildren().add(stack);
    }
}
