package dev.turtywurty.gamedashboard.data;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.util.Utils;
import dev.turtywurty.gamedashboard.util.VDFtoJson;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class SteamHandler {
    public static Map<Path, List<Integer>> getLibraryFolderGameMap(Path steamPath) {
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

            return libraryFolderGameMap;
        } catch (IOException | JSONException exception) {
            GameDashboardApp.LOGGER.error("Failed to read libraryfolders.vdf", exception);
            return Collections.emptyMap();
        }
    }

    public static List<Path> findAppManifests(Map<Path, List<Integer>> libraryFolderGameMap) {
        List<Path> appManifests = new ArrayList<>();
        for (Path libraryFolder : libraryFolderGameMap.keySet()) {
            for (int id : libraryFolderGameMap.get(libraryFolder)) {
                Path appManifest = libraryFolder.resolve("steamapps").resolve("appmanifest_" + id + ".acf");
                if (Files.exists(appManifest))
                    appManifests.add(appManifest);
            }
        }

        return appManifests;
    }

    private static APIConnector.GameResult findClosestMatch(String name, List<APIConnector.GameResult> results) {
        if (name.equals("Battlefield™️ V")) {
            results.forEach(result -> System.out.println(result.getName()));
        }

        return results.stream()
                .filter(result -> result != null && result.getName() != null)
                .min(Comparator.comparingInt(result -> Utils.levenshteinDistance(name, result.getName())))
                .orElse(null);
    }

    public static String normalizeSteamName(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9 \\-_:']", "");
        if (normalizedName.endsWith("demo"))
            normalizedName = normalizedName.substring(0, normalizedName.length() - 4);

        if (normalizedName.endsWith("game"))
            normalizedName = normalizedName.substring(0, normalizedName.length() - 4);

        return normalizedName.trim();
    }

    private static String getExecutionCommand(JSONObject appState) {
        return appState.getString("LauncherPath") + " -applaunch " + appState.getString("appid");
    }

    private static List<APIConnector.GameResult> findResults(String name) {
        String searchableName = normalizeSteamName(name);
        if (searchableName.isBlank())
            return Collections.emptyList();

        GameDashboardApp.LOGGER.info("Searching for {}...", searchableName);
        List<APIConnector.GameResult> results = APIConnector.search(searchableName, true).join();
        if (results.isEmpty())
            return Collections.emptyList();

        return results;
    }

    public static Map<String, Supplier<Game>> locateSteamGames(Path steamPath) {
        Map<Path, List<Integer>> libraryFolderGameMap = getLibraryFolderGameMap(steamPath);
        if (libraryFolderGameMap.isEmpty())
            return Collections.emptyMap();

        List<Path> appManifests = findAppManifests(libraryFolderGameMap);

        List<LocationDetails> nameAndCommands = new ArrayList<>();
        for (Path appManifestPath : appManifests) {
            String appManifestContent;
            try {
                appManifestContent = Files.readString(appManifestPath);
            } catch (IOException exception) {
                GameDashboardApp.LOGGER.error("Failed to read appmanifest_{}", appManifestPath.getFileName(), exception);
                continue;
            }

            JSONObject appManifestAsJson = VDFtoJson.toJSONObject(appManifestContent, true);
            JSONObject appState = appManifestAsJson.getJSONObject("AppState");
            String name = appState.getString("name").trim();
            String executionCommand = getExecutionCommand(appState);
            int appId = appState.getInt("appid");
            nameAndCommands.add(new LocationDetails(name, executionCommand, appId));
        }

        return findGameResults(nameAndCommands);
    }

    private static @NotNull Map<String, Supplier<Game>> findGameResults(List<LocationDetails> nameAndCommands) {
        Map<String, Supplier<Game>> futures = new HashMap<>();
        for (LocationDetails locationDetails : nameAndCommands) {
            String name = locationDetails.name();
            String executionCommand = locationDetails.executionCommand();
            int appId = locationDetails.appId();

            futures.put(name, () -> {
                List<APIConnector.GameResult> results = findResults(name);
                if (results.isEmpty()) {
                    GameDashboardApp.LOGGER.warn("No results found for {}", name);
                    return null;
                }

                APIConnector.GameResult gameResult = findClosestMatch(name, results);
                if (gameResult == null) {
                    GameDashboardApp.LOGGER.warn("No closest match found for {}", name);
                    return null;
                }

                return new Game(
                        name,
                        gameResult.getSummary(),
                        executionCommand,
                        gameResult.getThumbCoverURL(),
                        gameResult.getCoverURL(),
                        name,
                        appId
                );
            });
        }

        return futures;
    }

    private static void loadGames(List<Supplier<Game>> futures, ObservableList<Game> games, ObservableList<String> loadingGames) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);  // Pool with 2 threads

        try {
            for (Supplier<Game> futureSupplier : futures) {
                // Submit each task to be executed concurrently
                executorService.submit(() -> {
                    try {
                        Game game = futureSupplier.get(); // Fetch game on background thread

                        // Once game is retrieved, update UI on the JavaFX thread
                        Platform.runLater(() -> {
                            String name = loadingGames.removeFirst();

                            if (game == null) {
                                GameDashboardApp.LOGGER.warn("Failed to load game {}", name);
                            } else {
                                games.add(game);
                            }
                        });
                    } catch (Exception exception) {
                        Platform.runLater(() -> {
                            String name = loadingGames.removeFirst();
                            GameDashboardApp.LOGGER.error("Error loading game {}", name, exception);
                        });
                    }
                });
            }
        } catch (Exception exception) {
            GameDashboardApp.LOGGER.error("Failed to load games", exception);
        } finally {
            executorService.shutdown();
        }
    }

    public static boolean isSteamLocationValid(String location) {
        if (location.isEmpty())
            return false;

        Path steamPath = Path.of(location);
        if (Files.notExists(steamPath))
            return false;

        if (Files.notExists(steamPath.resolve("steam.exe")))
            return false;

        Path steamAppsPath = steamPath.resolve("steamapps");
        if (Files.notExists(steamAppsPath))
            return false;

        Path libraryFoldersPath = steamAppsPath.resolve("libraryfolders.vdf");
        return Files.exists(libraryFoldersPath);
    }

    public void onSteamLocationUpdated(String location, ObservableList<Game> games, ObservableList<String> loadingGames, StringProperty steamLocation) {
        if (!isSteamLocationValid(location)) {
            steamLocation.set("");
            return;
        }

        games.removeIf(Game::isSteam); // Remove any existing steam games

        Map<String, Supplier<Game>> steamGames = locateSteamGames(Path.of(location));
        if (steamGames.isEmpty()) {
            steamLocation.set("");
            return;
        }

        Platform.runLater(() -> loadingGames.addAll(steamGames.keySet()));

        IntegerProperty completed = new SimpleIntegerProperty(0);
        new Thread(() -> loadGames(new ArrayList<>(steamGames.values()), games, loadingGames)).start();

        completed.addListener((observable, oldValue, newValue) -> {
            if (newValue.intValue() == steamGames.size()) {
                steamLocation.set(location);
                GameDashboardApp.LOGGER.info("Steam games loaded!");
            }
        });
    }

    public record LocationDetails(String name, String executionCommand, int appId) {
    }
}
