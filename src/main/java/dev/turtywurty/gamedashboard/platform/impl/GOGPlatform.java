package dev.turtywurty.gamedashboard.platform.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.GOGGame;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class GOGPlatform implements Platform {
    private static final Gson GSON = new Gson();
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";
    private static final String INSTALLED_PRODUCTS_QUERY = """
            SELECT DISTINCT details.*,
                installed.installationPath AS installationPath,
                originalImages.value AS originalImages,
                launchParameters.executablePath AS executablePath,
                launchParameters.commandLineArgs AS commandLineArgs
            FROM "Product Details View" details
            INNER JOIN InstalledBaseProducts installed
                ON CAST(details.productId AS INTEGER) = installed.productId
            LEFT JOIN ProductsToReleaseKeys releases
                ON releases.gogId = installed.productId
            LEFT JOIN GamePieces originalImages
                ON originalImages.releaseKey = releases.releaseKey
                AND originalImages.gamePieceTypeId = (
                    SELECT id
                    FROM GamePieceTypes
                    WHERE type = 'originalImages'
                )
            LEFT JOIN PlayTasks playTasks
                ON playTasks.gameReleaseKey = releases.releaseKey
                AND playTasks.isPrimary = 1
            LEFT JOIN PlayTaskLaunchParameters launchParameters
                ON launchParameters.playTaskId = playTasks.id
            """;

    @Override
    public Image getIcon() {
        return new Image(getClass().getResource("/images/platforms/gog.png").toExternalForm());
    }

    @Override
    public String getName() {
        return "GOG";
    }

    @Override
    public String getWebsite() {
        return "https://www.gog.com/";
    }

    @Override
    public String getDescription() {
        return "GOG.com (formerly Good Old Games) is a digital distribution platform and online store for video games and films, known for offering DRM-free content and a wide selection of classic and indie titles.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding GOG installation", 1);
            Path databasePath = getDefaultDatabasePath();
            if (databasePath == null || !isValidDatabaseFile(databasePath)) {
                progressMonitor.done();
                return;
            }

            addGOGGames(progressMonitor, databasePath);
        };
    }

    private static void addGOGGames(ProgressMonitor progressMonitor, Path databasePath) {
        if (!isValidDatabaseFile(databasePath)) {
            progressMonitor.done();
            return;
        }

        List<GOGProduct> products = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(INSTALLED_PRODUCTS_QUERY)) {
            GameDashboardApp.LOGGER.info("Connected to GOG database at {}", databasePath.toAbsolutePath());

            while (resultSet.next()) {
                GOGProduct product = readProduct(resultSet, databasePath);
                if (product != null) {
                    products.add(product);
                }
            }
        } catch (SQLException exception) {
            progressMonitor.done();
            GameDashboardApp.LOGGER.error("Failed to connect to GOG database", exception);
            return;
        }

        progressMonitor.worked(1);
        if (products.isEmpty()) {
            progressMonitor.done();
            return;
        }

        progressMonitor.start("Loading GOG games", products.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (GOGProduct product : products) {
                executor.submit(() -> addGOGGame(progressMonitor, product));
            }
        } finally {
            progressMonitor.done();
        }
    }

    private static void addGOGGame(ProgressMonitor progressMonitor, GOGProduct product) {
        try {
            String description = product.description();
            String thumbCoverImageURL = product.thumbCoverImageURL();
            String coverImageURL = product.coverImageURL();
            APIConnector.GameResult gameResult = null;

            if (description == null || description.isBlank() || coverImageURL == null || coverImageURL.isBlank()) {
                gameResult = searchGame(product.title());
                if (gameResult != null) {
                    GameDashboardApp.LOGGER.info("Found metadata for GOG title '{}'", product.title());
                }

                if ((description == null || description.isBlank()) && gameResult != null) {
                    description = gameResult.getSummary();
                }

                if ((coverImageURL == null || coverImageURL.isBlank()) && gameResult != null) {
                    coverImageURL = gameResult.getCoverURL();
                }
            }

            var game = GOGGame.builder(
                            product.title(),
                            description == null ? "" : description,
                            product.executionCommand(),
                            product.productId(),
                            product.url(),
                            product.slug()
                    )
                    .images(
                            thumbCoverImageURL == null || thumbCoverImageURL.isBlank()
                                    ? PLACEHOLDER_COVER_URL
                                    : thumbCoverImageURL,
                            coverImageURL == null || coverImageURL.isBlank()
                                    ? PLACEHOLDER_COVER_URL
                                    : coverImageURL
                    )
                    .igdbGameId(gameResult == null ? null : gameResult.getIgdbGameId())
                    .nickname(product.title())
                    .build();
            javafx.application.Platform.runLater(() -> {
                if (!Database.getInstance().addGame(game)) {
                    Database.getInstance().updateGame(game, game);
                }
            });
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static @Nullable GOGProduct readProduct(ResultSet resultSet, Path databasePath) {
        try {
            long productId = Long.parseLong(resultSet.getString("productId"));
            String title = resultSet.getString("title");
            if (title == null || title.isBlank())
                return null;

            String url = getJsonString(resultSet.getString("links"), "product_card");
            String slug = resultSet.getString("slug");
            String description = resultSet.getString("description");
            String executionCommand = getExecutionCommand(
                    resultSet.getString("installationPath"),
                    resultSet.getString("executablePath"),
                    resultSet.getString("commandLineArgs")
            );
            JsonObject originalImages = getJsonObject(resultSet.getString("originalImages"));
            JsonObject images = getJsonObject(resultSet.getString("images"));
            String thumbCoverImageURL = firstNonBlank(
                    getFirstJsonString(originalImages, "squareIcon"),
                    getFirstJsonString(images, "icon", "sidebarIcon2x", "sidebarIcon")
            );
            String coverImageURL = firstNonBlank(
                    getFirstJsonString(originalImages, "verticalCover"),
                    getFirstJsonString(originalImages, "background"),
                    getFirstJsonString(images, "background", "logo2x", "logo")
            );
            thumbCoverImageURL = firstNonBlank(
                    findLocalWebCacheImage(databasePath, productId, thumbCoverImageURL),
                    thumbCoverImageURL
            );
            coverImageURL = firstNonBlank(
                    findLocalWebCacheImage(databasePath, productId, coverImageURL),
                    coverImageURL
            );
            return new GOGProduct(productId, title, description, executionCommand, thumbCoverImageURL, coverImageURL, url, slug);
        } catch (NumberFormatException | SQLException exception) {
            GameDashboardApp.LOGGER.error("Failed to read product details from GOG database", exception);
            return null;
        }
    }

    private static @Nullable String getExecutionCommand(
            @Nullable String installationPath,
            @Nullable String executablePath,
            @Nullable String commandLineArgs
    ) {
        if (executablePath == null || executablePath.isBlank())
            return null;

        Path executable = Path.of(executablePath);
        if (!executable.isAbsolute() && installationPath != null && !installationPath.isBlank()) {
            executable = Path.of(installationPath, executablePath);
        }

        String command = executable.toString();
        if (commandLineArgs != null && !commandLineArgs.isBlank()) {
            command += " " + commandLineArgs.trim();
        }

        return command;
    }

    private static @Nullable JsonObject getJsonObject(String json) {
        if (json == null || json.isBlank())
            return null;

        try {
            return GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable String getJsonString(String json, String memberName) {
        return getFirstJsonString(getJsonObject(json), memberName);
    }

    private static @Nullable String getFirstJsonString(@Nullable JsonObject object, String... memberNames) {
        if (object == null)
            return null;

        for (String memberName : memberNames) {
            if (object.has(memberName) && !object.get(memberName).isJsonNull()) {
                String value = object.get(memberName).getAsString();
                if (value != null && !value.isBlank())
                    return value;
            }
        }

        return null;
    }

    private static @Nullable String findLocalWebCacheImage(Path databasePath, long productId, @Nullable String imageURL) {
        if (imageURL == null || imageURL.isBlank())
            return null;

        String filename = getFilenameFromURL(imageURL);
        if (filename == null || filename.isBlank())
            return null;

        Path storagePath = databasePath.getParent();
        if (storagePath == null)
            return null;

        Path galaxyPath = storagePath.getParent();
        if (galaxyPath == null)
            return null;

        Path webCachePath = galaxyPath.resolve("webcache");
        if (!Files.isDirectory(webCachePath))
            return null;

        try (var webCacheDirectories = Files.list(webCachePath)) {
            return webCacheDirectories
                    .map(directory -> directory.resolve("gog").resolve(Long.toString(productId)).resolve(filename))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .map(path -> path.toUri().toString())
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private static @NotNull String getFilenameFromURL(String imageURL) {
        int queryStart = imageURL.indexOf('?');
        String path = queryStart >= 0 ? imageURL.substring(0, queryStart) : imageURL;
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex >= 0 ? path.substring(separatorIndex + 1) : path;
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value;
        }

        return null;
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var description = new Label(
                "Choose the GOG Galaxy database used to find your GOG library.");
        description.getStyleClass().add("dialog-description");
        description.setWrapText(true);

        var header = new VBox(description);
        header.getStyleClass().add("dialog-header");

        var errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);

        Path defaultDatabasePath = getDefaultDatabasePath();
        var databaseField = new TextField(
                defaultDatabasePath != null && isValidDatabaseFile(defaultDatabasePath)
                        ? defaultDatabasePath.toString()
                        : ""
        );
        databaseField.setPromptText(defaultDatabasePath == null ? "" : defaultDatabasePath.toString());
        HBox.setHgrow(databaseField, Priority.ALWAYS);
        var databaseCard = createPathCard(
                "GOG Galaxy database",
                databaseField,
                "Select GOG Galaxy Database",
                () -> chooseDatabase(databaseField, errorLabel)
        );

        hideError(errorLabel);

        var content = new VBox(header, databaseCard, errorLabel);
        content.getStyleClass().add("gog-configuration-content");
        VBox.setMargin(databaseCard, new Insets(8, 0, 0, 0));

        return new ManualEntryView(content, progressMonitor -> {
            String databasePath = databaseField.getText();
            if (!isValidDatabaseFile(databasePath)) {
                showError(errorLabel, "Select a valid GOG Galaxy database.");
                return;
            }

            hideError(errorLabel);
            CompletableFuture.runAsync(() -> addGOGGames(progressMonitor, Path.of(databasePath)))
                    .exceptionally(throwable -> {
                        progressMonitor.done();
                        return null;
                    });
        });
    }

    private VBox createPathCard(String labelText, TextField field, String chooserTitle, Runnable browseAction) {
        var label = new Label(labelText);
        label.getStyleClass().add("field-label");

        var browseButton = new Button("Browse");
        browseButton.getStyleClass().add("browse-button");
        browseButton.setAccessibleText(chooserTitle);
        browseButton.setOnAction(event -> browseAction.run());

        var row = new HBox(field, browseButton);
        row.getStyleClass().add("gog-location-row");
        row.setAlignment(Pos.CENTER_LEFT);

        var card = new VBox(label, row);
        card.getStyleClass().add("gog-location-card");
        return card;
    }

    private void chooseDatabase(TextField databaseField, Label errorLabel) {
        var chooser = new FileChooser();
        chooser.setTitle("Select GOG Galaxy Database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database", "galaxy-2.0.db", "*.db"));

        String currentPath = databaseField.getText();
        File currentFile = currentPath == null || currentPath.isBlank() ? null : new File(currentPath);
        File initialDirectory = currentFile != null && currentFile.getParentFile() != null
                ? currentFile.getParentFile()
                : new File(System.getProperty("user.home"));
        if (initialDirectory.isDirectory()) {
            chooser.setInitialDirectory(initialDirectory);
        }

        File selectedFile = chooser.showOpenDialog(databaseField.getScene().getWindow());
        if (selectedFile != null) {
            databaseField.setText(selectedFile.getAbsolutePath());
            hideError(errorLabel);
        }
    }

    private static @Nullable Path getDefaultDatabasePath() {
        return switch (OperatingSystem.getCurrent()) {
            case WINDOWS -> Path.of(getProgramDataPath(), "GOG.com", "Galaxy", "storage", "galaxy-2.0.db");
            case MACOS ->
                    Path.of(System.getProperty("user.home"), "Library", "Application Support", "GOG.com", "Galaxy", "storage", "galaxy-2.0.db");
            case LINUX ->
                    Path.of(System.getProperty("user.home"), ".config", "GOG.com", "Galaxy", "storage", "galaxy-2.0.db");
            default -> null;
        };
    }

    private static String getProgramDataPath() {
        String programData = System.getenv("PROGRAMDATA");
        return programData == null || programData.isBlank() ? "C:\\ProgramData" : programData;
    }

    private static boolean isValidDatabaseFile(String databasePath) {
        if (databasePath == null || databasePath.isBlank())
            return false;

        try {
            return isValidDatabaseFile(Path.of(databasePath));
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private static boolean isValidDatabaseFile(Path databasePath) {
        return Files.isRegularFile(databasePath);
    }

    private void hideError(Label errorLabel) {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private record GOGProduct(
            long productId,
            String title,
            @Nullable String description,
            @Nullable String executionCommand,
            @Nullable String thumbCoverImageURL,
            @Nullable String coverImageURL,
            @Nullable String url,
            @Nullable String slug
    ) {
    }

    private static APIConnector.GameResult searchGame(String title) {
        APIConnector.GameResult result = APIConnector.findBestFuzzyGameMatch(title, true, true)
                .join()
                .gameResult();
        if (result == null) {
            GameDashboardApp.LOGGER.warn(
                    "No confident metadata match found for GOG title '{}'; using placeholder metadata",
                    title
            );
        }

        return result;
    }
}
