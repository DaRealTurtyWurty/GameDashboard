package domains.brighton.rg764.gamedashboard.view.add_game;

import domains.brighton.rg764.gamedashboard.data.APIConnector;
import domains.brighton.rg764.gamedashboard.data.Database;
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
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AddGamePane extends BorderPane {
    private final ComboBox<String> serviceComboBox = new ComboBox<>();
    private final Label serviceErrorLabel = new Label();
    private final ComboBox<String> gameTitleComboBox = new ComboBox<>();
    private final Label gameTitleErrorLabel = new Label();
    private final TextField gameExecutableTextField = new TextField();
    private final Button browseButton = new Button("Browse");
    private final Label gameExecutableErrorLabel = new Label();

    private final Button cancelButton = new Button("Cancel");
    private final Button nextButton = new Button("Next");

    private final AtomicBoolean closedFromNext = new AtomicBoolean(false);

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
            this.closedFromNext.set(false);
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

            if (this.gameExecutableTextField.getText() == null || this.gameExecutableTextField.getText().isBlank()) {
                this.gameExecutableErrorLabel.setText("Please select the game's executable");
                this.gameExecutableErrorLabel.setVisible(true);
                valid = false;
            } else {
                String dir = this.gameExecutableTextField.getText();
                Path path = Path.of(dir);
                if (Files.notExists(path)) {
                    this.gameExecutableErrorLabel.setText("Invalid game executable");
                    this.gameExecutableErrorLabel.setVisible(true);
                    valid = false;
                } else {
                    if (Files.isDirectory(path)) {
                        this.gameExecutableErrorLabel.setText("Game executable cannot be a directory");
                        this.gameExecutableErrorLabel.setVisible(true);
                        valid = false;
                    } else if (!Files.isExecutable(path)) {
                        this.gameExecutableErrorLabel.setText("Game executable must be executable");
                        this.gameExecutableErrorLabel.setVisible(true);
                        valid = false;
                    } else {
                        this.gameExecutableErrorLabel.setVisible(false);
                    }
                }
            }

            if (hasGame(this.gameTitleComboBox.getValue())) {
                this.gameTitleErrorLabel.setText("Game already exists in database");
                this.gameTitleErrorLabel.setVisible(true);
                valid = false;
            } else {
                this.gameTitleErrorLabel.setVisible(false);
            }

            if (!valid)
                return;

            GameService service = GameService.valueOf(this.serviceComboBox.getValue().toUpperCase(Locale.ROOT));
            String gameTitle = this.gameTitleComboBox.getValue();
            Path gameExecutable = Path.of(this.gameExecutableTextField.getText());

            Scene scene = getScene();
            switch (service) {
                case STEAM -> scene.setRoot(new AddSteamGamePane(gameTitle, gameExecutable));
                case ORIGIN -> scene.setRoot(new AddOriginGamePane(gameTitle, gameExecutable));
                case EPIC_GAMES -> scene.setRoot(new AddEpicGamesGamePane(gameTitle, gameExecutable));
                case UPLAY -> scene.setRoot(new AddUplayGamePane(gameTitle, gameExecutable));
                case BATTLE_NET -> scene.setRoot(new AddBattleNetGamePane(gameTitle, gameExecutable));
                case OTHER -> {
                    APIConnector.GameResult gameResult = construct();
                    if (gameResult == null) {
                        scene.setRoot(new AddOtherGamePane(gameTitle, gameExecutable));
                    } else {
                        scene.getWindow().hide();
                        this.closedFromNext.set(true);
                    }
                }
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

    private static boolean hasGame(String gameTitle) {
        return Database.getInstance().getGames().stream()
                .anyMatch(game -> game.getTitle().trim().equalsIgnoreCase(gameTitle.trim()));
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

        var gameExecutableLabel = new Label("Game Executable");
        gameExecutableLabel.setPrefWidth(100);
        gameExecutableLabel.setAlignment(Pos.CENTER_LEFT);
        gameExecutableLabel.setTextFill(Color.web("#eee"));

        var serviceHContainer = new HBox(serviceLabel, this.serviceComboBox);
        serviceHContainer.setSpacing(10);
        serviceHContainer.setAlignment(Pos.CENTER_LEFT);
        serviceHContainer.setFillHeight(false);

        var serviceContainer = new VBox(serviceHContainer, this.serviceErrorLabel);

        for (GameService service : GameService.values()) {
            if (service == GameService.OTHER) {
                this.serviceComboBox.getItems().add(service.getName());
                continue;
            }

            if (service != GameService.STEAM) {
                this.serviceComboBox.getItems().add(service.getName() + " (Coming Soon)");
            }
        }

        this.serviceComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
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
                    ImageView imageView = new ImageView(ImageCache.getImage(split[1]));
                    imageView.setFitWidth(35);
                    imageView.setFitHeight(35);
                    setGraphic(imageView);
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

                        CompletableFuture<List<APIConnector.GameResult>> gameResultsFuture = APIConnector.search(newValue);
                        gameResultsFuture.thenAccept(gameResults -> {
                            String[] items = gameResults.stream()
                                    .map(result -> result.getName() + "||" + result.getThumbCoverURL())
                                    .toArray(String[]::new);
                            Platform.runLater(() -> {
                                if (items.length != 0) {
                                    gameTitleComboBox.getItems().setAll(items);
                                    gameTitleComboBox.show();
                                }
                            });
                        });
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

                String[] split = string.split("\\|\\|");
                return split[0];
            }
        });

        this.gameTitleComboBox.setVisibleRowCount(5);
        this.gameTitleComboBox.setPrefWidth(300);

        var gameExecutableHContainer = new HBox(gameExecutableLabel, this.gameExecutableTextField, this.browseButton);
        gameExecutableHContainer.setSpacing(10);
        gameExecutableHContainer.setAlignment(Pos.CENTER_LEFT);
        gameExecutableHContainer.setFillHeight(false);

        var gameExecutableContainer = new VBox(gameExecutableHContainer, this.gameExecutableErrorLabel);

        this.browseButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Game Executable");
            chooser.setInitialDirectory(Path.of(System.getProperty("user.home")).toFile());
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Executable Files", "*.exe"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().get(0));

            File result = chooser.showOpenDialog(getScene().getWindow());
            if (result == null)
                return;

            Path path = Path.of(result.getAbsolutePath());
            this.gameExecutableTextField.setText(path.toAbsolutePath().toString());
        });

        this.gameExecutableErrorLabel.setVisible(false);
        this.gameExecutableErrorLabel.setTextFill(Color.web("#ff0000"));

        form.getChildren().addAll(serviceContainer, gameTitleContainer, gameExecutableContainer);
        form.setSpacing(10);
        form.setAlignment(Pos.CENTER);
        form.setBackground(Utils.createBackground("#3f3f4a"));

        return form;
    }

    public @Nullable APIConnector.GameResult construct() {
        if (!this.closedFromNext.get())
            return null;

        if (this.gameTitleComboBox.getValue() == null || this.gameTitleComboBox.getValue().isBlank())
            return null;

        if (this.gameExecutableTextField.getText() == null || this.gameExecutableTextField.getText().isBlank())
            return null;

        if (this.serviceComboBox.getValue() == null || this.serviceComboBox.getValue().isBlank())
            return null;

        // validate executable
        Path executable = Path.of(this.gameExecutableTextField.getText());
        if (Files.notExists(executable) || Files.isDirectory(executable) || !Files.isExecutable(executable))
            return null;

        // validate service
        try {
            GameService.valueOf(this.serviceComboBox.getValue().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        // validate game title
        CompletableFuture<List<APIConnector.GameResult>> gameResultsFuture = APIConnector.search(this.gameTitleComboBox.getValue().split("\\|\\|")[0], true);
        List<APIConnector.GameResult> gameResults = gameResultsFuture.join();

        if (gameResults.isEmpty())
            return null;

        // find closest match
        String gameTitle = this.gameTitleComboBox.getValue();
        AtomicReference<APIConnector.GameResult> gameResult = new AtomicReference<>();
        for (APIConnector.GameResult result : gameResults) {
            if (result.getName().equalsIgnoreCase(gameTitle)) {
                gameResult.set(result);
                break;
            }
        }

        if (gameResult.get() == null) {
            // sort by levenshtein distance
            gameResults.stream().min((a, b) -> {
                        int aDist = Utils.levenshteinDistance(a.getName(), gameTitle);
                        int bDist = Utils.levenshteinDistance(b.getName(), gameTitle);
                        return Integer.compare(aDist, bDist);
                    })
                    .ifPresent(gameResult::set);
        }

        return gameResult.get();
    }

    public String getExecutablePath() {
        return this.gameExecutableTextField.getText();
    }

    public GameService getService() {
        return GameService.valueOf(this.serviceComboBox.getValue().toUpperCase(Locale.ROOT));
    }
}
