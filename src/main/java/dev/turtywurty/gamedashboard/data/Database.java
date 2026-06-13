package dev.turtywurty.gamedashboard.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.model.DashboardConfig;
import dev.turtywurty.gamedashboard.data.model.SteamCache;
import dev.turtywurty.gamedashboard.data.model.SteamGameCacheEntry;
import dev.turtywurty.gamedashboard.data.store.ConfigStore;
import dev.turtywurty.gamedashboard.data.store.SteamCacheStore;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Database {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Database INSTANCE = new Database();

    private final ConfigStore configStore;
    private final SteamCacheStore steamCacheStore;
    private final SteamHandler steamHandler;

    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private final ObservableList<Game> readOnlyGames = FXCollections.unmodifiableObservableList(this.games);
    private final ObservableList<String> loadingGames = FXCollections.observableArrayList();
    private final ObservableList<String> readOnlyLoadingGames =
            FXCollections.unmodifiableObservableList(this.loadingGames);
    private final ObservableList<String> epicGamesInstallLocations = FXCollections.observableArrayList();
    private final ObservableList<String> readOnlyEpicGamesInstallLocations =
            FXCollections.unmodifiableObservableList(this.epicGamesInstallLocations);
    private final ReadOnlyStringWrapper steamLocation = new ReadOnlyStringWrapper("");

    private boolean loadingConfig;

    private Database() {
        Path appDataPath = getAppDataPath();
        this.configStore = new ConfigStore(appDataPath, GSON);
        this.steamCacheStore = new SteamCacheStore(appDataPath, GSON);
        this.steamHandler = new SteamHandler();

        load();

        this.games.addListener((ListChangeListener<Game>) change -> save());
        this.epicGamesInstallLocations.addListener((ListChangeListener<String>) change -> save());
        this.steamLocation.addListener((observable, oldValue, newValue) -> {
            if (this.loadingConfig)
                return;

            if (newValue == null) {
                this.steamLocation.set("");
                return;
            }

            this.steamHandler.onSteamLocationUpdated(
                    newValue,
                    this.games,
                    this.loadingGames,
                    this.steamLocation
            );
            save();
        });
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public static Path getAppDataPath() {
        return GameDashboardApp.APP_DATA_PATH;
    }

    public void load() {
        DashboardConfig config = this.configStore.load();

        this.loadingConfig = true;
        try {
            this.games.setAll(config.games());
            this.steamLocation.set(config.steamLocation());
            this.epicGamesInstallLocations.setAll(config.epicInstallLocations());
            restoreSteamCache(config.steamLocation());
        } finally {
            this.loadingConfig = false;
        }
    }

    public void save() {
        if (this.loadingConfig)
            return;

        this.configStore.save(new DashboardConfig(
                new ArrayList<>(this.games),
                getSteamLocation(),
                new ArrayList<>(this.epicGamesInstallLocations)
        ));

        if (!getSteamLocation().isEmpty())
            this.steamCacheStore.save(constructSteamCache());
    }

    public ObservableList<Game> getGames() {
        return this.readOnlyGames;
    }

    public ObservableList<String> getLoadingGames() {
        return this.readOnlyLoadingGames;
    }

    public ObservableList<String> getEpicGamesInstallLocations() {
        return this.readOnlyEpicGamesInstallLocations;
    }

    public boolean addGame(Game game) {
        if (game == null || containsGame(game))
            return false;

        return this.games.add(game);
    }

    public boolean removeGame(Game game) {
        Optional<Game> matchingGame = findMatchingGame(game);
        return matchingGame.filter(this.games::remove).isPresent();
    }

    public boolean updateGame(Game original, Game replacement) {
        if (original == null || replacement == null)
            return false;

        Optional<Game> matchingGame = findMatchingGame(original);
        if (matchingGame.isEmpty())
            return false;

        Optional<Game> conflictingGame = findMatchingGame(replacement);
        if (conflictingGame.isPresent() && conflictingGame.get() != matchingGame.get())
            return false;

        this.games.set(this.games.indexOf(matchingGame.get()), replacement);
        return true;
    }

    public Optional<Game> getGameBySteamAppId(int steamAppId) {
        if (steamAppId < 0)
            return Optional.empty();

        return this.games.stream()
                .filter(Game::isSteam)
                .filter(game -> game.getSteamAppId() == steamAppId)
                .findFirst();
    }

    public Optional<Game> getGameByExecutionCommand(String executionCommand) {
        String normalizedCommand = normalizeExecutionCommand(executionCommand);
        if (normalizedCommand.isEmpty())
            return Optional.empty();

        return this.games.stream()
                .filter(game -> normalizeExecutionCommand(game.getExecutionCommand()).equals(normalizedCommand))
                .findFirst();
    }

    public boolean containsGame(Game game) {
        return findMatchingGame(game).isPresent();
    }

    public void setSteamLocation(String steamLocation) {
        this.steamLocation.set(steamLocation == null ? "" : steamLocation);
    }

    public ReadOnlyStringProperty steamLocationProperty() {
        return this.steamLocation.getReadOnlyProperty();
    }

    public @NotNull String getSteamLocation() {
        String location = this.steamLocation.get();
        return location == null ? "" : location;
    }

    private void restoreSteamCache(String configuredSteamLocation) {
        if (configuredSteamLocation.isEmpty())
            return;

        Optional<SteamCache> cachedSteamGames = this.steamCacheStore.load();
        if (cachedSteamGames.isEmpty()
                || !cachedSteamGames.get().steamLocation().equals(configuredSteamLocation)) {
            this.steamCacheStore.save(constructSteamCache());
            return;
        }

        for (SteamGameCacheEntry entry : cachedSteamGames.get().games()) {
            if (getGameBySteamAppId(entry.appId()).isPresent())
                continue;

            this.games.add(new Game(
                    entry.name(),
                    "",
                    entry.executionPath(),
                    entry.thumbCoverImageURL(),
                    entry.coverImageURL(),
                    entry.name(),
                    entry.appId()
            ));
        }
    }

    private SteamCache constructSteamCache() {
        List<SteamGameCacheEntry> cacheEntries = this.games.stream()
                .filter(Game::isSteam)
                .map(game -> new SteamGameCacheEntry(
                        game.getSteamAppId(),
                        game.getTitle(),
                        game.getExecutionCommand(),
                        game.getThumbCoverImageURL(),
                        game.getCoverImageURL()
                ))
                .toList();
        return new SteamCache(getSteamLocation(), cacheEntries);
    }

    private Optional<Game> findMatchingGame(Game game) {
        if (game == null)
            return Optional.empty();

        if (game.isSteam())
            return getGameBySteamAppId(game.getSteamAppId());

        return getGameByExecutionCommand(game.getExecutionCommand());
    }

    private static String normalizeExecutionCommand(String executionCommand) {
        return executionCommand == null
                ? ""
                : executionCommand.trim().toLowerCase(Locale.ROOT);
    }
}
