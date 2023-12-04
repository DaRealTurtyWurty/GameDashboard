package domains.brighton.rg764.gamedashboard.view.general;

import domains.brighton.rg764.gamedashboard.data.Database;
import domains.brighton.rg764.gamedashboard.view.steam.SteamConfigurationPane;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class TopMenuBar extends MenuBar {
    private final Menu homeMenu;
    private final Menu viewMenu;
    private final Menu helpMenu;

    public TopMenuBar() {
        this.homeMenu = new Menu("Home");
        this.viewMenu = new Menu("View");
        this.helpMenu = new Menu("Help");

        var steamSetup = new MenuItem(Database.getInstance().getSteamLocation().isEmpty() ? "Setup Steam" : "Configure Steam");
        steamSetup.setOnAction(e -> {
            var steamConfigurationPane = new SteamConfigurationPane();
            var modalScene = new Scene(steamConfigurationPane, 400, 300);

            var modal = new Stage();
            modal.setTitle("Setup Steam");
            modal.setScene(modalScene);
            modal.initOwner(getScene().getWindow());
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setResizable(false);
            modal.centerOnScreen();
            modal.showAndWait();

            steamConfigurationPane.construct();
        });

        this.homeMenu.getItems().add(steamSetup);

        getMenus().addAll(this.homeMenu, this.viewMenu, this.helpMenu);
    }
}
