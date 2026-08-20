package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
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
        VBox column = new VBox(18);
        column.setAlignment(Pos.CENTER);
        // VBox stretches children to its own width by default, which would push a left-aligned
        // text block out to the window edges instead of centring it as a group.
        column.setFillWidth(false);
        column.getChildren().add(new MenuTitle(title));
        column.getChildren().addAll(content);

        StackPane root = new StackPane(column);
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setAlignment(Pos.CENTER);
        root.setBackground(starfield());
        return root;
    }

    private static Background starfield() {
        Image image = Assets.image(Sprite.L1_MID);
        BackgroundImage backdrop = new BackgroundImage(
                image,
                BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(GameConfig.WIDTH, GameConfig.HEIGHT, false, false, false, true));
        Background background = new Background(backdrop);
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
