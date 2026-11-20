package com.hashimjacobs.spacecase.update;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

import com.hashimjacobs.spacecase.GameVersion;

/**
 * Finding a newer release and installing it, for the menus.
 *
 * Three steps, each run by the caller off the JavaFX thread: {@link #check} asks whether there is
 * anything newer, {@link #prepare} gets it onto the machine (a verified download, or a git pull),
 * and {@link #install} hands it over. The steps differ by {@link Installation.Kind}; the screens do
 * not need to know how.
 *
 * Releases are found through {@code update.txt}, which the release workflow attaches to every
 * release. {@code releases/latest/download/} is GitHub's own redirect to the newest release that is
 * not a draft or pre-release, so this needs no API, no token and no JSON. {@code -Dspacecase.updateUrl}
 * points it somewhere else, such as a local server for testing.
 */
public final class Updates {

    /** Where a player goes when the game cannot install for them. */
    public static final String RELEASE_PAGE = "github.com/hashjaco/Space-Wars/releases";

    private static final URI DEFAULT_LIST =
            URI.create("https://github.com/hashjaco/Space-Wars/releases/latest/download/update.txt");

    /** The Android build's private folder, from a class compiled only into that build. */
    private static final String ANDROID_FOLDER = "com.hashimjacobs.spacecase.AttachUpdateFolder";

    /** What the Android side watches for; see {@code TvActivity}. */
    private static final String APK = "SpaceCase.apk";

    /** A newer release, and everything needed to install it. */
    public static final class Offer {
        private final String headline;
        private final String detail;
        private final String whyNot;
        private final Manifest.Entry entry;
        private final URI url;
        private volatile Path prepared;

        private Offer(String headline, String detail, String whyNot, Manifest.Entry entry, URI url) {
            this.headline = headline;
            this.detail = detail;
            this.whyNot = whyNot;
            this.entry = entry;
            this.url = url;
        }

        /** "Update to 2.8", or "Update: 3 new changes" for a source checkout. */
        public String headline() {
            return headline;
        }

        /** A second line: what to expect, or the newest commit's subject. */
        public String detail() {
            return detail;
        }

        /** Whether this machine can install it from the game. */
        public boolean installable() {
            return whyNot == null;
        }

        /** Why not, when it cannot, with where to get it instead. */
        public String whyNot() {
            return whyNot == null ? "" : whyNot;
        }

        /** Whether {@link Updates#prepare} has finished, so {@link Updates#install} can run. */
        public boolean prepared() {
            return prepared != null;
        }
    }

    private final Installation installation;
    private final URI list;
    private volatile Offer known;

    public Updates() {
        this(Installation.current(), listUrl());
    }

    Updates(Installation installation, URI list) {
        this.installation = installation;
        this.list = list;
    }

    public Installation installation() {
        return installation;
    }

    /** The offer the last {@link #check} found, for the title screen to show without asking again. */
    public Optional<Offer> known() {
        return Optional.ofNullable(known);
    }

    /**
     * Asks whether there is anything newer than this build. Blocks on the network (or on git).
     *
     * @return the offer, or empty when this is the newest
     * @throws IOException when it could not tell, with a sentence for the screen
     */
    public Optional<Offer> check() throws IOException {
        Offer offer = installation.kind() == Installation.Kind.SOURCE ? checkSource() : checkRelease();
        known = offer;
        return Optional.ofNullable(offer);
    }

    private Offer checkSource() throws IOException {
        SourceCheckout.Behind behind = new SourceCheckout(installation.location()).behind();
        if (behind.commits() == 0) {
            return null;
        }
        String changes = behind.commits() == 1 ? "1 new change" : behind.commits() + " new changes";
        return new Offer("Update: " + changes, behind.newest(), null, null, null);
    }

    private Offer checkRelease() throws IOException {
        Manifest manifest;
        try {
            manifest = Manifest.parse(Download.text(list));
        } catch (IllegalArgumentException unreadable) {
            throw new IOException("the update list could not be read");
        }
        if (!Versions.isNewer(manifest.version(), GameVersion.current())) {
            return null;
        }
        String headline = "Update to " + manifest.version();
        Optional<Manifest.Entry> entry = manifest.entry(installation.platform());
        if (installation.kind() == Installation.Kind.UNKNOWN || entry.isEmpty()) {
            return new Offer(headline, "from " + RELEASE_PAGE, "Space Case " + manifest.version()
                    + " has no download this copy can install itself. Get it from " + RELEASE_PAGE + ".", null, null);
        }
        String detail = installation.kind() == Installation.Kind.ANDROID
                ? "download, then Android asks to install"
                : "download, install and restart";
        return new Offer(headline, detail, null, entry.get(), list.resolve(entry.get().file()));
    }

    /**
     * Gets the offer onto this machine: the verified download, or the pull for a checkout.
     *
     * @param progress 0..1 as a download arrives, -1 when its size is unknown
     */
    public void prepare(Offer offer, DoubleConsumer progress) throws IOException {
        if (!offer.installable()) {
            throw new IOException(offer.whyNot());
        }
        if (installation.kind() == Installation.Kind.SOURCE) {
            progress.accept(-1);
            new SourceCheckout(installation.location()).pull();
            offer.prepared = installation.location();
            return;
        }
        Path folder = folder();
        String name = installation.kind() == Installation.Kind.ANDROID ? APK + ".verified" : offer.entry.file();
        offer.prepared = Download.fetch(offer.url, folder.resolve(name), offer.entry.sha256(), progress);
    }

    /**
     * Hands the prepared update over.
     *
     * @return true when the game must now exit so the install can finish; false on Android, where
     *         the system's install prompt takes over and the game carries on until it is replaced
     */
    public boolean install(Offer offer) throws IOException {
        if (offer.prepared == null) {
            throw new IOException("nothing has been downloaded yet");
        }
        if (installation.kind() == Installation.Kind.ANDROID) {
            // TvActivity watches for SpaceCase.apk to be moved into place. Moved back first if an
            // earlier prompt was dismissed, so a second press is a second arrival.
            Path apk = offer.prepared.resolveSibling(APK);
            if (Files.exists(apk)) {
                Files.move(apk, offer.prepared, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(offer.prepared, apk, StandardCopyOption.ATOMIC_MOVE);
            return false;
        }
        Install.handOff(installation, offer.prepared, folder().resolve("update.log"));
        return true;
    }

    /**
     * Deletes the downloads an earlier update left: by now they have been installed or refused.
     * The log stays, for whoever wants to know how the last update went.
     */
    public void cleanUp() {
        if (installation.kind() == Installation.Kind.SOURCE) {
            return;
        }
        try {
            Path folder = folder();
            if (!Files.isDirectory(folder)) {
                return;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
                for (Path file : files) {
                    if (!file.getFileName().toString().equals("update.log")) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        } catch (IOException | RuntimeException left) {
            System.err.println("[update] could not clear old downloads: " + left);
        }
    }

    /** Where downloads go: the temp folder, or the Android app's own files, which its installer can read. */
    private Path folder() throws IOException {
        if (installation.kind() != Installation.Kind.ANDROID) {
            return Path.of(System.getProperty("java.io.tmpdir"), "space-case-update");
        }
        try {
            @SuppressWarnings("unchecked")
            Supplier<Path> android = (Supplier<Path>) Class.forName(ANDROID_FOLDER)
                    .getDeclaredConstructor().newInstance();
            Path folder = android.get().resolve("update");
            Files.createDirectories(folder);
            return folder;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            throw new IOException("this build has no storage to download into", unavailable);
        }
    }

    private static URI listUrl() {
        String override = System.getProperty("spacecase.updateUrl");
        return override == null || override.isBlank() ? DEFAULT_LIST : URI.create(override);
    }
}
