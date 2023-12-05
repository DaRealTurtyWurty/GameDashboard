package domains.brighton.rg764.gamedashboard.preloader;

import javafx.application.Preloader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import lombok.Getter;


public class GameDashboardPreloader extends Preloader {
    private Stage stage;
    private ProgressBar progressBar;

    @Getter
    private static GameDashboardPreloader instance;

    public GameDashboardPreloader() {
        instance = this;
    }

    public void setLoaded() {
        progressBar.setProgress(1);
        stage.hide();
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        BorderPane root = new BorderPane();

        ImageView logo = new ImageView(getClass()
                .getResource("/images/logo_transparent_bg.png")
                .toExternalForm());
        logo.setFitWidth(300);
        logo.setFitHeight(300);

        Label title = new Label("Game Dashboard");
        title.setStyle("-fx-font-size: 40px;");
        title.setTextFill(Color.WHITE);

        VBox topBox = new VBox();
        topBox.getChildren().addAll(logo, title);
        topBox.setSpacing(10);
        topBox.setStyle("-fx-padding: 10px;");
        topBox.setAlignment(Pos.CENTER);

        root.setTop(topBox);

        Label label = new Label("Loading...");
        label.setStyle("-fx-font-size: 20px;");
        label.setTextFill(Color.WHITE);

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);
        progressBar.setPrefHeight(20);
        progressBar.setProgress(0);

        Label progressLabel = new Label("0%");
        progressLabel.setStyle("-fx-font-size: 20px;");
        progressLabel.setTextFill(Color.WHITE);

        progressBar.progressProperty().addListener((observable, oldValue, newValue) -> {
            progressLabel.setText((int) (newValue.doubleValue() * 100) + "%");
        });

        VBox centerBox = new VBox();
        centerBox.getChildren().addAll(label, progressBar, progressLabel);
        centerBox.setSpacing(10);
        centerBox.setStyle("-fx-padding: 10px;");
        centerBox.setAlignment(Pos.CENTER);

        root.setCenter(centerBox);

        BorderPane.setAlignment(topBox, Pos.CENTER);
        BorderPane.setAlignment(centerBox, Pos.CENTER);

        root.setStyle("-fx-background-color: #2c2c5c;");

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);

        primaryStage.setTitle("Game Dashboard");
        primaryStage.getIcons().add(new Image(getClass()
                .getResource("/images/logo.png")
                .toExternalForm()));
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.centerOnScreen();
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(event -> System.exit(0));
        primaryStage.setAlwaysOnTop(true);
        primaryStage.show();
    }

    @Override
    public void handleProgressNotification(ProgressNotification info) {
        progressBar.setProgress(info.getProgress());
    }
}
