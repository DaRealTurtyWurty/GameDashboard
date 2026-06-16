package dev.turtywurty.gamedashboard.view.steam;

import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.SteamHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

public class SteamConfigurationPane extends BorderPane {
    private final TextField executableField;
    private final TextField libraryFoldersField;
    private final Label errorLabel;
    private boolean saved;

    public SteamConfigurationPane() {
        getStyleClass().addAll("dialog-pane", "steam-configuration-pane");

        var title = new Label(!Database.getInstance().isSteamConfigured()
                ? "Set up Steam"
                : "Configure Steam");
        title.getStyleClass().add("dialog-title");

        var description = new Label(
                "Choose the Steam executable and libraryfolders.vdf file used to find your Steam library.");
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        var header = new VBox(title, description);
        header.getStyleClass().add("dialog-header");

        this.executableField = new TextField(Database.getInstance().getSteamExecutable());
        this.executableField.setPromptText("C:\\Program Files (x86)\\Steam\\steam.exe");
        HBox.setHgrow(this.executableField, Priority.ALWAYS);
        var executableCard = createPathCard(
                "Steam executable",
                this.executableField,
                "Select Steam Executable",
                this::chooseExecutable
        );

        this.libraryFoldersField = new TextField(Database.getInstance().getSteamLibraryFolders());
        this.libraryFoldersField.setPromptText(
                "C:\\Program Files (x86)\\Steam\\steamapps\\libraryfolders.vdf"
        );
        HBox.setHgrow(this.libraryFoldersField, Priority.ALWAYS);
        var libraryFoldersCard = createPathCard(
                "Steam library folders file",
                this.libraryFoldersField,
                "Select libraryfolders.vdf",
                this::chooseLibraryFolders
        );

        this.errorLabel = new Label();
        this.errorLabel.getStyleClass().add("error-label");
        this.errorLabel.setWrapText(true);
        hideError();

        var content = new VBox(header, executableCard, libraryFoldersCard, this.errorLabel);
        content.getStyleClass().add("steam-configuration-content");
        VBox.setMargin(executableCard, new Insets(8, 0, 0, 0));
        setCenter(content);

        var cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setMinWidth(86);
        cancelButton.setOnAction(event -> {
            this.saved = false;
            getScene().getWindow().hide();
        });

        var autoDetectButton = new Button("Auto Detect");
        autoDetectButton.getStyleClass().add("secondary-button");
        autoDetectButton.setMinWidth(100);
        autoDetectButton.setOnAction(event -> autoDetect());

        var saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setMinWidth(86);
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> validateAndClose());

        var actions = new HBox(autoDetectButton, cancelButton, saveButton);
        actions.getStyleClass().addAll("dialog-actions", "steam-dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        setBottom(actions);
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

    private void chooseExecutable() {
        chooseFile(this.executableField, "Select Steam Executable", null);
    }

    private void chooseLibraryFolders() {
        chooseFile(
                this.libraryFoldersField,
                "Select libraryfolders.vdf",
                new FileChooser.ExtensionFilter("Valve Data Format", "libraryfolders.vdf", "*.vdf")
        );
    }

    private void chooseFile(TextField field, String title, FileChooser.ExtensionFilter filter) {
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

        File selectedFile = chooser.showOpenDialog(getScene().getWindow());
        if (selectedFile != null) {
            field.setText(selectedFile.getAbsolutePath());
            hideError();
        }
    }

    private void validateAndClose() {
        String executable = this.executableField.getText();
        if (executable == null || executable.isBlank()) {
            showError("Steam executable cannot be empty.");
            return;
        }

        try {
            if (!SteamHandler.isSteamExecutableValid(Path.of(executable))) {
                showError("Select a valid Steam executable.");
                return;
            }
        } catch (InvalidPathException exception) {
            showError("Select a valid Steam executable.");
            return;
        }

        String libraryFolders = this.libraryFoldersField.getText();
        if (libraryFolders == null || libraryFolders.isBlank()) {
            showError("Steam library folders file cannot be empty.");
            return;
        }

        try {
            if (!SteamHandler.isSteamLibraryFoldersValid(Path.of(libraryFolders))) {
                showError("Select a valid libraryfolders.vdf file.");
                return;
            }
        } catch (InvalidPathException exception) {
            showError("Select a valid libraryfolders.vdf file.");
            return;
        }

        hideError();
        this.saved = true;
        getScene().getWindow().hide();
    }

    private void showError(String message) {
        this.errorLabel.setText(message);
        this.errorLabel.setManaged(true);
        this.errorLabel.setVisible(true);
    }

    private void hideError() {
        this.errorLabel.setManaged(false);
        this.errorLabel.setVisible(false);
    }

    private void autoDetect() {
        Optional<Path> executable = SteamHandler.discoverSteamLocation();
        Optional<Path> libraryFolders = SteamHandler.discoverSteamLibraryFoldersLocation();

        executable.ifPresent(path -> this.executableField.setText(path.toString()));
        libraryFolders.ifPresent(path -> this.libraryFoldersField.setText(path.toString()));

        if (executable.isEmpty() && libraryFolders.isEmpty()) {
            showError("Steam could not be detected. Select both files manually.");
        } else if (executable.isEmpty()) {
            showError("The Steam executable could not be detected. Select it manually.");
        } else if (libraryFolders.isEmpty()) {
            showError("libraryfolders.vdf could not be detected. Select it manually.");
        } else {
            hideError();
        }
    }

    public void construct() {
        if (this.saved) {
            Database.getInstance().setSteamConfiguration(
                    this.executableField.getText(),
                    this.libraryFoldersField.getText()
            );
        }
    }
}
