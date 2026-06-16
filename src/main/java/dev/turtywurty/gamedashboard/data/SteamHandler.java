package dev.turtywurty.gamedashboard.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.data.game.impl.SteamGame;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.VDFtoJson;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import mslinks.ShellLink;
import mslinks.ShellLinkException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SteamHandler {
    public static Map<Path, List<Integer>> getLibraryFolderGameMap(Path libraryFoldersPath) {
        try {
            String content = Files.readString(libraryFoldersPath);
            JsonObject vdfAsJson = VDFtoJson.toJSONObject(content, true);
            JsonArray libraryFolders = vdfAsJson.getAsJsonArray("libraryfolders");

            Map<Path, List<Integer>> libraryFolderGameMap = new HashMap<>();
            for (Object libraryFolder : libraryFolders) {
                if (!(libraryFolder instanceof JsonObject obj))
                    continue;

                String path = obj.get("path").getAsString();
                JsonObject apps = obj.get("apps").isJsonArray() ? new JsonObject() : obj.getAsJsonObject("apps");
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
        } catch (IOException exception) {
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

    private static String getExecutionCommand(JsonObject appState) {
        return appState.get("LauncherPath").getAsString() + " -applaunch " + appState.get("appid").getAsString();
    }

    public static Map<String, Supplier<Game>> locateSteamGames(Path libraryFoldersPath) {
        Map<Path, List<Integer>> libraryFolderGameMap = getLibraryFolderGameMap(libraryFoldersPath);
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

            JsonObject appManifestAsJson = VDFtoJson.toJSONObject(appManifestContent, true);
            JsonObject appState = appManifestAsJson.getAsJsonObject("AppState");
            String name = appState.get("name").getAsString().trim();
            String executionCommand = getExecutionCommand(appState);
            int appId = appState.get("appid").getAsInt();
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
                GameDashboardApp.LOGGER.info("Fetching game details for {} (AppID: {})...", name, appId);
                APIConnector.GameResult gameResult;
                try {
                    Integer igdbId = APIConnector.getGameIdFromExternalId(
                            APIConnector.ExternalPlatform.STEAM,
                            String.valueOf(appId)
                    ).join();
                    if (igdbId == null) {
                        GameDashboardApp.LOGGER.info(
                                "Skipping {} (AppID: {}): game does not exist in the metadata database",
                                name,
                                appId
                        );
                        return null;
                    }

                    gameResult = APIConnector.getGameByID(igdbId, false, true).join();
                } catch (Exception exception) {
                    GameDashboardApp.LOGGER.error("Failed to fetch game details for {} (AppID: {})", name, appId, exception);
                    return null;
                }

                if (gameResult == null) {
                    GameDashboardApp.LOGGER.info(
                            "Skipping {} (AppID: {}): game metadata no longer exists",
                            name,
                            appId
                    );
                    return null;
                }

                return SteamGame.builder(name, gameResult.getSummary(), executionCommand, appId)
                        .images(gameResult.getThumbCoverURL(), gameResult.getCoverURL())
                        .nickname(name)
                        .build();
            });
        }

        return futures;
    }

    private static void loadGames(
            Map<String, Supplier<Game>> futures,
            ObservableList<Game> games,
            ObservableList<String> loadingGames,
            ProgressMonitor progressMonitor
    ) {

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            for (Map.Entry<String, Supplier<Game>> entry : futures.entrySet()) {
                String name = entry.getKey();
                Supplier<Game> futureSupplier = entry.getValue();
                executorService.submit(() -> {
                    try {
                        Game game = futureSupplier.get();

                        Platform.runLater(() -> {
                            if (game == null) {
                                GameDashboardApp.LOGGER.warn("Failed to load game {}", name);
                            } else {
                                games.add(game);
                            }

                            loadingGames.remove(name);
                        });
                    } catch (Exception exception) {
                        Platform.runLater(() -> {
                            GameDashboardApp.LOGGER.error("Error loading game {}", name, exception);
                            loadingGames.remove(name);
                        });
                    } finally {
                        if (progressMonitor != null) {
                            progressMonitor.worked(1);
                        }
                    }
                });
            }
        } catch (Exception exception) {
            GameDashboardApp.LOGGER.error("Failed to load games", exception);
        }
    }

    public static boolean isSteamExecutableValid(Path executable) {
        if (executable == null || !Files.isRegularFile(executable))
            return false;

        String fileName = executable.getFileName().toString();

        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> "steam.exe".equalsIgnoreCase(fileName);
            case MACOS -> "steam_osx".equals(fileName);
            default -> "steam".equals(fileName);
        };
    }

    public static boolean isSteamLibraryFoldersValid(Path libraryFoldersPath) {
        return libraryFoldersPath != null
                && Files.isRegularFile(libraryFoldersPath)
                && "libraryfolders.vdf".equalsIgnoreCase(libraryFoldersPath.getFileName().toString());
    }

    public static boolean isSteamConfigurationValid(Path executable, Path libraryFoldersPath) {
        return isSteamExecutableValid(executable) && isSteamLibraryFoldersValid(libraryFoldersPath);
    }

    public static Optional<Path> discoverSteamLocation() {
        if (OperatingSystem.CURRENT == OperatingSystem.WINDOWS) {
            Optional<Path> registryPath = findSteamLocationFromRegistry();
            if (registryPath.isPresent())
                return registryPath;

            Optional<Path> processPath = findProcessRunningPath("steam.exe", false);
            if (processPath.isPresent())
                return processPath;

            Optional<Path> startMenuPath = findSteamLocationFromStartMenu();
            if (startMenuPath.isPresent())
                return startMenuPath;

            Optional<Path> uriProtocolPath = findSteamLocationFromURIProtocol();
            if (uriProtocolPath.isPresent())
                return uriProtocolPath;

            // Check common installation paths
            for (char drive = 'C'; drive <= 'Z'; drive++) {
                try {
                    Path commonPath = Path.of(drive + ":\\Program Files (x86)\\Steam\\steam.exe");
                    if (Files.isRegularFile(commonPath))
                        return Optional.of(commonPath);

                    commonPath = Path.of(drive + ":\\Program Files\\Steam\\steam.exe");
                    if (Files.isRegularFile(commonPath))
                        return Optional.of(commonPath);

                    commonPath = Path.of(drive + ":\\Steam\\steam.exe");
                    if (Files.isRegularFile(commonPath))
                        return Optional.of(commonPath);
                } catch (Exception ignored) {
                    // Some drives may not be happy with this, so we just ignore any exceptions and continue checking other drives.
                }
            }
        } else if (OperatingSystem.CURRENT == OperatingSystem.MACOS) {
            Optional<Path> spotlightPath = findSteamFromSpotlight();
            if (spotlightPath.isPresent())
                return spotlightPath;

            Path steamExecutable = Path.of("/Applications/Steam.app/Contents/MacOS/steam_osx");
            if (Files.isRegularFile(steamExecutable))
                return Optional.of(steamExecutable);

            return findProcessRunningPath("steam", true);
        } else if (OperatingSystem.CURRENT == OperatingSystem.LINUX) {
            Optional<Path> commandPath = findCommandPath("steam");
            if (commandPath.isPresent())
                return commandPath;

            return findProcessRunningPath("steam", true);
        }

        return Optional.empty();
    }

    public static Optional<Path> discoverSteamLibraryFoldersLocation() {
        return discoverSteamLocation().flatMap(SteamHandler::discoverSteamLibraryFoldersLocation);
    }

    public static Optional<Path> discoverSteamLibraryFoldersLocation(Path steamExecutable) {
        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> findLibraryFoldersLocation(steamExecutable.getParent());
            case MACOS -> findLibraryFoldersLocation(Path.of(
                    System.getProperty("user.home"),
                    "Library",
                    "Application Support",
                    "Steam"
            ));
            case LINUX -> {
                String userHome = System.getProperty("user.home");
                List<Path> candidates = List.of(
                        Path.of(userHome, ".local", "share", "Steam"),
                        Path.of(userHome, ".steam", "steam"),
                        Path.of(userHome, ".var", "app", "com.valvesoftware.Steam", ".local", "share", "Steam")
                );

                yield candidates.stream()
                        .map(SteamHandler::findLibraryFoldersLocation)
                        .flatMap(Optional::stream)
                        .findFirst();
            }
            default -> Optional.empty();
        };
    }

    private static Optional<Path> findLibraryFoldersLocation(Path steamRoot) {
        if (steamRoot == null)
            return Optional.empty();

        Path libraryFoldersPath = steamRoot.resolve("steamapps").resolve("libraryfolders.vdf");
        return Files.isRegularFile(libraryFoldersPath)
                ? Optional.of(libraryFoldersPath)
                : Optional.empty();
    }

    private static Optional<Path> findSteamLocationFromRegistry() {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return Optional.empty();

        String steamPath = readRegistryValue(
                "HKCU\\SOFTWARE\\Valve\\Steam",
                "SteamPath"
        );
        if (steamPath != null && !steamPath.isEmpty()) {
            Path path = Path.of(steamPath).resolve("steam.exe");
            if (Files.isRegularFile(path))
                return Optional.of(path);
        }

        String steamExe = readRegistryValue(
                "HKCU\\SOFTWARE\\Valve\\Steam",
                "SteamExe"
        );
        if (steamExe != null && !steamExe.isEmpty()) {
            Path path = Path.of(steamExe);
            if (Files.isRegularFile(path))
                return Optional.of(path);
        }

        return Optional.empty();
    }

    private static String readRegistryValue(String key, @Nullable String valueName) {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return null;

        try {
            Process process = new ProcessBuilder(
                    "reg",
                    "query",
                    key,
                    valueName != null ? "/v" : "/ve",
                    valueName != null ? valueName : ""
            ).start();

            String output = new String(process.getInputStream().readAllBytes());
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.contains("REG_SZ") || line.contains("REG_EXPAND_SZ")) {
                    String[] parts = line.trim().split("\\t");
                    if (parts.length >= 3)
                        return parts[parts.length - 1];
                }
            }
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read registry value", exception);
        }

        return null;
    }

    private static Optional<Path> findProcessRunningPath(String processName, boolean contains) {
        return ProcessHandle.allProcesses()
                .map(processHandle -> processHandle.info().command())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(command -> contains
                        ? command.contains(processName)
                        : command.endsWith(processName))
                .findFirst()
                .map(Path::of)
                .filter(Files::isRegularFile);
    }

    private static Optional<Path> findCommandPath(String command) {
        try {
            Process process = new ProcessBuilder("sh", "-c", "command -v " + command).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (process.waitFor() != 0 || output.isEmpty())
                return Optional.empty();

            Path path = Path.of(output);
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to find command {}", command, exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<Path> findSteamLocationFromStartMenu() {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return Optional.empty();

        Path startMenuPath1 = Path.of(System.getenv("APPDATA"), "Microsoft", "Windows", "Start Menu", "Programs", "Steam");
        Path startMenuPath2 = Path.of(System.getenv("PROGRAMDATA"), "Microsoft", "Windows", "Start Menu", "Programs", "Steam");

        Stream<Path> startMenuPaths = Stream.of(startMenuPath1, startMenuPath2)
                .filter(Files::exists);
        return startMenuPaths.flatMap(start -> {
                    try {
                        return Files.walk(start);
                    } catch (IOException exception) {
                        GameDashboardApp.LOGGER.error("Failed to walk through Start Menu path {}", start, exception);
                        return Stream.empty();
                    }
                })
                .filter(path -> path.toString().endsWith(".lnk"))
                .map(SteamHandler::resolveShortcut)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(Files::isRegularFile)
                .findFirst();
    }

    private static Optional<Path> resolveShortcut(Path shortcutPath) {
        try {
            return Optional.ofNullable(new ShellLink(shortcutPath).resolveTarget())
                    .map(Path::of);
        } catch (IOException | ShellLinkException exception) {
            GameDashboardApp.LOGGER.error("Failed to resolve shortcut {}", shortcutPath, exception);
            return Optional.empty();
        }
    }

    private static Optional<Path> findSteamLocationFromURIProtocol() {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return Optional.empty();

        String steamURI = readRegistryValue(
                "HKEY_CLASSES_ROOT\\steam\\Shell\\Open\\Command",
                ""
        );
        if (steamURI != null && !steamURI.isEmpty()) {
            String path = steamURI.replace("\"%1\"", "").trim();
            Path steamExecutable = Path.of(path);
            if (Files.isRegularFile(steamExecutable))
                return Optional.of(steamExecutable);
        }

        return Optional.empty();
    }

    private static Optional<Path> findSteamFromSpotlight() {
        if (OperatingSystem.CURRENT != OperatingSystem.MACOS)
            return Optional.empty();

        try {
            Process process = new ProcessBuilder(
                    "mdfind",
                    "kMDItemCFBundleIdentifier == 'com.valvesoftware.steam'"
            ).start();

            String output = new String(process.getInputStream().readAllBytes());
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.isBlank())
                    continue;

                Path steamExecutable = Path.of(line.trim(), "Contents", "MacOS", "steam_osx");
                if (Files.isRegularFile(steamExecutable))
                    return Optional.of(steamExecutable);
            }

            return Optional.empty();
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to find Steam location from Spotlight", exception);
            return Optional.empty();
        }
    }

    public void onSteamConfigurationUpdated(
            Path executable,
            Path libraryFoldersPath,
            ObservableList<Game> games,
            ObservableList<String> loadingGames,
            ProgressMonitor progressMonitor
    ) {
        if (!isSteamConfigurationValid(executable, libraryFoldersPath))
            return;

        runOnFxThread(() -> games.removeIf(SteamGame.class::isInstance));

        Map<String, Supplier<Game>> steamGames = locateSteamGames(libraryFoldersPath);
        if (steamGames.isEmpty()) {
            if (progressMonitor != null) {
                progressMonitor.done();
            }

            return;
        }

        if (progressMonitor != null) {
            progressMonitor.start("Loading Steam games", steamGames.size());
        }

        runOnFxThread(() -> loadingGames.addAll(steamGames.keySet()));
        Runnable loadAction = () -> {
            loadGames(steamGames, games, loadingGames, progressMonitor);
            if (progressMonitor != null) {
                progressMonitor.done();
            }
        };

        if (progressMonitor == null) {
            new Thread(loadAction).start();
        } else {
            loadAction.run();
        }
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public record LocationDetails(String name, String executionCommand, int appId) {
    }
}
