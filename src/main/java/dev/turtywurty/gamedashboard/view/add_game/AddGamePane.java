package dev.turtywurty.gamedashboard.view.add_game;

import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.GameService;
import dev.turtywurty.gamedashboard.util.ImageCache;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class AddGamePane extends BorderPane {
    private final ComboBox<String> serviceComboBox = new ComboBox<>();
    private final Label serviceErrorLabel = createErrorLabel();
    private final ComboBox<String> gameTitleComboBox = new ComboBox<>();
    private final Label gameTitleErrorLabel = createErrorLabel();
    private final TextField gameExecutableTextField = new TextField();
    private final Label gameExecutableErrorLabel = createErrorLabel();

    private final AtomicBoolean closedFromNext = new AtomicBoolean(false);
    private final AtomicLong searchGeneration = new AtomicLong();

    public AddGamePane() {
        getStyleClass().addAll("dialog-pane", "add-game-pane");

        var title = new Label("Add a game");
        title.getStyleClass().add("dialog-title");

        var description = new Label(
                "Choose a service, find your game, and select the executable used to launch it.");
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        var header = new VBox(title, description);
        header.getStyleClass().add("dialog-header");

        configureServiceField();
        configureGameTitleField();
        configureExecutableField();

        var form = new VBox(
                createFieldGroup("Service", this.serviceComboBox, this.serviceErrorLabel),
                createFieldGroup("Game title", this.gameTitleComboBox, this.gameTitleErrorLabel),
                createExecutableFieldGroup());
        form.getStyleClass().add("add-game-form");

        var content = new VBox(header, form);
        content.getStyleClass().add("add-game-content");
        setCenter(content);
        setBottom(createActions());
    }

    private void configureServiceField() {
        for (GameService service : GameService.values()) {
            if (service == GameService.OTHER) {
                this.serviceComboBox.getItems().add(service.getName());
            } else if (service != GameService.STEAM) {
                this.serviceComboBox.getItems().add(service.getName() + " (Coming Soon)");
            }
        }

        this.serviceComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setDisable(!empty && !GameService.OTHER.getName().equals(item));
            }
        });
        this.serviceComboBox.setValue(GameService.OTHER.getName());
        this.serviceComboBox.setMaxWidth(Double.MAX_VALUE);
    }

    private void configureGameTitleField() {
        this.gameTitleComboBox.setEditable(true);
        this.gameTitleComboBox.setPromptText("Search for a game");
        this.gameTitleComboBox.setVisibleRowCount(5);
        this.gameTitleComboBox.setMaxWidth(Double.MAX_VALUE);
        this.gameTitleComboBox.setCellFactory(param -> new GameSearchResultCell());
        this.gameTitleComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return getGameTitle(value);
            }

            @Override
            public String fromString(String value) {
                return getGameTitle(value);
            }
        });

        this.gameTitleComboBox.getEditor().textProperty().addListener(new ChangeListener<>() {
            private final Timeline searchDelay = new Timeline(new KeyFrame(
                    Duration.millis(500),
                    event -> searchForGames(gameTitleComboBox.getEditor().getText())));

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                gameTitleComboBox.hide();
                this.searchDelay.stop();
                searchGeneration.incrementAndGet();

                if (newValue == null || newValue.isBlank()) {
                    gameTitleComboBox.getItems().clear();
                    hideError(gameTitleErrorLabel);
                } else {
                    this.searchDelay.playFromStart();
                }
            }
        });
    }

    private void configureExecutableField() {
        this.gameExecutableTextField.setPromptText("Select the game's executable");
        HBox.setHgrow(this.gameExecutableTextField, Priority.ALWAYS);
    }

    private VBox createExecutableFieldGroup() {
        var browseButton = new Button("Browse");
        browseButton.getStyleClass().add("browse-button");
        browseButton.setOnAction(event -> chooseExecutable());

        var executableRow = new HBox(this.gameExecutableTextField, browseButton);
        executableRow.getStyleClass().add("add-game-executable-row");
        executableRow.setAlignment(Pos.CENTER_LEFT);

        return createFieldGroup("Game executable", executableRow, this.gameExecutableErrorLabel);
    }

    private HBox createActions() {
        var cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setMinWidth(86);
        cancelButton.setOnAction(event -> {
            this.closedFromNext.set(false);
            getScene().getWindow().hide();
        });

        var nextButton = new Button("Next");
        nextButton.getStyleClass().add("primary-button");
        nextButton.setMinWidth(86);
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(event -> continueToNextStep());

        var actions = new HBox(cancelButton, nextButton);
        actions.getStyleClass().addAll("dialog-actions", "add-game-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        return actions;
    }

    private void continueToNextStep() {
        if (!validateForm())
            return;

        GameService service = GameService.valueOf(this.serviceComboBox.getValue().toUpperCase(Locale.ROOT));
        String gameTitle = getGameTitle(this.gameTitleComboBox.getValue());
        Path gameExecutable = Path.of(this.gameExecutableTextField.getText());

        Scene scene = getScene();
        switch (service) {
            case STEAM -> scene.setRoot(new AddSteamGamePane(gameTitle, gameExecutable));
            case ORIGIN -> scene.setRoot(new AddOriginGamePane(gameTitle, gameExecutable));
            case EPIC_GAMES -> scene.setRoot(new AddEpicGamesGamePane(gameTitle, gameExecutable));
            case UPLAY -> scene.setRoot(new AddUplayGamePane(gameTitle, gameExecutable));
            case BATTLE_NET -> scene.setRoot(new AddBattleNetGamePane(gameTitle, gameExecutable));
            case OTHER -> {
                this.closedFromNext.set(true);
                APIConnector.GameResult gameResult = construct();
                if (gameResult == null) {
                    this.closedFromNext.set(false);
                    scene.setRoot(new AddOtherGamePane(gameTitle, gameExecutable));
                    scene.getWindow().setWidth(720);
                    scene.getWindow().setHeight(600);
                } else {
                    scene.getWindow().hide();
                }
            }
        }
    }

    private boolean validateForm() {
        boolean valid = true;

        String service = this.serviceComboBox.getValue();
        if (service == null || service.isBlank()) {
            showError(this.serviceErrorLabel, "Choose a service.");
            valid = false;
        } else {
            try {
                GameService.valueOf(service.toUpperCase(Locale.ROOT));
                hideError(this.serviceErrorLabel);
            } catch (IllegalArgumentException ignored) {
                showError(this.serviceErrorLabel, "Choose an available service.");
                valid = false;
            }
        }

        String gameTitle = getGameTitle(this.gameTitleComboBox.getValue());
        if (gameTitle.isBlank())
            gameTitle = getGameTitle(this.gameTitleComboBox.getEditor().getText());

        if (gameTitle.isBlank()) {
            showError(this.gameTitleErrorLabel, "Enter or select a game title.");
            valid = false;
        } else if (hasGame(gameTitle)) {
            showError(this.gameTitleErrorLabel, "This game is already in your dashboard.");
            valid = false;
        } else {
            this.gameTitleComboBox.setValue(gameTitle);
            hideError(this.gameTitleErrorLabel);
        }

        String executableText = this.gameExecutableTextField.getText();
        if (executableText == null || executableText.isBlank()) {
            showError(this.gameExecutableErrorLabel, "Select the game's executable.");
            valid = false;
        } else {
            try {
                Path executable = Path.of(executableText);
                if (Files.notExists(executable)) {
                    showError(this.gameExecutableErrorLabel, "The selected executable does not exist.");
                    valid = false;
                } else if (Files.isDirectory(executable)) {
                    showError(this.gameExecutableErrorLabel, "Select an executable file, not a folder.");
                    valid = false;
                } else if (!Files.isExecutable(executable)) {
                    showError(this.gameExecutableErrorLabel, "The selected file is not executable.");
                    valid = false;
                } else {
                    hideError(this.gameExecutableErrorLabel);
                }
            } catch (RuntimeException ignored) {
                showError(this.gameExecutableErrorLabel, "The executable path is invalid.");
                valid = false;
            }
        }

        return valid;
    }

    private void chooseExecutable() {
        var chooser = new FileChooser();
        chooser.setTitle("Select Game Executable");

        String currentPath = this.gameExecutableTextField.getText();
        File currentFile = currentPath == null || currentPath.isBlank() ? null : new File(currentPath);
        File initialDirectory = currentFile != null && currentFile.getParentFile() != null
                && currentFile.getParentFile().isDirectory()
                ? currentFile.getParentFile()
                : new File(System.getProperty("user.home"));
        chooser.setInitialDirectory(initialDirectory);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Executable Files", "*.exe"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().getFirst());

        File result = chooser.showOpenDialog(getScene().getWindow());
        if (result == null)
            return;

        this.gameExecutableTextField.setText(result.getAbsolutePath());
        hideError(this.gameExecutableErrorLabel);
    }

    private void searchForGames(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank())
            return;

        long generation = this.searchGeneration.get();
        APIConnector.search(normalizedQuery).whenComplete((results, error) -> Platform.runLater(() -> {
            if (generation != this.searchGeneration.get())
                return;

            String currentQuery = this.gameTitleComboBox.getEditor().getText();
            if (!normalizedQuery.equals(currentQuery == null ? "" : currentQuery.trim()))
                return;

            if (error != null) {
                this.gameTitleComboBox.getItems().clear();
                showError(this.gameTitleErrorLabel, "Game search is currently unavailable.");
                return;
            }

            hideError(this.gameTitleErrorLabel);
            List<String> items = results.stream()
                    .map(result -> result.getName() + "||" + result.getThumbCoverURL())
                    .toList();

            this.gameTitleComboBox.getItems().setAll(items);
            this.gameTitleComboBox.getEditor().setText(currentQuery);
            this.gameTitleComboBox.getEditor().positionCaret(currentQuery.length());

            if (!items.isEmpty())
                Platform.runLater(this.gameTitleComboBox::show);
        }));
    }

    private static VBox createFieldGroup(String labelText, javafx.scene.Node control, Label errorLabel) {
        var label = new Label(labelText);
        label.getStyleClass().add("field-label");

        var group = new VBox(label, control, errorLabel);
        group.getStyleClass().add("add-game-field");
        return group;
    }

    private static Label createErrorLabel() {
        var label = new Label();
        label.getStyleClass().add("error-label");
        label.setWrapText(true);
        hideError(label);
        return label;
    }

    private static void showError(Label label, String message) {
        label.setText(message);
        label.setManaged(true);
        label.setVisible(true);
    }

    private static void hideError(Label label) {
        label.setManaged(false);
        label.setVisible(false);
    }

    private static boolean hasGame(String gameTitle) {
        return Database.getInstance().getGames().stream()
                .anyMatch(game -> game.getTitle().trim().equalsIgnoreCase(gameTitle.trim()));
    }

    private static String getGameTitle(String value) {
        if (value == null || value.isBlank())
            return "";
        return value.split("\\|\\|", 2)[0].trim();
    }

    public @Nullable APIConnector.GameResult construct() {
        if (!this.closedFromNext.get())
            return null;

        String gameTitle = getGameTitle(this.gameTitleComboBox.getValue());
        if (gameTitle.isBlank())
            return null;

        String executableText = this.gameExecutableTextField.getText();
        if (executableText == null || executableText.isBlank())
            return null;

        Path executable = Path.of(executableText);
        if (Files.notExists(executable) || Files.isDirectory(executable) || !Files.isExecutable(executable))
            return null;

        try {
            GameService.valueOf(this.serviceComboBox.getValue().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        List<APIConnector.GameResult> gameResults = APIConnector.search(gameTitle, true).join();
        if (gameResults.isEmpty())
            return null;

        AtomicReference<APIConnector.GameResult> gameResult = new AtomicReference<>();
        gameResults.stream()
                .filter(result -> result.getName().equalsIgnoreCase(gameTitle))
                .findFirst()
                .ifPresent(gameResult::set);

        if (gameResult.get() == null) {
            gameResults.stream().min((a, b) -> Integer.compare(
                            Utils.levenshteinDistance(a.getName(), gameTitle),
                            Utils.levenshteinDistance(b.getName(), gameTitle)))
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

    private static class GameSearchResultCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (item == null || empty) {
                setText(null);
                setGraphic(null);
                return;
            }

            String[] parts = item.split("\\|\\|", 2);
            setText(parts[0]);
            if (parts.length == 1 || parts[1].isBlank()) {
                setGraphic(null);
                return;
            }

            var cover = new ImageView(ImageCache.getImage(parts[1]));
            cover.setFitWidth(32);
            cover.setFitHeight(32);
            cover.setPreserveRatio(true);
            setGraphic(cover);
        }
    }
}
