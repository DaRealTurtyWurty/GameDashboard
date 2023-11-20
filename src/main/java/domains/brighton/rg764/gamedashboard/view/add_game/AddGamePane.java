package domains.brighton.rg764.gamedashboard.view.add_game;

import domains.brighton.rg764.gamedashboard.data.APIConnector;
import domains.brighton.rg764.gamedashboard.data.GameService;
import domains.brighton.rg764.gamedashboard.util.ImageCache;
import domains.brighton.rg764.gamedashboard.util.Utils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AddGamePane extends BorderPane {
    private final ComboBox<String> serviceComboBox = new ComboBox<>();
    private final Label serviceErrorLabel = new Label();
    private final ComboBox<String> gameTitleComboBox = new ComboBox<>();
    private final Label gameTitleErrorLabel = new Label();
    private final TextField gameDirectoryTextField = new TextField();
    private final Button browseButton = new Button("Browse");
    private final Label gameDirectoryErrorLabel = new Label();

    private final Button cancelButton = new Button("Cancel");
    private final Button nextButton = new Button("Next");

    public AddGamePane() {
        Label title = new Label("Add Game");
        title.setTextFill(Color.web("#eee"));
        title.setAlignment(Pos.CENTER);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setPrefHeight(50);
        title.setPrefWidth(400);
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        setTop(title);
        setCenter(createForm());
        setBottom(createButtons());

        setPadding(Utils.createInsets(10));
        setBackground(Utils.createBackground("#3f3f4a"));
    }

    private HBox createButtons() {
        var buttons = new HBox(this.cancelButton, this.nextButton);
        buttons.setSpacing(10);
        buttons.setAlignment(Pos.CENTER);
        buttons.setBackground(Utils.createBackground("#3f3f4a"));

        this.cancelButton.setOnAction(event -> {
            getScene().getWindow().hide();
        });

        this.nextButton.setOnAction(event -> {
            // Validate

            boolean valid = true;
            if (this.serviceComboBox.getValue() == null || this.serviceComboBox.getValue().isBlank()) {
                this.serviceErrorLabel.setText("Please select a service");
                this.serviceErrorLabel.setVisible(true);
                valid = false;
            } else {
                try {
                    GameService.valueOf(this.serviceComboBox.getValue().toUpperCase(Locale.ROOT));
                    this.serviceErrorLabel.setVisible(false);
                } catch (IllegalArgumentException ignored) {
                    this.serviceErrorLabel.setText("Invalid service");
                    this.serviceErrorLabel.setVisible(true);
                    valid = false;
                }
            }

            if (this.gameTitleComboBox.getValue() == null || this.gameTitleComboBox.getValue().isBlank()) {
                this.gameTitleErrorLabel.setText("Please select a game title");
                this.gameTitleErrorLabel.setVisible(true);
                valid = false;
            } else {
                this.gameTitleErrorLabel.setVisible(false);
            }

            if (this.gameDirectoryTextField.getText() == null || this.gameDirectoryTextField.getText().isBlank()) {
                this.gameDirectoryErrorLabel.setText("Please select a game directory");
                this.gameDirectoryErrorLabel.setVisible(true);
                valid = false;
            } else {
                String dir = this.gameDirectoryTextField.getText();
                Path path = Path.of(dir);
                if (Files.notExists(path)) {
                    this.gameDirectoryErrorLabel.setText("Invalid game directory");
                    this.gameDirectoryErrorLabel.setVisible(true);
                    valid = false;
                } else {
                    this.gameDirectoryErrorLabel.setVisible(false);
                    if (!Files.isDirectory(path)) {
                        this.gameDirectoryErrorLabel.setText("Game directory must be a directory");
                        this.gameDirectoryErrorLabel.setVisible(true);
                        valid = false;
                    } else {
                        this.gameDirectoryErrorLabel.setVisible(false);
                    }
                }
            }

            if (!valid)
                return;

            GameService service = GameService.valueOf(this.serviceComboBox.getValue().toUpperCase(Locale.ROOT));
            String gameTitle = this.gameTitleComboBox.getValue();
            Path gameDirectory = Path.of(this.gameDirectoryTextField.getText());

            Scene scene = getScene();
            switch (service) {
                case STEAM -> scene.setRoot(new AddSteamGamePane(gameTitle, gameDirectory));
                case ORIGIN -> scene.setRoot(new AddOriginGamePane(gameTitle, gameDirectory));
                case EPIC_GAMES -> scene.setRoot(new AddEpicGamesGamePane(gameTitle, gameDirectory));
                case UPLAY -> scene.setRoot(new AddUplayGamePane(gameTitle, gameDirectory));
                case BATTLE_NET -> scene.setRoot(new AddBattleNetGamePane(gameTitle, gameDirectory));
                case OTHER -> scene.setRoot(new AddOtherGamePane(gameTitle, gameDirectory));
                default -> {
                    this.serviceErrorLabel.setText("Invalid service, please choose from (" +
                            Arrays.stream(GameService.values())
                                    .map(GameService::getName)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("") +
                            ")");
                    this.serviceErrorLabel.setVisible(true);

                    System.err.println("Invalid service was provided: " + service);
                }
            }
        });

        return buttons;
    }

    private VBox createForm() {
        var form = new VBox();

        var serviceLabel = new Label("Service");
        serviceLabel.setPrefWidth(100);
        serviceLabel.setAlignment(Pos.CENTER_LEFT);
        serviceLabel.setTextFill(Color.web("#eee"));

        var gameTitleLabel = new Label("Game Title");
        gameTitleLabel.setPrefWidth(100);
        gameTitleLabel.setAlignment(Pos.CENTER_LEFT);
        gameTitleLabel.setTextFill(Color.web("#eee"));

        var gameDirectoryLabel = new Label("Game Directory");
        gameDirectoryLabel.setPrefWidth(100);
        gameDirectoryLabel.setAlignment(Pos.CENTER_LEFT);
        gameDirectoryLabel.setTextFill(Color.web("#eee"));

        var serviceHContainer = new HBox(serviceLabel, this.serviceComboBox);
        serviceHContainer.setSpacing(10);
        serviceHContainer.setAlignment(Pos.CENTER_LEFT);
        serviceHContainer.setFillHeight(false);

        var serviceContainer = new VBox(serviceHContainer, this.serviceErrorLabel);

        for (GameService service : GameService.values()) {
            if(service == GameService.OTHER) {
                this.serviceComboBox.getItems().add(service.getName());
                continue;
            }

            if(service != GameService.STEAM) {
                this.serviceComboBox.getItems().add(service.getName() + " (Coming Soon)");
            }
        }

        this.serviceComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if(item != null && !empty) {
                    setText(item);
                    setDisable(!GameService.OTHER.getName().equals(item));
                }
            }
        });

        this.serviceComboBox.setValue(GameService.OTHER.getName());

        this.serviceErrorLabel.setVisible(false);
        this.serviceErrorLabel.setTextFill(Color.web("#ff0000"));

        var gameTitleHContainer = new HBox(gameTitleLabel, this.gameTitleComboBox);
        gameTitleHContainer.setSpacing(10);
        gameTitleHContainer.setAlignment(Pos.CENTER_LEFT);
        gameTitleHContainer.setFillHeight(false);

        var gameTitleContainer = new VBox(gameTitleHContainer, this.gameTitleErrorLabel);

        this.gameTitleErrorLabel.setVisible(false);
        this.gameTitleErrorLabel.setTextFill(Color.web("#ff0000"));

        this.gameTitleComboBox.setEditable(true);
        this.gameTitleComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String[] split = item.split("\\|\\|");
                setText(split[0]);
                if (split.length > 1) {
                    setGraphic(new ImageView(ImageCache.getImage(split[1])));
                }
            }
        });

        this.gameTitleComboBox.editorProperty().get().textProperty().addListener(new ChangeListener<>() {
            private final Timeline timeline = new Timeline(new KeyFrame(
                    Duration.millis(500),
                    event -> {
                        String newValue = gameTitleComboBox.editorProperty().get().getText();
                        if (newValue == null || newValue.isBlank())
                            return;

                        new Thread(() -> {
                            List<APIConnector.GameResult> gameTitles = APIConnector.search(newValue);
                            String[] items = gameTitles.stream()
                                    .map(result -> result.getName() + "||" + result.getCoverURL())
                                    .toArray(String[]::new);
                            Platform.runLater(() -> {
                                if (items.length != 0) {
                                    gameTitleComboBox.getItems().setAll(items);
                                    gameTitleComboBox.show();
                                }
                            });
                        }).start();
                    }
            ));

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                gameTitleComboBox.hide();
                gameTitleComboBox.getItems().clear();
                this.timeline.stop();
                this.timeline.play();
            }
        });

        this.gameTitleComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String object) {
                if (object == null || object.isBlank())
                    return "";

                String[] split = object.split("\\|\\|");
                return split[0];
            }

            @Override
            public String fromString(String string) {
                if (string == null || string.isBlank())
                    return "";

                return string;
            }
        });

        this.gameTitleComboBox.setVisibleRowCount(5);
        this.gameTitleComboBox.setPrefWidth(300);

        var gameDirectoryHContainer = new HBox(gameDirectoryLabel, this.gameDirectoryTextField, this.browseButton);
        gameDirectoryHContainer.setSpacing(10);
        gameDirectoryHContainer.setAlignment(Pos.CENTER_LEFT);
        gameDirectoryHContainer.setFillHeight(false);

        var gameDirectoryContainer = new VBox(gameDirectoryHContainer, this.gameDirectoryErrorLabel);

        this.browseButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Game Directory");
            chooser.setInitialDirectory(Path.of(System.getProperty("user.home")).toFile());
            File result = chooser.showDialog(getScene().getWindow());
            if (result == null)
                return;

            Path path = Path.of(result.getAbsolutePath());
            this.gameDirectoryTextField.setText(path.toAbsolutePath().toString());
        });

        this.gameDirectoryErrorLabel.setVisible(false);
        this.gameDirectoryErrorLabel.setTextFill(Color.web("#ff0000"));

        form.getChildren().addAll(serviceContainer, gameTitleContainer, gameDirectoryContainer);
        form.setSpacing(10);
        form.setAlignment(Pos.CENTER);
        form.setBackground(Utils.createBackground("#3f3f4a"));

        return form;
    }
}
