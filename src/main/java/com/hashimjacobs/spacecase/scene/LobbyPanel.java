package com.hashimjacobs.spacecase.scene;

import java.util.List;

import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import com.hashimjacobs.spacecase.net.LobbyModel;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * The room: the code to read out, who is in it, and the button that begins the fight.
 *
 * Holds no rules. Every question it answers -- what has been typed, who is here, whether the host
 * may start -- is put to {@link LobbyModel}, which is why that class can be tested without a
 * toolkit and this one needs no test at all. Same split as {@code SystemMapModel} and
 * {@code SystemMapView}.
 *
 * The rows never change shape, only their text. Rebuilding the panel as peers arrive would throw
 * the player's cursor back to the top every time somebody joined.
 */
final class LobbyPanel extends VBox {

    private final LobbyModel model;
    private final Runnable onAct;

    private final MenuButton roomRow;
    private final MenuButton[] seatRows = new MenuButton[LobbyModel.MAX_PEERS];
    private final MenuButton actionRow;
    private final MenuPanel panel;
    private final MenuNavigator navigator;

    /** @param onAct run when the player presses the action row: join a room, or start the game */
    LobbyPanel(LobbyModel model, Runnable onAct, Runnable onBack) {
        super(Tokens.GAP_L);
        this.model = model;
        this.onAct = onAct;

        roomRow = new MenuButton("", () -> { });
        roomRow.setLocked(true);
        for (int i = 0; i < seatRows.length; i++) {
            seatRows[i] = new MenuButton("", () -> { });
            seatRows[i].setLocked(true);
        }
        actionRow = new MenuButton("", this::act);
        MenuButton backRow = new MenuButton("Back", onBack);

        MenuButton[] rows = new MenuButton[seatRows.length + 3];
        rows[0] = roomRow;
        System.arraycopy(seatRows, 0, rows, 1, seatRows.length);
        rows[rows.length - 2] = actionRow;
        rows[rows.length - 1] = backRow;

        panel = new MenuPanel(rows);
        navigator = panel.navigator();
        navigator.setOnBack(onBack);
        getChildren().add(panel);
        refresh();
    }

    MenuNavigator navigator() {
        return navigator;
    }

    /**
     * Typing gets first refusal, the same order {@code NameEntryPanel} uses and for the same
     * reason: the navigator claims W and S, which are also letters somebody may need to type.
     */
    boolean handleKey(KeyCode code) {
        boolean typed = model.handleKey(code);
        if (typed) {
            refresh();
            return true;
        }
        return navigator.handleKey(code);
    }

    /** Rewrites every row from the model. Cheap enough to call on every frame, and it is. */
    void refresh() {
        switch (model.state()) {
            case CHOOSING -> {
                roomRow.setRow("ROOM CODE", pad(model.typed()));
                actionRow.setText("Join");
                actionRow.setLocked(!model.codeIsComplete());
            }
            case CONNECTING -> {
                roomRow.setRow("ROOM CODE", pad(model.typed()));
                actionRow.setText("Connecting...");
                actionRow.setLocked(true);
            }
            case WAITING -> {
                roomRow.setRow("ROOM CODE", model.room());
                actionRow.setText(model.isHost() ? "Start" : "Waiting for the host");
                actionRow.setLocked(!model.canStart());
            }
            case STARTING -> {
                roomRow.setRow("ROOM CODE", model.room());
                actionRow.setText("Starting...");
                actionRow.setLocked(true);
            }
            case FAILED -> {
                roomRow.setRow("COULD NOT JOIN", model.failure());
                actionRow.setText("Try again");
                actionRow.setLocked(false);
            }
        }
        refreshSeats();
    }

    private void refreshSeats() {
        List<Integer> peers = model.peers();
        for (int i = 0; i < seatRows.length; i++) {
            int seat = i + 1;
            String who = !peers.contains(seat) ? "empty"
                    : seat == model.localSlot() ? "you"
                    : "ready";
            seatRows[i].setRow("PLAYER " + seat, who);
            // Locked either way -- a seat is something to look at, not something to press. The
            // difference is only that an occupied one is worth reading.
            seatRows[i].setLocked(true);
        }
    }

    private void act() {
        if (model.state() == LobbyModel.State.FAILED) {
            model.reset();
            refresh();
            return;
        }
        onAct.run();
    }

    /** Underscores for what has not been typed, so the field reads as four slots rather than one. */
    private static String pad(String typed) {
        StringBuilder shown = new StringBuilder(typed);
        while (shown.length() < LobbyModel.CODE_LENGTH) {
            shown.append('_');
        }
        return shown.toString();
    }
}
