package com.hashimjacobs.spacecase.scene;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Sprite;

/** Shared chrome for the menu screens: starfield backdrop, centred title and content column. */
final class MenuScreen {

    private MenuScreen() {
    }

    static StackPane build(String title, Node... content) {
        VBox column = new VBox(Tokens.GAP_L);
        column.setAlignment(Pos.CENTER);
        // VBox stretches children to its own width by default, which would push a left-aligned
        // text block out to the window edges instead of centring it as a group.
        column.setFillWidth(false);
        column.getChildren().add(MenuTitle.plate(title, Tokens.SIZE_DISPLAY));
        column.getChildren().addAll(content);

        // A scrim between the sky and the menu. Level one's planet is bright and sits under the
        // button column, and rows have to stay readable over whatever is behind them -- a backdrop
        // that competes with the thing you are trying to choose is worse than no backdrop.
        Region scrim = new Region();
        scrim.setBackground(new Background(new BackgroundFill(Tokens.veil(0.45), null, null)));

        StackPane root = new StackPane(scrim, column);
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setAlignment(Pos.CENTER);
        root.setBackground(starfield());
        return root;
    }

    /**
     * The menu backdrop: level one's sky, planet and all.
     *
     * Was L1_MID, which is the scattered-dust layer -- {@code backgrounds()} only draws a planet
     * into layer zero, so the menus were showing the one level-one layer with nothing in it and
     * reading as a plain black field. L1_FAR is the same canvas size with the planet in it.
     *
     * Tiling it was always pointless at exactly canvas size, and would repeat the planet if the
     * window were ever larger, so it does not repeat. The art is transparent where the sky is,
     * hence the fill underneath -- the same bargain {@code Renderer.drawScrollingBackground} makes.
     */
    private static Background starfield() {
        Image image = Assets.image(Sprite.L1_FAR);
        BackgroundImage backdrop = new BackgroundImage(
                image,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(GameConfig.WIDTH, GameConfig.HEIGHT, false, false, false, true));
        Background background = new Background(
                List.of(new BackgroundFill(Tokens.SPACE, null, null)), List.of(backdrop));
        return background;
    }

    /** A small ship decal used to dress the start screen. */
    static ImageView decal(Sprite sprite, double width) {
        ImageView view = new ImageView(Assets.image(sprite));
        view.setPreserveRatio(true);
        view.setFitWidth(width);
        return view;
    }

    static Text caption(String text, double size, Color color) {
        Text caption = new Text(text);
        caption.setFill(color);
        caption.setFont(Font.font(Tokens.BODY, size));
        return caption;
    }
}
