package domains.brighton.rg764.gamedashboard.util;

import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;

public class Utils {
    public static Background createBackground(String hexColor) {
        return new Background(new BackgroundFill(Color.web(hexColor), null, null));
    }
}
