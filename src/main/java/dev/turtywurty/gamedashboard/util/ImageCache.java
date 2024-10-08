package dev.turtywurty.gamedashboard.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ImageCache {
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".cache", "game-dashboard");
    private static final Map<String, Image> CACHE = new HashMap<>();

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
            Files.createDirectories(path.getParent());
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

                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
                if (bufferedImage != null) {
                    ImageIO.write(bufferedImage, "png", path.toFile());
                } else {
                    // TODO: Log error
                }
            }

            return image;
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Log exception
            return null; // TODO: Return placeholder image
        }
    }

    private static String scrambleURL(String url) {
        return Base64.getEncoder().encodeToString(url.getBytes());
    }

    private static String unscrambleURL(String url) {
        return new String(Base64.getDecoder().decode(url));
    }
}
