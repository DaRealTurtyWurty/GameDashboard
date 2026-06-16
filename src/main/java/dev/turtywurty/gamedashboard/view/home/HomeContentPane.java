package dev.turtywurty.gamedashboard.view.home;

import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.util.Utils;
import dev.turtywurty.gamedashboard.view.general.GridGameEntry;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.Comparator;
import java.util.List;

public class HomeContentPane extends BorderPane {
    private static final Comparator<Game> GAME_TITLE_COMPARATOR =
            Comparator.comparing(Game::getTitle, String.CASE_INSENSITIVE_ORDER);

    private final SimpleObjectProperty<ContentDisplay> contentDisplay = new SimpleObjectProperty<>(ContentDisplay.GRID);

    private final StackPane contentContainer = new StackPane();

    private ScrollPane scrollPane;
    private Pane content;

    public HomeContentPane() {
        getStyleClass().add("dashboard-content");

        // TODO: Create views for LIST and DETAILS

        this.content = new FlowPane();
        this.content.getStyleClass().add("game-grid");
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

        refreshGameTiles();
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

        this.contentContainer.getChildren().setAll(this.scrollPane);
        this.contentContainer.setAlignment(Pos.CENTER);

        Database.getInstance().getGames().addListener((ListChangeListener<? super Game>) change -> {
            refreshGameTiles();
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

    private void refreshGameTiles() {
        this.content.getChildren().removeIf(node -> node.getUserData() instanceof Game);

        Node[] gameTiles = Database.getInstance().getGames().stream()
                .sorted(GAME_TITLE_COMPARATOR)
                .map(GridGameEntry::new)
                .map(GridGameEntry::getNode)
                .toArray(Node[]::new);

        this.content.getChildren().addAll(0, List.of(gameTiles));
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
