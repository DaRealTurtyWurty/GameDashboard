package domains.brighton.rg764.gamedashboard.view.steam;

import domains.brighton.rg764.gamedashboard.data.Database;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;

public class SteamConfigurationPane extends BorderPane {
    private final Label title;

    private final Label locationLabel;
    private final TextField locationField;
    private final Button browseButton;
    private final Label errorLabel;

    public SteamConfigurationPane() {
        this.title = new Label(Database.getInstance().getSteamLocation().isEmpty() ? "Setup Steam" : "Configure Steam");
        this.title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        this.locationLabel = new Label("Steam Location:");
        this.locationLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        this.locationLabel.setPrefWidth(120);

        this.locationField = new TextField();
        this.locationField.setPrefWidth(200);
        this.locationField.setPromptText("C:\\Program Files\\Steam");
        if (!Database.getInstance().getSteamLocation().isEmpty())
            this.locationField.setText(Database.getInstance().getSteamLocation());

        this.browseButton = new Button("Browse");
        this.browseButton.setPrefWidth(80);

        this.errorLabel = new Label();
        this.errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: red;");
        this.errorLabel.setPrefWidth(300);
        this.errorLabel.setVisible(false);

        this.browseButton.setOnAction(event -> {
            var chooser = new DirectoryChooser();
            chooser.setTitle("Select Steam Installation Directory");
            chooser.setInitialDirectory(Database.getInstance().getSteamLocation().isEmpty() ?
                    new File(System.getProperty("user.home")) :
                    new File(Database.getInstance().getSteamLocation()));

            var directory = chooser.showDialog(getScene().getWindow());
            if (directory != null && directory.exists() && directory.isDirectory()) {
                this.errorLabel.setVisible(false);
                this.locationField.setText(directory.getAbsolutePath());
            } else {
                this.errorLabel.setText("Invalid directory selected.");
                this.errorLabel.setVisible(true);
            }
        });

        setTop(this.title);
        BorderPane.setAlignment(this.title, Pos.TOP_CENTER);

        VBox center = new VBox();
        HBox location = new HBox();
        location.getChildren().addAll(this.locationLabel, this.locationField, this.browseButton);
        center.getChildren().addAll(location, this.errorLabel);
        setCenter(center);
        BorderPane.setAlignment(center, Pos.CENTER);

        var bottom = new HBox();

        var cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(event -> getScene().getWindow().hide());

        var okButton = new Button("OK");
        okButton.setPrefWidth(80);
        okButton.setOnAction(event -> {
            var steamLocation = this.locationField.getText();
            if (steamLocation == null || steamLocation.isBlank()) {
                this.errorLabel.setText("Steam location cannot be empty.");
                this.errorLabel.setVisible(true);
            } else {
                var steam = new File(steamLocation);
                if (steam.exists() && steam.isDirectory()) {
                    getScene().getWindow().hide();
                } else {
                    this.errorLabel.setText("Invalid Steam location.");
                    this.errorLabel.setVisible(true);
                }
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottom.getChildren().addAll(cancelButton, spacer, okButton);
        setBottom(bottom);
        BorderPane.setAlignment(bottom, Pos.BOTTOM_CENTER);
    }

    public void construct() {
        String steamLocation = this.locationField.getText();
        Database.getInstance().setSteamLocation(steamLocation);
    }
}
