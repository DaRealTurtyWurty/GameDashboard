package domains.brighton.rg764.gamedashboard.view;

import domains.brighton.rg764.gamedashboard.view.general.TopMenuBar;
import domains.brighton.rg764.gamedashboard.view.home.HomeContentPane;
import domains.brighton.rg764.gamedashboard.view.home.HomeSidebarPane;
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
