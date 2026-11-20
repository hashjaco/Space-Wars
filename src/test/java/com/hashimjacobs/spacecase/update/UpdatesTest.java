package com.hashimjacobs.spacecase.update;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The release path end to end against a local server: check, download, refuse a bad file. */
class UpdatesTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private URI list;

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = files.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        list = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest/update.txt");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Updates jarAt(Path jar) {
        return new Updates(new Installation(Installation.Kind.JAR, "jar-linux", jar), list);
    }

    @Test
    void theSameVersionIsNoOffer() throws IOException {
        files.put("/latest/update.txt", "version 0.1\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(jarAt(dir.resolve("game.jar")).check().isEmpty());
    }

    @Test
    void aNewerReleaseIsDownloadedAndChecked() throws Exception {
        byte[] jar = "the new game".getBytes(StandardCharsets.UTF_8);
        files.put("/latest/space-case-linux.jar", jar);
        files.put("/latest/update.txt", ("version 999.0\njar-linux space-case-linux.jar " + sha(jar) + "\n")
                .getBytes(StandardCharsets.UTF_8));

        Updates updates = jarAt(dir.resolve("game.jar"));
        Updates.Offer offer = updates.check().orElseThrow();
        assertEquals("Update to 999.0", offer.headline());
        assertTrue(offer.installable());
        assertTrue(updates.known().isPresent());

        double[] last = {0};
        updates.prepare(offer, fraction -> last[0] = fraction);
        assertTrue(offer.prepared());
        assertEquals(1.0, last[0], 1e-9);
        Path downloaded = Path.of(System.getProperty("java.io.tmpdir"), "space-case-update", "space-case-linux.jar");
        assertArrayEquals(jar, Files.readAllBytes(downloaded));
        updates.cleanUp();
        assertFalse(Files.exists(downloaded));
    }

    @Test
    void aFileThatDoesNotMatchItsChecksumIsThrownAway() throws Exception {
        files.put("/f.bin", "tampered".getBytes(StandardCharsets.UTF_8));
        Path target = dir.resolve("f.bin");
        URI url = list.resolve("/f.bin");
        IOException refused = assertThrows(IOException.class,
                () -> Download.fetch(url, target, sha("original".getBytes(StandardCharsets.UTF_8)), f -> { }));
        assertTrue(refused.getMessage().contains("checksum"));
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(dir.resolve("f.bin.part")));
    }

    @Test
    void aReleaseWithNothingForThisMachineSaysWhereToGetIt() throws IOException {
        files.put("/latest/update.txt", "version 999.0\n".getBytes(StandardCharsets.UTF_8));
        Updates.Offer offer = jarAt(dir.resolve("game.jar")).check().orElseThrow();
        assertFalse(offer.installable());
        assertTrue(offer.whyNot().contains(Updates.RELEASE_PAGE));
    }

    @Test
    void noListIsAnErrorNotAnOffer() {
        assertThrows(IOException.class, () -> jarAt(dir.resolve("game.jar")).check());
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
