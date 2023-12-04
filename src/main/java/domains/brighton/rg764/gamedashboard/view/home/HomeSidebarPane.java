package domains.brighton.rg764.gamedashboard.view.home;

import domains.brighton.rg764.gamedashboard.data.Database;
import domains.brighton.rg764.gamedashboard.util.Utils;
import domains.brighton.rg764.gamedashboard.view.general.GameSidebarEntry;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

public class HomeSidebarPane extends VBox {
    private final HomeSidebarPane.Header header;
    private final Separator separator0 = new Separator();
    private final HomeSidebarPane.Content content;
    private final Separator separator1 = new Separator();
    private final HomeSidebarPane.Footer footer;

    public HomeSidebarPane() {
        this.header = new HomeSidebarPane.Header();
        this.header.setPrefHeight(35);

        this.separator0.setPrefHeight(10);
        this.separator0.setPrefWidth(200);
        this.separator0.setValignment(VPos.CENTER);
        this.separator0.setPadding(Utils.createInsets(5, 0, 10, 0));

        this.content = new HomeSidebarPane.Content();
        this.content.setPrefHeight(500);

        this.separator1.setPrefHeight(10);
        this.separator1.setPrefWidth(200);
        this.separator1.setValignment(VPos.CENTER);
        this.separator1.setPadding(Utils.createInsets(10, 0, 10, 0));

        this.footer = new HomeSidebarPane.Footer();
        this.footer.setPrefHeight(30);

        VBox.setVgrow(this.header, Priority.NEVER);
        VBox.setVgrow(this.content, Priority.ALWAYS);
        VBox.setVgrow(this.footer, Priority.NEVER);

        getChildren().addAll(this.header, this.separator0, this.content, this.separator1, this.footer);

        setBackground(Utils.createBackground("#3f3f4a"));
    }

    public static class Header extends BorderPane {
        private final VBox titleVBox;
        private final Label title;
        private final Label subtitle;
        private final ImageView logo;

        public Header() {
            this.title = new Label("Game Dashboard");
            this.title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
            this.title.setTextFill(Color.web("#eee"));
            this.title.setWrapText(true);
            this.title.setTextAlignment(TextAlignment.CENTER);

            this.subtitle = new Label("Manage your games");
            this.subtitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            this.subtitle.setTextFill(Color.web("#eee"));
            this.subtitle.setWrapText(true);
            this.subtitle.setTextAlignment(TextAlignment.CENTER);

            Region spacer = new Region();
            spacer.setPrefHeight(10);

            this.logo = new ImageView("https://via.placeholder.com/50x50");
            this.logo.setFitHeight(50);
            this.logo.setFitWidth(50);
            this.logo.setPreserveRatio(true);

            this.titleVBox = new VBox(this.title, this.subtitle, spacer);
            this.titleVBox.setAlignment(Pos.CENTER);

            setTop(this.titleVBox);
            // setBottom(this.logo);

            setAlignment(this.titleVBox, Pos.CENTER);
            // setAlignment(this.logo, Pos.CENTER);
        }
    }

    public static class Content extends BorderPane {

        private final Label title;
        private final ScrollPane scrollPane = new ScrollPane();
        private final VBox gamesVBox;

        public Content() {
            this.title = new Label("Games");
            this.title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
            this.title.setTextFill(Color.web("#eee"));
            this.title.setWrapText(true);
            this.title.setTextAlignment(TextAlignment.CENTER);
            this.title.setAlignment(Pos.CENTER);
            this.title.setMaxWidth(200);

            this.scrollPane.setFitToWidth(true);
            this.scrollPane.setFitToHeight(true);
            this.scrollPane.setPrefWidth(200);
            this.scrollPane.setPrefHeight(500);
            this.scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

            this.gamesVBox = new VBox();
            this.gamesVBox.setSpacing(5);
            this.gamesVBox.setBackground(Utils.createBackground("#3f3f4a"));
            this.gamesVBox.getChildren()
                    .setAll(Database.getInstance().getGames()
                            .stream()
                            .map(GameSidebarEntry::new)
                            .toArray(GameSidebarEntry[]::new));

            this.scrollPane.setContent(this.gamesVBox);
            this.scrollPane.setBackground(Utils.createBackground("#3f3f4a"));

            setTop(this.title);
            setCenter(this.scrollPane);

            setAlignment(this.title, Pos.CENTER);
            setAlignment(this.scrollPane, Pos.CENTER);

            Database.getInstance().getGames().addListener((ListChangeListener.Change<?> change) -> {
                this.gamesVBox.getChildren().setAll(Database.getInstance().getGames()
                        .stream()
                        .map(GameSidebarEntry::new)
                        .toArray(GameSidebarEntry[]::new));
            });
        }
    }

    public static class Footer extends BorderPane {
        private final Label totalGames;

        public Footer() {
            this.totalGames = new Label("Total Games: " + Database.getInstance().getGames().size());
            this.totalGames.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            this.totalGames.setTextFill(Color.web("#eee"));
            this.totalGames.setWrapText(true);
            this.totalGames.setTextAlignment(TextAlignment.CENTER);
            this.totalGames.setAlignment(Pos.CENTER);
            this.totalGames.setPadding(Utils.createInsets(0, 0, 5, 0));
            this.totalGames.setMaxWidth(200);

            setCenter(this.totalGames);
            setAlignment(this.totalGames, Pos.CENTER);

            Database.getInstance().getGames().addListener((ListChangeListener.Change<?> change) ->
                    this.totalGames.setText("Total Games: " + change.getList().size()));
        }
    }
}
