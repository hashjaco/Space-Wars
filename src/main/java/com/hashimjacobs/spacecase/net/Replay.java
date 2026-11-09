package com.hashimjacobs.spacecase.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.hashimjacobs.spacecase.engine.Intent;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * Everything a machine needs to replay a run and arrive at the same score: the seed, the terms of
 * every level as it began (tick, loadouts), and every player's intent on every fight tick.
 *
 * The same idea as the arcade's replay-to-verify, applied to a run the leaderboard would otherwise
 * have to take on trust. Recorded by {@code GameLoop}, read by {@code engine.Replayer}. Gzipped: a
 * twenty-minute run is a few hundred kilobytes raw and compresses twenty to one, since most ticks
 * repeat the last.
 *
 * Layout, after a version byte: a header (mode, difficulty, endless, resumed, players), then
 * records tagged by one byte: 'L' + a {@link LevelStart} string; 'I' + one intent per player, in
 * seat order; 'E' end.
 */
public final class Replay {

    private static final int VERSION = 2;   // 2: the button byte carries the ship special

    public record Header(long seed, GameMode mode, Difficulty difficulty, boolean endless,
                         boolean resumed, int players) {
    }

    /** One level's terms followed by every fight tick's intents. */
    public record Segment(LevelStart start, List<Intent[]> ticks) {
    }

    private Replay() {
    }

    /** Accumulates a run as it is played; {@link #finish()} seals it. */
    public static final class Recorder {
        private final Header header;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);
        private int ticks;

        public Recorder(Header header) {
            this.header = header;
            try {
                out.writeByte(VERSION);
                out.writeLong(header.seed());
                out.writeUTF(header.mode().name());
                out.writeUTF(header.difficulty().name());
                out.writeBoolean(header.endless());
                out.writeBoolean(header.resumed());
                out.writeByte(header.players());
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        public Header header() {
            return header;
        }

        public void level(LevelStart start) {
            try {
                out.writeByte('L');
                out.writeUTF(start.encode());
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        /** The intents one fight tick ran, in seat order. */
        public void tick(List<Intent> intents) {
            try {
                out.writeByte('I');
                for (Intent intent : intents) {
                    out.writeDouble(intent.moveX());
                    out.writeDouble(intent.moveY());
                    out.writeByte(intent.buttons());
                }
                ticks++;
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        public int ticks() {
            return ticks;
        }

        /** The run so far, gzipped. The recorder can keep going; this is a snapshot. */
        public byte[] finish() {
            try {
                ByteArrayOutputStream packed = new ByteArrayOutputStream();
                try (GZIPOutputStream gz = new GZIPOutputStream(packed)) {
                    bytes.writeTo(gz);
                    gz.write('E');
                }
                return packed.toByteArray();
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    public record Parsed(Header header, List<Segment> segments) {
    }

    public static Parsed read(byte[] packed) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(packed)))) {
            int version = in.readByte();
            if (version != VERSION) {
                throw new IOException("replay version " + version);
            }
            Header header = new Header(in.readLong(), GameMode.valueOf(in.readUTF()),
                    Difficulty.valueOf(in.readUTF()), in.readBoolean(), in.readBoolean(), in.readByte());
            List<Segment> segments = new ArrayList<>();
            Segment current = null;
            while (true) {
                int tag = in.read();
                if (tag < 0 || tag == 'E') {
                    break;
                }
                if (tag == 'L') {
                    LevelStart start = LevelStart.decode(in.readUTF())
                            .orElseThrow(() -> new IOException("unreadable level start"));
                    current = new Segment(start, new ArrayList<>());
                    segments.add(current);
                } else if (tag == 'I') {
                    if (current == null) {
                        throw new IOException("intents before any level");
                    }
                    Intent[] intents = new Intent[header.players()];
                    for (int i = 0; i < intents.length; i++) {
                        intents[i] = Intent.of(in.readDouble(), in.readDouble(), in.readByte());
                    }
                    current.ticks().add(intents);
                } else {
                    throw new IOException("unknown record " + tag);
                }
            }
            return new Parsed(header, segments);
        }
    }
}
