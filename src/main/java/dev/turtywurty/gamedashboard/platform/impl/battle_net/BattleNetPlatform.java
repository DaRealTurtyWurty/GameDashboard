package dev.turtywurty.gamedashboard.platform.impl.battle_net;

import com.google.gson.JsonObject;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.BattleNetGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class BattleNetPlatform implements Platform {
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";
    private static final Set<String> NON_GAME_PRODUCT_CODES = Set.of("agent", "bna");

    @Override
    public Image getIcon() {
        return new Image(getClass().getResource("/images/platforms/battle_net.png").toExternalForm());
    }

    @Override
    public String getName() {
        return "Battle.net";
    }

    @Override
    public String getWebsite() {
        return "https://battle.net/";
    }

    @Override
    public String getDescription() {
        return "Battle.net is Blizzard Entertainment's desktop launcher for games like World of Warcraft, Diablo, Overwatch, Hearthstone, and StarCraft.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding Battle.net games", 1);
            addBattleNetGames(progressMonitor);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose Battle.net's Agent product.db file. Installed games are read from this database, so custom install locations are supported.",
                null,
                false,
                8
        );

        Path defaultProductDbPath = getDefaultProductDbPath();
        var productDbField = form.addFileField(
                "Battle.net product.db",
                defaultProductDbPath == null || !Files.isRegularFile(defaultProductDbPath)
                        ? ""
                        : defaultProductDbPath.toString(),
                defaultProductDbPath == null ? "Battle.net product.db" : defaultProductDbPath.toString(),
                "Select Battle.net product.db",
                new FileChooser.ExtensionFilter("Battle.net product database", "product.db", "*.db")
        );

        return form.build(progressMonitor -> {
            String productDbPath = productDbField.getText();
            if (!form.validate(
                    productDbField,
                    BattleNetPlatform::isValidProductDbPath,
                    "Select a valid Battle.net product.db file."
            ))
                return;

            form.hideError();
            ManualEntryForm.runAsync(
                    progressMonitor,
                    () -> addBattleNetGames(progressMonitor, Path.of(productDbPath))
            );
        });
    }

    private static Path getDefaultProductDbPath() {
        return switch (OperatingSystem.getCurrent()) {
            case WINDOWS -> Path.of(getProgramDataPath(), "Battle.net", "Agent", "product.db");
            case MACOS -> Path.of("/Users/Shared/Battle.net/Agent/product.db");
            default -> null;
        };
    }

    private static String getProgramDataPath() {
        String programData = System.getenv("PROGRAMDATA");
        return programData == null || programData.isBlank() ? "C:\\ProgramData" : programData;
    }

    private static boolean isValidProductDbPath(String value) {
        if (value == null || value.isBlank())
            return false;

        try {
            return Files.isRegularFile(Path.of(value));
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private static void addBattleNetGames(ProgressMonitor progressMonitor) {
        addBattleNetGames(progressMonitor, getDefaultProductDbPath());
    }

    private static void addBattleNetGames(ProgressMonitor progressMonitor, Path productDbPath) {
        progressMonitor.start("Reading Battle.net product database", 1);

        List<BattleNetInstallation> installations = discover(productDbPath);
        progressMonitor.worked(1);

        if (installations.isEmpty()) {
            progressMonitor.done();
            return;
        }

        progressMonitor.start("Loading Battle.net games", installations.size());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (BattleNetInstallation installation : installations) {
                executor.submit(() -> addBattleNetGame(progressMonitor, installation));
            }
        } finally {
            progressMonitor.done();
        }
    }

    private static void addBattleNetGame(
            ProgressMonitor progressMonitor,
            BattleNetInstallation installation
    ) {
        try {
            APIConnector.GameResult metadata = APIConnector.findBestFuzzyGameMatch(
                    installation.title(),
                    true,
                    true
            ).join().gameResult();
            String thumbnail = firstNonBlank(
                    resourceUrl(installation.icon()).orElse(null),
                    metadata == null ? null : metadata.getThumbCoverURL(),
                    PLACEHOLDER_COVER_URL
            );
            String cover = firstNonBlank(
                    resourceUrl(installation.coverArt()).orElse(null),
                    resourceUrl(installation.installBackground()).orElse(null),
                    resourceUrl(installation.background()).orElse(null),
                    metadata == null ? null : metadata.getCoverURL(),
                    PLACEHOLDER_COVER_URL
            );

            BattleNetGame game = BattleNetGame.builder(
                            installation.title(),
                            metadata == null || metadata.getSummary() == null ? "" : metadata.getSummary(),
                            getExecutionCommand(installation.productCode()),
                            installation.productCode(),
                            installation.uid(),
                            installation.installPath().toString()
                    )
                    .images(thumbnail, cover)
                    .igdbGameId(metadata == null ? null : metadata.getIgdbGameId())
                    .nickname(installation.title())
                    .build();
            resourceUrl(installation.logo()).ifPresent(game::setCoverLogoImageURL);

            javafx.application.Platform.runLater(() -> {
                if (!Database.getInstance().addGame(game))
                    Database.getInstance().updateGame(game, game);
            });
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.error(
                    "Failed to load Battle.net game '{}'",
                    installation.title(),
                    exception
            );
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static String getExecutionCommand(String productCode) {
        return "battle.net://launch/" + productCode;
    }

    private static List<BattleNetInstallation> discover(Path productDbPath) {
        if (productDbPath == null || !Files.isRegularFile(productDbPath))
            return Collections.emptyList();

        try {
            BattleNetProductDbParser.Database db = BattleNetProductDbParser.parseProductDb(productDbPath);
            return db.productInstall.stream()
                    .filter(BattleNetPlatform::isGameInstall)
                    .map(installation -> toInstallation(productDbPath, installation))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(BattleNetInstallation::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException | RuntimeException exception) {
            GameDashboardApp.LOGGER.error("Failed to parse Battle.net product database: {}", productDbPath, exception);
            return Collections.emptyList();
        }
    }

    private static boolean isGameInstall(BattleNetProductDbParser.ProductInstall installation) {
        if (installation == null || Boolean.TRUE.equals(installation.hidden))
            return false;

        String productCode = firstNonBlank(installation.productCode, installation.uid);
        return productCode != null && !NON_GAME_PRODUCT_CODES.contains(productCode.toLowerCase(Locale.ROOT));
    }

    private static Optional<BattleNetInstallation> toInstallation(Path productDbPath, BattleNetProductDbParser.ProductInstall installation) {
        Path battleNetCachePath = switch (OperatingSystem.getCurrent()) {
            case WINDOWS -> {
                String localAppData = System.getenv("LOCALAPPDATA");
                yield localAppData == null || localAppData.isBlank()
                        ? null
                        : Path.of(localAppData, "Battle.net", "Cache");
            }
            case MACOS -> Path.of(System.getProperty("HOME"), "Library", "Caches", "Battle.net");
            default -> null;
        };

        Path agentCachePath = productDbPath != null
                ? productDbPath.getParent().resolve("data").resolve("cache")
                : null;
        String productCode = firstNonBlank(installation.productCode, installation.uid);
        if (productCode == null)
            return Optional.empty();

        BattleNetNGDPTableParser.Table table = new BattleNetNGDPTableParser.Table(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
        if (agentCachePath != null && Files.isDirectory(agentCachePath)) {
            try {
                Optional<Path> versionFile = findNewestVersionFile(agentCachePath, productCode);
                if (versionFile.isPresent())
                    table = BattleNetNGDPTableParser.parse(versionFile.get());
            } catch (IOException exception) {
                GameDashboardApp.LOGGER.error("Failed to read Battle.net agent cache for product code: {}", productCode, exception);
            }
        }

        String region = installation.settings == null ? null : installation.settings.playRegion;
        String language = installation.settings == null ? null : installation.settings.selectedTextLanguage;

        String productConfig = findProductConfig(table, region).orElse(null);
        if (productConfig == null) {
            GameDashboardApp.LOGGER.warn("Failed to find ProductConfig for product code: {} in region: {}", productCode, region);
            return Optional.empty();
        }

        Path jsonPath = agentCachePath != null
                ? cachePathFromHash(agentCachePath, productConfig)
                : null;
        if (jsonPath == null || Files.notExists(jsonPath) || !Files.isRegularFile(jsonPath)) {
            GameDashboardApp.LOGGER.warn("Failed to find product config JSON for product code: {} at path: {}", productCode, jsonPath);
            return Optional.empty();
        }

        Map<String, BattleNetProductConfigParser.Section> config;
        try {
            config = BattleNetProductConfigParser.parse(jsonPath);
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to parse product config JSON for product code: {} at path: {}", productCode, jsonPath, exception);
            return Optional.empty();
        }

        String displayName = findDisplayName(config, language).orElse(null);

        if (displayName == null || displayName.isBlank()) {
            BattleNetProductConfigParser.Section all = config.get("all");
            if (all != null && all.config() != null && all.config().form() != null && all.config().form().gameDir() != null)
                displayName = all.config().form().gameDir().dirname();
        }

        if (displayName == null || displayName.isBlank()) {
            displayName = installFolderName(installation).orElse(null);
        }

        if (displayName == null || displayName.isBlank()) {
            GameDashboardApp.LOGGER.warn("Failed to determine display name for product code: {} at path: {}", productCode, jsonPath);
            return Optional.empty();
        }

        ResolvedCatalogResources resources = resolveCatalogResources(productCode, installation.uid, language, battleNetCachePath);
        Optional<Path> installPath = installPath(installation);
        if (installPath.isEmpty())
            return Optional.empty();

        return Optional.of(new BattleNetInstallation(
                displayName,
                productCode,
                installation.uid,
                installPath.get(),
                versionString(installation),
                resources.background(),
                resources.installBackground(),
                resources.coverArt(),
                resources.logo(),
                resources.icon()
        ));
    }

    private static Optional<Path> findNewestVersionFile(Path agentCachePath, String productCode) throws IOException {
        String prefix = "version-" + productCode + "-";
        try (Stream<Path> files = Files.list(agentCachePath)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .max(Comparator.comparingLong(BattleNetPlatform::sequenceFromCacheFileName));
        }
    }

    private static long sequenceFromCacheFileName(Path path) {
        String name = path.getFileName().toString();
        int dash = name.lastIndexOf('-');
        if (dash < 0 || dash == name.length() - 1)
            return -1;

        try {
            return Long.parseLong(name.substring(dash + 1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static Optional<String> findProductConfig(BattleNetNGDPTableParser.Table table, String region) {
        if (table == null || table.rows().isEmpty())
            return Optional.empty();

        return Stream.of(region, "us", "eu", "kr")
                .filter(Objects::nonNull)
                .map(candidate -> queryProductConfig(table, candidate))
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> table.rows().stream()
                        .map(row -> row.get("ProductConfig"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(value -> !value.isBlank())
                        .findFirst());
    }

    private static Optional<String> queryProductConfig(BattleNetNGDPTableParser.Table table, String region) {
        try {
            return table.querySql("SELECT ProductConfig FROM ngdp WHERE Region = ?", region)
                    .firstValue(String.class)
                    .filter(value -> !value.isBlank());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> findDisplayName(Map<String, BattleNetProductConfigParser.Section> config, String language) {
        for (String locale : productConfigLocaleOrder(language)) {
            BattleNetProductConfigParser.Section section = config.get(locale);
            Optional<String> displayName = displayNameFromSection(section);
            if (displayName.isPresent())
                return displayName;
        }

        for (BattleNetProductConfigParser.Section section : config.values()) {
            Optional<String> displayName = displayNameFromSection(section);
            if (displayName.isPresent())
                return displayName;
        }

        return Optional.empty();
    }

    private static List<String> productConfigLocaleOrder(String language) {
        List<String> locales = new ArrayList<>();
        String normalized = normalizeProductConfigLocale(language);
        if (normalized != null)
            locales.add(normalized);

        locales.addAll(List.of("enus", "engb", "esmx", "ptbr", "ptpt", "frfr", "dede", "eses", "itit", "ruru", "jajp", "kokr", "plpl", "trtr", "zhtw"));
        return locales.stream().distinct().toList();
    }

    private static Optional<String> displayNameFromSection(BattleNetProductConfigParser.Section section) {
        if (section == null || section.config() == null || section.config().install() == null)
            return Optional.empty();

        for (JsonObject installElement : section.config().install()) {
            if (installElement == null || !installElement.has("add_remove_programs_key"))
                continue;

            JsonObject addRemovePrograms = installElement.getAsJsonObject("add_remove_programs_key");
            if (addRemovePrograms == null || !addRemovePrograms.has("display_name"))
                continue;

            String displayName = addRemovePrograms.get("display_name").getAsString();
            if (displayName != null && !displayName.isBlank())
                return Optional.of(displayName);
        }

        return Optional.empty();
    }

    private static Optional<String> installFolderName(BattleNetProductDbParser.ProductInstall installation) {
        if (installation.settings == null || installation.settings.installPath == null || installation.settings.installPath.isBlank())
            return Optional.empty();

        try {
            Path fileName = Path.of(installation.settings.installPath).getFileName();
            return fileName == null ? Optional.empty() : Optional.of(fileName.toString());
        } catch (InvalidPathException exception) {
            String[] folderSplit = installation.settings.installPath.split("[/\\\\]");
            return folderSplit.length == 0 ? Optional.empty() : Optional.of(folderSplit[folderSplit.length - 1]);
        }
    }

    private static Optional<Path> installPath(BattleNetProductDbParser.ProductInstall installation) {
        if (installation.settings == null || installation.settings.installPath == null || installation.settings.installPath.isBlank())
            return Optional.empty();

        try {
            Path path = Path.of(installation.settings.installPath).normalize();
            return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private static String versionString(BattleNetProductDbParser.ProductInstall installation) {
        if (installation.cachedProductState == null || installation.cachedProductState.baseProductState == null)
            return "";

        return firstNonBlank(
                installation.cachedProductState.baseProductState.currentVersionStr,
                installation.cachedProductState.baseProductState.currentVersion,
                ""
        );
    }

    private static ResolvedCatalogResources resolveCatalogResources(String productCode, String uid, String language, Path battleNetCachePath) {
        if (battleNetCachePath == null || !Files.isDirectory(battleNetCachePath))
            return ResolvedCatalogResources.EMPTY;

        try (Stream<Path> files = Files.walk(battleNetCachePath, 3)) {
            Optional<CatalogMatch> match = files.filter(Files::isRegularFile)
                    .map(path -> readCatalogMatch(path, productCode, uid))
                    .flatMap(Optional::stream)
                    .findFirst();

            if (match.isEmpty())
                return ResolvedCatalogResources.EMPTY;

            BattleNetCatalogParser.BaseProduct base = match.get().base();
            BattleNetCatalogParser.Catalog catalog = match.get().catalog();
            return new ResolvedCatalogResources(
                    resolvedResource(catalog, base.background(), language, battleNetCachePath),
                    resolvedResource(catalog, base.installBackground(), language, battleNetCachePath),
                    resolvedResource(catalog, base.keyArt(), language, battleNetCachePath),
                    resolvedResource(catalog, base.logo(), language, battleNetCachePath),
                    resolvedResource(catalog, base.iconMedium(), language, battleNetCachePath)
            );
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.debug("Failed to scan Battle.net catalog cache at {}", battleNetCachePath, exception);
            return ResolvedCatalogResources.EMPTY;
        }
    }

    private static Optional<CatalogMatch> readCatalogMatch(Path path, String productCode, String uid) {
        try {
            String json = Files.readString(path);
            if (json.isBlank() || json.charAt(0) != '{' || !json.contains("\"products\"") || !json.contains("\"installs\""))
                return Optional.empty();

            BattleNetCatalogParser.Catalog catalog = BattleNetCatalogParser.parse(json);
            return findCatalogMatch(catalog, productCode, uid);
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<CatalogMatch> findCatalogMatch(BattleNetCatalogParser.Catalog catalog, String productCode, String uid) {
        if (catalog.installs() == null || catalog.products() == null)
            return Optional.empty();

        Set<String> installUids = new HashSet<>();
        for (Map.Entry<String, BattleNetCatalogParser.Install> entry : catalog.installs().entrySet()) {
            BattleNetCatalogParser.Install install = entry.getValue();
            if (install == null)
                continue;

            if (equalsIgnoreCase(install.tactProduct(), productCode) || equalsIgnoreCase(entry.getKey(), uid))
                installUids.add(entry.getKey());
        }

        if (installUids.isEmpty())
            return Optional.empty();

        for (BattleNetCatalogParser.Product product : catalog.products()) {
            if (product == null || product.base() == null || product.base().types() == null)
                continue;

            for (BattleNetCatalogParser.ProductType type : product.base().types().values()) {
                if (type != null && installUids.stream().anyMatch(candidate -> equalsIgnoreCase(candidate, type.uid())))
                    return Optional.of(new CatalogMatch(catalog, product.base()));
            }
        }

        return catalog.products().stream()
                .filter(product -> product != null && product.base() != null)
                .findFirst()
                .map(product -> new CatalogMatch(catalog, product.base()));
    }

    private static Optional<ResolvedResource> resolvedResource(BattleNetCatalogParser.Catalog catalog, String resourceKey, String locale, Path cacheDir) {
        if (catalog.files() == null || resourceKey == null || resourceKey.isBlank())
            return Optional.empty();

        BattleNetCatalogParser.FileEntry fileEntry = null;

        Map<String, BattleNetCatalogParser.FileEntry> localized =
                catalog.files().get(normalizeCatalogLocale(locale));
        if (localized != null) {
            fileEntry = localized.get(resourceKey);
        }

        if (fileEntry == null) {
            Map<String, BattleNetCatalogParser.FileEntry> defaults = catalog.files().get("default");
            if (defaults != null) {
                fileEntry = defaults.get(resourceKey);
            }
        }

        if (fileEntry == null)
            return Optional.empty();

        Path cachePath = cachePathFromHash(cacheDir, fileEntry.hash());
        if (!Files.isRegularFile(cachePath))
            return Optional.empty();

        return Optional.of(new ResolvedResource(resourceKey, fileEntry.hash(), fileEntry.name(), cachePath));
    }

    private static Path cachePathFromHash(Path cacheDir, String hash) {
        if (hash == null || hash.length() < 4)
            throw new IllegalArgumentException("Invalid Battle.net cache hash: " + hash);

        return cacheDir
                .resolve(hash.substring(0, 2))
                .resolve(hash.substring(2, 4))
                .resolve(hash);
    }

    private static String normalizeCatalogLocale(String locale) {
        if (locale == null || locale.isBlank())
            return "default";

        String cleaned = locale
                .replace("-", "")
                .replace("_", "")
                .trim();

        if (cleaned.equalsIgnoreCase("default"))
            return "default";

        if (cleaned.length() != 4)
            return cleaned;

        String language = cleaned.substring(0, 2).toLowerCase();
        String region = cleaned.substring(2, 4).toUpperCase();

        return language + region;
    }

    private static String normalizeProductConfigLocale(String locale) {
        if (locale == null || locale.isBlank())
            return null;

        return locale
                .replace("-", "")
                .replace("_", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && first.equalsIgnoreCase(second);
    }

    private static Optional<String> resourceUrl(Optional<ResolvedResource> resource) {
        return resource
                .filter(resolvedResource -> Files.isRegularFile(resolvedResource.cachePath()))
                .map(resolvedResource -> resolvedResource.cachePath().toUri().toString());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value;
        }

        return null;
    }

    private record CatalogMatch(BattleNetCatalogParser.Catalog catalog, BattleNetCatalogParser.BaseProduct base) {
    }

    private record ResolvedCatalogResources(
            Optional<ResolvedResource> background,
            Optional<ResolvedResource> installBackground,
            Optional<ResolvedResource> coverArt,
            Optional<ResolvedResource> logo,
            Optional<ResolvedResource> icon
    ) {
        private static final ResolvedCatalogResources EMPTY = new ResolvedCatalogResources(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private record BattleNetInstallation(
            String title,
            String productCode,
            String uid,
            Path installPath,
            String version,
            Optional<ResolvedResource> background,
            Optional<ResolvedResource> installBackground,
            Optional<ResolvedResource> coverArt,
            Optional<ResolvedResource> logo,
            Optional<ResolvedResource> icon
    ) {
    }

    public record ResolvedResource(String resourceKey, String hash, String fileName, Path cachePath) {
    }
}
