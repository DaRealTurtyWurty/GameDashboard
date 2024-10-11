package dev.turtywurty.gamedashboard.data;

import com.google.gson.*;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Pair;
import lombok.Getter;
import net.harawata.appdirs.AppDirsFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public class Database {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Database INSTANCE = new Database();
    private static final Path APP_DATA_PATH = Path.of(AppDirsFactory.getInstance().getUserDataDir("GameDashboard", GameDashboardApp.VERSION, "turtywurty.dev"));

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Pair<Path, List<SteamGameCacheData>> EMPTY_CACHE = new Pair<>(Path.of(""), Collections.EMPTY_LIST);

    private final SteamHandler steamHandler = new SteamHandler();
    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private final ObservableList<String> loadingGames = FXCollections.observableArrayList();
    private final StringProperty steamLocation = new SimpleStringProperty();
    private final ObservableList<String> epicGamesInstallLocations = FXCollections.observableArrayList();

    private Database() {
        if (Files.notExists(APP_DATA_PATH) || !Files.isRegularFile(APP_DATA_PATH) || !Files.isReadable(APP_DATA_PATH) || !Files.isWritable(APP_DATA_PATH)) {
            writeEmptyJson();
        }

        if (!readJson()) {
            writeEmptyJson();
        }

        this.steamLocation.addListener((observable, oldValue, newValue) -> {
            steamHandler.onSteamLocationUpdated(newValue, this.games, this.loadingGames, steamLocation);
            writeJson();
        });
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public static Path getAppDataPath() {
        return APP_DATA_PATH;
    }

    public boolean readJson() {
        String json;
        try {
            json = Files.readString(APP_DATA_PATH);
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read games.json", exception);
            return false;
        }

        JsonObject object = GSON.fromJson(json, JsonObject.class);
        JsonArray array = object.getAsJsonArray("games");
        if (array == null)
            array = new JsonArray();

        List<Game> games = new ArrayList<>();
        for (JsonElement element : array) {
            Game game = GSON.fromJson(element, Game.class);
            games.add(game);
        }

        this.games.clear();
        this.games.addAll(games);
        this.steamLocation.set(object.has("steamLocation") ? object.get("steamLocation").getAsString() : "");
        if (this.steamLocation.isBound() && !this.steamLocation.get().isEmpty()) {
            Pair<Path, List<SteamGameCacheData>> cache = readSteamCache();
            // check whether the steam location has changed
            if (!cache.getKey().toString().equals(this.steamLocation.get())) {
                writeSteamCache(Path.of(this.steamLocation.get()), constructSteamCache().getValue());
            } else {
                for (SteamGameCacheData data : cache.getValue()) {
                    if (this.games.stream().noneMatch(game -> game.getTitle().equals(data.name()))) {
                        this.games.add(new Game(data.name(), "", data.executionPath(), data.thumbCoverImageURL(), data.coverImageURL(), data.name(), data.appId()));
                    }
                }
            }
        }

        if (object.has("epicGamesInstallLocations")) {
            JsonArray epicGamesInstallLocations = object.getAsJsonArray("epicGamesInstallLocations");
            for (JsonElement element : epicGamesInstallLocations) {
                this.epicGamesInstallLocations.add(element.getAsString());
            }
        }

        return true;
    }

    public void writeJson() {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        for (Game game : games) {
            array.add(GSON.toJsonTree(game));
        }

        json.add("games", array);
        json.addProperty("steamLocation", steamLocation.get());

        JsonArray epicGamesInstallLocations = new JsonArray();
        for (String location : this.epicGamesInstallLocations) {
            epicGamesInstallLocations.add(location);
        }

        json.add("epicGamesInstallLocations", epicGamesInstallLocations);

        try {
            Files.writeString(APP_DATA_PATH, GSON.toJson(json));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to write games.json", exception);
        }
    }

    public void addGame(Game game) {
        games.add(game);
        writeJson();
    }

    public void removeGame(Game game) {
        games.remove(game);
        writeJson();
    }

    public void updateGame(Game game) {
        Game matching = this.games.stream()
                .filter(g -> g.getTitle().equals(game.getTitle()))
                .findFirst()
                .orElse(null);

        if (matching == null)
            return;

        int index = this.games.indexOf(matching);
        this.games.set(index, game);

        writeJson();
    }

    public Optional<Game> getGame(String title) {
        return this.games.stream()
                .filter(game -> game.getTitle().equals(title))
                .findFirst();
    }

    public boolean containsGame(String title) {
        return this.games.stream().anyMatch(game -> game.getTitle().equals(title));
    }

    public void setSteamLocation(String steamLocation) {
        this.steamLocation.set(steamLocation);
        writeJson();
    }

    public StringProperty steamLocationProperty() {
        return this.steamLocation;
    }

    public @NotNull String getSteamLocation() {
        return this.steamLocation.isBound() ? this.steamLocation.get() : "";
    }

    private static void writeEmptyJson() {
        try {
            Files.deleteIfExists(APP_DATA_PATH);
            Files.createDirectories(APP_DATA_PATH.getParent());
            Files.createFile(APP_DATA_PATH);

            JsonObject json = new JsonObject();
            json.add("games", new JsonArray());
            json.addProperty("steamLocation", "");
            json.add("epicGamesInstallLocations", new JsonArray());
            Files.writeString(APP_DATA_PATH, GSON.toJson(json));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to write games.json", exception);
        }
    }

    private Pair<Path, List<SteamGameCacheData>> constructSteamCache() {
        if (!steamLocation.isBound() || steamLocation.get().isEmpty())
            return EMPTY_CACHE;

        List<SteamGameCacheData> cache = new ArrayList<>();
        for (Game game : this.games) {
            if (!game.isSteam())
                continue;

            cache.add(new SteamGameCacheData(game.getSteamAppId(), game.getTitle(), game.getExecutionCommand(), game.getThumbCoverImageURL(), game.getCoverImageURL()));
        }

        return new Pair<>(Path.of(steamLocation.get()), cache);
    }

    private static void writeSteamCache(Path steamLocation, List<SteamGameCacheData> cache) {
        Path cachePath = APP_DATA_PATH.resolve("steam_cache.json");
        try {
            Files.deleteIfExists(cachePath);
            Files.createDirectories(cachePath.getParent());
            Files.createFile(cachePath);

            var object = new JsonObject();
            object.addProperty("steamLocation", steamLocation.toString());

            var array = new JsonArray();
            for (SteamGameCacheData data : cache) {
                var cacheObject = new JsonObject();
                cacheObject.addProperty("appId", data.appId());
                cacheObject.addProperty("name", data.name());
                cacheObject.addProperty("executionPath", data.executionPath());
                cacheObject.addProperty("thumbCoverImageURL", data.thumbCoverImageURL());
                cacheObject.addProperty("coverImageURL", data.coverImageURL());
                array.add(cacheObject);
            }

            object.add("cache", array);

            Files.writeString(cachePath, GSON.toJson(object));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to write steam_cache.json", exception);
        }
    }

    private static Pair<Path, List<SteamGameCacheData>> readSteamCache() {
        final Path cachePath = APP_DATA_PATH.resolve("steam_cache.json");
        if (Files.notExists(cachePath) || !Files.isRegularFile(cachePath) || !Files.isReadable(cachePath))
            return EMPTY_CACHE;

        try {
            String json = Files.readString(cachePath);
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null)
                return EMPTY_CACHE;

            String steamLocation = object.has("steamLocation") ? object.get("steamLocation").getAsString() : "";

            List<SteamGameCacheData> cache = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray("cache")) {
                if (!element.isJsonObject())
                    continue;

                JsonObject cacheObject = element.getAsJsonObject();
                int appId = cacheObject.has("appId") ? cacheObject.get("appId").getAsInt() : -1;
                String name = cacheObject.has("name") ? cacheObject.get("name").getAsString() : "";
                String executionPath = cacheObject.has("executionPath") ? cacheObject.get("executionPath").getAsString() : "";
                String thumbCoverImageURL = cacheObject.has("thumbCoverImageURL") ? cacheObject.get("thumbCoverImageURL").getAsString() : "";
                String coverImageURL = cacheObject.has("coverImageURL") ? cacheObject.get("coverImageURL").getAsString() : "";
                if (appId == -1 || name.isEmpty() || executionPath.isEmpty() || thumbCoverImageURL.isEmpty() || coverImageURL.isEmpty())
                    continue;

                cache.add(new SteamGameCacheData(appId, name, executionPath, thumbCoverImageURL, coverImageURL));
            }

            return new Pair<>(Path.of(steamLocation), cache);
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read steam_cache.json", exception);
            return EMPTY_CACHE;
        }
    }

    private record SteamGameCacheData(int appId, String name, String executionPath, String thumbCoverImageURL,
                                      String coverImageURL) {
    }
}
