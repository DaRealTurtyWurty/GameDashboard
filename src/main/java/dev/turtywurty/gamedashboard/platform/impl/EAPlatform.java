package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.EAAppGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OSUtils;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.scene.image.Image;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EAPlatform implements Platform {
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";
    private static final List<String> REGISTRY_ROOTS = List.of(
            "HKLM\\SOFTWARE\\EA Games",
            "HKLM\\SOFTWARE\\WOW6432Node\\EA Games",
            "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
    );
    private static final Pattern COPYRIGHT_YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final byte[] LEGAL_COPYRIGHT_KEY = "LegalCopyright".getBytes(StandardCharsets.UTF_16LE);
    private static final int VERSION_VALUE_BYTES = 512;
    private static final Set<String> EXECUTABLE_EXCLUSIONS = Set.of(
            "cleanup", "unins", "uninstall", "installer", "update", "updater", "crash", "reporter"
    );

    private static void addGames(ProgressMonitor progressMonitor, Path dataDirectory) {
        progressMonitor.start("Reading EA app installations", 1);
        List<EAInstallation> installations = discover(dataDirectory);
        progressMonitor.worked(1);
        if (installations.isEmpty()) {
            progressMonitor.done();
            return;
        }

        EAArtworkCache artworkCache = EAArtworkCache.loadDefault();
        progressMonitor.start("Loading EA app games", installations.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (EAInstallation installation : installations)
                executor.submit(() -> addGame(progressMonitor, installation, artworkCache));
        } finally {
            progressMonitor.done();
        }
    }

    private static void addGame(
            ProgressMonitor progressMonitor,
            EAInstallation installation,
            EAArtworkCache artworkCache
    ) {
        try {
            EAArtworkCache.Artwork artwork = artworkCache.find(installation.title());
            APIConnector.GameResult metadata = findMetadata(installation);

            String thumbnail = firstNonBlank(
                    artwork.squareUrl(),
                    metadata == null ? null : metadata.getThumbCoverURL(),
                    artwork.portraitUrl(),
                    PLACEHOLDER_COVER_URL
            );
            String cover = selectCover(artwork, metadata);
            String description = metadata == null || metadata.getSummary() == null
                    ? ""
                    : metadata.getSummary();

            EAAppGame game = EAAppGame.builder(
                            installation.title(),
                            description,
                            installation.executable().toString(),
                            installation.softwareId(),
                            installation.installDataPath().toString()
                    )
                    .images(thumbnail, cover)
                    .coverLogo(artwork.logoUrl())
                    .igdbGameId(metadata == null ? null : metadata.getIgdbGameId())
                    .nickname(installation.title())
                    .build();

            GameDashboardApp.LOGGER.info(
                    "Found EA app installation '{}' at {}",
                    installation.title(),
                    installation.installLocation()
            );
            javafx.application.Platform.runLater(() -> {
                if (!Database.getInstance().addGame(game))
                    Database.getInstance().updateGame(game, game);
            });
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.error("Failed to load EA app game '{}'", installation.title(), exception);
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static String selectCover(EAArtworkCache.Artwork artwork, APIConnector.GameResult metadata) {
        return firstNonBlank(
                usableMetadataUrl(metadata == null ? null : metadata.getCoverURL()),
                artwork.squareUrl(),
                artwork.portraitUrl(),
                PLACEHOLDER_COVER_URL
        );
    }

    private static String usableMetadataUrl(String url) {
        return PLACEHOLDER_COVER_URL.equals(url) ? null : url;
    }

    private static APIConnector.GameResult findMetadata(EAInstallation installation) {
        try {
            Integer releaseYear = findReleaseYear(installation.executable()).orElse(null);
            GameDashboardApp.LOGGER.info(
                    "IGDB matching EA title '{}' with executable release year {}",
                    installation.title(),
                    releaseYear
            );
            return APIConnector.findBestFuzzyGameMatch(
                    installation.title(), true, true, null, releaseYear
            ).join().gameResult();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn(
                    "Unable to load fallback metadata for EA title '{}'",
                    installation.title(),
                    exception
            );
            return null;
        }
    }

    private static Path getDefaultDataDirectory() {
        if (OperatingSystem.getCurrent() != OperatingSystem.WINDOWS)
            return null;
        String programData = System.getenv("PROGRAMDATA");
        return Path.of(programData == null || programData.isBlank() ? "C:\\ProgramData" : programData, "EA Desktop");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value;
        }
        return "";
    }

    private static List<EAInstallation> discover(Path eaDesktopDataDirectory) {
        Path installData = eaDesktopDataDirectory.resolve("InstallData");
        if (!Files.isDirectory(installData))
            return List.of();

        List<OSUtils.RegistryEntry> registryEntries = readRegistryEntries();
        try (Stream<Path> children = Files.list(installData)) {
            return children
                    .filter(Files::isDirectory)
                    .map(path -> createInstallation(path, registryEntries))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(EAInstallation::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read EA install data at {}", installData, exception);
            return List.of();
        }
    }

    private static boolean isValidDataDirectory(Path path) {
        return path != null && Files.isDirectory(path.resolve("InstallData"));
    }

    private static Optional<EAInstallation> createInstallation(
            Path installDataPath,
            List<OSUtils.RegistryEntry> registryEntries
    ) {
        String folderName = installDataPath.getFileName().toString();
        OSUtils.RegistryEntry registryEntry = registryEntries.stream()
                .max(Comparator.comparingInt(entry -> matchScore(folderName, entry)))
                .filter(entry -> matchScore(folderName, entry) >= 45)
                .orElse(null);

        // reg.exe transliterates symbols such as the trademark sign to a literal "T"
        // when its output is redirected. InstallData directory names are the stable,
        // uncorrupted product names maintained by the EA app itself.
        String title = cleanTitle(folderName);
        Path installLocation = registryEntry == null ? null : getInstallLocation(registryEntry);
        if (installLocation == null || !Files.isDirectory(installLocation)) {
            GameDashboardApp.LOGGER.warn("EA install '{}' has no readable installation directory", title);
            return Optional.empty();
        }

        Path executable = registryEntry == null ? null : getDisplayIcon(registryEntry);
        if (executable == null || !Files.isRegularFile(executable))
            executable = findLikelyExecutable(installLocation, title).orElse(null);
        if (executable == null) {
            GameDashboardApp.LOGGER.warn("EA install '{}' has no launch executable", title);
            return Optional.empty();
        }

        return Optional.of(new EAInstallation(
                title,
                findSoftwareId(installDataPath).orElse(normalize(folderName)),
                installDataPath,
                installLocation,
                executable
        ));
    }

    private static Optional<Integer> findReleaseYear(Path executable) {
        if (executable == null || !Files.isRegularFile(executable))
            return Optional.empty();

        try (InputStream input = new BufferedInputStream(Files.newInputStream(executable))) {
            int matchedBytes = 0;
            int value;
            while ((value = input.read()) >= 0) {
                if ((byte) value == LEGAL_COPYRIGHT_KEY[matchedBytes]) {
                    matchedBytes++;
                    if (matchedBytes == LEGAL_COPYRIGHT_KEY.length)
                        return findPlausibleYear(input.readNBytes(VERSION_VALUE_BYTES));
                } else {
                    matchedBytes = (byte) value == LEGAL_COPYRIGHT_KEY[0] ? 1 : 0;
                }
            }
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.debug("Unable to read version metadata from {}", executable, exception);
        }
        return Optional.empty();
    }

    private static Optional<Integer> findPlausibleYear(byte[] versionValue) {
        String value = new String(versionValue, StandardCharsets.UTF_16LE);
        Matcher matcher = COPYRIGHT_YEAR.matcher(value);
        if (!matcher.find())
            return Optional.empty();

        int year = Integer.parseInt(matcher.group(1));
        return year >= 1970 && year <= Year.now().getValue() + 1
                ? Optional.of(year)
                : Optional.empty();
    }

    private static List<OSUtils.RegistryEntry> readRegistryEntries() {
        List<OSUtils.RegistryEntry> entries = new ArrayList<>();
        for (String root : REGISTRY_ROOTS)
            entries.addAll(OSUtils.readRegistryEntries(root, true));
        return entries;
    }

    private static int matchScore(String folderName, OSUtils.RegistryEntry entry) {
        String target = normalize(folderName);
        int score = nameScore(target, normalize(entry.keyName()));
        score = Math.max(score, nameScore(target, normalize(entry.value("DisplayName"))));

        Path installLocation = getInstallLocation(entry);
        if (installLocation != null && installLocation.getFileName() != null)
            score = Math.max(score, nameScore(target, normalize(installLocation.getFileName().toString())) + 20);
        if (getDisplayIcon(entry) != null)
            score += 5;
        return score;
    }

    private static int nameScore(String left, String right) {
        if (left.isBlank() || right.isBlank())
            return 0;
        if (left.equals(right))
            return 100;
        if (left.contains(right) || right.contains(left))
            return 75;

        Set<String> leftTokens = new LinkedHashSet<>(List.of(left.split(" ")));
        Set<String> rightTokens = new LinkedHashSet<>(List.of(right.split(" ")));
        leftTokens.removeIf(String::isBlank);
        rightTokens.removeIf(String::isBlank);
        if (leftTokens.isEmpty() || rightTokens.isEmpty())
            return 0;

        long common = leftTokens.stream().filter(rightTokens::contains).count();
        return (int) Math.round(common * 70.0 / Math.max(leftTokens.size(), rightTokens.size()));
    }

    private static Optional<String> findSoftwareId(Path installDataPath) {
        try (Stream<Path> children = Files.list(installDataPath)) {
            return children
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("base-") && name.length() > 5)
                    .map(name -> name.substring(5))
                    .findFirst();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static Path getInstallLocation(OSUtils.RegistryEntry entry) {
        String value = firstNonBlank(entry.value("InstallLocation"), entry.value("Install Dir"));
        return toPath(value);
    }

    private static Path getDisplayIcon(OSUtils.RegistryEntry entry) {
        String value = entry.value("DisplayIcon");
        if (value == null || value.isBlank())
            return null;

        value = value.trim();
        if (value.startsWith("\"") && value.indexOf('"', 1) > 1)
            value = value.substring(1, value.indexOf('"', 1));
        else if (value.lastIndexOf(',') > 2)
            value = value.substring(0, value.lastIndexOf(','));
        return toPath(value);
    }

    private static Path toPath(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return Path.of(value.replace("\"", "").trim()).normalize();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Optional<Path> findLikelyExecutable(Path installLocation, String title) {
        String normalizedTitle = normalize(title).replace(" ", "");
        try (Stream<Path> files = Files.walk(installLocation, 4)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe"))
                    .filter(path -> EXECUTABLE_EXCLUSIONS.stream().noneMatch(exclusion ->
                            path.getFileName().toString().toLowerCase(Locale.ROOT).contains(exclusion)))
                    .max(Comparator.comparingInt(path -> executableScore(path, normalizedTitle)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static int executableScore(Path path, String normalizedTitle) {
        String fileName = normalize(path.getFileName().toString().replaceFirst("(?i)\\.exe$", ""))
                .replace(" ", "");
        int score = normalizedTitle.contains(fileName) || fileName.contains(normalizedTitle) ? 50 : 0;
        String fullPath = path.toString().toLowerCase(Locale.ROOT);
        if (fullPath.contains("\\game\\bin\\"))
            score += 20;
        if (fileName.contains("launcher"))
            score += 5;
        return score;
    }

    public static String normalize(String value) {
        if (value == null)
            return "";
        return value.toLowerCase(Locale.ROOT)
                .replace("™", "")
                .replace("®", "")
                .replace("(tm)", "")
                .replace("&", " and ")
                .replaceAll("\\biii\\b", "3")
                .replaceAll("\\bii\\b", "2")
                .replaceAll("\\biv\\b", "4")
                .replaceAll("\\bv\\b", "5")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String cleanTitle(String value) {
        if (value == null)
            return "";
        return value
                .replaceAll("(?i)\\(TM\\)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    @Override
    public Image getIcon() {
        return new Image(getClass().getResource("/images/platforms/ea_app.png").toExternalForm());
    }

    @Override
    public String getName() {
        return "EA app";
    }

    @Override
    public String getWebsite() {
        return "https://www.ea.com/ea-app";
    }

    @Override
    public String getDescription() {
        return "The EA app is a digital distribution platform developed by Electronic Arts, providing access to EA games, subscriptions, downloads, and social features.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            Path dataDirectory = getDefaultDataDirectory();
            if (dataDirectory == null || !isValidDataDirectory(dataDirectory)) {
                progressMonitor.start("Finding EA app installation data", 1);
                progressMonitor.worked(1);
                progressMonitor.done();
                return;
            }
            addGames(progressMonitor, dataDirectory);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the EA Desktop data directory containing InstallData. This is normally C:\\ProgramData\\EA Desktop.",
                "ea-app",
                false,
                0
        );

        Path defaultDirectory = getDefaultDataDirectory();
        var directoryField = form.addDirectoryField(
                "EA Desktop data directory",
                defaultDirectory == null ? "" : defaultDirectory.toString(),
                null,
                "Select EA Desktop Data Directory",
                false
        );

        return form.build(progressMonitor -> {
            Optional<Path> selectedDirectory = form.readPath(
                    directoryField,
                    "Select a valid EA Desktop data directory."
            );
            if (selectedDirectory.isEmpty())
                return;
            Path directory = selectedDirectory.get();
            if (!isValidDataDirectory(directory)) {
                form.showError("The selected directory does not contain InstallData.");
                return;
            }

            form.hideError();
            ManualEntryForm.runAsync(progressMonitor, () -> addGames(progressMonitor, directory));
        });
    }

    private record EAInstallation(
            String title,
            String softwareId,
            Path installDataPath,
            Path installLocation,
            Path executable
    ) {
    }
}
