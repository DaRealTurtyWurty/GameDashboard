package domains.brighton.rg764.gamedashboard.view.general;

import domains.brighton.rg764.gamedashboard.data.Game;
import domains.brighton.rg764.gamedashboard.util.ImageCache;
import domains.brighton.rg764.gamedashboard.util.Utils;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.Glow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import lombok.Getter;

public class GameSidebarEntry extends HBox {
    private final ImageView icon;
    private final Label title;

    @Getter
    private final Game game;

    public GameSidebarEntry(Game game) {
        this.game = game;

        setPadding(Utils.createInsets(5, 10, 5, 10));
        setBackground(Utils.createBackground("#2f2f3a"));

        this.icon = new ImageView(ImageCache.getImage(game.getThumbCoverImageURL()));
        this.icon.setFitHeight(50);
        this.icon.setFitWidth(50);
        this.icon.setPreserveRatio(true);

        this.title = new Label(game.getTitle());
        this.title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        this.title.setTextFill(Color.web("#eee"));
        this.title.setPrefWidth(150);
        this.title.setWrapText(true);
        this.title.setAlignment(Pos.CENTER_LEFT);
        this.title.setTextAlignment(TextAlignment.LEFT);
        this.title.setPrefHeight(50);
        this.title.setPadding(Utils.createInsets(0, 0, 0, 5));

        getChildren().addAll(this.icon, this.title);

        setOnMouseClicked(event -> {
            // TODO: Switch main content pane to game info view
        });

        // TODO: 3d effect
        setOnMouseEntered(event -> setEffect(new Glow()));
        setOnMouseExited(event -> setEffect(null));
    }

    public void setIcon(String url) {
        this.icon.setImage(ImageCache.getImage(url));
    }
}
