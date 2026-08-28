package com.hashimjacobs.spacecase.scene;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import com.hashimjacobs.spacecase.net.Cloud;
import com.hashimjacobs.spacecase.net.RelayClient;
import com.hashimjacobs.spacecase.prefs.Account;
import com.hashimjacobs.spacecase.prefs.Profile;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Moving a campaign between a player's two machines.
 *
 * The whole of the flow is two buttons and a code. Upload on the machine that has the runs, read
 * the code off it, type it here, download. There is no sign-in because there is nobody to sign in
 * as: the code {@link Account} generated on first run is the entire credential, and it is a
 * capability rather than an identity -- whoever has it has the profile, which is exactly the
 * property wanted for something you read off your own screen and type into your own laptop.
 *
 * <h2>Threads</h2>
 *
 * Both buttons block on a server. That runs off the JavaFX thread and comes back through
 * {@link Platform#runLater}, which is the one-shot version of the bargain {@code SceneRouter}
 * makes for the lobby -- the lobby polls on a timer because it also has packets to drain every
 * frame, and this has one answer to wait for.
 *
 * Restoring is not done here. A download that lands is handed to the router, which asks before
 * writing over anything: this panel can say what happened, but only a screen can ask a question.
 */
final class CloudPanel extends VBox {

    private final Account account;
    private final Consumer<String> onDownloaded;

    private final MenuButton codeRow;
    private final MenuButton typedRow;
    private final MenuButton uploadRow;
    private final MenuButton downloadRow;
    private final MenuNavigator navigator;
    private final Text status;

    /** What has been typed into the other-code row, unpunctuated. */
    private final StringBuilder typed = new StringBuilder();

    /** Set while a request is out, so neither button can be pressed twice into the same answer. */
    private boolean busy;

    /**
     * @param onDownloaded handed an encoded {@code prefs.Profile} that arrived intact; the router
     *                     confirms before writing it, because this overwrites a campaign
     */
    CloudPanel(Account account, Consumer<String> onDownloaded, Runnable onBack) {
        super(Tokens.GAP_L);
        this.account = account;
        this.onDownloaded = onDownloaded;
        setAlignment(Pos.CENTER);

        codeRow = new MenuButton("", () -> { });
        codeRow.setLocked(true);
        typedRow = new MenuButton("", () -> { });
        uploadRow = new MenuButton("Upload this machine", this::upload);
        downloadRow = new MenuButton("Download to this machine", this::download);
        MenuButton backRow = new MenuButton("Back", onBack);

        MenuPanel panel = new MenuPanel(codeRow, typedRow, uploadRow, downloadRow, backRow);
        status = MenuScreen.caption("", 12, Tokens.TEXT_FAINT);
        getChildren().addAll(panel, status);

        navigator = panel.navigator();
        navigator.setOnBack(onBack);
        // On Upload rather than row one, which is the code this machine already has and cannot be
        // pressed. Upload is also the first half of the flow: it is what the machine holding the
        // campaign does, and the machine receiving one arrives here to type into the row above.
        navigator.focus(2);
        refresh();
    }

    /**
     * Typing is offered the key first, for the reason it is on every other screen that types: the
     * navigator claims W and S to walk the rows, and both are characters a code can contain.
     */
    boolean handleKey(KeyCode code) {
        boolean handled = type(code) || navigator.handleKey(code);
        if (handled) {
            refresh();
        }
        return handled;
    }

    private boolean type(KeyCode code) {
        // Only while the other-code row is the focused one. Everywhere else a letter is a letter
        // the menu wants, and typing into a row nobody is looking at is how a code gets half
        // entered without anybody noticing.
        if (navigator.focusedIndex() != 1 || busy) {
            return false;
        }
        if (code == KeyCode.BACK_SPACE) {
            if (typed.length() > 0) {
                typed.deleteCharAt(typed.length() - 1);
            }
            return true;
        }
        // KeyCode.getName() rather than the event's character, so a gamepad-synthesised press --
        // which carries no character at all -- still types. The same filter LobbyModel applies.
        String key = code.getName();
        if (!((code.isLetterKey() || code.isDigitKey()) && key.length() == 1)) {
            return false;
        }
        char character = Character.toUpperCase(key.charAt(0));
        if (!Account.isCodeCharacter(character)) {
            // Not in the alphabet. Swallowed rather than passed on, because a player pressing O
            // where they meant zero is aiming at this field and not at the menu behind it.
            return true;
        }
        if (typed.length() < Account.CODE_LENGTH) {
            typed.append(character);
        }
        return true;
    }

    private void upload() {
        if (busy) {
            return;
        }
        // Exported on this thread, deliberately: it is a preference read, and doing it here means
        // what gets uploaded is what was on screen when the button was pressed.
        String profile = Profile.export();
        String code = account.code();
        run("Uploading...", () -> {
            Cloud.push(RelayClient.defaultBase(), code, profile);
            return new Answer("Uploaded. Type this code on the other machine.", null);
        });
    }

    private void download() {
        if (busy) {
            return;
        }
        // A typed code is adopted before it is used, so a machine that has pulled a profile keeps
        // pulling and pushing the same one instead of asking for the code again every time.
        if (typed.length() == Account.CODE_LENGTH && !account.adopt(typed.toString())) {
            say("That is not a sync code.");
            return;
        }
        if (typed.length() > 0 && typed.length() < Account.CODE_LENGTH) {
            say("A sync code is " + Account.CODE_LENGTH + " characters.");
            return;
        }
        URI base = RelayClient.defaultBase();
        String code = account.code();
        run("Downloading...", () -> {
            String profile = Cloud.pull(base, code);
            return profile == null
                    ? new Answer("There is no profile under that code yet.", null)
                    : new Answer("", profile);
        });
    }

    /**
     * What one request came back with: a line for the caption, and a profile if it brought one.
     *
     * The profile travels back rather than being acted on where it arrives, because it arrives on
     * a background thread and everything that would be done with it -- showing a confirmation,
     * writing preferences the menus are reading -- belongs to the JavaFX one.
     */
    private record Answer(String message, String profile) {
    }

    /** One request: say what is happening, do it off the thread, act on it back on the thread. */
    private void run(String pending, Attempt attempt) {
        busy = true;
        say(pending);
        refresh();
        CompletableFuture.supplyAsync(() -> {
            try {
                return attempt.get();
            } catch (IOException failed) {
                // The message, not a fixed line about the network: the server refuses an oversized
                // profile with a 413 and Cloud says so, which a "could not reach the server" would
                // have thrown away in favour of a guess about what went wrong.
                String said = failed.getMessage();
                return new Answer(said == null || said.isBlank()
                        ? "Could not reach the server." : said, null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new Answer("Interrupted.", null);
            }
        }).thenAccept(answer -> Platform.runLater(() -> {
            busy = false;
            say(answer.message());
            refresh();
            if (answer.profile() != null) {
                onDownloaded.accept(answer.profile());
            }
        }));
    }

    /** Something that talks to the server and reports back. */
    @FunctionalInterface
    private interface Attempt {
        Answer get() throws IOException, InterruptedException;
    }

    private void say(String message) {
        status.setText(message);
    }

    private void refresh() {
        codeRow.setRow("THIS MACHINE", Account.grouped(account.code()));
        typedRow.setRow("OTHER CODE", Account.grouped(pad(typed.toString())));
        uploadRow.setLocked(busy);
        downloadRow.setLocked(busy);
    }

    /** Underscores for what is not typed, so the field reads as twelve slots rather than one. */
    private static String pad(String entered) {
        StringBuilder shown = new StringBuilder(entered);
        while (shown.length() < Account.CODE_LENGTH) {
            shown.append('_');
        }
        return shown.toString();
    }
}
