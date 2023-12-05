package domains.brighton.rg764.gamedashboard;

import domains.brighton.rg764.gamedashboard.preloader.GameDashboardPreloader;
import javafx.application.Application;

public class Startup {
    public static void main(String[] args) {
        System.setProperty("javafx.preloader", GameDashboardPreloader.class.getName());
        Application.launch(GameDashboardApp.class, args);
    }
}
