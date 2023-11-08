package domains.brighton.rg764.gamedashboard.view.home;

import domains.brighton.rg764.gamedashboard.data.Database;
import domains.brighton.rg764.gamedashboard.data.Game;
import eu.hansolo.tilesfx.tools.FlowGridPane;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.UUID;

public class HomeContentPane extends BorderPane {
    private final SimpleObjectProperty<ContentDisplay> contentDisplay = new SimpleObjectProperty<>(ContentDisplay.GRID);

    private final StackPane contentContainer = new StackPane();

    private Button addGameButton;
    private ScrollPane scrollPane;
    private Node content;

    public HomeContentPane() {
        this.addGameButton = new Button("Add Game");
        this.addGameButton.setOnAction(event -> {
            // TODO: Create modal
            Database.getInstance().addGame(new Game(
                    "Test Game " + (Database.getInstance().getGames().size() + 1),
                    "This is a test game",
                    "https://via.placeholder.com/150",
                    UUID.randomUUID().toString()
            ));
        });

        // TODO: Create proper views for the content displays
        this.contentDisplay.addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case GRID:
                    this.content = new FlowGridPane(7, 15);
                    this.scrollPane = new ScrollPane(this.content);
                    this.scrollPane.setFitToWidth(true);
                    this.scrollPane.setFitToHeight(true);
                    this.contentContainer.getChildren().setAll(this.scrollPane, this.addGameButton);
                    StackPane.setAlignment(this.addGameButton, Pos.BOTTOM_RIGHT);
                    break;
                case LIST, DETAILS:
                    this.content = new VBox();
                    this.scrollPane = new ScrollPane(this.content);
                    this.scrollPane.setFitToWidth(true);
                    this.scrollPane.setFitToHeight(true);
                    this.contentContainer.getChildren().setAll(this.scrollPane, this.addGameButton);
                    StackPane.setAlignment(this.addGameButton, Pos.BOTTOM_RIGHT);
                    break;
            }
        });

        setContentDisplay(ContentDisplay.GRID);
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
        GRID, LIST, DETAILS
    }
}
