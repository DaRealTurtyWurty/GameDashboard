package domains.brighton.rg764.gamedashboard.view.home;

import domains.brighton.rg764.gamedashboard.data.Database;
import domains.brighton.rg764.gamedashboard.data.Game;
import domains.brighton.rg764.gamedashboard.util.Utils;
import domains.brighton.rg764.gamedashboard.view.general.GameSidebarEntry;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

public class HomeSidebarPane extends VBox {
    private final HomeSidebarPane.Header header;
    private final HomeSidebarPane.Content content;
    private final HomeSidebarPane.Footer footer;

    public HomeSidebarPane() {
        this.header = new HomeSidebarPane.Header();
        this.header.setPrefHeight(50);

        this.content = new HomeSidebarPane.Content();
        this.content.setPrefHeight(500);

        this.footer = new HomeSidebarPane.Footer();
        this.footer.setPrefHeight(50);

        getChildren().addAll(this.header, this.content, this.footer);

        setBackground(Utils.createBackground("#3f3f4a"));
    }

    public static class Header extends BorderPane {
        private final Label title;
        private final Label subtitle;
        private final ImageView logo;

        public Header() {
            this.title = new Label("Game Dashboard");
            this.title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

            this.subtitle = new Label("Welcome to the Game Dashboard!");
            this.subtitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            this.subtitle.setWrapText(true);
            this.subtitle.setMaxWidth(200);
            this.subtitle.setTextAlignment(TextAlignment.CENTER);
            this.subtitle.setAlignment(Pos.CENTER);

            this.logo = new ImageView("https://via.placeholder.com/50x50");
            this.logo.setFitHeight(50);
            this.logo.setFitWidth(50);
            this.logo.setPreserveRatio(true);

            setTop(this.title);
            setCenter(this.subtitle);
            setBottom(this.logo);

            setAlignment(this.title, Pos.CENTER);
            setAlignment(this.subtitle, Pos.CENTER);
            setAlignment(this.logo, Pos.CENTER);
        }
    }

    public static class Content extends BorderPane {
        private final ObservableList<Game> games = Database.getInstance().getGames();

        private final Label title;
        private final VBox gamesVBox;

        public Content() {
            this.title = new Label("Games");
            this.title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            this.gamesVBox = new VBox();
            this.gamesVBox.setSpacing(10);

            setTop(this.title);
            setCenter(this.gamesVBox);

            setAlignment(this.title, Pos.CENTER);
            setAlignment(this.gamesVBox, Pos.CENTER);

            this.games.addListener((ListChangeListener.Change<?> change) -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        List<Game> added = change.getAddedSubList()
                                .stream()
                                .filter(Game.class::isInstance)
                                .map(Game.class::cast)
                                .toList();

                        for (Game game : added) {
                            this.gamesVBox.getChildren().add(new GameSidebarEntry(game));
                        }
                    } else if (change.wasRemoved()) {
                        List<Game> removed = change.getRemoved()
                                .stream()
                                .filter(Game.class::isInstance)
                                .map(Game.class::cast)
                                .toList();

                        List<GameSidebarEntry> toRemove = new ArrayList<>();
                        for (Game game : removed) {
                            List<GameSidebarEntry> children = this.gamesVBox.getChildren()
                                    .stream()
                                    .filter(GameSidebarEntry.class::isInstance)
                                    .map(GameSidebarEntry.class::cast)
                                    .toList();

                            for (GameSidebarEntry entry : children) {
                                if (entry.getGame().equals(game)) {
                                    toRemove.add(entry);
                                }
                            }
                        }

                        this.gamesVBox.getChildren().removeAll(toRemove);
                    }
                }

                // sort the games by title
                this.gamesVBox.getChildren().sort((object1, object2) -> {
                    if (object1 instanceof GameSidebarEntry entry1 && object2 instanceof GameSidebarEntry entry2) {
                        return entry1.getGame().getTitle().compareTo(entry2.getGame().getTitle());
                    }

                    return 0;
                });
            });
        }
    }

    public static class Footer extends BorderPane {
        private final Label totalGames;

        public Footer() {
            this.totalGames = new Label("Total Games: 0");
            this.totalGames.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

            setCenter(this.totalGames);
            
            setAlignment(this.totalGames, Pos.CENTER);

            Database.getInstance().getGames().addListener((ListChangeListener.Change<?> change) ->
                    this.totalGames.setText("Total Games: " + change.getList().size()));
        }
    }
}
