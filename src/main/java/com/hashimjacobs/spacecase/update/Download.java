package com.hashimjacobs.spacecase.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.DoubleConsumer;

import com.hashimjacobs.spacecase.net.Http;

/**
 * Fetching a release's file, and refusing it unless it is exactly the file the manifest names.
 *
 * The SHA-256 is checked as the bytes arrive, into a {@code .part} file beside the target, and only
 * a match is moved onto the target name. A mismatch -- a truncated download, a proxy's error page,
 * or anything else -- is deleted and never reaches an installer.
 */
public final class Download {

    /** How long to wait for the first byte. The body then takes as long as it takes. */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private Download() {
    }

    /** The plain text at {@code url}, for {@code update.txt}. Follows GitHub's redirects. */
    static String text(URI url) throws IOException {
        HttpClient http = client();
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(url).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("the update list answered " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", interrupted);
        } finally {
            Http.close(http);
        }
    }

    /**
     * Downloads {@code url} to {@code target}, checking it against {@code sha256}.
     *
     * @param progress told 0..1 as the file arrives, or -1 when the server gave no length
     * @throws IOException if it could not be fetched or is not the file the manifest names
     */
    public static Path fetch(URI url, Path target, String sha256, DoubleConsumer progress) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpClient http = client();
        try {
            HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(url).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("the download answered " + response.statusCode());
            }
            long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            MessageDigest digest = sha256();
            try (InputStream in = new DigestInputStream(response.body(), digest);
                 OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    done += read;
                    progress.accept(length > 0 ? Math.min(1.0, done / (double) length) : -1);
                }
            }
            String got = HexFormat.of().formatHex(digest.digest());
            if (!got.equalsIgnoreCase(sha256)) {
                Files.deleteIfExists(part);
                throw new IOException("the download did not match its checksum, so it was thrown away");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(part);
            throw new IOException("interrupted", interrupted);
        } catch (IOException failed) {
            Files.deleteIfExists(part);
            throw failed;
        } finally {
            Http.close(http);
        }
    }

    private static HttpClient client() {
        // GitHub answers a release download with a redirect to its file store.
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every Java runtime has SHA-256", impossible);
        }
    }
}
