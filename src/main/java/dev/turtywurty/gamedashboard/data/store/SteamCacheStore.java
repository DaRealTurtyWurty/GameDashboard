package dev.turtywurty.gamedashboard.data.store;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.model.SteamCache;
import dev.turtywurty.gamedashboard.data.model.SteamGameCacheEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SteamCacheStore {
    private static final String FILE_NAME = "steam_cache.json";

    private final Gson gson;
    private final Path cachePath;

    public SteamCacheStore(Path appDataPath, Gson gson) {
        this.cachePath = Objects.requireNonNull(appDataPath, "appDataPath").resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public Optional<SteamCache> load() {
        if (!Files.isRegularFile(this.cachePath) || !Files.isReadable(this.cachePath))
            return Optional.empty();

        try {
            SteamCache cache = this.gson.fromJson(Files.readString(this.cachePath), SteamCache.class);
            return normalize(cache);
        } catch (IOException | JsonParseException | InvalidPathException exception) {
            GameDashboardApp.LOGGER.error("Failed to load {}", FILE_NAME, exception);
            return Optional.empty();
        }
    }

    public void save(SteamCache cache) {
        Optional<SteamCache> normalizedCache;
        try {
            normalizedCache = normalize(Objects.requireNonNull(cache, "cache"));
        } catch (InvalidPathException exception) {
            GameDashboardApp.LOGGER.error("Invalid Steam location; {} was not saved", FILE_NAME, exception);
            return;
        }

        if (normalizedCache.isEmpty()) {
            GameDashboardApp.LOGGER.warn("Invalid Steam cache; {} was not saved", FILE_NAME);
            return;
        }

        try {
            JsonFileStore.writeAtomically(this.cachePath, this.gson.toJson(normalizedCache.get()));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to save {}", FILE_NAME, exception);
        }
    }

    private static Optional<SteamCache> normalize(SteamCache cache) {
        if (cache == null || cache.steamLocation() == null || cache.steamLocation().isBlank())
            return Optional.empty();

        Path.of(cache.steamLocation());
        List<SteamGameCacheEntry> games = cache.games() == null
                ? List.of()
                : cache.games().stream()
                .filter(SteamCacheStore::isValid)
                .toList();
        return Optional.of(new SteamCache(cache.steamLocation(), games));
    }

    private static boolean isValid(SteamGameCacheEntry entry) {
        return entry != null
                && entry.appId() >= 0
                && entry.name() != null
                && !entry.name().isBlank()
                && entry.executionPath() != null
                && !entry.executionPath().isBlank();
    }
}
