package dev.turtywurty.gamedashboard.platform.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.ExecutableLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.LaunchTarget;
import dev.turtywurty.gamedashboard.data.game.UriLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.impl.ItchGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public final class ItchPlatform implements Platform {
    private static final Gson GSON = new Gson();
    private static final String INSTALLED_GAMES_QUERY = """
            SELECT
                caves.id AS cave_id,
                caves.game_id AS game_id,
                caves.install_folder_name AS install_folder_name,
                caves.verdict AS verdict,
                install_locations.path AS install_location_path,
                games.title AS title,
                games.url AS url
            FROM caves
            INNER JOIN games
                ON games.id = caves.game_id
            LEFT JOIN install_locations
                ON install_locations.id = caves.install_location_id
            """;

    private static void addItchGames(ProgressMonitor progressMonitor, Path databasePath) {
        if (!isValidDatabaseFile(databasePath)) {
            progressMonitor.done();
            return;
        }

        List<ItchProduct> products = new ArrayList<>();
        List<Path> installLocations = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(INSTALLED_GAMES_QUERY)) {
            GameDashboardApp.LOGGER.info("Connected to itch database at {}", databasePath.toAbsolutePath());

            while (resultSet.next()) {
                ItchProduct product = readProduct(resultSet);
                if (product != null) {
                    products.add(product);
                }
            }

            installLocations.addAll(readInstallLocations(connection));
        } catch (SQLException exception) {
            progressMonitor.done();
            GameDashboardApp.LOGGER.error("Failed to connect to itch database", exception);
            return;
        }

        addReceiptProducts(products, installLocations);

        progressMonitor.worked(1);
        if (products.isEmpty()) {
            progressMonitor.done();
            return;
        }

        progressMonitor.start("Loading itch.io games", products.size());
        try {
            for (ItchProduct product : products) {
                addItchGame(progressMonitor, product);
            }
        } finally {
            progressMonitor.done();
        }
    }

    private static List<Path> readInstallLocations(Connection connection) {
        List<Path> installLocations = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT path FROM install_locations")) {
            while (resultSet.next()) {
                String path = resultSet.getString("path");
                if (path == null || path.isBlank())
                    continue;

                Path installLocation = Utils.toPathOrNull(path);
                if (installLocation != null && Files.isDirectory(installLocation)) {
                    installLocations.add(installLocation);
                }
            }
        } catch (SQLException exception) {
            GameDashboardApp.LOGGER.warn("Failed to read itch install locations", exception);
        }

        return installLocations;
    }

    private static void addReceiptProducts(List<ItchProduct> products, List<Path> installLocations) {
        Set<Long> existingGameIds = new HashSet<>();
        for (ItchProduct product : products) {
            existingGameIds.add(product.gameId());
        }

        for (Path installLocation : installLocations) {
            try (Stream<Path> paths = Files.walk(installLocation)) {
                paths.filter(path -> path.getFileName() != null && "receipt.json.gz".equals(path.getFileName().toString()))
                        .filter(path -> path.getParent() != null && ".itch".equals(path.getParent().getFileName().toString()))
                        .map(ItchPlatform::readReceiptProduct)
                        .filter(product -> product != null && existingGameIds.add(product.gameId()))
                        .forEach(products::add);
            } catch (Exception exception) {
                GameDashboardApp.LOGGER.warn("Failed to scan itch receipts under {}", installLocation, exception);
            }
        }
    }

    private static @Nullable ItchProduct readReceiptProduct(Path receiptPath) {
        Path itchDirectory = receiptPath.getParent();
        if (itchDirectory == null)
            return null;

        Path installPath = itchDirectory.getParent();
        if (installPath == null)
            return null;

        try (var inputStream = new GZIPInputStream(Files.newInputStream(receiptPath));
             var reader = new InputStreamReader(inputStream)) {
            JsonObject receipt = GSON.fromJson(reader, JsonObject.class);
            if (receipt == null || !receipt.has("game") || !receipt.get("game").isJsonObject())
                return null;

            JsonObject game = receipt.getAsJsonObject("game");
            if (!game.has("id") || game.get("id").isJsonNull())
                return null;

            long gameId = game.get("id").getAsLong();
            String title = getJsonString(game, "title");
            if (title == null || title.isBlank())
                return null;

            String url = getJsonString(game, "url");
            LaunchTarget launchTarget = getReceiptLaunchTarget(installPath, receipt, url);
            return new ItchProduct(
                    gameId,
                    "receipt:" + gameId + ":" + installPath,
                    title,
                    url,
                    installPath.toString(),
                    launchTarget
            );
        } catch (Exception exception) {
            GameDashboardApp.LOGGER.warn("Failed to read itch receipt {}", receiptPath, exception);
            return null;
        }
    }

    private static LaunchTarget getReceiptLaunchTarget(Path installPath, JsonObject receipt, @Nullable String url) {
        Path executable = findFirstReceiptExecutable(installPath, receipt);
        if (executable != null)
            return new ExecutableLaunchTarget(executable, List.of(), executable.getParent());

        if (url != null && !url.isBlank())
            return new UriLaunchTarget(url);

        return new UriLaunchTarget(installPath.toUri().toString());
    }

    private static @Nullable Path findFirstReceiptExecutable(Path installPath, JsonObject receipt) {
        if (!receipt.has("files") || !receipt.get("files").isJsonArray())
            return null;

        JsonArray files = receipt.getAsJsonArray("files");
        for (int index = 0; index < files.size(); index++) {
            if (!files.get(index).isJsonPrimitive())
                continue;

            String file = files.get(index).getAsString();
            if (file == null || file.isBlank() || !file.toLowerCase().endsWith(".exe"))
                continue;

            Path executable = installPath.resolve(file).normalize();
            if (Files.isRegularFile(executable))
                return executable;
        }

        return null;
    }

    private static void addItchGame(ProgressMonitor progressMonitor, ItchProduct product) {
        try {
            APIConnector.GameResult gameResult = findIGDBGame(product.gameId(), product.title());
            String description = gameResult == null || gameResult.getSummary() == null ? "" : gameResult.getSummary();
            String thumbCoverImageURL = gameResult == null || gameResult.getThumbCoverURL() == null
                    ? Utils.PLACEHOLDER_COVER_URL
                    : gameResult.getThumbCoverURL();
            String coverImageURL = gameResult == null || gameResult.getCoverURL() == null
                    ? Utils.PLACEHOLDER_COVER_URL
                    : gameResult.getCoverURL();

            var game = ItchGame.builder(
                            product.title(),
                            description,
                            product.launchTarget(),
                            product.gameId(),
                            product.caveId(),
                            product.url(),
                            product.installPath()
                    )
                    .images(thumbCoverImageURL, coverImageURL)
                    .igdbGameId(gameResult == null ? null : gameResult.getIgdbGameId())
                    .nickname(product.title())
                    .build();

            Utils.runOnFxThread(() -> {
                if (!Database.getInstance().addGame(game)) {
                    Database.getInstance().updateGame(game, game);
                }
            });
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static @Nullable ItchProduct readProduct(ResultSet resultSet) {
        try {
            String caveId = resultSet.getString("cave_id");
            long gameId = resultSet.getLong("game_id");
            String title = resultSet.getString("title");
            if (caveId == null || caveId.isBlank() || title == null || title.isBlank())
                return null;

            String url = resultSet.getString("url");
            Path installPath = getInstallPath(
                    resultSet.getString("install_location_path"),
                    resultSet.getString("install_folder_name"),
                    resultSet.getString("verdict")
            );
            LaunchTarget launchTarget = getLaunchTarget(installPath, resultSet.getString("verdict"), url);
            return new ItchProduct(gameId, caveId, title, url, installPath.toString(), launchTarget);
        } catch (SQLException | RuntimeException exception) {
            GameDashboardApp.LOGGER.error("Failed to read itch game from database", exception);
            return null;
        }
    }

    private static Path getInstallPath(
            @Nullable String installLocationPath,
            @Nullable String installFolderName,
            @Nullable String verdictJson
    ) {
        String basePath = getVerdictBasePath(verdictJson);
        if (basePath != null && !basePath.isBlank())
            return Path.of(basePath);

        if (installLocationPath != null && !installLocationPath.isBlank()
                && installFolderName != null && !installFolderName.isBlank())
            return Path.of(installLocationPath, installFolderName);

        throw new IllegalArgumentException("itch game is missing an install path");
    }

    private static @Nullable String getVerdictBasePath(@Nullable String verdictJson) {
        JsonObject verdict = getJsonObject(verdictJson);
        return getJsonString(verdict, "basePath");
    }

    private static LaunchTarget getLaunchTarget(Path installPath, @Nullable String verdictJson, @Nullable String url) {
        Path executable = findExecutableCandidate(installPath, verdictJson);
        if (executable != null)
            return new ExecutableLaunchTarget(executable, List.of(), executable.getParent());

        if (url != null && !url.isBlank())
            return new UriLaunchTarget(url);

        return new UriLaunchTarget(installPath.toUri().toString());
    }

    private static @Nullable Path findExecutableCandidate(Path installPath, @Nullable String verdictJson) {
        JsonObject verdict = getJsonObject(verdictJson);
        if (verdict == null || !verdict.has("candidates") || !verdict.get("candidates").isJsonArray())
            return null;

        JsonArray candidates = verdict.getAsJsonArray("candidates");
        for (int index = 0; index < candidates.size(); index++) {
            if (!candidates.get(index).isJsonObject())
                continue;

            JsonObject candidate = candidates.get(index).getAsJsonObject();
            if (!"windows".equalsIgnoreCase(getJsonString(candidate, "flavor")))
                continue;

            String executablePath = getJsonString(candidate, "path");
            if (executablePath == null || executablePath.isBlank())
                continue;

            Path executable = installPath.resolve(executablePath).normalize();
            if (Files.isRegularFile(executable))
                return executable;
        }

        return null;
    }

    private static @Nullable JsonObject getJsonObject(@Nullable String json) {
        if (json == null || json.isBlank())
            return null;

        try {
            return GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable String getJsonString(@Nullable JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || object.get(memberName).isJsonNull())
            return null;

        String value = object.get(memberName).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static @Nullable Path getDefaultDatabasePath() {
        return switch (OperatingSystem.getCurrent()) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield appData == null || appData.isBlank()
                        ? null
                        : Path.of(appData, "itch", "db", "butler.db");
            }
            case MACOS -> Path.of(System.getProperty("user.home"), "Library", "Application Support", "itch", "db", "butler.db");
            case LINUX -> Path.of(System.getProperty("user.home"), ".config", "itch", "db", "butler.db");
            default -> null;
        };
    }

    private static boolean isValidDatabaseFile(String databasePath) {
        return isValidDatabaseFile(Utils.toPathOrNull(databasePath));
    }

    private static boolean isValidDatabaseFile(Path databasePath) {
        return databasePath != null && Files.isRegularFile(databasePath);
    }

    private static @Nullable APIConnector.GameResult findIGDBGame(long itchGameId, String title) {
        Integer igdbGameId = APIConnector.getGameIdFromExternalId(
                        APIConnector.ExternalPlatform.ITCH_IO,
                        Long.toString(itchGameId)
                )
                .join();
        if (igdbGameId == null) {
            GameDashboardApp.LOGGER.warn(
                    "No IGDB external ID match found for itch.io title '{}' with itch game id {}; falling back to fuzzy matching",
                    title,
                    itchGameId
            );
            return findFuzzyIGDBGame(title);
        }

        APIConnector.GameResult result = APIConnector.getGameByID(igdbGameId, true, true).join();
        if (result == null) {
            GameDashboardApp.LOGGER.warn(
                    "IGDB game {} resolved from itch.io game id {} but could not be loaded for '{}'; falling back to fuzzy matching",
                    igdbGameId,
                    itchGameId,
                    title
            );
            return findFuzzyIGDBGame(title);
        }

        return result;
    }

    private static @Nullable APIConnector.GameResult findFuzzyIGDBGame(String title) {
        APIConnector.GameResult result = APIConnector.findBestFuzzyGameMatch(title, true, true)
                .join()
                .gameResult();
        if (result == null) {
            GameDashboardApp.LOGGER.warn(
                    "No confident metadata match found for itch.io title '{}'; using placeholder metadata",
                    title
            );
        }

        return result;
    }

    @Override
    public Image getIcon() {
        return new Image(getClass().getResource("/images/platforms/itch.io.png").toExternalForm());
    }

    @Override
    public String getName() {
        return "itch.io";
    }

    @Override
    public String getWebsite() {
        return "https://itch.io/";
    }

    @Override
    public String getDescription() {
        return "itch.io is an open marketplace for independent digital creators, including indie games, tabletop games, tools, comics, and game assets.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding itch.io installation", 1);
            Path databasePath = getDefaultDatabasePath();
            if (databasePath == null || !isValidDatabaseFile(databasePath)) {
                progressMonitor.done();
                return;
            }

            addItchGames(progressMonitor, databasePath);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the itch.io butler database used to find your installed itch.io library.",
                "itch",
                true,
                0
        );

        Path defaultDatabasePath = getDefaultDatabasePath();
        var databaseField = form.addFileField(
                "itch.io butler database",
                defaultDatabasePath != null && isValidDatabaseFile(defaultDatabasePath)
                        ? defaultDatabasePath.toString() : "",
                defaultDatabasePath == null ? "" : defaultDatabasePath.toString(),
                "Select itch.io Butler Database",
                new FileChooser.ExtensionFilter("SQLite Database", "butler.db", "*.db")
        );

        return form.build(progressMonitor -> {
            String databasePath = databaseField.getText();
            if (!form.validate(databaseField, ItchPlatform::isValidDatabaseFile, "Select a valid itch.io butler database."))
                return;

            form.hideError();
            ManualEntryForm.runAsync(progressMonitor, () -> addItchGames(progressMonitor, Path.of(databasePath)));
        });
    }

    private record ItchProduct(
            long gameId,
            String caveId,
            String title,
            @Nullable String url,
            String installPath,
            LaunchTarget launchTarget
    ) {
    }
}
