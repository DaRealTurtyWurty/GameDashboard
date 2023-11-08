package domains.brighton.rg764.gamedashboard;

import domains.brighton.rg764.gamedashboard.view.GameDashboardPane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GameDashboardApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        Scene scene = new Scene(new GameDashboardPane(), 800, 600);
        primaryStage.setTitle("Game Dashboard");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }
}
