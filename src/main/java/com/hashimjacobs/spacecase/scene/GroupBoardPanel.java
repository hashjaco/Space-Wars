package com.hashimjacobs.spacecase.scene;

import java.util.function.Consumer;

import com.hashimjacobs.spacecase.net.GroupBoardModel;
import com.hashimjacobs.spacecase.net.GroupListing;
import com.hashimjacobs.spacecase.ui.Tokens;

import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 * The game board: three filters, the open groups, and the ways off the screen.
 *
 * Holds no rules; every row is written from {@link GroupBoardModel}, the same split as
 * {@link LobbyPanel} and for the same reason. The rows never change in number, only in text, so a
 * group opening or filling somewhere in the world rewrites a line rather than moving the cursor.
 */
final class GroupBoardPanel extends VBox {

    private static final int FILTER_ROWS = 3;

    private final GroupBoardModel model;
    private final MenuButton galaxyRow;
    private final MenuButton difficultyRow;
    private final MenuButton seatsRow;
    private final MenuButton[] groupRows = new MenuButton[GroupBoardModel.ROWS];
    private final Text status = MenuScreen.caption("", Tokens.SIZE_SMALL, Tokens.TEXT_FAINT);
    private final MenuNavigator navigator;
    private int focused = FILTER_ROWS;

    /**
     * @param onJoin   run with the group on a pressed row that has a seat free
     * @param onCreate opens the form for a group of the pilot's own
     * @param onCode   joins a private group by the code its host read out
     */
    GroupBoardPanel(GroupBoardModel model, Consumer<GroupListing> onJoin, Runnable onCreate,
                    Runnable onCode, Runnable onBack) {
        super(Tokens.GAP_L);
        this.model = model;

        galaxyRow = filter(model::cycleGalaxy);
        difficultyRow = filter(model::cycleDifficulty);
        seatsRow = filter(direction -> model.toggleOpenOnly());

        MenuButton[] rows = new MenuButton[FILTER_ROWS + groupRows.length + 3];
        rows[0] = galaxyRow;
        rows[1] = difficultyRow;
        rows[2] = seatsRow;
        for (int i = 0; i < groupRows.length; i++) {
            int row = i;
            groupRows[i] = new MenuButton("", () -> model.at(row)
                    .filter(GroupListing::isOpen).ifPresent(onJoin));
            rows[FILTER_ROWS + i] = groupRows[i];
        }
        rows[rows.length - 3] = new MenuButton("Create Group", onCreate);
        rows[rows.length - 2] = new MenuButton("Join by Code", onCode);
        rows[rows.length - 1] = new MenuButton("Back", onBack);

        MenuPanel panel = new MenuPanel(rows);
        navigator = panel.navigator();
        navigator.setOnBack(onBack);
        // On the first group rather than on a filter: the list is what the screen is for.
        navigator.focus(focused);
        navigator.alsoOnFocusMoved(row -> {
            focused = row;
            refresh();
        });
        status.setWrappingWidth(Tokens.ROW_WIDTH);
        getChildren().addAll(panel, status);
        refresh();
    }

    /** A row that steps with Left and Right, and forward with Enter. Asks the relay again after. */
    private MenuButton filter(java.util.function.IntConsumer cycle) {
        MenuButton row = new MenuButton("", () -> changed(cycle, 1));
        row.setOnAdjust(direction -> changed(cycle, direction));
        return row;
    }

    private void changed(java.util.function.IntConsumer cycle, int direction) {
        cycle.accept(direction);
        model.refreshNow();
        refresh();
    }

    MenuNavigator navigator() {
        return navigator;
    }

    /** Rewrites every row from the model. Called when a list arrives and when the cursor moves. */
    void refresh() {
        galaxyRow.setRow("GALAXY", model.galaxyLabel());
        difficultyRow.setRow("DIFFICULTY", model.difficultyLabel());
        seatsRow.setRow("SHOW", model.openOnlyLabel());
        for (int i = 0; i < groupRows.length; i++) {
            GroupListing listing = model.at(i).orElse(null);
            groupRows[i].setRow(listing == null ? "" : listing.host(),
                    listing == null ? "" : listing.value());
            // A full group stays on the board, dimmed: it says the level is being flown, and a
            // seat in it may open at any moment.
            groupRows[i].setLocked(!model.canJoin(i));
        }
        GroupListing under = model.at(focused - FILTER_ROWS).orElse(null);
        status.setText(under == null ? model.status() : under.detail());
    }
}
