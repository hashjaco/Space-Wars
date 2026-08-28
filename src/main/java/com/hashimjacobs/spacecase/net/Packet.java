package com.hashimjacobs.spacecase.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.hashimjacobs.spacecase.engine.Intent;

/**
 * One thing a peer says: what a player did, what their world looks like, or where a fight begins.
 *
 * Binary rather than the comma-separated strings the save format uses. That idiom is right for a
 * payload written once per level into a preferences node; {@link Kind#INTENT} goes out sixty times
 * a second, and {@code ByteBuffer.putDouble} is exact and locale-proof where {@code
 * Double.toString} invites neither of those questions to be asked. {@link Kind#START} is the
 * exception and carries a string, because it is sent once per level and its payload is exactly the
 * text encoding the rest of the game already uses.
 *
 * @param playerNumber who it is about, or the sender for anything that is not about one player
 * @param tick         the simulation tick it describes, never the frame it was sent on
 * @param intent       the player's input, for {@link Kind#INTENT}; null otherwise
 * @param checksum     that peer's {@code World.checksum()} at {@code tick}, for
 *                     {@link Kind#CHECKSUM}; zero otherwise
 * @param text         an encoded {@link LevelStart}, for {@link Kind#START}; null otherwise
 */
public record Packet(Kind kind, int playerNumber, int tick, Intent intent, long checksum,
                     String text) {

    public enum Kind {
        /** A player's input for a tick. The overwhelming majority of traffic. */
        INTENT,
        /** A peer's world fingerprint, so a divergence is caught near where it started. */
        CHECKSUM,
        /**
         * This peer has finished its own between-levels business and will wait for the others.
         *
         * Carries that pilot's loadout, because the garage is local and its purchases are not:
         * an upgrade changes a ship's damage, hull and speed, so every peer has to know what
         * everyone else bought or their simulations disagree on the first shot of the next level.
         */
        READY,
        /** The host's terms for the next fight: seed, tick, difficulty, loadouts. */
        START
    }

    /** Kind, player, tick. Every packet begins with these; only the body differs. */
    private static final int HEADER = 1 + 1 + 4;

    private static final int INTENT_BODY = 8 + 8 + 1;
    private static final int CHECKSUM_BODY = 8;

    public static Packet intent(int playerNumber, int tick, Intent intent) {
        return new Packet(Kind.INTENT, playerNumber, tick, intent, 0, null);
    }

    public static Packet checksum(int playerNumber, int tick, long checksum) {
        return new Packet(Kind.CHECKSUM, playerNumber, tick, null, checksum, null);
    }

    /**
     * @param level   which level this peer is ready for, so an early READY cannot free a later barrier
     * @param loadout that pilot's encoded {@link com.hashimjacobs.spacecase.garage.Loadout}
     */
    public static Packet ready(int playerNumber, int level, String loadout) {
        return new Packet(Kind.READY, playerNumber, level, null, 0,
                loadout == null ? "" : loadout);
    }

    public static Packet start(int playerNumber, LevelStart start) {
        return new Packet(Kind.START, playerNumber, start.resumeTick(), null, 0, start.encode());
    }

    public byte[] encode() {
        byte[] body = switch (kind) {
            case INTENT -> {
                ByteBuffer buffer = ByteBuffer.allocate(INTENT_BODY);
                buffer.putDouble(intent.moveX());
                buffer.putDouble(intent.moveY());
                buffer.put((byte) (intent.firing() ? 1 : 0));
                yield buffer.array();
            }
            case CHECKSUM -> ByteBuffer.allocate(CHECKSUM_BODY).putLong(checksum).array();
            case READY -> text.getBytes(StandardCharsets.UTF_8);
            case START -> text.getBytes(StandardCharsets.UTF_8);
        };

        ByteBuffer packet = ByteBuffer.allocate(HEADER + body.length);
        packet.put((byte) kind.ordinal());
        packet.put((byte) playerNumber);
        packet.putInt(tick);
        packet.put(body);
        return packet.array();
    }

    /**
     * @throws IllegalArgumentException on anything this build did not write. A peer on a different
     *         version of the game is the likely cause, and is worth saying out loud rather than
     *         simulating from whatever the bytes happened to decode to.
     */
    public static Packet decode(byte[] bytes) {
        if (bytes.length < HEADER) {
            throw new IllegalArgumentException(
                    "a packet is at least " + HEADER + " bytes, got " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int kindIndex = buffer.get();
        if (kindIndex < 0 || kindIndex >= Kind.values().length) {
            throw new IllegalArgumentException("unknown packet kind " + kindIndex);
        }
        Kind kind = Kind.values()[kindIndex];
        int playerNumber = buffer.get();
        int tick = buffer.getInt();
        int body = bytes.length - HEADER;

        return switch (kind) {
            case INTENT -> {
                require(kind, body, INTENT_BODY);
                yield intent(playerNumber, tick,
                        new Intent(buffer.getDouble(), buffer.getDouble(), buffer.get() != 0));
            }
            case CHECKSUM -> {
                require(kind, body, CHECKSUM_BODY);
                yield checksum(playerNumber, tick, buffer.getLong());
            }
            // The two variable-length bodies, so the two with no length to check. A START that is
            // nonsense is caught by LevelStart, which answers "nothing at all" rather than throwing.
            case READY -> ready(playerNumber, tick,
                    new String(bytes, HEADER, body, StandardCharsets.UTF_8));
            case START -> new Packet(kind, playerNumber, tick, null, 0,
                    new String(bytes, HEADER, body, StandardCharsets.UTF_8));
        };
    }

    private static void require(Kind kind, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    kind + " carries " + expected + " bytes, got " + actual);
        }
    }
}
