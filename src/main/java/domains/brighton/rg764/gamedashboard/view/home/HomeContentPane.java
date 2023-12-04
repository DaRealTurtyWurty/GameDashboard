package domains.brighton.rg764.gamedashboard.view.home;

import domains.brighton.rg764.gamedashboard.data.APIConnector;
import domains.brighton.rg764.gamedashboard.data.Database;
import domains.brighton.rg764.gamedashboard.data.Game;
import domains.brighton.rg764.gamedashboard.data.GameService;
import domains.brighton.rg764.gamedashboard.util.Utils;
import domains.brighton.rg764.gamedashboard.view.add_game.AddGamePane;
import domains.brighton.rg764.gamedashboard.view.general.GridGameEntry;
import eu.hansolo.tilesfx.Tile;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.Nullable;

public class HomeContentPane extends BorderPane {
    private final SimpleObjectProperty<ContentDisplay> contentDisplay = new SimpleObjectProperty<>(ContentDisplay.GRID);

    private final StackPane contentContainer = new StackPane();

    private Button addGameButton;
    private ScrollPane scrollPane;
    private Pane content;

    public HomeContentPane() {
        this.addGameButton = new Button("Add Game");
        this.addGameButton.setOnAction(event -> {
            var pane = new AddGamePane();
            Scene modalScene = new Scene(pane, 400, 300);
            // TODO: Add stylesheet

            Stage modal = new Stage();
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

        // TODO: Create proper views for the content displays

        this.content = new FlowPane();
        this.content.setPrefWidth(500);
        this.content.setPrefHeight(500);
        this.content.setBackground(Utils.createBackground("#3f3f4a"));
        this.content.setPadding(Utils.createInsets(15, 15, 15, 15));
        ((FlowPane)this.content).setHgap(15);
        ((FlowPane)this.content).setVgap(15);
        ((FlowPane)this.content).setAlignment(Pos.CENTER);

        this.content.getChildren().setAll(Database.getInstance().getGames().stream()
                .map(GridGameEntry::new)
                .map(GridGameEntry::getTile)
                .toArray(Tile[]::new));

        this.scrollPane = new ScrollPane(this.content);
        this.scrollPane.setFitToWidth(true);
        this.scrollPane.setFitToHeight(true);
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        this.contentContainer.getChildren().setAll(this.scrollPane, this.addGameButton);
        this.contentContainer.setAlignment(Pos.CENTER);
        StackPane.setAlignment(this.addGameButton, Pos.BOTTOM_RIGHT);

        Database.getInstance().getGames().addListener((ListChangeListener<? super Game>) change -> {
            this.content.getChildren().setAll(Database.getInstance().getGames().stream()
                    .map(GridGameEntry::new)
                    .map(GridGameEntry::getTile)
                    .toArray(Tile[]::new));
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
