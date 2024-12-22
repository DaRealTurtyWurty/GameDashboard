package dev.turtywurty.gamedashboard;

import dev.turtywurty.gamedashboard.preloader.GameDashboardPreloader;
import dev.turtywurty.gamedashboard.view.GameDashboardPane;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

public class GameDashboardApp extends Application {
    private static final Dotenv ENVIRONMENT = Dotenv.configure()
            .directory("./env")
            .filename(".env")
            .load();

    public static final Logger LOGGER = LoggerFactory.getLogger(GameDashboardApp.class);
    public static final String VERSION = "1.0.0";

    @Override
    public void start(Stage primaryStage) {
        Scene scene = new Scene(new GameDashboardPane(), 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        primaryStage.setTitle("Game Dashboard");
        primaryStage.getIcons().add(new Image(getClass().getResource("/images/logo_transparent_bg.png").toExternalForm()));
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        Platform.setImplicitExit(false);

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            Platform.exit();
        });

        primaryStage.setOnShown(ignored -> GameDashboardPreloader.getInstance().setLoaded());
        primaryStage.show();
    }

    public static String getAPIKey() throws RuntimeException {
        return Optional.ofNullable(ENVIRONMENT.get("API_KEY", null))
                .orElseThrow(() -> new RuntimeException("API key not found"));
    }

    public static final Path APP_DATA_PATH = getAppDataDirectory();

    private static Path getAppDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Path.of(System.getenv("APPDATA"), "GameDashboard");
        } else if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "GameDashboard");
        } else if (os.contains("nix") || os.contains("nux")) {
            return Path.of(System.getProperty("user.home"), ".config", "GameDashboard");
        }

        return Path.of(System.getProperty("user.dir"), "GameDashboard");
    }
}
