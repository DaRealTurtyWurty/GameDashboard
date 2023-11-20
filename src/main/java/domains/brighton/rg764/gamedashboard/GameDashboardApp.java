package domains.brighton.rg764.gamedashboard;

import domains.brighton.rg764.gamedashboard.view.GameDashboardPane;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Optional;

public class GameDashboardApp extends Application {
    private static final Dotenv ENVIRONMENT = Dotenv.configure()
            .directory("./env")
            .filename(".env")
            .load();

    @Override
    public void start(Stage primaryStage) {
        Scene scene = new Scene(new GameDashboardPane(), 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        primaryStage.setTitle("Game Dashboard");
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static String getAPIKey() throws RuntimeException {
        return Optional.ofNullable(ENVIRONMENT.get("API_KEY", null))
                .orElseThrow(() -> new RuntimeException("API key not found"));
    }
}
