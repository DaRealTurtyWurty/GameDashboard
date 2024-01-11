package dev.turtywurty.gamedashboard.view;

import dev.turtywurty.gamedashboard.view.general.TopMenuBar;
import dev.turtywurty.gamedashboard.view.home.HomeContentPane;
import dev.turtywurty.gamedashboard.view.home.HomeSidebarPane;
import javafx.scene.layout.BorderPane;

public class GameDashboardPane extends BorderPane {
    private final TopMenuBar menuBar;
    private final HomeSidebarPane gameSidebarPane;
    private final HomeContentPane gameContentPane;

    public GameDashboardPane() {
        this.menuBar = new TopMenuBar();
        this.menuBar.setPrefHeight(30);

        this.gameSidebarPane = new HomeSidebarPane();
        this.gameSidebarPane.setPrefWidth(200);

        this.gameContentPane = new HomeContentPane();
        this.gameContentPane.setPrefWidth(600);

        setTop(this.menuBar);
        setLeft(this.gameSidebarPane);
        setCenter(this.gameContentPane);
    }
}
