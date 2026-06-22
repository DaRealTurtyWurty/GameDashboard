package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.UbisoftGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OSUtils;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.scene.image.Image;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UbisoftPlatform implements Platform {
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";
    private static final List<String> INSTALL_ROOTS = List.of(
            "HKLM\\SOFTWARE\\Ubisoft\\Launcher\\Installs",
            "HKLM\\SOFTWARE\\WOW6432Node\\Ubisoft\\Launcher\\Installs"
    );
    private static final List<String> UNINSTALL_ROOTS = List.of(
            "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
    );
    private static final List<String> LAUNCHER_KEYS = List.of(
            "HKLM\\SOFTWARE\\Ubisoft\\Launcher",
            "HKLM\\SOFTWARE\\WOW6432Node\\Ubisoft\\Launcher"
    );
    private static final Pattern GAME_KEY = Pattern.compile("(?i).*\\\\Installs\\\\(\\d+)$");
    private static final Pattern UNINSTALL_GAME_KEY = Pattern.compile("(?i).*\\\\Uplay Install (\\d+)$");

    private static void addGames(ProgressMonitor progressMonitor) {
        addGames(progressMonitor, null);
    }

    private static void addGames(ProgressMonitor progressMonitor, Path launcherExecutable) {
        progressMonitor.start("Reading Ubisoft Connect installations", 1);
        List<UbisoftInstallation> installations = discover(launcherExecutable);
        progressMonitor.worked(1);
        if (installations.isEmpty()) {
            progressMonitor.done();
            return;
        }

        progressMonitor.start("Loading Ubisoft Connect games", installations.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (UbisoftInstallation installation : installations)
                executor.submit(() -> addGame(progressMonitor, installation));
        } finally {
            progressMonitor.done();
        }
    }

    private static void addGame(
            ProgressMonitor progressMonitor,
            UbisoftInstallation installation
    ) {
        try {
            APIConnector.GameResult metadata = findMetadata(installation.title());
            String localIcon = installation.icon() != null && Files.isRegularFile(installation.icon())
                    ? installation.icon().toUri().toString()
                    : null;
            String thumbnail = firstNonBlank(
                    metadata == null ? null : metadata.getThumbCoverURL(),
                    localIcon,
                    PLACEHOLDER_COVER_URL
            );
            String cover = firstNonBlank(
                    usableMetadataUrl(metadata == null ? null : metadata.getCoverURL()),
                    localIcon,
                    PLACEHOLDER_COVER_URL
            );

            UbisoftGame game = UbisoftGame.builder(
                            installation.title(),
                            metadata == null || metadata.getSummary() == null ? "" : metadata.getSummary(),
                            installation.gameId(),
                            installation.installLocation().toString(),
                            installation.launcherExecutable() == null
                                    ? ""
                                    : installation.launcherExecutable().toString(),
                            installation.language()
                    )
                    .images(thumbnail, cover)
                    .igdbGameId(metadata == null ? null : metadata.getIgdbGameId())
                    .nickname(installation.title())
                    .build();

            GameDashboardApp.LOGGER.info(
                    "Found Ubisoft Connect installation '{}' (ID {}) at {}",
                    installation.title(),
                    installation.gameId(),
                    installation.installLocation()
            );
            javafx.application.Platform.runLater(() -> {
                if (!Database.getInstance().addGame(game))
                    Database.getInstance().updateGame(game, game);
            });
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.error(
                    "Failed to load Ubisoft Connect game '{}'",
                    installation.title(),
                    exception
            );
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static APIConnector.GameResult findMetadata(String title) {
        try {
            return APIConnector.findBestFuzzyGameMatch(title, true, true)
                    .join()
                    .gameResult();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to load metadata for Ubisoft title '{}'", title, exception);
            return null;
        }
    }

    private static String usableMetadataUrl(String url) {
        return PLACEHOLDER_COVER_URL.equals(url) ? null : url;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value;
        }
        return "";
    }

    private static List<UbisoftInstallation> discover() {
        return discover(null);
    }

    private static List<UbisoftInstallation> discover(Path launcherExecutableOverride) {
        if (OperatingSystem.getCurrent() != OperatingSystem.WINDOWS)
            return List.of();

        List<OSUtils.RegistryEntry> installEntries = queryAll(INSTALL_ROOTS, true);
        if (installEntries.isEmpty())
            return List.of();

        Map<Integer, OSUtils.RegistryEntry> uninstallEntries = readUninstallEntries(installEntries);
        Path launcherExecutable = launcherExecutableOverride != null
                && Files.isRegularFile(launcherExecutableOverride)
                ? launcherExecutableOverride
                : findLauncherExecutable().orElse(null);

        return createInstallations(installEntries, uninstallEntries, launcherExecutable);
    }

    private static List<UbisoftInstallation> discoverFromRegistryOutput(
            String installsOutput,
            String uninstallOutput,
            Path launcherExecutable
    ) {
        Map<Integer, OSUtils.RegistryEntry> uninstallEntries = indexByGameId(
                parseRegistryOutput(uninstallOutput),
                UNINSTALL_GAME_KEY
        );
        return createInstallations(parseRegistryOutput(installsOutput), uninstallEntries, launcherExecutable);
    }

    private static List<UbisoftInstallation> createInstallations(
            List<OSUtils.RegistryEntry> installEntries,
            Map<Integer, OSUtils.RegistryEntry> uninstallEntries,
            Path launcherExecutable
    ) {
        Map<Integer, UbisoftInstallation> installations = new LinkedHashMap<>();
        installEntries.stream()
                .map(entry -> createInstallation(entry, uninstallEntries, launcherExecutable))
                .flatMap(Optional::stream)
                .forEach(installation -> installations.putIfAbsent(installation.gameId(), installation));
        return installations.values().stream()
                .sorted(Comparator.comparing(UbisoftInstallation::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Optional<UbisoftInstallation> createInstallation(
            OSUtils.RegistryEntry installEntry,
            Map<Integer, OSUtils.RegistryEntry> uninstallEntries,
            Path launcherExecutable
    ) {
        Integer gameId = gameId(installEntry.key(), GAME_KEY);
        if (gameId == null)
            return Optional.empty();

        OSUtils.RegistryEntry uninstallEntry = uninstallEntries.get(gameId);
        Path installLocation = toPath(firstNonBlank(
                installEntry.value("InstallDir"),
                uninstallEntry == null ? null : uninstallEntry.value("InstallLocation")
        ));
        if (installLocation == null || !Files.isDirectory(installLocation)) {
            GameDashboardApp.LOGGER.warn("Ubisoft game {} has no readable installation directory", gameId);
            return Optional.empty();
        }

        String title = uninstallEntry == null ? null : uninstallEntry.value("DisplayName");
        if (title == null || title.isBlank())
            title = installLocation.getFileName() == null ? "Ubisoft game " + gameId : installLocation.getFileName().toString();

        Path icon = uninstallEntry == null ? null : displayIcon(uninstallEntry.value("DisplayIcon"));
        String website = uninstallEntry == null ? null : uninstallEntry.value("URLInfoAbout");
        return Optional.of(new UbisoftInstallation(
                gameId,
                title.trim(),
                installLocation,
                installEntry.value("Language"),
                icon,
                website,
                launcherExecutable
        ));
    }

    private static Optional<Path> findLauncherExecutable() {
        for (String key : LAUNCHER_KEYS) {
            List<OSUtils.RegistryEntry> entries = query(key, false);
            if (entries.isEmpty())
                continue;

            Path installDirectory = toPath(entries.getFirst().value("InstallDir"));
            if (installDirectory == null)
                continue;

            Path executable = installDirectory.resolve("UbisoftConnect.exe");
            if (Files.isRegularFile(executable))
                return Optional.of(executable);
        }
        return Optional.empty();
    }

    private static boolean isValidLauncherDirectory(Path directory) {
        return directory != null && Files.isRegularFile(directory.resolve("UbisoftConnect.exe"));
    }

    private static Map<Integer, OSUtils.RegistryEntry> readUninstallEntries(List<OSUtils.RegistryEntry> installEntries) {
        Map<Integer, OSUtils.RegistryEntry> uninstallEntries = new HashMap<>();
        for (OSUtils.RegistryEntry installEntry : installEntries) {
            Integer gameId = gameId(installEntry.key(), GAME_KEY);
            if (gameId == null || uninstallEntries.containsKey(gameId))
                continue;

            for (String root : UNINSTALL_ROOTS) {
                List<OSUtils.RegistryEntry> entries = query(root + "\\Uplay Install " + gameId, false);
                if (!entries.isEmpty()) {
                    uninstallEntries.put(gameId, entries.getFirst());
                    break;
                }
            }
        }
        return uninstallEntries;
    }

    private static List<OSUtils.RegistryEntry> queryAll(List<String> roots, boolean recursive) {
        List<OSUtils.RegistryEntry> entries = new ArrayList<>();
        for (String root : roots)
            entries.addAll(query(root, recursive));
        return entries;
    }

    private static List<OSUtils.RegistryEntry> query(String root, boolean recursive) {
        return OSUtils.readRegistryEntries(root, recursive);
    }

    private static List<OSUtils.RegistryEntry> parseRegistryOutput(String output) {
        return OSUtils.parseRegistryOutput(output);
    }

    private static Map<Integer, OSUtils.RegistryEntry> indexByGameId(
            List<OSUtils.RegistryEntry> entries,
            Pattern pattern
    ) {
        Map<Integer, OSUtils.RegistryEntry> indexed = new HashMap<>();
        for (OSUtils.RegistryEntry entry : entries) {
            Integer gameId = gameId(entry.key(), pattern);
            if (gameId != null)
                indexed.putIfAbsent(gameId, entry);
        }
        return indexed;
    }

    private static Integer gameId(String key, Pattern pattern) {
        Matcher matcher = pattern.matcher(key);
        if (!matcher.matches())
            return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Path displayIcon(String value) {
        if (value == null || value.isBlank())
            return null;
        String path = value.trim();
        if (path.startsWith("\"") && path.indexOf('"', 1) > 1)
            path = path.substring(1, path.indexOf('"', 1));
        else if (path.lastIndexOf(',') > 2)
            path = path.substring(0, path.lastIndexOf(','));
        return toPath(path);
    }

    private static Path toPath(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return Path.of(value.replace("\"", "").trim()).normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    @Override
    public Image getIcon() {
        return new Image(Objects.requireNonNull(
                getClass().getResource("/images/platforms/ubisoft.png")
        ).toExternalForm());
    }

    @Override
    public String getName() {
        return "Ubisoft Connect";
    }

    @Override
    public String getWebsite() {
        return "https://www.ubisoft.com/";
    }

    @Override
    public String getDescription() {
        return "Ubisoft Connect is a digital distribution platform developed by Ubisoft, offering access to Ubisoft games, rewards, multiplayer services, and social features.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return UbisoftPlatform::addGames;
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the Ubisoft Connect installation directory containing UbisoftConnect.exe. "
                        + "Installed games are read from Ubisoft's Windows registry entries.",
                null,
                false,
                8
        );

        Path defaultDirectory = findLauncherExecutable().map(Path::getParent).orElse(null);
        var directoryField = form.addDirectoryField(
                "Ubisoft Connect installation directory",
                defaultDirectory == null ? "" : defaultDirectory.toString(),
                "Ubisoft Connect installation directory",
                "Select Ubisoft Connect Installation Directory",
                false
        );

        return form.build(progressMonitor -> {
            Optional<Path> selectedDirectory = form.readPath(
                    directoryField,
                    "Select a valid Ubisoft Connect installation directory."
            );
            if (selectedDirectory.isEmpty())
                return;
            Path directory = selectedDirectory.get();

            if (!isValidLauncherDirectory(directory)) {
                form.showError("The selected directory does not contain UbisoftConnect.exe.");
                return;
            }

            form.hideError();
            Path launcherExecutable = directory.resolve("UbisoftConnect.exe");
            Thread.ofVirtual().start(() -> {
                try {
                    addGames(progressMonitor, launcherExecutable);
                } catch (RuntimeException exception) {
                    GameDashboardApp.LOGGER.error("Ubisoft Connect manual discovery failed", exception);
                    progressMonitor.done();
                }
            });
        });
    }

    private record UbisoftInstallation(
            int gameId,
            String title,
            Path installLocation,
            String language,
            Path icon,
            String website,
            Path launcherExecutable
    ) {
    }
}
