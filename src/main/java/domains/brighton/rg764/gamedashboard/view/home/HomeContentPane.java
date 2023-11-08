package domains.brighton.rg764.gamedashboard.view.home;

import eu.hansolo.tilesfx.tools.FlowGridPane;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class HomeContentPane extends BorderPane {
    private final SimpleObjectProperty<ContentDisplay> contentDisplay = new SimpleObjectProperty<>(ContentDisplay.GRID);

    private Node content;

    public HomeContentPane() {
        // TODO: Create proper views for the content displays
        this.contentDisplay.addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case GRID:
                    this.content = new FlowGridPane(7, 15);
                    setCenter(this.content);
                    break;
                case LIST:
                    this.content = new VBox();
                    setCenter(this.content);
                    break;
                case DETAILS:
                    this.content = new VBox();
                    setCenter(this.content);
                    break;
            }
        });

        setContentDisplay(ContentDisplay.GRID);
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
