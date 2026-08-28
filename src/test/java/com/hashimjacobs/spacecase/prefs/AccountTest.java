package com.hashimjacobs.spacecase.prefs;

import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anonymous account: made once, kept, and never confused with itself.
 *
 * Uses a throwaway node, never the real one -- the same bargain every other test in this package
 * makes, and it matters more here, because the values under test are the ones that address a
 * player's cloud profile.
 */
class AccountTest {

    private final Preferences scratch =
            Preferences.userRoot().node("space-case-test-" + System.nanoTime());

    @AfterEach
    void removeScratchNode() throws BackingStoreException {
        scratch.removeNode();
        scratch.flush();
    }

    @Test
    void anIdentityIsMadeOnceAndKept() {
        Account account = Account.load(scratch);
        String id = account.id();
        String code = account.code();

        assertEquals(id, account.id(), "asking twice must not mint a second identity");
        assertEquals(code, account.code());
        assertEquals(id, Account.load(scratch).id(), "and it has to survive a restart");
        assertEquals(code, Account.load(scratch).code());
    }

    /**
     * The one thing that must never be true of these two values.
     *
     * The id is posted to a public board with every run and the code opens a saved campaign, so a
     * build that folded them into one field would be publishing the credential. Cheap to assert,
     * and the assertion is the reason the second field exists.
     */
    @Test
    void thePublicIdIsNotTheSecretCode() {
        Account account = Account.load(scratch);
        assertNotEquals(account.id(), account.code());
        assertFalse(account.id().contains(account.code()));
    }

    @Test
    void aCodeIsTypeableAndUnambiguous() {
        String code = Account.load(scratch).code();
        assertEquals(Account.CODE_LENGTH, code.length());
        assertTrue(Account.isCode(code));
        for (char c : code.toCharArray()) {
            assertFalse("IO01".indexOf(c) >= 0,
                    "a code is read off one screen and typed into another: " + code);
        }
    }

    /**
     * Not a test of the random number generator, which needs no help from here. It is a test that
     * the code is drawn per call rather than derived from something every installation shares --
     * a constant seed or a machine name would collide every player's profile onto one.
     */
    @Test
    void codesDifferBetweenInstallations() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Preferences other = scratch.node("install" + i);
            seen.add(Account.load(other).code());
        }
        assertEquals(50, seen.size(), "fifty fresh installations produced a duplicate code");
    }

    @Test
    void adoptingAnotherMachinesCodeMovesOnlyTheCode() {
        Account account = Account.load(scratch);
        String id = account.id();

        assertTrue(account.adopt("ABCD-EFGH-JKLM"), "punctuation is how the code is shown");
        assertEquals("ABCDEFGHJKLM", account.code());
        assertEquals(id, account.id(), "the runs flown on this machine still belong to this pilot");
    }

    @Test
    void anythingThatIsNotACodeIsRefusedAndChangesNothing() {
        Account account = Account.load(scratch);
        String held = account.code();

        assertFalse(account.adopt("ABCD"), "too short");
        assertFalse(account.adopt("ABCDEFGHJKLMNP"), "too long");
        assertFalse(account.adopt("ABCDEFGHJKL0"), "a zero is not in the alphabet");
        assertFalse(account.adopt(null));
        assertEquals(held, account.code(), "a refused code must not have half-replaced the real one");
    }
}
