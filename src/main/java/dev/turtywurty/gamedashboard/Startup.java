package dev.turtywurty.gamedashboard;

import dev.turtywurty.gamedashboard.preloader.GameDashboardPreloader;
import javafx.application.Application;

public class Startup {
    public static void main(String[] args) {
        System.setProperty("javafx.preloader", GameDashboardPreloader.class.getName());
        Application.launch(GameDashboardApp.class, args);
    }
}
