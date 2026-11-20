package com.hashimjacobs.spacecase.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Handing a downloaded release to whatever installs it, then starting the game again.
 *
 * A running program cannot replace its own files -- Windows will not even let it try -- so every
 * desktop install is a short script started here and left running: it waits for this process to
 * exit, does the install, and launches the new game. The caller exits straight after
 * {@link #handOff} returns. If the install step fails, the script still starts whatever is now
 * there, so a failed update leaves the old game rather than no game.
 *
 * The scripts write to {@code update.log} in the download folder, which is the place to look when
 * an update did not take.
 */
final class Install {

    private Install() {
    }

    /**
     * Starts the script that finishes the install once this process has gone.
     *
     * @param file the verified download; for a source checkout, unused (the pull has already run)
     * @param log  where the script's output goes
     * @throws IOException if this machine lacks what the install needs, with what to do instead
     */
    static void handOff(Installation installation, Path file, Path log) throws IOException {
        long pid = ProcessHandle.current().pid();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String script;
        switch (installation.kind()) {
            case JPACKAGE:
                script = jpackage(installation, file, pid, windows);
                break;
            case JAR:
                script = windows ? jarWindows(installation.location(), file, pid) : jarUnix(installation.location(), file, pid);
                break;
            case SOURCE:
                script = windows ? sourceWindows(installation.location(), pid) : sourceUnix(installation.location(), pid);
                break;
            default:
                throw new IOException("this copy of the game cannot install updates itself");
        }
        start(script, windows, log);
    }

    private static String jpackage(Installation installation, Path file, long pid, boolean windows) throws IOException {
        Path app = installation.location();
        String name = file.getFileName().toString();
        if (windows && name.endsWith(".msi")) {
            // msiexec raises its own UAC prompt for a machine-wide install.
            return waitWindows(pid)
                    + "Start-Process -FilePath msiexec.exe -ArgumentList ('/i \"' + " + ps(file) + " + '\" /passive') -Wait\n"
                    + "Start-Process -FilePath " + ps(app) + "\n";
        }
        if (name.endsWith(".dmg")) {
            // .../SpaceCase.app/Contents/MacOS/SpaceCase -> .../SpaceCase.app
            Path bundle = app.getParent().getParent().getParent();
            return waitUnix(pid)
                    + "mnt=$(mktemp -d)\n"
                    + "if hdiutil attach -nobrowse -readonly -mountpoint \"$mnt\" " + sh(file) + "; then\n"
                    + "  new=$(ls -d \"$mnt\"/*.app | head -n 1)\n"
                    + "  rm -rf " + sh(bundle + ".old") + "\n"
                    + "  if mv " + sh(bundle) + " " + sh(bundle + ".old") + "; then\n"
                    + "    if ditto \"$new\" " + sh(bundle) + "; then rm -rf " + sh(bundle + ".old") + ";\n"
                    + "    else rm -rf " + sh(bundle) + "; mv " + sh(bundle + ".old") + " " + sh(bundle) + "; fi\n"
                    + "  fi\n"
                    + "  hdiutil detach \"$mnt\" -quiet\n"
                    + "fi\n"
                    + "open " + sh(bundle) + "\n";
        }
        if (name.endsWith(".deb")) {
            if (!onPath("pkexec")) {
                throw new IOException("this machine has no pkexec to ask for the password; install it with: sudo apt install "
                        + file);
            }
            // pkexec asks for the password in a desktop dialog, since there is no terminal.
            return waitUnix(pid)
                    + "pkexec apt-get install -y " + sh(file) + " || pkexec dpkg -i " + sh(file) + "\n"
                    + sh(app) + " &\n";
        }
        throw new IOException("there is no installer for " + name + " on this machine");
    }

    private static String jarUnix(Path jar, Path file, long pid) {
        return waitUnix(pid)
                + "mv -f " + sh(file) + " " + sh(jar) + "\n"
                + sh(javaCommand(false)) + " -jar " + sh(jar) + " &\n";
    }

    private static String jarWindows(Path jar, Path file, long pid) {
        return waitWindows(pid)
                + "Move-Item -Force -LiteralPath " + ps(file) + " -Destination " + ps(jar) + "\n"
                + "Start-Process -FilePath " + ps(javaCommand(true)) + " -ArgumentList ('-jar \"' + " + ps(jar) + " + '\"')\n";
    }

    /** The pull already ran in the game; this only rebuilds and restarts it. */
    private static String sourceUnix(Path root, long pid) {
        return waitUnix(pid)
                + "cd " + sh(root) + " && ./mvnw -q javafx:run &\n";
    }

    private static String sourceWindows(Path root, long pid) {
        return waitWindows(pid)
                + "Start-Process -FilePath " + ps(root.resolve("mvnw.cmd")) + " -ArgumentList '-q','javafx:run' -WorkingDirectory "
                + ps(root) + "\n";
    }

    private static String waitUnix(long pid) {
        return "while kill -0 " + pid + " 2>/dev/null; do sleep 0.3; done\n";
    }

    private static String waitWindows(long pid) {
        return "Wait-Process -Id " + pid + " -ErrorAction SilentlyContinue\n";
    }

    private static void start(String script, boolean windows, Path log) throws IOException {
        Files.createDirectories(log.toAbsolutePath().getParent());
        List<String> command = windows
                ? List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                        "-Command", script)
                // Ignoring hang-up keeps the script alive if the game was started from a terminal
                // that closes with it.
                : List.of("sh", "-c", "trap '' HUP\n" + script);
        new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .redirectInput(ProcessBuilder.Redirect.from(new File(windows ? "NUL" : "/dev/null")))
                .start();
    }

    /** The java binary running this game; javaw on Windows when asked, so no console opens. */
    private static Path javaCommand(boolean windowless) {
        Path java = ProcessHandle.current().info().command().map(Path::of)
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java"));
        if (windowless) {
            Path javaw = java.resolveSibling("javaw.exe");
            if (Files.exists(javaw)) {
                return javaw;
            }
        }
        return java;
    }

    private static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, program))) {
                return true;
            }
        }
        return false;
    }

    /** Single-quoted for sh, with any single quote in the path closed, escaped and reopened. */
    static String sh(Object path) {
        return "'" + path.toString().replace("'", "'\"'\"'") + "'";
    }

    /** Single-quoted for PowerShell, which escapes a single quote by doubling it. */
    static String ps(Object path) {
        return "'" + path.toString().replace("'", "''") + "'";
    }
}
