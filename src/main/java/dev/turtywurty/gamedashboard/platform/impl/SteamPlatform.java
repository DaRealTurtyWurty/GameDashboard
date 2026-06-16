package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.SteamHandler;
import dev.turtywurty.gamedashboard.platform.Platform;
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
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SteamPlatform implements Platform {
    @Override
    public Image getIcon() {
        return new Image(getClass().getResource("/images/platforms/steam.png").toExternalForm());
    }

    @Override
    public String getName() {
        return "Steam";
    }

    @Override
    public String getWebsite() {
        return "https://store.steampowered.com/";
    }

    @Override
    public String getDescription() {
        return "Steam is a digital distribution platform developed by Valve Corporation, offering a wide range of video games, software, and community features for gamers.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding Steam installation", 2);
            Optional<Path> steamLocation = SteamHandler.discoverSteamLocation();
            progressMonitor.worked(1);
            if (steamLocation.isEmpty()) {
                progressMonitor.done();
                return;
            }

            Optional<Path> steamLibraryFoldersLocation = SteamHandler.discoverSteamLibraryFoldersLocation(steamLocation.get());
            progressMonitor.worked(1);
            if (steamLibraryFoldersLocation.isEmpty()) {
                progressMonitor.done();
                return;
            }

            boolean configured = Database.getInstance().setSteamConfiguration(
                    steamLocation.get().toString(),
                    steamLibraryFoldersLocation.get().toString(),
                    progressMonitor
            );
            if (!configured) {
                progressMonitor.done();
            }
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var description = new Label(
                "Choose the Steam executable and libraryfolders.vdf file used to find your Steam library.");
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        var header = new VBox(description);
        header.getStyleClass().add("dialog-header");

        var errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);

        var executableField = new TextField(Database.getInstance().getSteamExecutable());
        executableField.setPromptText("C:\\Program Files (x86)\\Steam\\steam.exe");
        HBox.setHgrow(executableField, Priority.ALWAYS);
        var executableCard = createPathCard(
                "Steam executable",
                executableField,
                "Select Steam Executable",
                () -> chooseExecutable(executableField, errorLabel)
        );

        var libraryFoldersField = new TextField(Database.getInstance().getSteamLibraryFolders());
        libraryFoldersField.setPromptText(
                "C:\\Program Files (x86)\\Steam\\steamapps\\libraryfolders.vdf"
        );
        HBox.setHgrow(libraryFoldersField, Priority.ALWAYS);
        var libraryFoldersCard = createPathCard(
                "Steam library folders file",
                libraryFoldersField,
                "Select libraryfolders.vdf",
                () -> chooseLibraryFolders(libraryFoldersField, errorLabel)
        );

        hideError(errorLabel);

        var content = new VBox(header, executableCard, libraryFoldersCard, errorLabel);
        content.getStyleClass().add("steam-configuration-content");
        VBox.setMargin(executableCard, new Insets(8, 0, 0, 0));

        return new ManualEntryView(content, progressMonitor -> {
            String executable = executableField.getText();
            if (!isValidSteamExecutable(executable)) {
                showError(errorLabel, "Select a valid Steam executable.");
                return;
            }

            String libraryFolders = libraryFoldersField.getText();
            if (!isValidLibraryFolders(libraryFolders)) {
                showError(errorLabel, "Select a valid libraryfolders.vdf file.");
                return;
            }

            hideError(errorLabel);
            progressMonitor.start("Saving Steam configuration", 1);
            CompletableFuture.runAsync(() -> {
                progressMonitor.worked(1);
                boolean configured = Database.getInstance().setSteamConfiguration(executable, libraryFolders, progressMonitor);
                if (!configured)
                    progressMonitor.done();
            }).exceptionally(throwable -> {
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
        row.getStyleClass().add("steam-location-row");
        row.setAlignment(Pos.CENTER_LEFT);

        var card = new VBox(label, row);
        card.getStyleClass().add("steam-location-card");
        return card;
    }

    private void chooseExecutable(TextField executableField, Label errorLabel) {
        chooseFile(executableField, "Select Steam Executable", null, errorLabel);
    }

    private void chooseLibraryFolders(TextField libraryFoldersField, Label errorLabel) {
        chooseFile(
                libraryFoldersField,
                "Select libraryfolders.vdf",
                new FileChooser.ExtensionFilter("Valve Data Format", "libraryfolders.vdf", "*.vdf"),
                errorLabel
        );
    }

    private void chooseFile(TextField field, String title, FileChooser.ExtensionFilter filter, Label errorLabel) {
        var chooser = new FileChooser();
        chooser.setTitle(title);
        if (filter != null) {
            chooser.getExtensionFilters().add(filter);
        }

        String currentPath = field.getText();
        File currentFile = currentPath == null || currentPath.isBlank() ? null : new File(currentPath);
        File initialDirectory = currentFile != null && currentFile.getParentFile() != null
                ? currentFile.getParentFile()
                : new File(System.getProperty("user.home"));
        if (initialDirectory.isDirectory()) {
            chooser.setInitialDirectory(initialDirectory);
        }

        File selectedFile = chooser.showOpenDialog(field.getScene().getWindow());
        if (selectedFile != null) {
            field.setText(selectedFile.getAbsolutePath());
            hideError(errorLabel);
        }
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

    private boolean isValidSteamExecutable(String executable) {
        if (executable == null || executable.isBlank())
            return false;

        try {
            return SteamHandler.isSteamExecutableValid(Path.of(executable));
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private boolean isValidLibraryFolders(String libraryFolders) {
        if (libraryFolders == null || libraryFolders.isBlank())
            return false;

        try {
            return SteamHandler.isSteamLibraryFoldersValid(Path.of(libraryFolders));
        } catch (InvalidPathException exception) {
            return false;
        }
    }
}
