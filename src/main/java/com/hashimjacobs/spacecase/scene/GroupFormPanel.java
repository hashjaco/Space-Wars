package com.hashimjacobs.spacecase.scene;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.hashimjacobs.spacecase.net.GroupFormModel;
import com.hashimjacobs.spacecase.net.GroupSpec;
import com.hashimjacobs.spacecase.ui.Tokens;

import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 * Opening a group: who may find it, how many may come, and what the pilot wants help with.
 *
 * Five rows that step with Left and Right, the pattern the settings screen uses. Which galaxies
 * and levels they offer is {@link GroupFormModel}'s business.
 */
final class GroupFormPanel extends VBox {

    private final GroupFormModel model;
    private final MenuButton visibilityRow;
    private final MenuButton maxRow;
    private final MenuButton galaxyRow;
    private final MenuButton levelRow;
    private final MenuButton difficultyRow;
    private final MenuNavigator navigator;
    private final Text hint = MenuScreen.caption("", Tokens.SIZE_SMALL, Tokens.TEXT_FAINT);

    GroupFormPanel(GroupFormModel model, Consumer<GroupSpec> onCreate, Runnable onBack) {
        super(Tokens.GAP_L);
        this.model = model;

        visibilityRow = choice(direction -> model.cycleVisibility());
        maxRow = choice(model::cycleMax);
        galaxyRow = choice(model::cycleGalaxy);
        levelRow = choice(model::cycleLevel);
        difficultyRow = choice(model::cycleDifficulty);

        MenuPanel panel = new MenuPanel(visibilityRow, maxRow, galaxyRow, levelRow, difficultyRow,
                new MenuButton("Create", () -> onCreate.accept(model.spec())),
                new MenuButton("Back", onBack));
        navigator = panel.navigator();
        navigator.setOnBack(onBack);
        getChildren().addAll(panel, hint);
        refresh();
    }

    private MenuButton choice(IntConsumer cycle) {
        MenuButton row = new MenuButton("", () -> changed(cycle, 1));
        row.setOnAdjust(direction -> changed(cycle, direction));
        return row;
    }

    private void changed(IntConsumer cycle, int direction) {
        cycle.accept(direction);
        refresh();
    }

    MenuNavigator navigator() {
        return navigator;
    }

    private void refresh() {
        visibilityRow.setRow("VISIBILITY", model.isPublic() ? "PUBLIC" : "PRIVATE");
        hint.setText(model.isPublic()
                ? "Public: listed on the board for anyone to join."
                : "Private: found only by the code you read out.");
        maxRow.setRow("PLAYERS", "UP TO " + model.maxPlayers());
        galaxyRow.setRow("GALAXY", model.galaxy().label().toUpperCase());
        levelRow.setRow("LEVEL", model.level().indexInGalaxy() + "  ·  "
                + model.level().label().toUpperCase());
        difficultyRow.setRow("DIFFICULTY", model.difficulty().label().toUpperCase());
    }
}
