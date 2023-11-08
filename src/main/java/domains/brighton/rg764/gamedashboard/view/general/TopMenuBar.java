package domains.brighton.rg764.gamedashboard.view.general;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;

public class TopMenuBar extends MenuBar {
    private final Menu homeMenu;
    private final Menu editMenu;
    private final Menu viewMenu;
    private final Menu helpMenu;

    public TopMenuBar() {
        this.homeMenu = new Menu("Home");
        this.editMenu = new Menu("Edit");
        this.viewMenu = new Menu("View");
        this.helpMenu = new Menu("Help");

        getMenus().addAll(this.homeMenu, this.editMenu, this.viewMenu, this.helpMenu);
    }
}
