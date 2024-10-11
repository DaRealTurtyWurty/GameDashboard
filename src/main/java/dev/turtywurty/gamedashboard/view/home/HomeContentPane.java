package dev.turtywurty.gamedashboard.view.home;

import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.Game;
import dev.turtywurty.gamedashboard.data.GameService;
import dev.turtywurty.gamedashboard.util.Utils;
import dev.turtywurty.gamedashboard.view.add_game.AddGamePane;
import dev.turtywurty.gamedashboard.view.general.GridGameEntry;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HomeContentPane extends BorderPane {
    private final SimpleObjectProperty<ContentDisplay> contentDisplay = new SimpleObjectProperty<>(ContentDisplay.GRID);

    private final StackPane contentContainer = new StackPane();

    private Button addGameButton;
    private ScrollPane scrollPane;
    private Pane content;

    public HomeContentPane() {
        this.addGameButton = new Button("Add Game");
        this.addGameButton.setEffect(new DropShadow());
        this.addGameButton.setOnAction(event -> {
            var pane = new AddGamePane();
            var modalScene = new Scene(pane, 400, 300);

            var modal = new Stage();
            modal.setTitle("Add Game");
            modal.setScene(modalScene);
            modal.initOwner(getScene().getWindow());
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setResizable(false);
            modal.centerOnScreen();
            modal.showAndWait();

            @Nullable APIConnector.GameResult gameResult = pane.construct();
            if (gameResult != null) {
                Database.getInstance().addGame(new Game(
                        gameResult.getName(),
                        gameResult.getSummary(),
                        pane.getExecutablePath(),
                        gameResult.getThumbCoverURL(),
                        gameResult.getCoverURL(),
                        gameResult.getName(),
                        pane.getService() == GameService.STEAM
                ));
            }
        });

        // TODO: Create views for LIST and DETAILS

        this.content = new FlowPane();
        this.content.setPrefWidth(500);
        this.content.setPrefHeight(500);
        this.content.setBackground(Utils.createBackground("#3f3f4a"));
        this.content.setPadding(Utils.createInsets(15, 15, 15, 15));
        ((FlowPane) this.content).setHgap(15);
        ((FlowPane) this.content).setVgap(15);
        ((FlowPane) this.content).setAlignment(Pos.CENTER);

        // ensure that the loading games are displayed second
        this.content.getChildren().addListener((ListChangeListener<? super Node>) c -> {

        });

        this.content.getChildren().addAll(Database.getInstance().getGames().stream()
                .map(GridGameEntry::new)
                .map(GridGameEntry::getTile)
                .toArray(Tile[]::new));
        this.content.getChildren().addAll(Database.getInstance().getLoadingGames().stream()
                .map(name -> {
                    Tile tile = TileBuilder.create()
                            .skinType(Tile.SkinType.CIRCULAR_PROGRESS)
                            .prefSize(150, 200)
                            .textAlignment(TextAlignment.CENTER)
                            .text(name)
                            .textSize(Tile.TextSize.BIGGER)
                            .roundedCorners(true)
                            .backgroundColor(Tile.BACKGROUND)
                            .build();
                    tile.setUserData(name);
                    tile.setGraphic(new ProgressIndicator());
                    return tile;
                })
                .toArray(Tile[]::new));

        this.scrollPane = new ScrollPane(this.content);
        this.scrollPane.setFitToWidth(true);
        this.scrollPane.setFitToHeight(true);
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        this.contentContainer.getChildren().setAll(this.scrollPane, this.addGameButton);
        this.contentContainer.setAlignment(Pos.CENTER);
        StackPane.setAlignment(this.addGameButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(this.addGameButton, Utils.createInsets(0, 15, 15, 0));

        Database.getInstance().getGames().addListener((ListChangeListener<? super Game>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    List<? extends Game> addedGames = change.getAddedSubList();
                    for (int index = 0; index < addedGames.stream()
                            .map(GridGameEntry::new)
                            .map(GridGameEntry::getTile)
                            .toArray(Tile[]::new).length; index++) {
                        // add before the first loading game
                        int loadingGameIndex = this.content.getChildren().indexOf(this.content.getChildren().stream()
                                .filter(node -> {
                                    Object userData = node.getUserData();
                                    return node instanceof Tile && userData instanceof String name && Database.getInstance().getLoadingGames().contains(name);
                                })
                                .findFirst()
                                .orElse(null));
                        if(loadingGameIndex == -1)
                            loadingGameIndex = this.content.getChildren().size();

                        this.content.getChildren().add(loadingGameIndex, new GridGameEntry(addedGames.get(index)).getTile());
                    }
                } else if (change.wasRemoved()) {
                    this.content.getChildren().removeIf(node -> {
                        Object userData = node.getUserData();
                        if (node instanceof Tile && userData instanceof Game game) {
                            return change.getRemoved().contains(game);
                        }

                        return false;
                    });
                }
            }
        });

        Database.getInstance().getLoadingGames().addListener((ListChangeListener<? super String>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    this.content.getChildren().addAll(change.getAddedSubList().stream()
                            .map(name -> {
                                Tile tile = TileBuilder.create()
                                        .skinType(Tile.SkinType.CUSTOM)
                                        .prefSize(150, 200)
                                        .textAlignment(TextAlignment.CENTER)
                                        .text(name)
                                        .textSize(Tile.TextSize.BIGGER)
                                        .roundedCorners(true)
                                        .backgroundColor(Color.web("#3f3f4a"))
                                        .build();
                                tile.setUserData(name);
                                tile.setGraphic(new ProgressIndicator());
                                return tile;
                            })
                            .toArray(Tile[]::new));
                } else if (change.wasRemoved()) {
                    this.content.getChildren().removeIf(node -> {
                        Object userData = node.getUserData();
                        if (node instanceof Tile && userData instanceof String name) {
                            return change.getRemoved().contains(name);
                        }

                        return false;
                    });
                }
            }
        });

        setCenter(this.contentContainer);
    }

    public ObservableValue<ContentDisplay> contentDisplayProperty() {
        return this.contentDisplay;
    }

    public ContentDisplay getContentDisplay() {
        return this.contentDisplay.getValue();
    }

    public void setContentDisplay(ContentDisplay contentDisplay) {
        this.contentDisplay.setValue(contentDisplay);
    }

    public enum ContentDisplay {
        GRID, LIST, DETAILS;
    }
}
