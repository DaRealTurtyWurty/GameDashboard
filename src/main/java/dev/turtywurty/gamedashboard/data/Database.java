package dev.turtywurty.gamedashboard.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.data.model.DashboardConfig;
import dev.turtywurty.gamedashboard.data.store.ConfigStore;
import dev.turtywurty.gamedashboard.data.store.GameStore;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Database {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Database INSTANCE = new Database();

    private final ConfigStore configStore;
    private final GameStore gameStore;
    private final SteamHandler steamHandler;

    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private final ObservableList<Game> readOnlyGames = FXCollections.unmodifiableObservableList(this.games);
    private final ObservableList<String> loadingGames = FXCollections.observableArrayList();
    private final ObservableList<String> readOnlyLoadingGames =
            FXCollections.unmodifiableObservableList(this.loadingGames);
    private final ObservableList<String> epicGamesInstallLocations = FXCollections.observableArrayList();
    private final ObservableList<String> readOnlyEpicGamesInstallLocations =
            FXCollections.unmodifiableObservableList(this.epicGamesInstallLocations);
    private final ReadOnlyStringWrapper steamExecutable = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper steamLibraryFolders = new ReadOnlyStringWrapper("");

    private boolean loadingConfig;

    private Database() {
        Path appDataPath = getAppDataPath();
        this.configStore = new ConfigStore(appDataPath, GSON);
        this.gameStore = new GameStore(appDataPath, GSON);
        this.steamHandler = new SteamHandler();

        load();

        this.games.addListener((ListChangeListener<Game>) change -> save());
        this.epicGamesInstallLocations.addListener((ListChangeListener<String>) change -> save());
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public static Path getAppDataPath() {
        return GameDashboardApp.APP_DATA_PATH;
    }

    public void load() {
        DashboardConfig config = this.configStore.load();
        List<Game> games = this.gameStore.load();
        String executable = config.steamExecutable();
        String libraryFolders = config.steamLibraryFolders();

        if (executable.isEmpty())
            executable = SteamHandler.discoverSteamLocation().map(Path::toString).orElse("");
        if (libraryFolders.isEmpty())
            libraryFolders = SteamHandler.discoverSteamLibraryFoldersLocation().map(Path::toString).orElse("");

        this.loadingConfig = true;
        try {
            this.games.setAll(games);
            this.steamExecutable.set(executable);
            this.steamLibraryFolders.set(libraryFolders);
            this.epicGamesInstallLocations.setAll(config.epicInstallLocations());
        } finally {
            this.loadingConfig = false;
        }

        if (!executable.equals(config.steamExecutable()) || !libraryFolders.equals(config.steamLibraryFolders())) {
            save();
        }
    }

    public void save() {
        if (this.loadingConfig)
            return;

        this.configStore.save(new DashboardConfig(
                getSteamExecutable(),
                getSteamLibraryFolders(),
                new ArrayList<>(this.epicGamesInstallLocations)
        ));
        this.gameStore.save(new ArrayList<>(this.games));
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

    public boolean containsGame(Game game) {
        return findMatchingGame(game).isPresent();
    }

    public boolean setSteamConfiguration(String executable, String libraryFolders) {
        if (executable == null || libraryFolders == null)
            return false;

        Path executablePath;
        Path libraryFoldersPath;
        try {
            executablePath = Path.of(executable);
            libraryFoldersPath = Path.of(libraryFolders);
        } catch (InvalidPathException exception) {
            return false;
        }
        if (!SteamHandler.isSteamConfigurationValid(executablePath, libraryFoldersPath))
            return false;

        this.steamExecutable.set(executablePath.toString());
        this.steamLibraryFolders.set(libraryFoldersPath.toString());
        this.steamHandler.onSteamConfigurationUpdated(
                executablePath,
                libraryFoldersPath,
                this.games,
                this.loadingGames
        );
        save();
        return true;
    }

    public ReadOnlyStringProperty steamExecutableProperty() {
        return this.steamExecutable.getReadOnlyProperty();
    }

    public @NotNull String getSteamExecutable() {
        String location = this.steamExecutable.get();
        return location == null ? "" : location;
    }

    public ReadOnlyStringProperty steamLibraryFoldersProperty() {
        return this.steamLibraryFolders.getReadOnlyProperty();
    }

    public @NotNull String getSteamLibraryFolders() {
        String location = this.steamLibraryFolders.get();
        return location == null ? "" : location;
    }

    public boolean isSteamConfigured() {
        return !getSteamExecutable().isEmpty() && !getSteamLibraryFolders().isEmpty();
    }

    private Optional<Game> findMatchingGame(Game game) {
        if (game == null)
            return Optional.empty();

        return this.games.stream()
                .filter(game::matches)
                .findFirst();
    }
}
