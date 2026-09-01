package com.hashimjacobs.spacecase.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Getting from "Multiplayer" to a fight: the rules half, with no screen attached.
 *
 * Apart from the view for the reason {@code SystemMapModel} is apart from {@code SystemMapView} and
 * {@code MenuNavigator} is apart from the panels it drives -- so it can be tested without starting
 * the JavaFX toolkit, which {@code docs/ROADMAP.md} rule 6 forbids the suite from doing.
 * There is no JavaFX left in it at all now that the code arrives as a string.
 *
 * Knows nothing about sockets either. The screen hands it what arrived and asks what to draw, which
 * is what lets the whole flow -- typing a code, peers arriving and leaving, the host starting -- be
 * exercised in a few milliseconds with no relay running.
 */
public final class LobbyModel {

    /**
     * Room codes are six characters, issued by the relay and read out loud.
     *
     * Six rather than the four this shipped with, and the number has to match {@code CODE_LENGTH}
     * in {@code server/src/index.ts} exactly -- it is what the field is sized for and what
     * {@link #codeIsComplete} waits for, so a client one character behind the relay can never
     * finish typing a code that exists. Four was a million rooms, which is a number a script can
     * walk; a room code is the only thing addressing a room, so the code is the lock.
     */
    public static final int CODE_LENGTH = 6;

    /**
     * What the relay's codes are drawn from: A-Z without I or O, digits 2-9.
     *
     * Mirrored from the server rather than shared, because there is nothing to share it through.
     * Typing is filtered against it so a player cannot enter a code that could not exist, and so a
     * zero cannot be typed where an O was meant.
     */
    public static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** A room seats this many; the relay refuses the next one before the socket opens. */
    public static final int MAX_PEERS = 4;

    public enum State {
        /** Typing a code to join, or about to ask for one. */
        CHOOSING,
        /** The socket is being opened on a background thread. */
        CONNECTING,
        /** In the room, watching who else arrives. */
        WAITING,
        /** Terms have been agreed and the fight is about to begin. */
        STARTING,
        /** The room was full, the code was wrong, or the relay did not answer. */
        FAILED
    }

    private State state = State.CHOOSING;
    private final StringBuilder typed = new StringBuilder();
    private String room = "";
    private int localSlot;
    private boolean host;
    private String failure = "";

    /** Every slot in the room, ascending, and what its pilot is flying. */
    private final Map<Integer, String> loadouts = new LinkedHashMap<>();
    private List<Integer> peers = List.of();

    public State state() {
        return state;
    }

    /** What has been typed so far, for the view to show as the player enters a code. */
    public String typed() {
        return typed.toString();
    }

    public String room() {
        return room;
    }

    public int localSlot() {
        return localSlot;
    }

    public boolean isHost() {
        return host;
    }

    /** Why the lobby gave up, in words a player can act on. Empty unless {@link State#FAILED}. */
    public String failure() {
        return failure;
    }

    public List<Integer> peers() {
        return peers;
    }

    /**
     * Whether the host may begin.
     *
     * Two conditions, and the second is the one that is easy to forget: somebody else has to be
     * here, and everybody who is here has to have said what they are flying. Starting without a
     * peer's loadout would begin the fight with the machines disagreeing about that ship.
     */
    public boolean canStart() {
        return host && state == State.WAITING && peers.size() >= 2
                && loadouts.keySet().containsAll(peers);
    }

    /**
     * Sets the code being typed, from the on-screen keyboard.
     *
     * The keyboard already offers only {@link #CODE_ALPHABET}, but the filter, the length clamp and
     * the state guard stay here rather than moving out to it. {@link #codeIsComplete} checks length
     * alone and the screen hands {@link #typed} straight to the relay, so this is the last place
     * that can stop a room code that could not exist from going on the wire.
     *
     * Replaced a {@code handleKey} that read letters off the keystroke. That is what made this
     * screen unusable on a controller: player one's d-pad speaks {@code W A S D}, so it typed into
     * the code field instead of walking the menu.
     */
    public void setTyped(String code) {
        if (state != State.CHOOSING) {
            return;
        }
        typed.setLength(0);
        for (int at = 0; at < code.length() && typed.length() < CODE_LENGTH; at++) {
            char character = Character.toUpperCase(code.charAt(at));
            if (CODE_ALPHABET.indexOf(character) >= 0) {
                typed.append(character);
            }
        }
    }

    /** Whether enough has been typed to try joining. */
    public boolean codeIsComplete() {
        return typed.length() == CODE_LENGTH;
    }

    public void connecting() {
        state = State.CONNECTING;
        failure = "";
    }

    /** The relay has answered: this machine is in, holding {@code slot}. */
    public void joined(String room, int slot, List<Integer> peers, boolean host) {
        this.room = room;
        this.localSlot = slot;
        this.host = host;
        this.peers = List.copyOf(peers);
        this.state = State.WAITING;
    }

    /** The roster as the relay last reported it. Peers who left take their loadout with them. */
    public void roster(List<Integer> peers) {
        this.peers = List.copyOf(peers);
        loadouts.keySet().retainAll(this.peers);
    }

    /** What a peer announced it is flying, from a {@link Packet.Kind#READY}. */
    public void loadout(int slot, String code) {
        loadouts.put(slot, code == null ? "" : code);
    }

    /**
     * Every pilot's loadout in player-number order, for the host to put in the opening terms.
     *
     * Ordered by slot rather than by who spoke first, because the list travels positionally in
     * {@link LevelStart} and a race in the lobby must not hand player two player three's ship.
     */
    public List<String> loadoutsInSeatOrder() {
        List<String> ordered = new ArrayList<>();
        for (int slot : peers) {
            ordered.add(loadouts.getOrDefault(slot, ""));
        }
        return ordered;
    }

    public void starting() {
        state = State.STARTING;
    }

    /** @param reason shown to the player, so it says what happened rather than that something did */
    public void failed(String reason) {
        state = State.FAILED;
        failure = reason;
    }

    /** Back to the code field, keeping nothing from the attempt that failed. */
    public void reset() {
        state = State.CHOOSING;
        typed.setLength(0);
        room = "";
        localSlot = 0;
        host = false;
        failure = "";
        peers = List.of();
        loadouts.clear();
    }
}
