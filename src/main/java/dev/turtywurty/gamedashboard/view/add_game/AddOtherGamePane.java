package dev.turtywurty.gamedashboard.view.add_game;

import dev.turtywurty.gamedashboard.view.ImagePickerPane;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

public class AddOtherGamePane extends BasicAddGamePane {
    private final Label titleLabel =
            new Label("Game was not found in the database. Please enter the game's details below.");

    private final Label gameTitleLabel = new Label("Game Title:");
    private final TextField gameTitleField = new TextField();

    private final ImagePickerPane imagePickerPane = new ImagePickerPane();

    private final Label descriptionLabel = new Label("Description:");
    private final TextArea descriptionArea = new TextArea();

    public AddOtherGamePane(String gameTitle, Path gameExecutable) {
        super(gameTitle, gameExecutable);

        this.titleLabel.setStyle("-fx-font-size: 1.5em; -fx-font-weight: bold;");
        this.titleLabel.setWrapText(true);
        this.titleLabel.setAlignment(Pos.CENTER);
        this.titleLabel.setPrefWidth(500);

        this.gameTitleField.setPromptText("Enter the game's title");
        this.gameTitleField.setText(gameTitle);
        this.gameTitleField.setPrefWidth(300);

        var gameTitleBox = new HBox(this.gameTitleLabel, this.gameTitleField);
        gameTitleBox.setSpacing(10);
        gameTitleBox.setAlignment(Pos.CENTER);

        this.descriptionArea.setPromptText("Enter the game's description");
        this.descriptionArea.setPrefWidth(Double.MAX_VALUE);
        this.descriptionArea.setPrefHeight(100);
        this.descriptionArea.setWrapText(true);

        var descriptionBox = new HBox(this.descriptionLabel, this.descriptionArea);
        descriptionBox.setSpacing(10);
        descriptionBox.setAlignment(Pos.CENTER);

        this.imagePickerPane.setPrefWidth(300);
        this.imagePickerPane.setPrefHeight(200);

        var vBox = new VBox(
                this.titleLabel,
                gameTitleBox,
                this.imagePickerPane,
                descriptionBox);

        vBox.setSpacing(10);
        vBox.setAlignment(Pos.CENTER);
        vBox.setPrefWidth(400);
        vBox.setPrefHeight(400);
        vBox.setStyle("-fx-padding: 10px;");

        setCenter(vBox);
    }
}
