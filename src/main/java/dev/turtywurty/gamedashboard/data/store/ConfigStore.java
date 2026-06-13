package dev.turtywurty.gamedashboard.data.store;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.Game;
import dev.turtywurty.gamedashboard.data.model.DashboardConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ConfigStore {
    private static final String FILE_NAME = "config.json";
    private static final DashboardConfig DEFAULT_CONFIG = new DashboardConfig(List.of(), "", List.of());

    private final Gson gson;
    private final Path configPath;

    public ConfigStore(Path appDataPath, Gson gson) {
        this.configPath = Objects.requireNonNull(appDataPath, "appDataPath").resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public DashboardConfig load() {
        if (!Files.isRegularFile(this.configPath) || !Files.isReadable(this.configPath)) {
            save(DEFAULT_CONFIG);
            return DEFAULT_CONFIG;
        }

        try {
            DashboardConfig config = this.gson.fromJson(Files.readString(this.configPath), DashboardConfig.class);
            if (config == null) {
                GameDashboardApp.LOGGER.error("{} must contain a JSON object", FILE_NAME);
                return DEFAULT_CONFIG;
            }

            return normalize(config);
        } catch (IOException | JsonParseException exception) {
            GameDashboardApp.LOGGER.error("Failed to load {}", FILE_NAME, exception);
            return DEFAULT_CONFIG;
        }
    }

    public void save(DashboardConfig config) {
        DashboardConfig normalizedConfig = normalize(Objects.requireNonNull(config, "config"));
        try {
            JsonFileStore.writeAtomically(this.configPath, this.gson.toJson(normalizedConfig));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to save {}", FILE_NAME, exception);
        }
    }

    private static DashboardConfig normalize(DashboardConfig config) {
        List<Game> games = config.games() == null
                ? List.of()
                : config.games().stream()
                .filter(game -> game != null && game.getTitle() != null)
                .toList();
        String steamLocation = config.steamLocation() == null ? "" : config.steamLocation();
        List<String> epicInstallLocations = config.epicInstallLocations() == null
                ? List.of()
                : config.epicInstallLocations().stream()
                .filter(location -> location != null && !location.isBlank())
                .toList();
        return new DashboardConfig(games, steamLocation, epicInstallLocations);
    }
}
