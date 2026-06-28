package dev.turtywurty.gamedashboard.platform.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.RiotGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.scene.image.Image;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class RiotPlatform implements Platform {
    private static final Gson GSON = new Gson();
    private static final Yaml YAML = createYamlParser();
    private static final String SETTINGS_SUFFIX = ".product_settings.yaml";

    @Override
    public Image getIcon() {
        return new Image(Objects.requireNonNull(
                getClass().getResource("/images/platforms/riot.png")
        ).toExternalForm());
    }

    @Override
    public String getName() {
        return "Riot Games";
    }

    @Override
    public String getWebsite() {
        return "https://www.riotgames.com/";
    }

    @Override
    public String getDescription() {
        return "Riot Games publishes and operates games including League of Legends, VALORANT, and Legends of Runeterra through the Riot Client.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            Path dataDirectory = getDefaultDataDirectory();
            if (!isValidDataDirectory(dataDirectory)) {
                progressMonitor.start("Finding Riot Games installation data", 1);
                progressMonitor.worked(1);
                progressMonitor.done();
                return;
            }

            addGames(progressMonitor, dataDirectory);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the Riot Games data directory containing Metadata and RiotClientInstalls.json. "
                        + "This is normally C:\\ProgramData\\Riot Games.",
                "riot",
                false,
                0
        );

        Path defaultDirectory = getDefaultDataDirectory();
        var directoryField = form.addDirectoryField(
                "Riot Games data directory",
                isValidDataDirectory(defaultDirectory)
                        ? defaultDirectory.toString()
                        : "",
                defaultDirectory == null ? "" : defaultDirectory.toString(),
                "Select Riot Games Data Directory",
                false
        );

        return form.build(progressMonitor -> {
            if (!form.validate(
                    directoryField,
                    RiotPlatform::isValidDataDirectory,
                    "Select a Riot Games data directory containing product metadata."
            ))
                return;

            form.hideError();
            ManualEntryForm.runAsync(
                    progressMonitor,
                    () -> addGames(progressMonitor, Path.of(directoryField.getText()))
            );
        });
    }

    private static void addGames(ProgressMonitor progressMonitor, Path riotDataDirectory) {
        progressMonitor.start("Reading Riot Games installations", 1);
        List<RiotInstallation> installations = discover(riotDataDirectory);
        progressMonitor.worked(1);
        if (installations.isEmpty()) {
            progressMonitor.done();
            return;
        }

        progressMonitor.start("Loading Riot Games titles", installations.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (RiotInstallation installation : installations) {
                executor.submit(() -> addGame(progressMonitor, installation));
            }
        } finally {
            progressMonitor.done();
        }
    }

    private static void addGame(ProgressMonitor progressMonitor, RiotInstallation installation) {
        try {
            APIConnector.GameResult metadata = findMetadata(installation.title());
            RiotGame game = RiotGame.builder(
                            installation.title(),
                            metadata == null || metadata.getSummary() == null ? "" : metadata.getSummary(),
                            installation.productId(),
                            installation.patchline(),
                            installation.installLocation().toString(),
                            installation.launcherExecutable().toString()
                    )
                    .images(
                            metadata == null ? Utils.PLACEHOLDER_COVER_URL : metadata.getThumbCoverURL(),
                            metadata == null ? Utils.PLACEHOLDER_COVER_URL : metadata.getCoverURL()
                    )
                    .igdbGameId(metadata == null ? null : metadata.getIgdbGameId())
                    .nickname(installation.title())
                    .build();

            GameDashboardApp.LOGGER.info(
                    "Found Riot Games installation '{}' ({}/{}) at {}",
                    installation.title(),
                    installation.productId(),
                    installation.patchline(),
                    installation.installLocation()
            );
            Utils.runOnFxThread(() -> {
                if (!Database.getInstance().addGame(game)) {
                    Database.getInstance().updateGame(game, game);
                }
            });
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.error("Failed to load Riot Games title '{}'", installation.title(), exception);
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static APIConnector.GameResult findMetadata(String title) {
        try {
            return APIConnector.findBestFuzzyGameMatch(title, true, true)
                    .join()
                    .gameResult();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to load metadata for Riot title '{}'", title, exception);
            return null;
        }
    }

    private static List<RiotInstallation> discover(Path riotDataDirectory) {
        if (riotDataDirectory == null || !Files.isDirectory(riotDataDirectory))
            return List.of();

        Path metadataDirectory = resolveMetadataDirectory(riotDataDirectory);
        if (!Files.isDirectory(metadataDirectory))
            return List.of();

        RiotClientConfig clientConfig = readClientConfig(riotDataDirectory);
        Map<String, RiotInstallation> installations = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(metadataDirectory, 3)) {
            files.filter(RiotPlatform::isProductSettingsFile)
                    .map(path -> createInstallation(path, clientConfig))
                    .flatMap(Optional::stream)
                    .forEach(installation -> installations.putIfAbsent(
                            installation.productId() + ':' + installation.patchline(),
                            installation
                    ));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read Riot Games metadata at {}", metadataDirectory, exception);
        }

        return installations.values().stream()
                .sorted(Comparator.comparing(RiotInstallation::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Optional<RiotInstallation> createInstallation(
            Path settingsFile,
            RiotClientConfig clientConfig
    ) {
        Map<String, String> settings = readYamlSettings(settingsFile);
        String folderName = settingsFile.getParent().getFileName().toString();
        ProductIdentity identity = productIdentity(folderName);
        String productId = Utils.firstNonBlank(
                settings.get("product_id"),
                identity.productId(),
                settings.get("product_name")
        );
        String patchline = Utils.firstNonBlank(
                settings.get("product_patchline"),
                settings.get("patchline"),
                identity.patchline(),
                "live"
        );
        if (productId.isBlank() || productId.equalsIgnoreCase("riot_client"))
            return Optional.empty();

        Path installLocation = toPath(settings.get("product_install_full_path"));
        Path productExecutable = toPath(settings.get("product_launch_full_path"));
        if (installLocation == null && productExecutable != null) {
            installLocation = productExecutable.getParent();
        }

        if (installLocation == null || !Files.isDirectory(installLocation))
            return Optional.empty();

        Path launcherExecutable = clientConfig.findLauncher(productExecutable).orElse(null);
        if (launcherExecutable == null || !Files.isRegularFile(launcherExecutable)) {
            GameDashboardApp.LOGGER.warn("Riot title '{}' has no readable Riot Client executable", productId);
            return Optional.empty();
        }

        String title = Utils.firstNonBlank(
                settings.get("product_display_name"),
                knownTitle(productId),
                humanize(settings.get("product_name")),
                humanize(productId)
        );
        return Optional.of(new RiotInstallation(
                productId,
                patchline,
                title,
                installLocation,
                launcherExecutable
        ));
    }

    private static RiotClientConfig readClientConfig(Path riotDataDirectory) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(riotDataDirectory.resolve("RiotClientInstalls.json"));
        Path metadataParent = resolveMetadataDirectory(riotDataDirectory).getParent();
        if (metadataParent != null) {
            candidates.add(metadataParent.resolve("RiotClientInstalls.json"));
        }

        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate))
                continue;

            try {
                JsonObject root = GSON.fromJson(Files.readString(candidate), JsonObject.class);
                if (root == null)
                    continue;

                List<Path> launchers = new ArrayList<>();
                addJsonPath(root, "rc_live", launchers);
                addJsonPath(root, "rc_default", launchers);
                Map<Path, Path> associatedClients = new LinkedHashMap<>();
                if (root.has("associated_client") && root.get("associated_client").isJsonObject()) {
                    root.getAsJsonObject("associated_client").entrySet().forEach(entry -> {
                        Path product = toPath(entry.getKey());
                        Path launcher = entry.getValue().isJsonPrimitive()
                                ? toPath(entry.getValue().getAsString())
                                : null;
                        if (product != null && launcher != null) {
                            associatedClients.put(product, launcher);
                            launchers.add(launcher);
                        }
                    });
                }

                return new RiotClientConfig(launchers, associatedClients);
            } catch (IOException | RuntimeException exception) {
                GameDashboardApp.LOGGER.warn("Unable to read Riot client configuration at {}", candidate, exception);
            }
        }
        return new RiotClientConfig(List.of(), Map.of());
    }

    private static void addJsonPath(JsonObject object, String memberName, List<Path> paths) {
        if (!object.has(memberName) || !object.get(memberName).isJsonPrimitive())
            return;

        Path path = toPath(object.get(memberName).getAsString());
        if (path != null) {
            paths.add(path);
        }
    }

    private static Map<String, String> readYamlSettings(Path file) {
        Map<String, String> settings = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(file)) {
            Object document = YAML.load(input);
            if (!(document instanceof Map<?, ?> values))
                return settings;

            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() instanceof String key && isScalar(entry.getValue())) {
                    settings.put(
                            key.toLowerCase(Locale.ROOT),
                            entry.getValue() == null ? "" : entry.getValue().toString()
                    );
                }
            }
        } catch (IOException | RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to read Riot product settings at {}", file, exception);
        }

        return settings;
    }

    private static Yaml createYamlParser() {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        return new Yaml(new SafeConstructor(options));
    }

    private static boolean isScalar(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private static ProductIdentity productIdentity(String folderName) {
        int separator = folderName.lastIndexOf('.');
        if (separator <= 0 || separator == folderName.length() - 1)
            return new ProductIdentity(folderName, "live");

        return new ProductIdentity(folderName.substring(0, separator), folderName.substring(separator + 1));
    }

    private static String knownTitle(String productId) {
        return switch (productId.toLowerCase(Locale.ROOT)) {
            case "league_of_legends" -> "League of Legends";
            case "valorant" -> "VALORANT";
            case "bacon" -> "Legends of Runeterra";
            default -> "";
        };
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank())
            return "";

        String[] words = value.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank())
                continue;

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return result.toString();
    }

    private static Path getDefaultDataDirectory() {
        if (OperatingSystem.getCurrent() != OperatingSystem.WINDOWS)
            return null;

        String programData = System.getenv("PROGRAMDATA");
        return Path.of(programData == null || programData.isBlank() ? "C:\\ProgramData" : programData, "Riot Games");
    }

    private static Path resolveMetadataDirectory(Path selectedDirectory) {
        return selectedDirectory.getFileName() != null
                && selectedDirectory.getFileName().toString().equalsIgnoreCase("Metadata")
                ? selectedDirectory
                : selectedDirectory.resolve("Metadata");
    }

    private static boolean isValidDataDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(resolveMetadataDirectory(directory)))
            return false;

        try (Stream<Path> files = Files.walk(resolveMetadataDirectory(directory), 3)) {
            return files.anyMatch(RiotPlatform::isProductSettingsFile);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isValidDataDirectory(String directory) {
        if (directory == null || directory.isBlank())
            return false;

        try {
            return isValidDataDirectory(Utils.toPathOrNull(directory));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isProductSettingsFile(Path file) {
        return Files.isRegularFile(file)
                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SETTINGS_SUFFIX);
    }

    private static Path toPath(String value) {
        if (value == null || value.isBlank())
            return null;

        try {
            return Utils.toPathOrNull(value.replace('/', '\\'));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private record ProductIdentity(String productId, String patchline) {
    }

    private record RiotInstallation(
            String productId,
            String patchline,
            String title,
            Path installLocation,
            Path launcherExecutable
    ) {
    }

    private record RiotClientConfig(List<Path> launchers, Map<Path, Path> associatedClients) {
        private Optional<Path> findLauncher(Path productExecutable) {
            if (productExecutable != null) {
                Path normalizedProduct = productExecutable.toAbsolutePath().normalize();
                Optional<Path> associated = this.associatedClients.entrySet().stream()
                        .filter(entry -> entry.getKey().toAbsolutePath().normalize().toString()
                                .equalsIgnoreCase(normalizedProduct.toString()))
                        .map(Map.Entry::getValue)
                        .filter(Files::isRegularFile)
                        .findFirst();
                if (associated.isPresent())
                    return associated;
            }

            return this.launchers.stream().filter(Files::isRegularFile).findFirst();
        }
    }
}
