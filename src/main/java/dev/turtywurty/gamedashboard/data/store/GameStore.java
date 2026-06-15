package dev.turtywurty.gamedashboard.data.store;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.game.Game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class GameStore {
    private static final String FILE_NAME = "games.json";

    private final Gson gson;
    private final Path gamesPath;

    public GameStore(Path appDataPath, Gson gson) {
        this.gamesPath = Objects.requireNonNull(appDataPath, "appDataPath").resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public List<Game> load() {
        if (!Files.isRegularFile(this.gamesPath) || !Files.isReadable(this.gamesPath)) {
            save(Collections.emptyList());
            return Collections.emptyList();
        }

        try {
            List<Game> games = this.gson.fromJson(Files.readString(this.gamesPath), new TypeToken<List<Game>>() {
            });
            if (games == null) {
                GameDashboardApp.LOGGER.error("{} must contain a JSON array", FILE_NAME);
                return Collections.emptyList();
            }

            return normalize(games);
        } catch (IOException | JsonParseException exception) {
            GameDashboardApp.LOGGER.error("Failed to load {}", FILE_NAME, exception);
            return Collections.emptyList();
        }
    }

    public void save(List<Game> games) {
        List<Game> normalizedGames = normalize(Objects.requireNonNull(games, "games"));
        try {
            JsonFileStore.writeAtomically(this.gamesPath, this.gson.toJson(normalizedGames));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to save {}", FILE_NAME, exception);
        }
    }

    private static List<Game> normalize(List<Game> games) {
        return games == null
                ? Collections.emptyList()
                : games.stream().filter(game -> game != null && game.getTitle() != null).toList();
    }
}