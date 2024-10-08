package dev.turtywurty.gamedashboard.data;

import com.google.gson.*;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Database {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Database INSTANCE = new Database();
    private final SteamHandler steamHandler = new SteamHandler();

    private final Path path = Path.of(System.getProperty("user.home"), "gamedashboard", "games.json");
    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private final ObservableList<String> loadingGames = FXCollections.observableArrayList();
    private final StringProperty steamLocation = new SimpleStringProperty();
    private final ObservableList<String> epicGamesInstallLocations = FXCollections.observableArrayList();

    private Database() {
        if (Files.notExists(path)) {
            writeEmptyJson(path);
        }

        if (readJson()) {
            writeEmptyJson(path);
        }

        this.steamLocation.addListener((observable, oldValue, newValue) ->
                steamHandler.onSteamLocationUpdated(newValue, this.games, this.loadingGames, steamLocation));
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public boolean readJson() {
        String json;
        try {
            json = Files.readString(path);
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
            Files.writeString(path, GSON.toJson(json));
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

    public Game getGame(String title) {
        return this.games.stream()
                .filter(game -> game.getTitle().equals(title))
                .findFirst()
                .orElse(null);
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

    private static void writeEmptyJson(Path path) {
        try {
            Files.deleteIfExists(path);
            Files.createDirectories(path.getParent());
            Files.createFile(path);

            JsonObject json = new JsonObject();
            json.add("games", new JsonArray());
            json.addProperty("steamLocation", "");
            json.add("epicGamesInstallLocations", new JsonArray());
            Files.writeString(path, GSON.toJson(json));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to write games.json", exception);
        }
    }
}
