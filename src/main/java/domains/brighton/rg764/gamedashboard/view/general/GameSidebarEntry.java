package domains.brighton.rg764.gamedashboard.view.general;

import domains.brighton.rg764.gamedashboard.data.Game;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import lombok.Getter;

public class GameSidebarEntry extends HBox {
    private final ImageView icon;
    private final Label title;

    @Getter
    private final Game game;

    public GameSidebarEntry(Game game) {
        this.game = game;

        this.icon = new ImageView(game.getCoverImageURL());
        this.icon.setFitHeight(50);
        this.icon.setFitWidth(50);
        this.icon.setPreserveRatio(true);
        this.icon.setSmooth(true);
        this.icon.setCache(true);
        this.icon.setPickOnBounds(true);

        this.title = new Label(game.getTitle());
        this.title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        this.title.setPrefWidth(150);

        getChildren().addAll(this.icon, this.title);
    }

    public void setIcon(String url) {
        this.icon.setImage(new ImageView(url).getImage());
    }
}
