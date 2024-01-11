package dev.turtywurty.gamedashboard.data;

import com.google.gson.*;
import dev.turtywurty.gamedashboard.util.Utils;
import dev.turtywurty.gamedashboard.util.VDFtoJson;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.util.Pair;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Getter
public class Database {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Database INSTANCE = new Database();
    private final Path path = Path.of(System.getProperty("user.home"), "gamedashboard", "games.json");
    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private final StringProperty steamLocation = new SimpleStringProperty();

    private Database() {
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path.getParent());
                Files.createFile(path);

                JsonObject json = new JsonObject();
                json.add("games", new JsonArray());
                json.addProperty("steamLocation", "");
                Files.writeString(path, GSON.toJson(json));
            } catch (IOException exception) {
                exception.printStackTrace(); // TODO: Create a logger
            }
        }

        try {
            readFromJson();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        } catch (JsonSyntaxException exception) {
            try {
                Files.deleteIfExists(path);
                Files.createDirectories(path.getParent());
                Files.createFile(path);

                JsonObject json = new JsonObject();
                json.add("games", new JsonArray());
                json.addProperty("steamLocation", "");
                Files.writeString(path, GSON.toJson(json));
            } catch (IOException ioException) {
                ioException.printStackTrace(); // TODO: Create a logger
            }
        }

        this.steamLocation.addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty())
                return;

            Path steamPath = Path.of(newValue);
            if (Files.notExists(steamPath)) {
                this.steamLocation.setValue("");
                return;
            }

            if (Files.notExists(steamPath.resolve("steam.exe"))) {
                this.steamLocation.setValue("");
                return;
            }

            Path steamAppsPath = steamPath.resolve("steamapps");
            if (Files.notExists(steamAppsPath)) {
                this.steamLocation.setValue("");
                return;
            }

            Path libraryFoldersPath = steamAppsPath.resolve("libraryfolders.vdf");
            if (Files.notExists(libraryFoldersPath)) {
                this.steamLocation.setValue("");
                return;
            }

            this.games.removeIf(Game::isSteam);
            Pair<ObservableBooleanValue, ObservableList<Game>> steamGames = locateSteamGames(steamPath);
            // listen for changes to the steam games list whilst the boolean value is true
            ListChangeListener<Game> listener = change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        List<? extends Game> added = change.getAddedSubList();
                        for (Game game : added) {
                            if (game.isSteam())
                                this.games.add(game);
                        }
                    } else if (change.wasRemoved()) {
                        List<? extends Game> removed = change.getRemoved();
                        for (Game game : removed) {
                            if (game.isSteam())
                                this.games.remove(game);
                        }
                    }
                }
            };

            ObservableList<Game> steamGamesList = steamGames.getValue();
            steamGamesList.addListener(listener);

            ObservableBooleanValue steamGamesLoaded = steamGames.getKey();
            ChangeListener<Boolean> changeListener = (observable1, oldValue1, newValue1) -> {
                if (newValue1) {
                    steamGamesList.removeListener(listener);
                    steamGamesList.clear();

                    try {
                        writeToJson();
                    } catch (IOException exception) {
                        exception.printStackTrace(); // TODO: Create a logger
                    }
                }
            };

            steamGamesLoaded.addListener(changeListener);

            // remove boolean listener after 5 minutes
            new Thread(() -> {
                try {
                    Thread.sleep(5 * 60 * 1000);
                } catch (InterruptedException exception) {
                    exception.printStackTrace(); // TODO: Create a logger
                }

                steamGamesList.removeListener(listener);
                steamGamesLoaded.removeListener(changeListener);
            }).start();
        });
    }

    private static Pair<ObservableBooleanValue, ObservableList<Game>> locateSteamGames(Path steamPath) {
        try {
            String content = Files.readString(steamPath.resolve("steamapps").resolve("libraryfolders.vdf"));
            JSONObject vdfAsJson = VDFtoJson.toJSONObject(content, true);
            JSONArray libraryFolders = vdfAsJson.getJSONArray("libraryfolders");

            Map<Path, List<Integer>> libraryFolderGameMap = new HashMap<>();
            for (Object libraryFolder : libraryFolders) {
                if (!(libraryFolder instanceof JSONObject obj))
                    continue;

                String path = obj.getString("path");
                JSONObject apps = obj.getJSONObject("apps");
                for (String appId : apps.keySet()) {
                    int id;
                    try {
                        id = Integer.parseInt(appId);
                    } catch (NumberFormatException exception) {
                        continue;
                    }

                    if (id < 0)
                        continue;

                    libraryFolderGameMap.computeIfAbsent(Path.of(path), k -> new ArrayList<>()).add(id);
                }
            }

            List<Path> appManifests = new ArrayList<>();
            for (Path libraryFolder : libraryFolderGameMap.keySet()) {
                for (int id : libraryFolderGameMap.get(libraryFolder)) {
                    Path appManifest = libraryFolder.resolve("steamapps").resolve("appmanifest_" + id + ".acf");
                    if (Files.exists(appManifest))
                        appManifests.add(appManifest);
                }
            }

            ObservableList<Game> games = FXCollections.observableArrayList();
            SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
            new Thread(() -> {
                for (int index = 0; index < appManifests.size(); index++) {
                    Path appManifestPath = appManifests.get(index);
                    String appManifestContent;
                    try {
                        appManifestContent = Files.readString(appManifestPath);
                    } catch (IOException exception) {
                        exception.printStackTrace(); // TODO: Create a logger
                        continue;
                    }
                    JSONObject appManifestAsJson = VDFtoJson.toJSONObject(appManifestContent, true);
                    JSONObject appState = appManifestAsJson.getJSONObject("AppState");
                    String name = appState.getString("name").trim();
                    String executionCommand = appState.getString("LauncherPath") + " -applaunch " + appState.getString("appid");

                    String normalizedName = name.replaceAll("[^a-zA-Z0-9 \\-_]", "")
                            .toLowerCase(Locale.ROOT);
                    if (normalizedName.endsWith("demo"))
                        normalizedName = normalizedName.substring(0, normalizedName.length() - 4);

                    if (normalizedName.endsWith("game"))
                        normalizedName = normalizedName.substring(0, normalizedName.length() - 4);

                    if (normalizedName.isBlank())
                        continue;

                    System.out.println("Searching for " + normalizedName + "...");
                    List<APIConnector.GameResult> results = APIConnector.search(normalizedName, true).join();
                    if (results.isEmpty())
                        continue;

                    // calculate levenshtein distance
                    APIConnector.GameResult gameResult = results.stream()
                            .min((o1, o2) -> {
                                // calculate levenshtein distance
                                int o1Distance = Utils.levenshteinDistance(o1.getName(), name);
                                int o2Distance = Utils.levenshteinDistance(o2.getName(), name);

                                return Integer.compare(o1Distance, o2Distance);
                            })
                            .orElse(null);
                    if (gameResult == null)
                        continue;

                    var game = new Game(
                            name,
                            gameResult.getSummary(),
                            executionCommand,
                            gameResult.getThumbCoverURL(),
                            gameResult.getCoverURL(),
                            name,
                            true
                    );

                    int finalIndex = index;
                    Platform.runLater(() -> {
                        games.add(game);

                        if (finalIndex == appManifests.size() - 1)
                            loaded.set(true);
                    });
                }
            }).start();

            return new Pair<>(loaded, games);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read libraryfolders.vdf", exception);
        }
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public static Gson getGson() {
        return GSON;
    }

    public void readFromJson() throws IOException, JsonSyntaxException {
        String json = Files.readString(path);
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
    }

    public void writeToJson() throws IOException {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        for (Game game : games) {
            array.add(GSON.toJsonTree(game));
        }

        json.add("games", array);
        json.addProperty("steamLocation", steamLocation.get());

        Files.writeString(path, GSON.toJson(json));
    }

    public void addGame(Game game) {
        games.add(game);

        try {
            writeToJson();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        }
    }

    public void removeGame(Game game) {
        games.remove(game);

        try {
            writeToJson();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        }
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

        try {
            writeToJson();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        }
    }

    public Game getGame(String title) {
        return this.games.stream()
                .filter(game -> game.getTitle().equals(title))
                .findFirst()
                .orElse(null);
    }

    public boolean containsGame(String title) {
        return this.games.stream()
                .anyMatch(game -> game.getTitle().equals(title));
    }

    public void setSteamLocation(String steamLocation) {
        this.steamLocation.set(steamLocation);
        try {
            writeToJson();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        }
    }

    public @NotNull String getSteamLocation() {
        return this.steamLocation.isBound() ? this.steamLocation.get() : "";
    }

    public StringProperty steamLocationProperty() {
        return this.steamLocation;
    }
}
