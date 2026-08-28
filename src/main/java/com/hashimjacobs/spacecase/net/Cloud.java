package com.hashimjacobs.spacecase.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.hashimjacobs.spacecase.mode.GameMode;

/**
 * The other half of the relay: the score board, and the shelf a profile is kept on.
 *
 * Request and response, where {@link RelayClient} is a socket. Nothing in here is on the path of a
 * running game -- a board is read on a menu screen and a score is posted after one -- so this is
 * plain blocking HTTP and the caller's job is only to keep it off the JavaFX thread, the same way
 * {@code SceneRouter} already keeps {@code RelayClient.join} off it.
 *
 * <h2>No JSON, again</h2>
 *
 * A submitted score is four comma-separated fields and a board is ten {@code name,score} lines,
 * which is the encoding {@code SaveSlot}, {@code Loadout} and the relay's control frames all
 * already use. The game still has no JSON parser in it and still does not need one.
 *
 * <h2>What a board can and cannot mean</h2>
 *
 * Every score in this game is computed on the machine that flew the run, so the board records what
 * players report rather than what they did. That is not a gap left to be closed later: closing it
 * means the server simulating the game, which is the host-authoritative design the netcode was
 * deliberately not built as. An account system would not help either -- it would only put a name
 * against a number that was already unverifiable. The board is a scoreboard among friends.
 */
public final class Cloud {

    /** One row of the board. */
    public record Entry(String name, int score) {
    }

    /**
     * Long enough for a cold Durable Object to wake, short enough that a menu is not held open by
     * a network that is not there. The same ten seconds {@code RelayClient} waits for a room.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private Cloud() {
    }

    /**
     * Posts a run and answers with where it stands on the board, counting from one.
     *
     * @param player the account's public id -- never its sync code, which is a credential
     * @return the rank, or 0 if the board could not be reached; a score is not worth an error
     *         screen, and the run has already been recorded locally by {@code prefs.HighScores}
     */
    public static int submit(URI base, String player, GameMode mode, String name, int score) {
        String body = player + "," + mode.name() + "," + name + "," + score;
        try {
            HttpResponse<String> response = send(HttpRequest.newBuilder(base.resolve("/score"))
                    .POST(HttpRequest.BodyPublishers.ofString(body)));
            return response.statusCode() == 200 ? Integer.parseInt(response.body().trim()) : 0;
        } catch (IOException | NumberFormatException unreachable) {
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    /**
     * The top of one mode's board, best first.
     *
     * An unreachable board reads as an empty one. The screen says so in a caption rather than
     * failing: a menu that cannot open because a server is down is worse than a menu with nothing
     * in it.
     */
    public static List<Entry> top(URI base, GameMode mode) {
        try {
            HttpResponse<String> response =
                    send(HttpRequest.newBuilder(base.resolve("/top/" + mode.name())).GET());
            return response.statusCode() == 200 ? parse(response.body()) : List.of();
        } catch (IOException unreachable) {
            return List.of();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** Uploads a {@code prefs.Profile} under this account's sync code, replacing what was there. */
    public static void push(URI base, String syncCode, String profile)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(saveUri(base, syncCode))
                .PUT(HttpRequest.BodyPublishers.ofString(profile)));
        if (response.statusCode() != 200) {
            throw new IOException("the profile would not upload: " + response.statusCode());
        }
    }

    /**
     * Downloads the profile kept under a sync code.
     *
     * @return the encoded profile, or null when nothing has ever been uploaded under that code --
     *         which is the ordinary answer to a mistyped one, and is worth telling apart from a
     *         failure so the screen can say "no profile there" rather than "something went wrong"
     */
    public static String pull(URI base, String syncCode) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(saveUri(base, syncCode)).GET());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("the profile would not download: " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Reads {@code name,score} lines into rows, skipping any it cannot.
     *
     * Package-private and pure, which is the whole of what {@code CloudTest} needs: the parsing is
     * the only part of this class with a decision in it, and it can be checked without a server.
     */
    static List<Entry> parse(String body) {
        List<Entry> board = new ArrayList<>();
        for (String line : body.split("\n")) {
            String[] fields = line.trim().split(",");
            if (fields.length != 2) {
                continue;
            }
            try {
                board.add(new Entry(fields[0], Integer.parseInt(fields[1].trim())));
            } catch (NumberFormatException notARow) {
                // One unreadable row costs that row, not the board. Same bargain the relay's
                // control frames make with a line an older build does not recognise.
            }
        }
        return List.copyOf(board);
    }

    private static URI saveUri(URI base, String syncCode) {
        return base.resolve("/save/" + syncCode);
    }

    /**
     * One request, with the client shut when it is done.
     *
     * Closing is safe here in a way it is not for a socket: {@code RelayClient.close} avoids
     * {@code HttpClient.close()} because it blocks until the relay answers a close frame and every
     * in-flight send has drained, which on a live WebSocket is seconds of nothing. A client with
     * one finished request and response has nothing left to drain. This is the same shape
     * {@code RelayClient.newRoom} already uses to ask for a room code.
     */
    private static HttpResponse<String> send(HttpRequest.Builder request)
            throws IOException, InterruptedException {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request.timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
