package domains.brighton.rg764.gamedashboard.util;

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;

public class Utils {
    public static Background createBackground(String hexColor) {
        return new Background(new BackgroundFill(Color.web(hexColor), null, null));
    }

    public static Insets createInsets(int top, int right, int bottom, int left) {
        return new Insets(top, right, bottom, left);
    }

    public static Insets createInsets(int topRightBottomLeft) {
        return new Insets(topRightBottomLeft);
    }
}
