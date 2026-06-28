package dev.turtywurty.gamedashboard.platform.impl;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.EpicGamesGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.scene.image.Image;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class EpicGamesPlatform implements Platform {
    private static final Gson GSON = new Gson();

    private static void addEpicGames(ProgressMonitor progressMonitor, Path path) {
        if (!isValidManifestsDirectory(path)) {
            progressMonitor.done();
            return;
        }

        try (var stream = Files.list(path).filter(EpicGamesPlatform::isManifestFile)) {
            List<Path> files = stream.toList();
            progressMonitor.worked(1);
            if (files.isEmpty()) {
                progressMonitor.done();
                return;
            }

            progressMonitor.start("Loading Epic Games games", files.size());
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Path manifestPath : files) {
                    executor.submit(() -> addEpicGame(progressMonitor, manifestPath));
                }
            }
            progressMonitor.done();
        } catch (Exception exception) {
            progressMonitor.done();
            GameDashboardApp.LOGGER.error("Failed to read Epic Games manifests", exception);
        }
    }

    private static void addEpicGame(ProgressMonitor progressMonitor, Path manifestPath) {
        try {
            EpicManifest manifest = GSON.fromJson(Files.readString(manifestPath), EpicManifest.class);
            if (!isValidManifest(manifest))
                return;

            String title = getDisplayTitle(manifest);
            String executable = getExecutionCommand(manifest);
            GameDashboardApp.LOGGER.info("Found Epic Games installation: {}", manifest.installLocation());

            APIConnector.GameResult result = searchGame(title);

            var game = EpicGamesGame.builder(
                            title,
                            result == null ? "" : result.getSummary(),
                            executable,
                            manifestPath
                    )
                    .images(
                            result == null ? Utils.PLACEHOLDER_COVER_URL : result.getThumbCoverURL(),
                            result == null ? Utils.PLACEHOLDER_COVER_URL : result.getCoverURL()
                    )
                    .igdbGameId(result == null ? null : result.getIgdbGameId())
                    .nickname(title)
                    .build();
            Utils.runOnFxThread(() -> Database.getInstance().addGame(game));
        } catch (Exception exception) {
            GameDashboardApp.LOGGER.error("Failed to read Epic Games manifest: {}", manifestPath, exception);
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static Path getDefaultManifestsDirectory() {
        return switch (OperatingSystem.getCurrent()) {
            case WINDOWS -> Path.of(getProgramDataPath(), "Epic", "EpicGamesLauncher", "Data", "Manifests");
            case MACOS ->
                    Path.of(System.getProperty("user.home"), "Library", "Application Support", "Epic", "EpicGamesLauncher", "Data", "Manifests");
            default -> null;
        };
    }

    private static String getProgramDataPath() {
        String programData = System.getenv("PROGRAMDATA");
        return programData == null || programData.isBlank() ? "C:\\ProgramData" : programData;
    }

    private static boolean isValidManifestsDirectory(String manifestsDirectory) {
        return isValidManifestsDirectory(Utils.toPathOrNull(manifestsDirectory));
    }

    private static boolean isValidManifestsDirectory(Path manifestsDirectory) {
        return manifestsDirectory != null && Files.isDirectory(manifestsDirectory);
    }

    private static boolean isManifestFile(Path file) {
        Path fileName = file.getFileName();
        return Files.isRegularFile(file)
                && fileName != null
                && fileName.toString().endsWith(".item");
    }

    private static boolean isValidManifest(EpicManifest manifest) {
        return manifest != null
                && manifest.installLocation() != null
                && !manifest.installLocation().isBlank()
                && manifest.launchExecutable() != null
                && !manifest.launchExecutable().isBlank()
                && isGame(manifest)
                && !getDisplayTitle(manifest).isBlank();
    }

    private static boolean isGame(EpicManifest manifest) {
        return manifest.appCategories() != null
                && manifest.appCategories().stream()
                .anyMatch(category -> category != null && category.toLowerCase(Locale.ROOT).equals("games"));
    }

    private static String getDisplayTitle(EpicManifest manifest) {
        String displayName = manifest.displayName();
        if (displayName != null && !displayName.isBlank())
            return displayName;

        String appName = manifest.appName();
        return appName == null ? "" : appName;
    }

    private static String getExecutionCommand(EpicManifest manifest) {
        return Path.of(manifest.installLocation(), manifest.launchExecutable()).toString();
    }

    private static APIConnector.GameResult searchGame(String title) {
        APIConnector.GameResult result = APIConnector.findBestFuzzyGameMatch(title, true, true)
                .join()
                .gameResult();
        if (result == null) {
            GameDashboardApp.LOGGER.warn(
                    "No confident metadata match found for Epic Games title '{}'; using placeholder metadata",
                    title
            );
        }

        return result;
    }

    @Override
    public Image getIcon() {
        return new Image(getClass().getResource("/images/platforms/epic_games.png").toExternalForm());
    }

    @Override
    public String getName() {
        return "Epic Games";
    }

    @Override
    public String getWebsite() {
        return "https://www.epicgames.com/store/en-US/";
    }

    @Override
    public String getDescription() {
        return "Epic Games is a digital distribution platform and game development company known for its popular games, including Fortnite, and the Unreal Engine, a widely used game development engine.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding Epic Games installation", 1);
            Path path = getDefaultManifestsDirectory();
            if (path == null) {
                progressMonitor.done();
                return;
            }

            addEpicGames(progressMonitor, path);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the Epic Games manifests directory used to find your Epic Games library.",
                "epic-games",
                true,
                0
        );

        Path defaultManifestsDirectory = getDefaultManifestsDirectory();
        var manifestsDirectoryField = form.addDirectoryField(
                "Epic Games manifests directory",
                defaultManifestsDirectory != null && isValidManifestsDirectory(defaultManifestsDirectory)
                        ? defaultManifestsDirectory.toString() : "",
                defaultManifestsDirectory == null ? "" : defaultManifestsDirectory.toString(),
                "Select Epic Games Manifests Directory",
                true
        );

        return form.build(progressMonitor -> {
            String manifestsDirectory = manifestsDirectoryField.getText();
            if (!form.validate(
                    manifestsDirectoryField,
                    EpicGamesPlatform::isValidManifestsDirectory,
                    "Select a valid Epic Games manifests directory."
            ))
                return;

            form.hideError();
            ManualEntryForm.runAsync(progressMonitor, () -> addEpicGames(progressMonitor, Path.of(manifestsDirectory)));
        });
    }

    public record EpicManifest(
            @SerializedName("AppName")
            String appName,
            @SerializedName("InstallLocation")
            String installLocation,
            @SerializedName("MainGameAppName")
            String mainGameAppName,
            @SerializedName("LaunchExecutable")
            String launchExecutable,
            @SerializedName("DisplayName")
            String displayName,
            @SerializedName("AppCategories")
            List<String> appCategories
    ) {
    }
}
