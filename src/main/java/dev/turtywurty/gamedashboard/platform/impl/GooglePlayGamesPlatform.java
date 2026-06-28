package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.ExecutableLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.LaunchTarget;
import dev.turtywurty.gamedashboard.data.game.UriLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.impl.GooglePlayGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OSUtils;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.scene.image.Image;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GooglePlayGamesPlatform implements Platform {
    private static final Pattern LAUNCH_URI_PATTERN = Pattern.compile(
            "googleplaygames://launch/\\?id=([A-Za-z0-9_.]+)(?:[&\\p{Alnum}=._%-]*)?"
    );
    private static final Pattern IMAGE_CACHE_PATTERN = Pattern.compile(
            "^(.+)\\.(appicon|background|logo)\\.(png|ico)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final int GENERATED_COVER_WIDTH = 600;
    private static final int GENERATED_COVER_HEIGHT = 800;

    @Override
    public Image getIcon() {
        return new Image(Objects.requireNonNull(
                getClass().getResource("/images/platforms/google_play.png")
        ).toExternalForm());
    }

    @Override
    public String getName() {
        return "Google Play Games";
    }

    @Override
    public String getWebsite() {
        return "https://play.google.com/googleplaygames";
    }

    @Override
    public String getDescription() {
        return "Google Play Games is Google's PC platform for playing select Android games on Windows.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS) {
                progressMonitor.start("Google Play Games discovery is only available on Windows", 1);
                progressMonitor.worked(1);
                progressMonitor.done();
                return;
            }

            Path dataPath = getDefaultDataPath();
            if (dataPath == null || !Files.isDirectory(dataPath)) {
                progressMonitor.start("Finding Google Play Games data", 1);
                progressMonitor.worked(1);
                progressMonitor.done();
                return;
            }

            addGames(progressMonitor, dataPath);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the Google Play Games data directory containing image_cache.",
                "google-play-games",
                false,
                0
        );

        Path defaultPath = getDefaultDataPath();
        var dataDirectoryField = form.addDirectoryField(
                "Google Play Games data directory",
                defaultPath != null && Files.isDirectory(defaultPath) ? defaultPath.toString() : "",
                defaultPath == null ? "" : defaultPath.toString(),
                "Select Google Play Games Data Directory",
                false
        );

        return form.build(progressMonitor -> {
            if (!form.validate(dataDirectoryField, Utils::isReadableDirectory, "Select a readable Google Play Games data directory."))
                return;

            form.hideError();
            ManualEntryForm.runAsync(
                    progressMonitor,
                    () -> addGames(progressMonitor, Path.of(dataDirectoryField.getText()))
            );
        });
    }

    private static void addGames(ProgressMonitor progressMonitor, Path dataPath) {
        progressMonitor.start("Reading Google Play Games metadata", 2);
        Map<String, GooglePlayInstallation> installations = new LinkedHashMap<>();
        discoverShortcuts().forEach(installation -> installations.put(installation.packageName(), installation));
        progressMonitor.worked(1);

        mergeImageCache(dataPath.resolve("image_cache"), installations);
        progressMonitor.worked(1);

        List<GooglePlayInstallation> games = installations.values().stream()
                .filter(GooglePlayGamesPlatform::isGameCandidate)
                .sorted(Comparator.comparing(GooglePlayInstallation::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (games.isEmpty()) {
            progressMonitor.done();
            return;
        }

        progressMonitor.start("Loading Google Play Games", games.size());
        try {
            for (GooglePlayInstallation game : games) {
                addGame(progressMonitor, game);
            }
        } finally {
            progressMonitor.done();
        }
    }

    private static List<GooglePlayInstallation> discoverShortcuts() {
        List<GooglePlayInstallation> installations = new ArrayList<>();
        OSUtils.findStartMenuShortcuts("Google Play Games").stream()
                .map(GooglePlayGamesPlatform::readShortcut)
                .flatMap(Optional::stream)
                .forEach(installations::add);
        return installations;
    }

    private static Optional<GooglePlayInstallation> readShortcut(Path shortcut) {
        try {
            String text = OSUtils.readShortcutText(shortcut);
            Matcher matcher = LAUNCH_URI_PATTERN.matcher(text);
            if (!matcher.find())
                return Optional.empty();

            String packageName = matcher.group(1);
            String launchUri = matcher.group();
            String title = Utils.stripExtension(shortcut.getFileName().toString());
            return Optional.of(new GooglePlayInstallation(
                    packageName,
                    title,
                    launchUri,
                    shortcut.toString(),
                    null,
                    null,
                    null
            ));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.warn("Unable to read Google Play Games shortcut {}", shortcut, exception);
            return Optional.empty();
        }
    }

    private static void mergeImageCache(Path imageCache, Map<String, GooglePlayInstallation> installations) {
        if (!Files.isDirectory(imageCache))
            return;

        try (Stream<Path> paths = Files.list(imageCache)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                Matcher matcher = IMAGE_CACHE_PATTERN.matcher(path.getFileName().toString());
                if (!matcher.matches())
                    return;

                String packageName = matcher.group(1);
                GooglePlayInstallation existing = installations.getOrDefault(
                        packageName,
                        GooglePlayInstallation.fromPackageName(packageName)
                );
                installations.put(packageName, existing.withAsset(matcher.group(2), matcher.group(3), path));
            });
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.warn("Unable to scan Google Play Games image cache at {}", imageCache, exception);
        }
    }

    private static void addGame(ProgressMonitor progressMonitor, GooglePlayInstallation installation) {
        try {
            APIConnector.GameResult metadata = findMetadata(installation);
            String generatedCover = metadata == null || Utils.isPlaceholderUrl(metadata.getCoverURL())
                    ? generatePortraitCover(installation).map(Path::toUri).map(Object::toString).orElse(null)
                    : null;
            String iconUrl = Utils.toImageUrl(installation.appIconPath());
            String backgroundUrl = Utils.toImageUrl(installation.backgroundPath());
            String logoUrl = Utils.toImageUrl(installation.logoPath());
            String coverUrl = Utils.firstNonBlank(
                    metadata == null ? null : metadata.getCoverURL(),
                    generatedCover,
                    backgroundUrl,
                    iconUrl,
                    Utils.PLACEHOLDER_COVER_URL
            );
            String thumbUrl = Utils.firstNonBlank(
                    metadata == null ? null : metadata.getThumbCoverURL(),
                    iconUrl,
                    generatedCover,
                    backgroundUrl,
                    Utils.PLACEHOLDER_COVER_URL
            );

            GooglePlayGame game = GooglePlayGame.builder(
                            installation.title(),
                            metadata == null || metadata.getSummary() == null ? "" : metadata.getSummary(),
                            createLaunchTarget(installation.launchUri()),
                            installation.packageName(),
                            installation.launchUri()
                    )
                    .shortcutPath(installation.shortcutPath())
                    .localAssets(
                            pathToString(installation.appIconPath()),
                            pathToString(installation.backgroundPath()),
                            pathToString(installation.logoPath())
                    )
                    .images(thumbUrl, coverUrl, metadata == null ? null : logoUrl)
                    .igdbGameId(metadata == null ? null : metadata.getIgdbGameId())
                    .nickname(installation.title())
                    .build();

            GameDashboardApp.LOGGER.info(
                    "Found Google Play Games title '{}' ({})",
                    installation.title(),
                    installation.packageName()
            );
            Utils.runOnFxThread(() -> {
                if (!Database.getInstance().addGame(game)) {
                    Database.getInstance().updateGame(game, game);
                }
            });
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.error("Failed to load Google Play Games title '{}'", installation.title(), exception);
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static @Nullable APIConnector.GameResult findMetadata(GooglePlayInstallation installation) {
        try {
            Integer igdbGameId = APIConnector.getGameIdFromExternalId(
                    APIConnector.ExternalPlatform.ANDROID,
                    installation.packageName()
            ).join();
            if (igdbGameId != null) {
                APIConnector.GameResult result = APIConnector.getGameByID(igdbGameId, true, true).join();
                if (result != null)
                    return result;
            }
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn(
                    "Unable to load IGDB external metadata for Google Play package {}",
                    installation.packageName(),
                    exception
            );
        }

        try {
            return APIConnector.findBestFuzzyGameMatch(installation.title(), true, true, "Android", null)
                    .join()
                    .gameResult();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn(
                    "Unable to load IGDB title metadata for Google Play title '{}'",
                    installation.title(),
                    exception
            );
            return null;
        }
    }

    private static Optional<Path> generatePortraitCover(GooglePlayInstallation installation) {
        Path source = installation.backgroundPath();
        if (source == null || !Files.isRegularFile(source)) {
            source = installation.appIconPath();
        }

        if (source == null || !Files.isRegularFile(source))
            return Optional.empty();

        Path output = generatedCoverPath(installation.packageName());
        try {
            Files.createDirectories(output.getParent());
            BufferedImage background = ImageIO.read(source.toFile());
            if (background == null)
                return Optional.empty();

            var cover = new BufferedImage(
                    GENERATED_COVER_WIDTH,
                    GENERATED_COVER_HEIGHT,
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D graphics = cover.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                drawCoverCrop(graphics, background);
                drawVignette(graphics);
                drawLogoOrTitle(graphics, installation);
            } finally {
                graphics.dispose();
            }

            ImageIO.write(cover, "png", output.toFile());
            return Optional.of(output);
        } catch (IOException | RuntimeException exception) {
            GameDashboardApp.LOGGER.warn(
                    "Unable to generate Google Play portrait cover for {}",
                    installation.packageName(),
                    exception
            );
            return Optional.empty();
        }
    }

    private static void drawCoverCrop(Graphics2D graphics, BufferedImage source) {
        double targetRatio = (double) GENERATED_COVER_WIDTH / GENERATED_COVER_HEIGHT;
        double sourceRatio = (double) source.getWidth() / source.getHeight();
        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        int cropX = 0;
        int cropY = 0;
        if (sourceRatio > targetRatio) {
            cropWidth = (int) Math.round(source.getHeight() * targetRatio);
            cropX = (source.getWidth() - cropWidth) / 2;
        } else {
            cropHeight = (int) Math.round(source.getWidth() / targetRatio);
            cropY = (source.getHeight() - cropHeight) / 2;
        }

        graphics.drawImage(
                source,
                0,
                0,
                GENERATED_COVER_WIDTH,
                GENERATED_COVER_HEIGHT,
                cropX,
                cropY,
                cropX + cropWidth,
                cropY + cropHeight,
                null
        );
    }

    private static void drawVignette(Graphics2D graphics) {
        graphics.setPaint(new GradientPaint(
                0,
                0,
                new Color(0, 0, 0, 90),
                0,
                GENERATED_COVER_HEIGHT,
                new Color(0, 0, 0, 210)
        ));
        graphics.fillRect(0, 0, GENERATED_COVER_WIDTH, GENERATED_COVER_HEIGHT);
    }

    private static void drawLogoOrTitle(Graphics2D graphics, GooglePlayInstallation installation) throws IOException {
        Path logoPath = installation.logoPath();
        if (logoPath != null && Files.isRegularFile(logoPath)) {
            BufferedImage logo = ImageIO.read(logoPath.toFile());
            if (logo != null) {
                int maxWidth = (int) (GENERATED_COVER_WIDTH * 0.78);
                int maxHeight = (int) (GENERATED_COVER_HEIGHT * 0.24);
                double scale = Math.min((double) maxWidth / logo.getWidth(), (double) maxHeight / logo.getHeight());
                int width = Math.max(1, (int) Math.round(logo.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(logo.getHeight() * scale));
                int x = (GENERATED_COVER_WIDTH - width) / 2;
                int y = GENERATED_COVER_HEIGHT - height - 96;
                graphics.drawImage(logo, x, y, width, height, null);
                return;
            }
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
        graphics.setColor(Color.WHITE);
        FontMetrics metrics = graphics.getFontMetrics();
        String title = installation.title();
        int x = Math.max(28, (GENERATED_COVER_WIDTH - metrics.stringWidth(title)) / 2);
        int y = GENERATED_COVER_HEIGHT - 120;
        graphics.drawString(title, x, y);
    }

    private static Path generatedCoverPath(String packageName) {
        return Path.of(
                System.getProperty("user.home"),
                ".cache",
                "game-dashboard",
                "google-play-games",
                packageName + ".cover.png"
        );
    }

    private static LaunchTarget createLaunchTarget(String launchUri) {
        if (OperatingSystem.CURRENT == OperatingSystem.WINDOWS)
            return new ExecutableLaunchTarget("explorer.exe", List.of(launchUri));

        return new UriLaunchTarget(launchUri);
    }

    private static boolean isGameCandidate(GooglePlayInstallation installation) {
        return installation.packageName() != null
                && !installation.packageName().isBlank()
                && !installation.packageName().startsWith("com.google.android.")
                && !installation.packageName().startsWith("com.android.");
    }

    private static @Nullable Path getDefaultDataPath() {
        String localAppData = System.getenv("LOCALAPPDATA");
        return localAppData == null || localAppData.isBlank()
                ? null
                : Path.of(localAppData, "Google", "Play Games");
    }

    private static @Nullable String pathToString(@Nullable Path path) {
        return path == null ? null : path.toString();
    }

    private static String humanizePackageName(String packageName) {
        int separator = packageName.lastIndexOf('.');
        String name = separator < 0 ? packageName : packageName.substring(separator + 1);
        return name.replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record GooglePlayInstallation(
            String packageName,
            String title,
            String launchUri,
            @Nullable String shortcutPath,
            @Nullable Path appIconPath,
            @Nullable Path backgroundPath,
            @Nullable Path logoPath
    ) {
        private static GooglePlayInstallation fromPackageName(String packageName) {
            return new GooglePlayInstallation(
                    packageName,
                    humanizePackageName(packageName),
                    "googleplaygames://launch/?id=" + packageName,
                    null,
                    null,
                    null,
                    null
            );
        }

        private GooglePlayInstallation withAsset(String kind, String extension, Path path) {
            String normalizedKind = kind.toLowerCase(Locale.ROOT);
            String normalizedExtension = extension.toLowerCase(Locale.ROOT);
            Path appIcon = this.appIconPath;
            Path background = this.backgroundPath;
            Path logo = this.logoPath;
            if ("appicon".equals(normalizedKind) && "png".equals(normalizedExtension)) {
                appIcon = path;
            } else if ("appicon".equals(normalizedKind) && appIcon == null) {
                appIcon = path;
            } else if ("background".equals(normalizedKind)) {
                background = path;
            } else if ("logo".equals(normalizedKind)) {
                logo = path;
            }

            return new GooglePlayInstallation(
                    this.packageName,
                    this.title,
                    this.launchUri,
                    this.shortcutPath,
                    appIcon,
                    background,
                    logo
            );
        }
    }
}
