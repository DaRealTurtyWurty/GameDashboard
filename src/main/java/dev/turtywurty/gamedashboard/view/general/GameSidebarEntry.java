package dev.turtywurty.gamedashboard.view.general;

import dev.turtywurty.gamedashboard.util.ImageCache;
import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.Glow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import lombok.Getter;

public class GameSidebarEntry extends HBox {
    private static final double ICON_SIZE = 50;

    private final ImageView icon;
    private final Label title;

    @Getter
    private final Game game;

    public GameSidebarEntry(Game game) {
        this.game = game;
        getStyleClass().add("game-sidebar-entry");

        setPadding(Utils.createInsets(5, 10, 5, 10));
        setBackground(Utils.createBackground("#2f2f3a"));

        this.icon = new ImageView(ImageCache.getImage(game.getThumbCoverImageURL()));
        this.icon.setFitHeight(ICON_SIZE);
        this.icon.setFitWidth(ICON_SIZE);
        this.icon.setPreserveRatio(true);

        var iconContainer = new StackPane(this.icon);
        iconContainer.setPrefSize(ICON_SIZE, ICON_SIZE);
        iconContainer.setMinSize(ICON_SIZE, ICON_SIZE);
        iconContainer.setMaxSize(ICON_SIZE, ICON_SIZE);

        String logoUrl = game.getCoverLogoImageURL();
        if (logoUrl != null && !logoUrl.isBlank()) {
            var logo = new ImageView(ImageCache.getImage(logoUrl));
            logo.setFitWidth(ICON_SIZE * 0.82);
            logo.setFitHeight(ICON_SIZE * 0.35);
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
            logo.setMouseTransparent(true);
            iconContainer.getChildren().add(logo);
        }

        this.title = new Label(game.getTitle());
        this.title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        this.title.setTextFill(Color.web("#eee"));
        this.title.setPrefWidth(150);
        this.title.setWrapText(true);
        this.title.setAlignment(Pos.CENTER_LEFT);
        this.title.setTextAlignment(TextAlignment.LEFT);
        this.title.setPrefHeight(50);
        this.title.setPadding(Utils.createInsets(0, 0, 0, 5));

        getChildren().addAll(iconContainer, this.title);

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
