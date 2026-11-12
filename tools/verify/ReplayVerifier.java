import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.hashimjacobs.spacecase.engine.Replayer;
import com.hashimjacobs.spacecase.net.Replay;

/**
 * Replays the recordings the relay is holding and tells it which scores they reproduce.
 *
 * Run by .github/workflows/verify.yml on a schedule, with the game's classes on the classpath:
 *
 *   mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *   VERIFY_SECRET=... java -cp target/classes:$(cat target/cp.txt) tools/verify/ReplayVerifier.java [relay base]
 *
 * A row is "ok" when the replay reaches the posted score for some seat, in the posted mode; "bad"
 * otherwise, including a recording that will not parse. A run resumed from a checkpoint is "skip":
 * it cannot be replayed from its seed, which makes it unverifiable rather than wrong, and the board
 * leaves it unmarked. Workers cannot run a JVM, which is why this
 * lives here rather than in server/.
 */
public final class ReplayVerifier {
    public static void main(String[] args) throws Exception {
        URI base = URI.create(args.length > 0 ? args[0] : "https://space-case-relay.hashimjacobs.workers.dev");
        String secret = System.getenv("VERIFY_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("VERIFY_SECRET is not set");
        }
        HttpClient http = HttpClient.newHttpClient();
        String pending = get(http, base.resolve("/replays/pending"), secret).body().trim();
        if (pending.isEmpty()) {
            System.out.println("nothing pending");
            return;
        }
        int ok = 0, bad = 0;
        for (String line : pending.split("\n")) {
            String[] fields = line.trim().split(",");
            if (fields.length != 3) {
                continue;
            }
            String player = fields[0], mode = fields[1];
            int score = Integer.parseInt(fields[2]);
            HttpResponse<byte[]> blob = http.send(HttpRequest.newBuilder(base.resolve("/replay/" + player + "/" + mode))
                    .header("Authorization", "Bearer " + secret).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            String verdict;
            try {
                Replay.Parsed parsed = Replay.read(blob.body());
                if (parsed.header().resumed()) {
                    verdict = "skip";
                    System.out.println(player + " " + mode + " posted " + score + ", resumed from a checkpoint -> skip");
                } else {
                    Replayer.Outcome outcome = Replayer.run(parsed);
                    List<Integer> scores = outcome.scores();
                    verdict = parsed.header().mode().name().equals(mode) && scores.contains(score) ? "ok" : "bad";
                    System.out.println(player + " " + mode + " posted " + score + ", replay reached " + scores + " -> " + verdict);
                }
            } catch (IOException | RuntimeException unreadable) {
                verdict = "bad";
                System.out.println(player + " " + mode + " unreadable: " + unreadable.getMessage() + " -> bad");
            }
            http.send(HttpRequest.newBuilder(base.resolve("/verify/" + player + "/" + mode))
                    .header("Authorization", "Bearer " + secret)
                    .POST(HttpRequest.BodyPublishers.ofString(verdict)).build(), HttpResponse.BodyHandlers.discarding());
            if (verdict.equals("ok")) {
                ok++;
            } else if (verdict.equals("bad")) {
                bad++;
            }
        }
        System.out.println(ok + " verified, " + bad + " rejected");
    }

    private static HttpResponse<String> get(HttpClient http, URI uri, String secret) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + secret).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
