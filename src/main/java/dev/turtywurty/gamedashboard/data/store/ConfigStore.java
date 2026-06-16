package dev.turtywurty.gamedashboard.data.store;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.model.DashboardConfig;
import dev.turtywurty.gamedashboard.util.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ConfigStore {
    private static final String FILE_NAME = "config.json";
    private static final DashboardConfig DEFAULT_CONFIG = new DashboardConfig("", "", List.of(), false);

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
        String configuredExecutable = config.steamExecutable() == null ? "" : config.steamExecutable().trim();
        String configuredLibraryFolders = config.steamLibraryFolders() == null
                ? ""
                : config.steamLibraryFolders().trim();
        String steamExecutable = normalizeSteamExecutable(configuredExecutable);
        String steamLibraryFolders = normalizeSteamLibraryFolders(
                configuredExecutable,
                configuredLibraryFolders
        );
        List<String> epicInstallLocations = config.epicInstallLocations() == null
                ? List.of()
                : config.epicInstallLocations().stream()
                .filter(location -> location != null && !location.isBlank())
                .toList();
        return new DashboardConfig(steamExecutable, steamLibraryFolders, epicInstallLocations, config.onboardingComplete());
    }

    private static String normalizeSteamExecutable(String configuredExecutable) {
        if (configuredExecutable.isEmpty())
            return "";

        Path path;
        try {
            path = Path.of(configuredExecutable);
        } catch (InvalidPathException exception) {
            return "";
        }

        if (!Files.isDirectory(path))
            return path.toString();

        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> path.resolve("steam.exe").toString();
            case MACOS -> path.resolve("Contents").resolve("MacOS").resolve("steam_osx").toString();
            default -> path.resolve("steam").toString();
        };
    }

    private static String normalizeSteamLibraryFolders(
            String configuredExecutable,
            String configuredLibraryFolders
    ) {
        if (!configuredLibraryFolders.isEmpty()) {
            Path path;
            try {
                path = Path.of(configuredLibraryFolders);
            } catch (InvalidPathException exception) {
                return "";
            }
            return Files.isDirectory(path) ? path.resolve("libraryfolders.vdf").toString() : path.toString();
        }

        if (configuredExecutable.isEmpty())
            return "";

        Path legacyPath;
        try {
            legacyPath = Path.of(configuredExecutable);
        } catch (InvalidPathException exception) {
            return "";
        }
        Path steamRoot = Files.isDirectory(legacyPath) ? legacyPath : legacyPath.getParent();
        return steamRoot == null
                ? ""
                : steamRoot.resolve("steamapps").resolve("libraryfolders.vdf").toString();
    }
}
