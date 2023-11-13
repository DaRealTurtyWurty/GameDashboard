package domains.brighton.rg764.gamedashboard.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ImageCache {
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".cache", "game-dashboard");
    private static final Map<String, Image> CACHE = new HashMap<>();

    static {
        try {
            if(Files.notExists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Log exception
        }
    }

    public static Image getImage(String url) {
        return getImage(url, true, true, false);
    }

    public static Image getImage(String url, boolean fromCache, boolean toCache, boolean background) {
        if (fromCache && CACHE.containsKey(url)) {
            return CACHE.get(url);
        }

        try {
            String filename = scrambleURL(url);
            Path path = Path.of(CACHE_DIR.toString(), filename);
            if(Files.exists(path)) {
                var image = new Image(path.toUri().toString(), background);
                if (toCache) {
                    CACHE.put(url, image);
                }

                return image;
            }

            var image = new Image(url, background);
            if(toCache) {
                CACHE.put(url, image);

                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", path.toFile());
            }

            return image;
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Log exception
            return null; // TODO: Return placeholder image
        }
    }

    private static String scrambleURL(String url) {
        var builder = new StringBuilder();
        for (char c : url.toCharArray()) {
            builder.append((char) (c + 3));
        }

        return builder.toString();
    }

    private static String unscrambleURL(String url) {
        var builder = new StringBuilder();
        for (char c : url.toCharArray()) {
            builder.append((char) (c - 3));
        }

        return builder.toString();
    }
}
