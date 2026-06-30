package dev.turtywurty.gamedashboard.platform.impl.individual;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.ExecutableLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.FileSystemsHolder;
import dev.turtywurty.gamedashboard.util.OSUtils;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RobloxPlatform implements Platform {
    private static final int IGDB_ID = 17269;
    private static final String TITLE = "Roblox";
    private static final String PLAYER_EXECUTABLE = "RobloxPlayerBeta.exe";
    private static final String SOBER_FLATPAK_ID = "org.vinegarhq.Sober";
    private static final List<String> MACOS_APP_BUNDLE_NAMES = List.of("Roblox.app", "RobloxPlayer.app");
    private static final List<String> UNINSTALL_REGISTRY_ROOTS = List.of(
            "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKEY_LOCAL_MACHINE\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKEY_LOCAL_MACHINE\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
    );

    @Override
    public Image getIcon() {
        return new Image(Objects.requireNonNull(
                getClass().getResource("/images/platforms/individual/roblox.png")
        ).toExternalForm());
    }

    @Override
    public String getName() {
        return TITLE;
    }

    @Override
    public String getWebsite() {
        return "https://www.roblox.com/";
    }

    @Override
    public String getDescription() {
        return "Roblox is an online platform and game creation system that allows users to design, create, and play games created by other users. It provides a wide range of tools and features for game development, including a scripting language called Lua, a physics engine, and a large library of assets and resources. Roblox has gained immense popularity among players of all ages, offering a diverse range of games across various genres.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding Roblox installation", 1);

            Path found = findInstallDirectory().orElse(null);
            progressMonitor.worked(1);

            if (found == null) {
                progressMonitor.done();
                return;
            }

            addGame(progressMonitor, found);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                manualInstallDirectoryHelp(),
                "roblox",
                false,
                0
        );

        var directoryField = form.addDirectoryField(
                "Roblox installation directory",
                "",
                defaultManualInstallDirectory(),
                "Select Roblox Directory",
                false
        );

        return form.build(progressMonitor -> {
            if (!form.validate(directoryField, Utils::isReadableDirectory, "Select a readable Roblox directory."))
                return;

            form.hideError();
            ManualEntryForm.runAsync(progressMonitor, () -> addGame(progressMonitor, Path.of(directoryField.getText())));
        });
    }

    private static void addGame(ProgressMonitor progressMonitor, Path installDirectory) {
        progressMonitor.start("Fetching Roblox installation details", 2);
        Optional<ExecutableLaunchTarget> launchTarget = findLaunchTarget(installDirectory);
        progressMonitor.worked(1);
        if (launchTarget.isEmpty()) {
            progressMonitor.done();
            return;
        }

        APIConnector.GameResult metadata = findMetadata();
        var game = getGame(metadata, launchTarget.get());

        GameDashboardApp.LOGGER.info("Found Roblox installation at {}", installDirectory);
        Utils.runOnFxThread(() -> {
            if (!Database.getInstance().addGame(game)) {
                Database.getInstance().updateGame(game, game);
            }
        });
        progressMonitor.worked(1);
        progressMonitor.done();
    }

    private static @NotNull Game getGame(APIConnector.GameResult metadata, ExecutableLaunchTarget launchTarget) {
        var game = new Game(
                TITLE,
                metadata == null || metadata.getSummary() == null ? "" : metadata.getSummary(),
                launchTarget
        );
        game.setThumbCoverImageURL(metadata == null ? Utils.PLACEHOLDER_COVER_URL : metadata.getThumbCoverURL());
        game.setCoverImageURL(metadata == null ? Utils.PLACEHOLDER_COVER_URL : metadata.getCoverURL());
        game.setIgdbGameId(metadata == null ? IGDB_ID : metadata.getIgdbGameId());
        game.setNickname(TITLE);
        return game;
    }

    private static Optional<ExecutableLaunchTarget> findLaunchTarget(Path installDirectory) {
        if (OperatingSystem.CURRENT == OperatingSystem.LINUX && isSoberInstallDirectory(installDirectory))
            return findFlatpakExecutable()
                    .map(flatpak -> new ExecutableLaunchTarget(
                            flatpak,
                            List.of("run", SOBER_FLATPAK_ID),
                            flatpak.getParent()
                    ));

        return findExecutable(installDirectory)
                .map(executable -> new ExecutableLaunchTarget(executable, List.of(), executable.getParent()));
    }

    private static Optional<Path> findExecutable(Path installDirectory) {
        for (String relativePath : List.of(
                PLAYER_EXECUTABLE,
                "Contents/MacOS/RobloxPlayer",
                "Contents/MacOS/Roblox",
                "Contents/MacOS/Roblox.app/Contents/MacOS/Roblox",
                "Bin/Roblox.exe",
                "Roblox.exe"
        )) {
            Path executable = installDirectory.resolve(relativePath(relativePath));
            if (Files.isRegularFile(executable))
                return Optional.of(executable);
        }

        try (Stream<Path> files = Files.walk(installDirectory, 4)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> isRobloxExecutable(path.getFileName().toString()))
                    .max(Comparator.comparingLong(RobloxPlatform::lastModifiedMillis));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.warn("Unable to scan Roblox installation at {}", installDirectory, exception);
            return Optional.empty();
        }
    }

    private static Optional<Path> findInstallDirectory() {
        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> findWindowsInstallDirectory();
            case MACOS -> findMacInstallDirectory();
            case LINUX -> findSoberInstallDirectory();
            case UNKNOWN -> Optional.empty();
        };
    }

    private static Optional<Path> findWindowsInstallDirectory() {
        return findInstallDirectoryFromRegistry()
                .or(RobloxPlatform::findInstallDirectoryFromLocalAppData)
                .or(RobloxPlatform::findLegacyInstallDirectory);
    }

    private static Optional<Path> findInstallDirectoryFromRegistry() {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return Optional.empty();

        return UNINSTALL_REGISTRY_ROOTS.stream()
                .flatMap(root -> OSUtils.readRegistryEntries(root, true).stream())
                .filter(RobloxPlatform::isRobloxPlayerEntry)
                .map(entry -> Utils.toPathOrNull(entry.value("InstallLocation")))
                .filter(Objects::nonNull)
                .filter(path -> Files.isRegularFile(path.resolve(PLAYER_EXECUTABLE)))
                .findFirst();
    }

    private static boolean isRobloxPlayerEntry(OSUtils.RegistryEntry entry) {
        String displayName = entry.value("DisplayName");
        return displayName != null && displayName.toLowerCase(Locale.ROOT).contains("roblox player");
    }

    private static Optional<Path> findInstallDirectoryFromLocalAppData() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank())
            return Optional.empty();

        Path versionsDirectory = Path.of(localAppData, "Roblox", "Versions");
        if (!Files.isDirectory(versionsDirectory))
            return Optional.empty();

        try (Stream<Path> children = Files.list(versionsDirectory)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve(PLAYER_EXECUTABLE)))
                    .max(Comparator.comparingLong(path -> lastModifiedMillis(path.resolve(PLAYER_EXECUTABLE))));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.warn("Unable to scan Roblox versions directory at {}", versionsDirectory, exception);
            return Optional.empty();
        }
    }

    private static Optional<Path> findLegacyInstallDirectory() {
        for (Path root : FileSystemsHolder.roots()) {
            Path robloxPath = root.resolve("ProgramData").resolve("Roblox Cooperation").resolve("Roblox");
            if (Files.isDirectory(robloxPath))
                return Optional.of(robloxPath);
        }

        return Optional.empty();
    }

    private static Optional<Path> findMacInstallDirectory() {
        String userHome = System.getProperty("user.home");
        return Stream.of(
                        Path.of("/Applications"),
                        userHome == null || userHome.isBlank() ? null : Path.of(userHome, "Applications")
                )
                .filter(Objects::nonNull)
                .flatMap(directory -> MACOS_APP_BUNDLE_NAMES.stream().map(directory::resolve))
                .filter(RobloxPlatform::isValidMacAppBundle)
                .findFirst();
    }

    private static boolean isValidMacAppBundle(Path appBundle) {
        if (!Files.isDirectory(appBundle))
            return false;

        return findExecutable(appBundle).isPresent()
                || OSUtils.readAppBundleMetadata(appBundle.resolve("Contents").resolve("Info.plist"))
                .map(metadata -> metadata.bundleIdentifier().startsWith("com.roblox."))
                .orElse(false);
    }

    private static Optional<Path> findSoberInstallDirectory() {
        if (findFlatpakExecutable().isEmpty() || !isSoberFlatpakInstalled())
            return Optional.empty();

        return Optional.of(soberDataDirectory())
                .filter(Files::isDirectory)
                .or(() -> findFlatpakExecutable().map(Path::getParent));
    }

    private static boolean isSoberInstallDirectory(Path installDirectory) {
        return soberDataDirectory().equals(installDirectory) || findFlatpakExecutable()
                .map(Path::getParent)
                .filter(installDirectory::equals)
                .isPresent();
    }

    private static Path soberDataDirectory() {
        return Path.of(System.getProperty("user.home"), ".var", "app", SOBER_FLATPAK_ID);
    }

    private static Optional<Path> findFlatpakExecutable() {
        return OSUtils.findCommandPath("flatpak");
    }

    private static boolean isSoberFlatpakInstalled() {
        Optional<Path> flatpak = findFlatpakExecutable();
        if (flatpak.isEmpty())
            return false;

        try {
            Process process = new ProcessBuilder(
                    flatpak.get().toString(),
                    "info",
                    SOBER_FLATPAK_ID
            ).redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.warn("Unable to query Sober Flatpak installation", exception);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String manualInstallDirectoryHelp() {
        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> "Choose the Roblox installation directory. This is normally under %LOCALAPPDATA%\\Roblox\\Versions\\version-*.";
            case MACOS -> "Choose the Roblox app bundle. This is normally /Applications/Roblox.app.";
            case LINUX -> "Choose the Sober data directory. This is normally ~/.var/app/org.vinegarhq.Sober.";
            case UNKNOWN -> "Choose the Roblox installation directory.";
        };
    }

    private static String defaultManualInstallDirectory() {
        if (OperatingSystem.CURRENT == OperatingSystem.MACOS)
            return "/Applications/Roblox.app";

        if (OperatingSystem.CURRENT == OperatingSystem.LINUX)
            return soberDataDirectory().toString();

        String localAppData = System.getenv("LOCALAPPDATA");
        return localAppData == null || localAppData.isBlank()
                ? "C:\\ProgramData\\Roblox Cooperation\\Roblox"
                : Path.of(localAppData, "Roblox", "Versions").toString();
    }

    private static boolean isRobloxExecutable(String filename) {
        return PLAYER_EXECUTABLE.equalsIgnoreCase(filename)
                || "Roblox.exe".equalsIgnoreCase(filename)
                || "RobloxPlayer".equals(filename)
                || "Roblox".equals(filename);
    }

    private static Path relativePath(String value) {
        return Path.of("", value.split("/"));
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0;
        }
    }

    private static APIConnector.GameResult findMetadata() {
        try {
            return APIConnector.getGameByID(IGDB_ID, true, true).join();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to load Roblox metadata from IGDB id {}", IGDB_ID, exception);
            return null;
        }
    }
}
