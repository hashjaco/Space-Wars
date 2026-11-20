package com.hashimjacobs.spacecase.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Raspberry Pi's path, against real throwaway repositories: an upstream and a clone of it. */
class SourceCheckoutTest {

    @TempDir
    Path dir;

    private Path upstream;
    private Path clone;

    @BeforeEach
    void repositories() throws Exception {
        Assumptions.assumeTrue(git(dir, "--version") == 0, "git is not installed");
        upstream = dir.resolve("upstream");
        Files.createDirectories(upstream);
        git(upstream, "init", "-q", "-b", "main");
        commit(upstream, "one");
        clone = dir.resolve("clone");
        assertEquals(0, git(dir, "clone", "-q", upstream.toString(), clone.toString()));
    }

    @Test
    void aCheckoutLevelWithItsUpstreamHasNothingToPull() throws IOException {
        assertEquals(0, new SourceCheckout(clone).behind().commits());
    }

    @Test
    void newCommitsUpstreamAreCountedAndPulled() throws Exception {
        commit(upstream, "two");
        commit(upstream, "three");
        SourceCheckout checkout = new SourceCheckout(clone);
        SourceCheckout.Behind behind = checkout.behind();
        assertEquals(2, behind.commits());
        assertEquals("three", behind.newest());

        checkout.pull();
        assertEquals(0, checkout.behind().commits());
        assertTrue(Files.exists(clone.resolve("three.txt")));
    }

    @Test
    void localChangesAreLeftAlone() throws Exception {
        commit(upstream, "two");
        Files.writeString(clone.resolve("one.txt"), "someone's work");
        IOException refused = assertThrows(IOException.class, () -> new SourceCheckout(clone).behind());
        assertTrue(refused.getMessage().contains("local changes"));
    }

    @Test
    void aBranchThatTracksNothingIsRefused() throws Exception {
        git(clone, "checkout", "-q", "-b", "loose");
        IOException refused = assertThrows(IOException.class, () -> new SourceCheckout(clone).behind());
        assertTrue(refused.getMessage().contains("tracks no remote branch"));
    }

    private static void commit(Path repo, String name) throws Exception {
        Files.writeString(repo.resolve(name + ".txt"), name);
        git(repo, "add", ".");
        assertEquals(0, git(repo, "-c", "user.name=t", "-c", "user.email=t@example.invalid",
                "commit", "-q", "-m", name));
    }

    private static int git(Path in, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", in.toString()));
        command.addAll(List.of(args));
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException noGit) {
            return -1;
        }
        process.waitFor(30, TimeUnit.SECONDS);
        return process.exitValue();
    }
}
