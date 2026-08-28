package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.Preferences;

/**
 * Opens {@link Account}'s throwaway-node loader to {@code tools/preview}, as
 * {@link ScratchSettings} does for settings and for the same reason.
 *
 * More important here than there. {@code Account.load()} mints a sync code on first read and keeps
 * it, so a capture run against the real store would print the machine's actual credential into a
 * PNG -- which is the one value in this game that must not end up in a screenshot.
 */
public final class ScratchAccount {

    /** A fresh identity on a node nothing else will ever read. Caller removes it when done. */
    public static Account on(Preferences node) {
        return Account.load(node);
    }

    private ScratchAccount() {
    }
}
