package com.hashimjacobs.spacecase.prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Everything a player would be sorry to lose, as one string.
 *
 * This is what moves between a player's two machines: their runs, their pilots, their scores and
 * their identity. It is the same trick {@code SaveSlot} plays one level down -- a thing worth
 * keeping becomes a line of text, and then it can be stored, sent or typed without any of the code
 * that stores, sends or types it knowing what is in it.
 *
 * <h2>What is deliberately not in here</h2>
 *
 * <b>Settings.</b> Not an oversight, and not a taste call either -- including them would be a bug.
 * {@code Settings} caches its values in fields and writes them all back on the way out, so a
 * download that changed the stored settings under a live {@code Settings} object would be undone
 * the next time the player quit. Beyond that, half of what is in there describes a machine rather
 * than a player: a gamepad's button numbers and its deadzone belong to the pad on the desk, not to
 * the person who bought the game twice.
 *
 * <h2>The format</h2>
 *
 * One line per stored value, {@code node|path/key|value}, with a version on the first line. Not
 * {@link Preferences#exportSubtree}, which exists and would have been one line: its XML covers the
 * whole subtree including the settings above, and there is no way to ask it for part of one. This
 * is thirty lines to keep an exact say over what leaves the machine, which is the right side of
 * that trade for something that overwrites a stranger's campaign if it is wrong.
 */
public final class Profile {

    /** Bumped only if the line shape below changes incompatibly. New nodes are not a break. */
    private static final int FORMAT_VERSION = 1;

    /**
     * Between the three fields of a line.
     *
     * A pipe rather than a comma, because the values are mostly {@code SaveSlot.encode} output and
     * that is comma-separated -- splitting on a comma would cut every save into fragments. Keys
     * carry a slash ({@code cleared/SOLO}), values carry commas and equals signs, and nothing
     * anywhere carries a pipe.
     */
    private static final String SEPARATOR = "|";

    /** Escaped, because a bare pipe in a regex is alternation and would split on every character. */
    private static final String SPLIT_ON = "\\|";

    static final String ACCOUNT_NODE = "account";

    /**
     * The nodes a profile is made of, and the order they are written in.
     *
     * Named one by one rather than discovered by listing the children of the package node, so that
     * a node added to this package later is out of the profile until somebody decides it belongs
     * in it. Discovery would have quietly started syncing settings the moment they moved.
     */
    private static final String[] NODES = {"saves", "pilots", "highscores", ACCOUNT_NODE};

    private Profile() {
    }

    /** The node the whole package hangs off, and the parent of everything in {@link #NODES}. */
    static Preferences root() {
        return Preferences.userNodeForPackage(Profile.class);
    }

    /** Everything worth keeping on this machine, ready to be uploaded. */
    public static String export() {
        return export(root());
    }

    static String export(Preferences root) {
        StringBuilder out = new StringBuilder().append(FORMAT_VERSION);
        for (String name : NODES) {
            appendNode(out, name, "", root.node(name));
        }
        return out.toString();
    }

    /**
     * Writes a downloaded profile over this machine's, replacing rather than merging.
     *
     * Replacing is what the player asked for: they are pulling their campaign onto a second
     * machine, and a merge would leave that machine's own half-finished runs interleaved with it in
     * a way nobody could reason about afterwards. So the profile's nodes are emptied first.
     *
     * <b>Emptied, never removed.</b> {@code Pilots} and {@code SaveGames} hold live handles to
     * these nodes and their children, and {@link Preferences#removeNode} would leave every one of
     * those handles throwing {@code IllegalStateException} on the next keystroke.
     * {@link Preferences#clear} takes the keys out and leaves the node, which is the difference
     * between a fresh profile and a game that has to be restarted to be playable.
     *
     * Parsed in full before anything is cleared, so a truncated download costs an error message
     * rather than the campaign that was already here.
     *
     * @return false if that was not a profile, in which case nothing was touched
     */
    public static boolean restore(String encoded) {
        return restore(root(), encoded);
    }

    static boolean restore(Preferences root, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return false;
        }
        String[] lines = encoded.split("\n");
        if (!Integer.toString(FORMAT_VERSION).equals(lines[0].trim())) {
            return false;
        }

        List<String[]> values = new ArrayList<>();
        for (int at = 1; at < lines.length; at++) {
            String line = lines[at].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split(SPLIT_ON, 3);
            // A line this build cannot read is skipped rather than fatal, the same way the relay's
            // control frames are: an older build must still be able to load a newer profile's
            // saves, having simply not known what one of its lines was for.
            if (fields.length == 3 && isProfileNode(fields[0])) {
                values.add(fields);
            }
        }
        if (values.isEmpty()) {
            return false;
        }

        for (String name : NODES) {
            clearTree(root.node(name));
        }
        for (String[] value : values) {
            nodeFor(root.node(value[0]), value[1]).put(keyOf(value[1]), value[2]);
        }
        flush(root);
        return true;
    }

    /** One node and everything under it, as {@code node|path/key|value} lines. */
    private static void appendNode(StringBuilder out, String name, String path, Preferences node) {
        try {
            for (String key : node.keys()) {
                String value = node.get(key, null);
                if (value != null) {
                    out.append('\n').append(name).append(SEPARATOR).append(path).append(key)
                            .append(SEPARATOR).append(value);
                }
            }
            for (String child : node.childrenNames()) {
                appendNode(out, name, path + child + "/", node.node(child));
            }
        } catch (BackingStoreException unreadable) {
            // A node that will not enumerate contributes nothing rather than failing the export.
            // A profile missing a pilot is worth uploading; no profile at all is not.
        }
    }

    /** Empties a node and its descendants of keys, leaving every node itself in place. */
    private static void clearTree(Preferences node) {
        try {
            node.clear();
            for (String child : node.childrenNames()) {
                clearTree(node.node(child));
            }
        } catch (BackingStoreException unreadable) {
            // Nothing useful to do, and nothing lost: what could not be cleared is about to be
            // written over by the restore anyway if the profile has anything to say about it.
        }
    }

    /** Walks the {@code career/HASH} part of a path, creating what is not there. */
    private static Preferences nodeFor(Preferences node, String path) {
        int split = path.lastIndexOf('/');
        return split < 0 ? node : node.node(path.substring(0, split));
    }

    private static String keyOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static boolean isProfileNode(String name) {
        for (String known : NODES) {
            if (known.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void flush(Preferences root) {
        try {
            root.flush();
        } catch (BackingStoreException e) {
            // The values are already in the store; a failed flush only delays them reaching disk.
        }
    }
}
