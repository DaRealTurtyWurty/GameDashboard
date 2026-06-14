package dev.turtywurty.gamedashboard.view.steam;

import dev.turtywurty.gamedashboard.data.Database;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;

public class SteamConfigurationPane extends BorderPane {
    private final TextField locationField;
    private final Label errorLabel;
    private boolean saved;

    public SteamConfigurationPane() {
        getStyleClass().addAll("dialog-pane", "steam-configuration-pane");

        var title = new Label(Database.getInstance().getSteamLocation().isEmpty()
                ? "Set up Steam"
                : "Configure Steam");
        title.getStyleClass().add("dialog-title");

        var description = new Label(
                "Choose the folder where Steam is installed. Game Dashboard uses it to find your Steam library.");
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        var header = new VBox(title, description);
        header.getStyleClass().add("dialog-header");

        var locationLabel = new Label("Steam installation folder");
        locationLabel.getStyleClass().add("field-label");

        this.locationField = new TextField();
        this.locationField.setPromptText("C:\\Program Files (x86)\\Steam");
        HBox.setHgrow(this.locationField, Priority.ALWAYS);
        if (!Database.getInstance().getSteamLocation().isEmpty())
            this.locationField.setText(Database.getInstance().getSteamLocation());

        var browseButton = new Button("Browse");
        browseButton.getStyleClass().add("browse-button");
        browseButton.setOnAction(event -> chooseSteamDirectory());

        var locationRow = new HBox(this.locationField, browseButton);
        locationRow.getStyleClass().add("steam-location-row");
        locationRow.setAlignment(Pos.CENTER_LEFT);

        this.errorLabel = new Label();
        this.errorLabel.getStyleClass().add("error-label");
        this.errorLabel.setWrapText(true);
        hideError();

        var locationCard = new VBox(locationLabel, locationRow, this.errorLabel);
        locationCard.getStyleClass().add("steam-location-card");

        var content = new VBox(header, locationCard);
        content.getStyleClass().add("steam-configuration-content");
        VBox.setMargin(locationCard, new Insets(8, 0, 0, 0));
        setCenter(content);

        var cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setMinWidth(86);
        cancelButton.setOnAction(event -> {
            this.saved = false;
            getScene().getWindow().hide();
        });

        var saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setMinWidth(86);
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> validateAndClose());

        var actions = new HBox(cancelButton, saveButton);
        actions.getStyleClass().addAll("dialog-actions", "steam-dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        setBottom(actions);
    }

    private void chooseSteamDirectory() {
        var chooser = new DirectoryChooser();
        chooser.setTitle("Select Steam Installation Directory");

        String currentPath = this.locationField.getText();
        File currentLocation = currentPath == null || currentPath.isBlank() ? null : new File(currentPath);
        chooser.setInitialDirectory(currentLocation != null && currentLocation.isDirectory()
                ? currentLocation
                : new File(System.getProperty("user.home")));

        File directory = chooser.showDialog(getScene().getWindow());
        if (directory == null)
            return;

        this.locationField.setText(directory.getAbsolutePath());
        hideError();
    }

    private void validateAndClose() {
        String steamLocation = this.locationField.getText();
        if (steamLocation == null || steamLocation.isBlank()) {
            showError("Steam location cannot be empty.");
            return;
        }

        File steamDirectory = new File(steamLocation);
        if (!steamDirectory.isDirectory()) {
            showError("Select a valid Steam installation folder.");
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

    public void construct() {
        if (this.saved)
            Database.getInstance().setSteamLocation(this.locationField.getText());
    }
}
