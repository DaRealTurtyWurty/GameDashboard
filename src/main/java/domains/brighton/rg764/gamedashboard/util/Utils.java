package domains.brighton.rg764.gamedashboard.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class Utils {
    public static Background createBackground(String hexColor) {
        return createBackground(Color.web(hexColor));
    }

    public static Background createBackground(Color color) {
        return new Background(new BackgroundFill(color, null, null));
    }

    public static Insets createInsets(int top, int right, int bottom, int left) {
        return new Insets(top, right, bottom, left);
    }

    public static Insets createInsets(int topRightBottomLeft) {
        return new Insets(topRightBottomLeft);
    }

    // https://www.geeksforgeeks.org/java-program-to-implement-levenshtein-distance-computing-algorithm/
    public static int levenshteinDistance(String source, String target) {
        // A 2D array to store previously calculated values
        int[][] distanceTable = new int[source.length() + 1][target.length() + 1]; // +1 to account for empty strings

        for (int row = 0; row <= source.length(); row++) {
            for (int column = 0; column <= target.length(); column++) {
                // If either string is empty, then the distance is the length of the other string
                if (row == 0) {
                    distanceTable[row][column] = column;
                } else if (column == 0) {
                    distanceTable[row][column] = row;
                }

                // Find the minimum among the replace, delete, and insert operations
                else {
                    int replace = distanceTable[row - 1][column - 1] + source.charAt(row - 1) == target.charAt(column - 1) ? 0 : 1;
                    int delete = distanceTable[row - 1][column] + 1;
                    int insert = distanceTable[row][column - 1] + 1;
                    distanceTable[row][column] = Math.min(replace, Math.min(delete, insert));
                }
            }
        }

        return distanceTable[source.length()][target.length()];
    }

    public static Color getAverageColor(Image backgroundImage) {
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(backgroundImage, null);
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        int[] pixels = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);

        int red = 0;
        int green = 0;
        int blue = 0;
        for (int pixel : pixels) {
            red += (pixel >> 16) & 0xFF;
            green += (pixel >> 8) & 0xFF;
            blue += pixel & 0xFF;
        }

        int total = width * height;
        return Color.rgb(red / total, green / total, blue / total);
    }
}
