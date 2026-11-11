package com.hashimjacobs.spacecase.scene;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
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

        Node keys = hints("\u2191\u2193", "move", "Enter", "select", "Esc", "back");
        StackPane root = new StackPane(scrim, column, keys);
        StackPane.setAlignment(keys, Pos.BOTTOM_CENTER);
        StackPane.setMargin(keys, new Insets(Tokens.GAP_L));
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setAlignment(Pos.CENTER);
        root.setBackground(starfield());
        return root;
    }

    /**
     * The menu backdrop: the title key art (tools/openart/fx.py title-backdrop), dark on its left
     * third where the menus sit. Every menu shares it so leaving the start screen is not a cut to a
     * different place. Not tiled: it is exactly canvas size and would repeat its planet.
     */
    private static Background starfield() {
        return backdrop(Assets.image(Sprite.TITLE_BACKDROP));
    }

    /**
     * The start screen: logo and menu down the left, the pilot's ship on the right, over the key art. Left-aligned because a vertical list needs an edge for the eye to run down,
     * and because the right of the key art is where its planet is.
     *
     * The scrim is a left-to-right fade rather than {@link #build}'s flat veil, so the menu reads and
     * the art on the right is not dimmed for nothing.
     */
    static StackPane title(Node logo, Node panel, Node hero, Node footer) {
        VBox left = new VBox(Tokens.GAP_XL, logo, panel);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setFillWidth(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox body = new HBox(left, spacer, hero);
        body.setAlignment(Pos.CENTER);
        body.setPadding(new Insets(Tokens.GAP_XL, Tokens.GAP_XL, Tokens.GAP_XL * 2, Tokens.GAP_XL * 2));

        Region scrim = new Region();
        scrim.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 1, 0, true,
                CycleMethod.NO_CYCLE, new Stop(0, Tokens.veil(0.6)), new Stop(0.6, Tokens.veil(0))), null, null)));

        StackPane root = new StackPane(scrim, body, footer);
        StackPane.setAlignment(footer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(footer, new Insets(Tokens.GAP_L));
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setBackground(starfield());
        return root;
    }

    /** A sky covering the whole screen, over the space colour: the menus' own, or a galaxy's. */
    static Background backdrop(Image image) {
        BackgroundImage backdrop = new BackgroundImage(
                image,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(1, 1, true, true, false, true));
        Background background = new Background(
                List.of(new BackgroundFill(Tokens.SPACE, null, null)), List.of(backdrop));
        return background;
    }

    /**
     * The controls for a screen, as keycaps: {@code hints("Enter", "select", "Esc", "back")}.
     *
     * Drawn as keys rather than a sentence so the eye finds the key first and its meaning second,
     * which is the order the question is asked in.
     */
    static HBox hints(String... keyThenMeaning) {
        HBox row = new HBox(Tokens.GAP_S);
        row.setAlignment(Pos.CENTER);
        row.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        row.setMouseTransparent(true);
        for (int i = 0; i + 1 < keyThenMeaning.length; i += 2) {
            Text key = caption(keyThenMeaning[i], Tokens.SIZE_CAPTION, Tokens.TEXT_MUTED);
            StackPane cap = new StackPane(key);
            cap.setPadding(new Insets(2, 7, 2, 7));
            cap.setBackground(new Background(new BackgroundFill(Tokens.SURFACE_2.deriveColor(0, 1, 1, 0.8),
                    new javafx.scene.layout.CornerRadii(Tokens.RADIUS_S), Insets.EMPTY)));
            cap.setBorder(new javafx.scene.layout.Border(new javafx.scene.layout.BorderStroke(Tokens.EDGE_STRONG,
                    javafx.scene.layout.BorderStrokeStyle.SOLID, new javafx.scene.layout.CornerRadii(Tokens.RADIUS_S),
                    new javafx.scene.layout.BorderWidths(Tokens.STROKE_HAIR))));
            Text meaning = caption(keyThenMeaning[i + 1], Tokens.SIZE_CAPTION, Tokens.TEXT_FAINT);
            HBox pair = new HBox(Tokens.GAP_XS + 2, cap, meaning);
            pair.setAlignment(Pos.CENTER);
            pair.setPadding(new Insets(0, Tokens.GAP_S, 0, 0));
            row.getChildren().add(pair);
        }
        return row;
    }

    /** A panel for things that are read rather than chosen: the results card. Styled as MenuPanel is. */
    static VBox card(Node... content) {
        VBox card = new VBox(Tokens.GAP_S, content);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(Tokens.GAP_L, Tokens.GAP_XL, Tokens.GAP_L, Tokens.GAP_XL));
        card.setMinWidth(Tokens.ROW_WIDTH + 2 * Tokens.GAP);
        javafx.scene.layout.CornerRadii corners = new javafx.scene.layout.CornerRadii(Tokens.RADIUS_L);
        card.setBackground(new Background(new BackgroundFill(
                Tokens.SURFACE_1.deriveColor(0, 1, 1, 0.7), corners, Insets.EMPTY)));
        card.setBorder(new javafx.scene.layout.Border(new javafx.scene.layout.BorderStroke(
                Tokens.BRAND, Tokens.EDGE, Tokens.EDGE, Tokens.EDGE,
                javafx.scene.layout.BorderStrokeStyle.SOLID, javafx.scene.layout.BorderStrokeStyle.SOLID,
                javafx.scene.layout.BorderStrokeStyle.SOLID, javafx.scene.layout.BorderStrokeStyle.SOLID,
                corners, new javafx.scene.layout.BorderWidths(Tokens.STROKE_BOLD, Tokens.STROKE_HAIR,
                        Tokens.STROKE_HAIR, Tokens.STROKE_HAIR), Insets.EMPTY)));
        return card;
    }

    static Text caption(String text, double size, Color color) {
        Text caption = new Text(text);
        caption.setFill(color);
        caption.setFont(Font.font(Tokens.BODY, size));
        return caption;
    }
}
