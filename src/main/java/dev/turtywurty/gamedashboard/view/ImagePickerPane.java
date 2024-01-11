package dev.turtywurty.gamedashboard.view;

import dev.turtywurty.gamedashboard.util.Utils;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;

public class ImagePickerPane extends BorderPane {
    private final Label label = new Label("Image Picker");
    private final Label imageLabel = new Label("Image:");

    private final TextField imageField = new TextField();
    private final ImageView imageView = new ImageView();

    private final StringProperty promptText = new SimpleStringProperty("Enter the image's URL");
    private final StringProperty chooserTitle = new SimpleStringProperty("Choose Image");
    private final ListProperty<String> chooserExtensions = new SimpleListProperty<>(
            FXCollections.observableArrayList("*.png", "*.jpg", "*.jpeg", "*.gif"));
    private final ObjectProperty<Path> chooserPath = new SimpleObjectProperty<>(
            Path.of(System.getProperty("user.home")));

    public ImagePickerPane() {
        this.label.setStyle("-fx-font-size: 1.5em; -fx-font-weight: bold;");
        this.label.setWrapText(true);
        this.label.setAlignment(Pos.CENTER);
        this.label.setPrefWidth(300);
        this.label.setPrefHeight(100);
        setTop(this.label);

        this.imageLabel.setStyle("-fx-font-size: 1.2em; -fx-font-weight: bold;");
        this.imageLabel.setWrapText(true);
        this.imageLabel.setAlignment(Pos.CENTER);
        this.imageLabel.setPrefWidth(300);
        this.imageLabel.setPrefHeight(100);

        this.imageField.setPromptText(this.promptText.get());
        this.imageField.setPrefWidth(300);
        this.imageField.setPrefHeight(100);

        this.imageView.setFitWidth(200);
        this.imageView.setFitHeight(200);
        this.imageView.setCursor(Cursor.HAND);
        this.imageView.setOnMouseClicked(this::listenForImagePress);
        this.imageView.setStyle("-fx-border-color: black; -fx-border-width: 1px;");
        this.imageView.setImage(new Image(
                getClass().getResource("/images/questionmark_placeholder.png").toExternalForm()));

        var hBox = new HBox(this.imageLabel, this.imageView);
        hBox.setSpacing(10);
        hBox.setStyle("-fx-padding: 10px;");
        hBox.setAlignment(Pos.CENTER);
        setCenter(hBox);

        setBackground(Utils.createBackground("#3f3f4a"));
    }

    protected void listenForImagePress(MouseEvent event) {
        if(event.getClickCount() == 2 && event.isPrimaryButtonDown()) {
            var fileChooser = new FileChooser();
            fileChooser.setTitle(this.chooserTitle.get());
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", chooserExtensions.get()));
            fileChooser.setInitialDirectory(this.chooserPath.get().toFile());

            var file = fileChooser.showOpenDialog(getScene().getWindow());
            if (file == null)
                return;

            Path path = file.toPath().toAbsolutePath();

            this.imageField.setText(path.toString());
            this.imageView.setImage(null);
            this.imageView.setImage(new Image(path.toUri().toString()));
        }
    }
}
