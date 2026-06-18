package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.EAAppGame;
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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class EAPlatform implements Platform {
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";

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
        return "Discover games installed by the EA app, including local launch executables and EA catalog artwork cached by the client.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            Path dataDirectory = getDefaultDataDirectory();
            if (dataDirectory == null || !EAInstallDiscovery.isValidDataDirectory(dataDirectory)) {
                progressMonitor.start("Finding EA app installation data", 1);
                progressMonitor.worked(1);
                progressMonitor.done();
                return;
            }
            addGames(progressMonitor, dataDirectory);
        };
    }

    private static void addGames(ProgressMonitor progressMonitor, Path dataDirectory) {
        progressMonitor.start("Reading EA app installations", 1);
        List<EAInstallDiscovery.EAInstallation> installations = EAInstallDiscovery.discover(dataDirectory);
        progressMonitor.worked(1);
        if (installations.isEmpty()) {
            progressMonitor.done();
            return;
        }

        EAArtworkCache artworkCache = EAArtworkCache.loadDefault();
        progressMonitor.start("Loading EA app games", installations.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (EAInstallDiscovery.EAInstallation installation : installations)
                executor.submit(() -> addGame(progressMonitor, installation, artworkCache));
        } finally {
            progressMonitor.done();
        }
    }

    private static void addGame(
            ProgressMonitor progressMonitor,
            EAInstallDiscovery.EAInstallation installation,
            EAArtworkCache artworkCache
    ) {
        try {
            EAArtworkCache.Artwork artwork = artworkCache.find(installation.title());
            APIConnector.GameResult metadata = findMetadata(installation.title());

            String thumbnail = firstNonBlank(
                    artwork.squareUrl(),
                    metadata == null ? null : metadata.getThumbCoverURL(),
                    PLACEHOLDER_COVER_URL
            );
            String cover = firstNonBlank(
                    artwork.portraitUrl(),
                    metadata == null ? null : metadata.getCoverURL(),
                    artwork.squareUrl(),
                    PLACEHOLDER_COVER_URL
            );
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

    private static APIConnector.GameResult findMetadata(String title) {
        try {
            return APIConnector.findBestFuzzyGameMatch(title, true, true).join().gameResult();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to load fallback metadata for EA title '{}'", title, exception);
            return null;
        }
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var description = new Label(
                "Choose the EA Desktop data directory containing InstallData. This is normally C:\\ProgramData\\EA Desktop."
        );
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        var errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        hideError(errorLabel);

        Path defaultDirectory = getDefaultDataDirectory();
        var directoryField = new TextField(defaultDirectory == null ? "" : defaultDirectory.toString());
        HBox.setHgrow(directoryField, Priority.ALWAYS);

        var browseButton = new Button("Browse");
        browseButton.getStyleClass().add("browse-button");
        browseButton.setOnAction(event -> chooseDirectory(directoryField, errorLabel));

        var row = new HBox(directoryField, browseButton);
        row.getStyleClass().add("ea-app-location-row");
        row.setAlignment(Pos.CENTER_LEFT);

        var fieldLabel = new Label("EA Desktop data directory");
        fieldLabel.getStyleClass().add("field-label");
        var fieldCard = new VBox(fieldLabel, row);
        fieldCard.getStyleClass().add("ea-app-location-card");

        var content = new VBox(description, fieldCard, errorLabel);
        content.getStyleClass().add("ea-app-configuration-content");
        VBox.setMargin(fieldCard, new Insets(8, 0, 0, 0));

        return new ManualEntryView(content, progressMonitor -> {
            Path directory;
            try {
                directory = Path.of(directoryField.getText());
            } catch (InvalidPathException exception) {
                showError(errorLabel, "Select a valid EA Desktop data directory.");
                return;
            }
            if (!EAInstallDiscovery.isValidDataDirectory(directory)) {
                showError(errorLabel, "The selected directory does not contain InstallData.");
                return;
            }

            hideError(errorLabel);
            CompletableFuture.runAsync(() -> addGames(progressMonitor, directory))
                    .exceptionally(throwable -> {
                        progressMonitor.done();
                        return null;
                    });
        });
    }

    private static Path getDefaultDataDirectory() {
        if (OperatingSystem.getCurrent() != OperatingSystem.WINDOWS)
            return null;
        String programData = System.getenv("PROGRAMDATA");
        return Path.of(programData == null || programData.isBlank() ? "C:\\ProgramData" : programData, "EA Desktop");
    }

    private static void chooseDirectory(TextField field, Label errorLabel) {
        var chooser = new DirectoryChooser();
        chooser.setTitle("Select EA Desktop Data Directory");
        File current = field.getText().isBlank() ? null : new File(field.getText());
        if (current != null && current.isDirectory())
            chooser.setInitialDirectory(current);

        File selected = chooser.showDialog(field.getScene().getWindow());
        if (selected != null) {
            field.setText(selected.getAbsolutePath());
            hideError(errorLabel);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value;
        }
        return "";
    }

    private static void hideError(Label errorLabel) {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }
}
