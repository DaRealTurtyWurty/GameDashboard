package dev.turtywurty.gamedashboard.platform.impl;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.EpicGamesGame;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class EpicGamesPlatform implements Platform {
    private static final Gson GSON = new Gson();
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";

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
                            result == null ? PLACEHOLDER_COVER_URL : result.getThumbCoverURL(),
                            result == null ? PLACEHOLDER_COVER_URL : result.getCoverURL()
                    )
                    .nickname(title)
                    .build();
            javafx.application.Platform.runLater(() -> Database.getInstance().addGame(game));
        } catch (Exception exception) {
            GameDashboardApp.LOGGER.error("Failed to read Epic Games manifest: {}", manifestPath, exception);
        } finally {
            progressMonitor.worked(1);
        }
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var description = new Label(
                "Choose the Epic Games manifests directory used to find your Epic Games library.");
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        var header = new VBox(description);
        header.getStyleClass().add("dialog-header");

        var errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);

        Path defaultManifestsDirectory = getDefaultManifestsDirectory();
        var manifestsDirectoryField = new TextField(
                defaultManifestsDirectory != null && isValidManifestsDirectory(defaultManifestsDirectory)
                        ? defaultManifestsDirectory.toString()
                        : ""
        );
        manifestsDirectoryField.setPromptText(defaultManifestsDirectory == null ? "" : defaultManifestsDirectory.toString());
        HBox.setHgrow(manifestsDirectoryField, Priority.ALWAYS);
        var manifestsDirectoryCard = createPathCard(
                "Epic Games manifests directory",
                manifestsDirectoryField,
                "Select Epic Games Manifests Directory",
                () -> chooseManifestsDirectory(manifestsDirectoryField, errorLabel)
        );

        hideError(errorLabel);

        var content = new VBox(header, manifestsDirectoryCard, errorLabel);
        content.getStyleClass().add("epic-games-configuration-content");
        VBox.setMargin(manifestsDirectoryCard, new Insets(8, 0, 0, 0));

        return new ManualEntryView(content, progressMonitor -> {
            String manifestsDirectory = manifestsDirectoryField.getText();
            if (!isValidManifestsDirectory(manifestsDirectory)) {
                showError(errorLabel, "Select a valid Epic Games manifests directory.");
                return;
            }

            hideError(errorLabel);
            CompletableFuture.runAsync(() -> addEpicGames(progressMonitor, Path.of(manifestsDirectory)))
                    .exceptionally(throwable -> {
                        progressMonitor.done();
                        return null;
                    });
        });
    }

    private VBox createPathCard(String labelText, TextField field, String chooserTitle, Runnable browseAction) {
        var label = new Label(labelText);
        label.getStyleClass().add("field-label");

        var browseButton = new Button("Browse");
        browseButton.getStyleClass().add("browse-button");
        browseButton.setAccessibleText(chooserTitle);
        browseButton.setOnAction(event -> browseAction.run());

        var row = new HBox(field, browseButton);
        row.getStyleClass().add("epic-games-location-row");
        row.setAlignment(Pos.CENTER_LEFT);

        var card = new VBox(label, row);
        card.getStyleClass().add("epic-games-location-card");
        return card;
    }

    private void chooseManifestsDirectory(TextField manifestsDirectoryField, Label errorLabel) {
        var chooser = new DirectoryChooser();
        chooser.setTitle("Select Epic Games Manifests Directory");

        String currentPath = manifestsDirectoryField.getText();
        File currentDirectory = currentPath == null || currentPath.isBlank() ? null : new File(currentPath);
        File initialDirectory = currentDirectory != null && currentDirectory.isDirectory()
                ? currentDirectory
                : new File(System.getProperty("user.home"));
        if (initialDirectory.isDirectory()) {
            chooser.setInitialDirectory(initialDirectory);
        }

        File selectedDirectory = chooser.showDialog(manifestsDirectoryField.getScene().getWindow());
        if (selectedDirectory != null) {
            manifestsDirectoryField.setText(selectedDirectory.getAbsolutePath());
            hideError(errorLabel);
        }
    }

    private static Path getDefaultManifestsDirectory() {
        return switch (OperatingSystem.getCurrent()) {
            case WINDOWS ->
                    Path.of(getProgramDataPath(), "Epic", "EpicGamesLauncher", "Data", "Manifests");
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
        if (manifestsDirectory == null || manifestsDirectory.isBlank())
            return false;

        try {
            return isValidManifestsDirectory(Path.of(manifestsDirectory));
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private static boolean isValidManifestsDirectory(Path manifestsDirectory) {
        return Files.isDirectory(manifestsDirectory);
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

    private void hideError(Label errorLabel) {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
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
