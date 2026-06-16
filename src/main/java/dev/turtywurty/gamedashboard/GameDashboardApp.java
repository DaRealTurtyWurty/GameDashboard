package dev.turtywurty.gamedashboard;

import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.preloader.GameDashboardPreloader;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.view.GameDashboardPane;
import dev.turtywurty.gamedashboard.view.onboarding.OnboardingPane;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

public class GameDashboardApp extends Application {
    public static final String STYLESHEET = "/css/style.css";

    private static final Dotenv ENVIRONMENT = Dotenv.configure()
            .directory("./env")
            .filename(".env")
            .load();

    private static GameDashboardApp app;

    public static final Logger LOGGER = LoggerFactory.getLogger(GameDashboardApp.class);

    @Override
    public void start(Stage primaryStage) {
        app = this;

        var root = new StackPane();

        Runnable openDashboard = () -> Platform.runLater(() -> root.getChildren().setAll(new GameDashboardPane()));

        if (Database.getInstance().isOnboardingComplete()) {
            openDashboard.run();
        } else {
            root.getChildren().setAll(new OnboardingPane(() -> {
                Database.getInstance().completeOnboarding();
                openDashboard.run();
            }));
        }

        var scene = new Scene(root, 800, 600);
        applyStylesheet(scene);
        primaryStage.setTitle("Game Dashboard");
        primaryStage.getIcons().add(new Image(getClass().getResource("/images/logo_transparent_bg.png").toExternalForm()));
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();

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

    public static void applyStylesheet(Scene scene) {
        scene.getStylesheets().add(GameDashboardApp.class.getResource(STYLESHEET).toExternalForm());
    }

    public static HostServices getHostServicesInstance() {
        return app.getHostServices();
    }

    private static Path getAppDataDirectory() {
        return switch (OperatingSystem.getCurrent()) {
            case WINDOWS -> Path.of(System.getenv("APPDATA"), "GameDashboard");
            case MACOS -> Path.of(System.getProperty("user.home"), "Library", "Application Support", "GameDashboard");
            case LINUX -> Path.of(System.getProperty("user.home"), ".config", "GameDashboard");
            default -> Path.of(System.getProperty("user.dir"), "GameDashboard");
        };
    }
}
