package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import com.hashimjacobs.spacecase.asset.Assets;

/**
 * The boxed heading at the top of each menu.
 *
 * Ported from the original Title, now set in the Lugosi face that ships in the repo but was never
 * loaded.
 */
public final class MenuTitle extends StackPane {

    public MenuTitle(String text) {
        this(text, 46, 470, 74);
    }

    public MenuTitle(String text, double fontSize, double width, double height) {
        Rectangle border = new Rectangle(width, height);
        border.setStroke(Color.web("#0ec417"));
        border.setStrokeWidth(2);
        border.setFill(Color.color(0, 0, 0, 0.35));

        Text heading = new Text(text);
        heading.setFill(Color.WHITE);
        heading.setFont(Font.font(Assets.displayFontFamily(), fontSize));

        setAlignment(Pos.CENTER);
        getChildren().addAll(border, heading);
    }
}
