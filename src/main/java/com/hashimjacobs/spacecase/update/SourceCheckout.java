package com.hashimjacobs.spacecase.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A git checkout the game is running from, as on the Raspberry Pi: updated by pulling.
 *
 * Only ever a fast-forward of the branch the checkout already tracks, and only on a clean tree. A
 * checkout with work in it belongs to whoever is doing that work; the game says why it will not
 * pull and leaves it alone.
 */
final class SourceCheckout {

    /** What {@link #behind} found. */
    record Behind(int commits, String upstream, String newest) {
    }

    private final Path root;

    SourceCheckout(Path root) {
        this.root = root;
    }

    /**
     * Fetches, then counts the commits the tracked branch has that this checkout does not.
     *
     * @throws IOException with a sentence a player can act on: no git, local changes, no upstream
     */
    Behind behind() throws IOException {
        String upstream = git(30, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
                .orElseThrow("this checkout's branch tracks no remote branch, so there is nothing to pull");
        if (!git(15, "status", "--porcelain", "--untracked-files=no").out().isBlank()) {
            throw new IOException("this checkout has local changes; commit or stash them, then check again");
        }
        git(120, "fetch", "--quiet").orElseThrow("git could not fetch");
        int commits = Integer.parseInt(git(15, "rev-list", "--count", "HEAD..@{u}")
                .orElseThrow("git could not compare with " + upstream).trim());
        String newest = commits == 0 ? "" : git(15, "log", "-1", "--format=%s", "@{u}").out().trim();
        return new Behind(commits, upstream, newest);
    }

    /** {@code git pull --ff-only}. */
    void pull() throws IOException {
        git(300, "pull", "--ff-only", "--quiet").orElseThrow("git could not fast-forward this checkout");
    }

    Path root() {
        return root;
    }

    private record Result(int exit, String out) {
        String orElseThrow(String why) throws IOException {
            if (exit != 0) {
                String detail = out.strip();
                int lastLine = detail.lastIndexOf('\n');
                throw new IOException(why + (detail.isEmpty() ? "" : ": " + detail.substring(lastLine + 1)));
            }
            return out;
        }
    }

    private Result git(int seconds, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(List.of(args));
        // Into a file rather than read from a pipe, so a git that hangs cannot hang the wait too.
        Path log = Files.createTempFile("space-case-git", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
        // Never stop to ask for a password: there is no terminal to ask on.
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process;
        try {
            process = builder.start();
        } catch (IOException missing) {
            Files.deleteIfExists(log);
            throw new IOException("git is not installed, so this checkout cannot update itself", missing);
        }
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("git took too long");
            }
            return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", interrupted);
        } finally {
            Files.deleteIfExists(log);
        }
    }

    private static java.io.File nullDevice() {
        return new java.io.File(System.getProperty("os.name", "").startsWith("Windows") ? "NUL" : "/dev/null");
    }
}
