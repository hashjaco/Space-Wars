package com.hashimjacobs.spacecase.net;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The socket to the relay, and the only class in the game that knows there is a network.
 *
 * Everything above it -- {@link Lockstep}, the loop, the simulation -- deals in byte arrays and
 * cannot tell a real match from the two peers wired to each other in {@code LockstepTest}. Kept
 * that way deliberately: the netcode is testable in milliseconds precisely because the transport is
 * this thin and this replaceable.
 *
 * Uses the JDK's own WebSocket, so the game gains no dependency for going online.
 *
 * <h2>Threads</h2>
 *
 * The one thing a caller has to get right. Frames arrive on the {@link HttpClient}'s executor,
 * while the simulation runs on the JavaFX thread, and {@link Lockstep} is deliberately not
 * thread-safe -- it feeds a deterministic simulation, and a lock on that path would be a lock on
 * every tick. So arriving packets are parked in a queue here and handed over only when the game
 * asks, on the game's own thread, through {@link #drainTo}. Call it once before each gate check;
 * nothing else may touch a {@code Lockstep} from anywhere else.
 */
public final class RelayClient implements AutoCloseable {

    /**
     * Where the relay lives.
     *
     * A constant rather than a setting: a player has no way to know another relay's address and no
     * reason to want one, and a wrong value here is a game that cannot find anybody. The system
     * property is for pointing the suite at {@code wrangler dev} on a laptop, and is the same
     * property {@code RelayIntegrationTest} already reads.
     */
    public static URI defaultBase() {
        return URI.create(System.getProperty("relay.base",
                "https://space-case-relay.hashimjacobs.workers.dev"));
    }

    /** How long a join waits for the socket and the relay's first word before giving up. */
    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(10);

    /** Whoever opens a group is seated first, and the first seat is slot one. */
    private static final int HOST_SLOT = 1;

    /**
     * What the relay says when a peer arrives, including this one.
     *
     * @param slot  the player number this machine flies, one to four
     * @param room  the code others type to join
     * @param peers every slot currently in the room, this one included, ascending
     */
    public record Welcome(int slot, String room, List<Integer> peers) {

        /** The lowest slot present hosts: it sends the seed and the level, and nothing else differs. */
        public boolean isHost() {
            return !peers.isEmpty() && peers.get(0) == slot;
        }
    }

    private final HttpClient http;
    private final URI base;
    private volatile WebSocket socket;
    private final Welcome welcome;
    private final Queue<byte[]> inbox;
    private final Roster roster;
    private final Link link;
    /** Who this pilot says they are, kept so a reconnect can say it again. */
    private final String identity;
    /** The newest frame handed to the socket; see {@link #queue}. */
    private CompletableFuture<?> lastSend = CompletableFuture.completedFuture(null);

    private RelayClient(HttpClient http, URI base, WebSocket socket, Welcome welcome,
                        Queue<byte[]> inbox, Roster roster, Link link, String identity) {
        this.http = http;
        this.base = base;
        this.socket = socket;
        this.welcome = welcome;
        this.inbox = inbox;
        this.roster = roster;
        this.link = link;
        this.identity = identity;
    }

    /** True while the socket is up. False from the moment it closes until {@link #reconnect()} succeeds. */
    public boolean isConnected() {
        return link.up;
    }

    /** The token the relay issued for this seat, which is what lets a dropped socket come back as it. */
    public String token() {
        return link.token;
    }

    /**
     * Opens a fresh socket to the same room and seat. Synchronous; call it off the FX thread. The
     * relay honours the token inside its reclaim window, and the seat it answers with has to be
     * ours -- a different one means the window closed and this match has moved on without us.
     */
    public void reconnect() throws IOException, InterruptedException {
        CompletableFuture<Welcome> welcomed = new CompletableFuture<>();
        Frames frames = new Frames(inbox, roster, welcomed, link);
        URI uri = URI.create(socketUri(base, welcome.room()) + "?slot=" + welcome.slot()
                + "&token=" + link.token + (identity.isEmpty() ? "" : "&" + identity));
        WebSocket fresh;
        try {
            fresh = http.newWebSocketBuilder().connectTimeout(JOIN_TIMEOUT)
                    .buildAsync(uri, frames).get(JOIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            Welcome again = welcomed.get(JOIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (again.slot() != welcome.slot()) {
                fresh.abort();
                throw new IOException("the room gave our seat away");
            }
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("could not rejoin room " + welcome.room() + ": " + rootCause(e), e);
        }
        socket = fresh;
        link.up = true;
    }

    /** Asks the relay for an unused room code. The host does this, then reads it out. */
    public static String newRoom(URI base) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newHttpClient();
        try {
            HttpRequest request = HttpRequest.newBuilder(base.resolve("/new"))
                    .timeout(JOIN_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(
                        "the relay would not issue a room code: " + response.statusCode());
            }
            return response.body().trim();
        } finally {
            Http.close(http);
        }
    }

    /**
     * Joins a room and waits for the relay to say which slot this machine holds.
     *
     * @param base the relay's http or https address; the socket goes to the ws or wss equivalent
     * @throws IOException if the room is full, does not answer, or answers with something else
     */
    public static RelayClient join(URI base, String room) throws IOException, InterruptedException {
        return join(base, room, null, null);
    }

    /**
     * Joins saying who this pilot is, so the others see a name in the lobby rather than a seat
     * number and the relay knows whose help to write down.
     *
     * @param name     the pilot's name, or null to send none
     * @param playerId the pilot's public board id -- {@code Account.id()}, never the sync code,
     *                 which is a credential -- or null to send none
     */
    public static RelayClient join(URI base, String room, String name, String playerId)
            throws IOException, InterruptedException {
        String identity = identity(name, playerId);
        Queue<byte[]> inbox = new ConcurrentLinkedQueue<>();
        Roster roster = new Roster();
        Link link = new Link();
        CompletableFuture<Welcome> welcomed = new CompletableFuture<>();

        HttpClient http = HttpClient.newHttpClient();
        WebSocket socket;
        try {
            socket = http.newWebSocketBuilder()
                    .connectTimeout(JOIN_TIMEOUT)
                    .buildAsync(URI.create(socketUri(base, room) + (identity.isEmpty() ? "" : "?" + identity)),
                            new Frames(inbox, roster, welcomed, link))
                    .get(JOIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // The relay refuses a fifth peer before the upgrade, so a full room arrives here as a
            // failed handshake rather than as a socket that opens and shuts.
            throw new IOException("could not join room " + room + ": " + rootCause(e), e);
        } catch (TimeoutException e) {
            throw new IOException("the relay did not answer within " + JOIN_TIMEOUT, e);
        }

        try {
            Welcome welcome = welcomed.get(JOIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            link.up = true;
            return new RelayClient(http, base, socket, welcome, inbox, roster, link, identity);
        } catch (ExecutionException | TimeoutException e) {
            socket.abort();
            throw new IOException("joined room " + room + " but it never said which slot", e);
        }
    }

    /** This machine's slot and the room it is in, as of joining. */
    public Welcome welcome() {
        return welcome;
    }

    /** Every slot in the room right now, ascending. Changes as people come and go. */
    public List<Integer> peers() {
        return roster.current();
    }

    /** Each seated pilot's name by slot. A pilot whose game sent none is absent. */
    public Map<Integer, String> names() {
        return roster.names;
    }

    /** What this room was opened for, or empty for a room that is only a code. */
    public Optional<GroupInfo> group() {
        return Optional.ofNullable(roster.group);
    }

    /** Slots whose pilots said they were leaving, as opposed to losing their connection. */
    public Set<Integer> left() {
        return roster.left;
    }

    /** Whether the host has gone and taken the group with them. Nothing more will arrive. */
    public boolean closed() {
        return roster.closed;
    }

    /**
     * Whether this machine names each level's terms.
     *
     * In a group that is whoever opened it, who is always seated first. Reading it off the roster,
     * as a bare room does, would crown a newcomer who arrived while the host's connection was down.
     */
    public boolean isHost() {
        return roster.group != null ? welcome.slot() == HOST_SLOT : welcome.isHost();
    }

    /**
     * Says something to the relay itself rather than to the other pilots: {@code leave},
     * {@code state,...} or {@code cleared}. Text is the relay's own channel and is never forwarded.
     *
     * @return when the line has been handed to the socket, for a caller that must not close first
     */
    public CompletableFuture<?> sendControl(String line) {
        if (!link.up) {
            return CompletableFuture.completedFuture(null);
        }
        return queue(() -> socket.sendText(line, true));
    }

    /**
     * Leaves on purpose: the seat is free at once rather than held for a minute, and the others
     * are told it was a decision. Then closes, once the word has actually gone.
     */
    public void leave() {
        sendControl("leave").whenComplete((sent, failed) -> close());
    }

    /** Wire this to {@code Lockstep}'s byte sink. Non-blocking. */
    public void send(byte[] packet) {
        if (!link.up) {
            return; // the re-feed after reconnect covers what was not sent
        }
        queue(() -> socket.sendBinary(ByteBuffer.wrap(packet), true));
    }

    /**
     * Sends one frame after the one before it has gone.
     *
     * The socket refuses a send while another is outstanding and reports it by failing the future
     * -- which, unread, is a packet silently never sent, and in a lockstep one missing input is a
     * stalled match. A send usually completes before the call returns, so this is normally a
     * formality; it matters on the frame that sends several, and for a goodbye that must not be
     * lost behind an input.
     */
    private synchronized CompletableFuture<?> queue(Supplier<CompletableFuture<WebSocket>> frame) {
        CompletableFuture<?> sent = lastSend
                // Whatever became of the frame before, this one still goes.
                .handle((done, failed) -> null)
                .thenCompose(ready -> {
                    try {
                        return frame.get();
                    } catch (IllegalStateException closed) {
                        link.up = false;
                        return CompletableFuture.completedFuture(null);
                    }
                });
        lastSend = sent;
        return sent;
    }

    /**
     * Hands every packet that has arrived to the sink, on the calling thread.
     *
     * Call this on the thread that owns the {@link Lockstep}, immediately before asking it whether
     * a tick may run. That is the whole of the thread contract.
     */
    public void drainTo(Consumer<byte[]> sink) {
        for (byte[] packet = inbox.poll(); packet != null; packet = inbox.poll()) {
            sink.accept(packet);
        }
    }

    /**
     * Says goodbye and stops waiting.
     *
     * Deliberately not {@code HttpClient.close()}, which blocks until the relay's answering close
     * frame arrives and every in-flight send has drained. On a socket the game is walking away from
     * that is seconds of nothing, and it made the integration suite take a minute. The relay treats
     * an abrupt close the same as a polite one -- it frees the slot and tells the room either way --
     * so there is nothing to wait for.
     */
    @Override
    public void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        Http.shutdownNow(http);
    }

    /** The query that says who is joining, or "" for nobody in particular. */
    private static String identity(String name, String playerId) {
        StringBuilder query = new StringBuilder();
        if (name != null && !name.isBlank()) {
            query.append("name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8));
        }
        if (playerId != null && !playerId.isBlank()) {
            query.append(query.isEmpty() ? "" : "&").append("player=")
                    .append(URLEncoder.encode(playerId, StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private static String socketUri(URI base, String room) {
        String scheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
        return scheme + "://" + base.getAuthority() + "/room/" + room;
    }

    /** What survives a socket: whether one is up right now, and the token that reopens the seat. */
    private static final class Link {
        volatile boolean up;
        volatile String token = "";
    }

    private static String rootCause(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    /** The roster, written by the socket thread and read by the game's. */
    private static final class Roster {

        private volatile List<Integer> peers = List.of();
        private volatile Map<Integer, String> names = Map.of();
        private volatile GroupInfo group;
        private volatile boolean closed;
        /** Concurrent, because the socket's thread adds while the game's thread reads. */
        private final Set<Integer> left = ConcurrentHashMap.newKeySet();

        void set(List<Integer> peers) {
            this.peers = List.copyOf(peers);
            // A slot on the roster again is a new pilot in a seat somebody left, not the leaver.
            left.removeAll(peers);
        }

        List<Integer> current() {
            return peers;
        }
    }

    /**
     * Turns frames into queue entries, and nothing more.
     *
     * Text is the relay talking and binary is another peer talking, a split the relay enforces by
     * never forwarding a client's text. So nothing a peer sends can be mistaken for the relay.
     */
    private static final class Frames implements WebSocket.Listener {

        private final Queue<byte[]> inbox;
        private final Roster roster;
        private final CompletableFuture<Welcome> welcomed;

        /**
         * A WebSocket message may arrive in pieces, and one intent split across two frames is two
         * unparseable halves. Both handlers accumulate until {@code last}.
         */
        private final List<ByteBuffer> binaryParts = new ArrayList<>();
        private final StringBuilder textParts = new StringBuilder();

        private final Link link;

        Frames(Queue<byte[]> inbox, Roster roster, CompletableFuture<Welcome> welcomed, Link link) {
            this.inbox = inbox;
            this.roster = roster;
            this.welcomed = welcomed;
            this.link = link;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            // Copied, not retained: the buffer belongs to the socket and is reused after this
            // returns, so keeping it would hand the game somebody else's next packet.
            ByteBuffer copy = ByteBuffer.allocate(data.remaining());
            copy.put(data).flip();
            binaryParts.add(copy);

            if (last) {
                int size = binaryParts.stream().mapToInt(ByteBuffer::remaining).sum();
                ByteBuffer whole = ByteBuffer.allocate(size);
                binaryParts.forEach(whole::put);
                binaryParts.clear();
                inbox.add(whole.array());
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            textParts.append(data);
            if (last) {
                accept(textParts.toString());
                textParts.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            link.up = false;
            welcomed.completeExceptionally(error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            link.up = false;
            welcomed.completeExceptionally(
                    new IOException("the relay closed the connection: " + status + " " + reason));
            return null;
        }

        /**
         * One control line: {@code welcome,<slot>,<room>,<slot>...} or {@code roster,<slot>...}.
         *
         * The same comma-separated idiom the save format uses, which is why there is no JSON
         * library anywhere near this. A line this client does not recognise is ignored rather than
         * fatal, so the relay can grow a new one without every old build refusing to play.
         */
        private void accept(String line) {
            String[] fields = line.split(",");
            switch (fields[0]) {
                case "welcome" -> {
                    List<Integer> peers = slots(fields, 3);
                    roster.set(peers);
                    welcomed.complete(new Welcome(
                            Integer.parseInt(fields[1]), fields[2], peers));
                }
                case "roster" -> roster.set(slots(fields, 1));
                case "token" -> link.token = fields.length > 1 ? fields[1] : "";
                case "names" -> roster.names = names(fields);
                case "group" -> GroupInfo.parse(fields).ifPresent(group -> roster.group = group);
                case "left" -> slots(fields, 1).forEach(roster.left::add);
                case "closed" -> roster.closed = true;
                default -> {
                }
            }
        }

        /** {@code names,<slot>,<name>,...}; a pilot with no name is left out. */
        private static Map<Integer, String> names(String[] fields) {
            Map<Integer, String> names = new HashMap<>();
            for (int i = 1; i + 1 < fields.length; i += 2) {
                if (!fields[i + 1].isBlank()) {
                    names.put(Integer.parseInt(fields[i]), fields[i + 1]);
                }
            }
            return Map.copyOf(names);
        }

        private static List<Integer> slots(String[] fields, int from) {
            List<Integer> slots = new ArrayList<>();
            for (int i = from; i < fields.length; i++) {
                if (!fields[i].isBlank()) {
                    slots.add(Integer.parseInt(fields[i]));
                }
            }
            return slots;
        }
    }
}
