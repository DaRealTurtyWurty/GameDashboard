package dev.turtywurty.gamedashboard.util;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ImageCache {
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".cache", "game-dashboard");
    private static final Map<String, Image> CACHE = new HashMap<>();
    private static final String PLACEHOLDER_IMAGE_URL =
            ImageCache.class.getResource("/images/questionmark_placeholder.png") == null
                    ? null
                    : ImageCache.class.getResource("/images/questionmark_placeholder.png").toExternalForm();

    public static Image getImage(String url) {
        return getImage(url, true, true, false);
    }

    public static Image getImage(String url, boolean fromCache, boolean toCache, boolean background) {
        if (url == null || url.isBlank())
            return getPlaceholderImage(background);

        if (fromCache && CACHE.containsKey(url))
            return CACHE.get(url);

        try {
            String filename = scrambleURL(url);
            Path path = Path.of(CACHE_DIR.toString(), filename);
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                var image = new Image(path.toUri().toString(), background);
                if (!isUsable(image))
                    return getPlaceholderImage(background);

                if (toCache) {
                    CACHE.put(url, image);
                }

                return image;
            }

            var image = loadImage(url, background);
            if (!isUsable(image))
                return getPlaceholderImage(background);

            if (toCache) {
                CACHE.put(url, image);

                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
                if (bufferedImage != null) {
                    ImageIO.write(bufferedImage, "png", path.toFile());
                } else {
                    GameDashboardApp.LOGGER.warn("Failed to convert FX Image to BufferedImage for caching: {}", url);
                }
            }

            return image;
        } catch (IOException | RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Failed to load image: {}", url, exception);
            return getPlaceholderImage(background);
        }
    }

    private static String scrambleURL(String url) {
        return Base64.getUrlEncoder().encodeToString(url.getBytes());
    }

    private static String unscrambleURL(String url) {
        return new String(Base64.getUrlDecoder().decode(url));
    }

    private static Image loadImage(String url, boolean background) {
        Image image = null;
        try {
            image = new Image(url, background);
            if (isUsable(image))
                return image;
        } catch (RuntimeException exception) {
            if (!isWebPURL(url))
                throw exception;
        }

        if (!isWebPURL(url))
            return image;

        try {
            BufferedImage bufferedImage = readWebP(url);
            if (bufferedImage == null)
                return image;

            return SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (IOException | RuntimeException exception) {
            return image;
        }
    }

    private static boolean isWebPURL(String url) {
        String normalizedURL = url.toLowerCase(Locale.ROOT);
        int queryStart = normalizedURL.indexOf('?');
        if (queryStart >= 0) {
            normalizedURL = normalizedURL.substring(0, queryStart);
        }

        return normalizedURL.endsWith(".webp");
    }

    private static BufferedImage readWebP(String url) throws IOException {
        if (url.startsWith("file:")) {
            return ImageIO.read(Path.of(URI.create(url)).toFile());
        }

        return ImageIO.read(new URL(url));
    }

    private static boolean isUsable(Image image) {
        return image != null && !image.isError() && image.getWidth() > 0 && image.getHeight() > 0;
    }

    private static Image getPlaceholderImage(boolean background) {
        if (PLACEHOLDER_IMAGE_URL == null)
            return new WritableImage(1, 1);

        return new Image(PLACEHOLDER_IMAGE_URL, background);
    }
}
