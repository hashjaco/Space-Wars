package com.hashimjacobs.spacecase.prefs;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Who this installation is, as far as anything outside the machine is concerned.
 *
 * There is no login, no password and no provider. Two random values written once on first run do
 * the whole job, because the whole job is small: the game needs to be able to say "this run and
 * that run came from the same player" and "this machine may read that profile", and neither of
 * those is a question about a person's identity.
 *
 * <h2>Why there are two values and not one</h2>
 *
 * The {@link #id() id} is public and the {@link #code() sync code} is a credential, so they must
 * not be the same string. The id goes to the score board with every run and could reasonably be
 * shown or logged; the code is the only thing standing between a stranger and a saved campaign,
 * and is read out loud only to your own second machine. Folding them together would mean posting a
 * password to a public table forever, which is the sort of thing that looks harmless right up
 * until the table grows a way to be read.
 *
 * Both are made with {@link SecureRandom}: a guessable sync code is the same as no sync code.
 */
public final class Account {

    /**
     * The room-code alphabet, for the same reason it exists there: no 0/O and no 1/I, because a
     * sync code gets read off one screen and typed into another.
     *
     * Duplicated from {@code net.LobbyModel} rather than shared, because {@code net} depends on
     * this package and not the other way round, and one thirty-two character constant is a cheaper
     * thing to keep in step than an inverted dependency.
     */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * Twelve characters, which is sixty bits.
     *
     * The number is set by what it guards. Four would have been a room code -- fine for something
     * that lasts one evening and holds nothing -- but a sync code is durable and grants a whole
     * campaign, so it is sized to be found by nobody rather than merely by nobody in a hurry.
     */
    public static final int CODE_LENGTH = 12;

    /** Groups of four, as it is shown and as people read it out. */
    private static final int GROUP = 4;

    private static final String ID_KEY = "id";
    private static final String CODE_KEY = "code";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Preferences store;

    private Account(Preferences store) {
        this.store = store;
    }

    public static Account load() {
        return new Account(Profile.root().node(Profile.ACCOUNT_NODE));
    }

    /** Package-private so tests can supply a throwaway node, as the rest of this package does. */
    static Account load(Preferences store) {
        return new Account(store);
    }

    /**
     * This player's public identity on the score board, made on first use and kept afterwards.
     *
     * A UUID rather than a sync code because it is not a secret and never needs typing. Its only
     * job is to keep one player's rows to one row per mode, so a hundred runs do not become a
     * hundred entries.
     */
    public String id() {
        String held = store.get(ID_KEY, null);
        if (held != null) {
            return held;
        }
        String made = UUID.randomUUID().toString();
        store.put(ID_KEY, made);
        flush();
        return made;
    }

    /**
     * The code that addresses this player's cloud profile, made on first use.
     *
     * Generated here rather than asked of the relay. Sixty bits does not collide at any number of
     * players this game will ever have, and a code that needs no round trip is a code that exists
     * before the first time the network is reachable.
     */
    public String code() {
        String held = store.get(CODE_KEY, null);
        if (held != null) {
            return held;
        }
        String made = newCode();
        store.put(CODE_KEY, made);
        flush();
        return made;
    }

    /**
     * Takes on a code read off another machine, so both of them address the same profile.
     *
     * Only the code moves. The id is left alone deliberately: a download brings the other machine's
     * id with it, and until then this machine's runs still belong to whoever has been flying them.
     *
     * @return false if that is not a sync code, in which case nothing changed
     */
    public boolean adopt(String code) {
        String cleaned = code == null ? "" : code.replace("-", "").trim().toUpperCase();
        if (!isCode(cleaned)) {
            return false;
        }
        store.put(CODE_KEY, cleaned);
        flush();
        return true;
    }

    /** Whether a string is a sync code at all, checked before it is used as an address. */
    public static boolean isCode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        for (int at = 0; at < code.length(); at++) {
            if (!isCodeCharacter(code.charAt(at))) {
                return false;
            }
        }
        return true;
    }

    /** Whether a keystroke belongs in a sync code, for the screen that takes one a character at a time. */
    public static boolean isCodeCharacter(char character) {
        return CODE_ALPHABET.indexOf(character) >= 0;
    }

    /** {@code ABCD-EFGH-JKLM}: the form it is shown in, and the only form anyone copies by eye. */
    public static String grouped(String code) {
        StringBuilder shown = new StringBuilder();
        for (int at = 0; at < code.length(); at++) {
            if (at > 0 && at % GROUP == 0) {
                shown.append('-');
            }
            shown.append(code.charAt(at));
        }
        return shown.toString();
    }

    private static String newCode() {
        byte[] bytes = new byte[CODE_LENGTH];
        RANDOM.nextBytes(bytes);
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (byte b : bytes) {
            // Masked to a byte first: Java's byte is signed, so the raw value is negative half the
            // time and would index backwards out of the string. Thirty-two divides 256 evenly, so
            // no character comes up more often than another and the code keeps its sixty bits.
            code.append(CODE_ALPHABET.charAt((b & 0xff) % CODE_ALPHABET.length()));
        }
        return code.toString();
    }

    private void flush() {
        try {
            store.flush();
        } catch (BackingStoreException e) {
            // Same bargain the rest of this package makes: a preference that will not write is not
            // worth interrupting play over. A code that fails to persist is regenerated next run,
            // which costs an upload rather than a campaign.
        }
    }
}
