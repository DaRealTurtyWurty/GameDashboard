package domains.brighton.rg764.gamedashboard.view.add_game;

import javafx.scene.layout.BorderPane;
import lombok.Getter;

import java.nio.file.Path;

@Getter
public class BasicAddGamePane extends BorderPane {
    private final String gameTitle;
    private final Path gameDirectory;

    public BasicAddGamePane(String gameTitle, Path gameDirectory) {
        this.gameTitle = gameTitle;
        this.gameDirectory = gameDirectory;
    }
}
