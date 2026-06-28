package dev.turtywurty.gamedashboard.platform.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.impl.MicrosoftStoreGame;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.FileSystemsHolder;
import dev.turtywurty.gamedashboard.util.OSUtils;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.scene.image.Image;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"ConstantValue", "SameParameterValue", "SpellCheckingInspection"})
public final class MicrosoftStorePlatform implements Platform {
    private static final Gson GSON = new Gson();
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";

    @Override
    public Image getIcon() {
        return new Image(Objects.requireNonNull(
                getClass().getResource("/images/platforms/microsoft_store.png")
        ).toExternalForm());
    }

    @Override
    public String getName() {
        return "Microsoft Store / Xbox";
    }

    @Override
    public String getWebsite() {
        return "https://www.xbox.com/";
    }

    @Override
    public String getDescription() {
        return "Discovers Microsoft Store and Xbox app games from Windows package metadata, Gaming Services, and MicrosoftGame.config manifests.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS) {
                progressMonitor.start("Microsoft Store discovery is only available on Windows", 1);
                progressMonitor.worked(1);
                progressMonitor.done();
                return;
            }

            addGames(progressMonitor, defaultXboxGameRoots());
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose an XboxGames directory to scan. Automatic discovery scans every drive root for XboxGames folders.",
                "microsoft-store",
                false,
                0
        );

        Path defaultRoot = defaultXboxGameRoots().stream().findFirst().orElse(Path.of("C:\\XboxGames"));
        var rootField = form.addDirectoryField(
                "XboxGames directory",
                Files.isDirectory(defaultRoot) ? defaultRoot.toString() : "",
                defaultRoot.toString(),
                "Select XboxGames Directory",
                false
        );

        return form.build(progressMonitor -> {
            if (!form.validate(rootField, MicrosoftStorePlatform::isReadableDirectory, "Select a readable XboxGames directory."))
                return;

            form.hideError();
            ManualEntryForm.runAsync(
                    progressMonitor,
                    () -> addGames(progressMonitor, List.of(Path.of(rootField.getText())))
            );
        });
    }

    private static void addGames(ProgressMonitor progressMonitor, List<Path> xboxGameRoots) {
        progressMonitor.start("Reading Microsoft Store package metadata", 2);
        Map<String, AppxPackage> packagesByIdentity = readAppxPackages();
        progressMonitor.worked(1);

        List<MicrosoftStoreInstallation> installations = discover(xboxGameRoots, packagesByIdentity);
        progressMonitor.worked(1);
        if (installations.isEmpty()) {
            progressMonitor.done();
            return;
        }

        progressMonitor.start("Loading Microsoft Store games", installations.size());
        for (MicrosoftStoreInstallation installation : installations) {
            addGame(progressMonitor, installation);
        }
        progressMonitor.done();
    }

    private static void addGame(ProgressMonitor progressMonitor, MicrosoftStoreInstallation installation) {
        try {
            APIConnector.GameResult metadata = findMetadata(installation.displayName());
            MicrosoftStoreGame game = MicrosoftStoreGame.builder(
                            installation.displayName(),
                            metadata == null || metadata.getSummary() == null ? installation.description() : metadata.getSummary(),
                            installation.launchId(),
                            installation.packageFamilyName(),
                            installation.packageFullName()
                    )
                    .storeId(installation.storeId())
                    .titleId(installation.titleId())
                    .installRoot(installation.installRoot() == null ? null : installation.installRoot().toString())
                    .contentRoot(installation.contentRoot() == null ? null : installation.contentRoot().toString())
                    .images(
                            firstNonBlank(metadata == null ? null : metadata.getThumbCoverURL(), toImageUrl(installation.smallLogo()), PLACEHOLDER_COVER_URL),
                            firstNonBlank(metadata == null ? null : metadata.getCoverURL(), toImageUrl(installation.coverImage()), PLACEHOLDER_COVER_URL),
                            null
                    )
                    .igdbGameId(metadata == null ? null : metadata.getIgdbGameId())
                    .nickname(installation.displayName())
                    .build();

            GameDashboardApp.LOGGER.info(
                    "Found Microsoft Store/Xbox game '{}' ({}) at {}",
                    installation.displayName(),
                    installation.packageFamilyName(),
                    installation.installRoot()
            );
            javafx.application.Platform.runLater(() -> {
                if (!Database.getInstance().addGame(game)) {
                    Database.getInstance().updateGame(game, game);
                }
            });
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.error("Failed to load Microsoft Store/Xbox title '{}'", installation.displayName(), exception);
        } finally {
            progressMonitor.worked(1);
        }
    }

    private static List<MicrosoftStoreInstallation> discover(
            List<Path> xboxGameRoots,
            Map<String, AppxPackage> packagesByIdentity
    ) {
        Map<String, MicrosoftStoreInstallation> installations = new LinkedHashMap<>();
        for (Path configFile : discoverGameConfigFiles(xboxGameRoots)) {
            createGameConfigInstallation(configFile, packagesByIdentity)
                    .ifPresent(installation -> installations.putIfAbsent(installation.packageFamilyName(), installation));
        }

        Set<String> knownPackageFullNames = new HashSet<>();
        installations.values().stream()
                .map(MicrosoftStoreInstallation::packageFullName)
                .filter(Objects::nonNull)
                .map(MicrosoftStorePlatform::normalizePackageFullName)
                .forEach(knownPackageFullNames::add);

        packagesByIdentity.values().stream()
                .filter(AppxPackage::inGamingRepository)
                .filter(appxPackage -> !knownPackageFullNames.contains(normalizePackageFullName(appxPackage.packageFullName())))
                .map(MicrosoftStorePlatform::createAppxInstallation)
                .flatMap(Optional::stream)
                .forEach(installation -> installations.putIfAbsent(installation.packageFamilyName(), installation));

        return installations.values().stream()
                .filter(installation -> !isExcludedInstallation(installation))
                .sorted(Comparator.comparing(MicrosoftStoreInstallation::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean isExcludedInstallation(MicrosoftStoreInstallation installation) {
        return equalsIgnoreCase(installation.displayName(), "Minecraft Launcher")
                || startsWithIgnoreCase(installation.packageFamilyName(), "Microsoft.4297127D64EC6_")
                || startsWithIgnoreCase(installation.packageFullName(), "Microsoft.4297127D64EC6_")
                || equalsIgnoreCase(installation.storeId(), "9PGW18NPBZV5");
    }

    private static Optional<MicrosoftStoreInstallation> createGameConfigInstallation(
            Path configFile,
            Map<String, AppxPackage> packagesByIdentity
    ) {
        try {
            Document document = readXml(configFile);
            Element identity = firstElement(document, "Identity").orElse(null);
            Element shellVisuals = firstElement(document, "ShellVisuals").orElse(null);
            Element executable = firstElement(document, "Executable").orElse(null);
            if (identity == null || shellVisuals == null)
                return Optional.empty();

            String identityName = identity.getAttribute("Name");
            AppxPackage appxPackage = packagesByIdentity.get(identityName.toLowerCase(Locale.ROOT));
            if (appxPackage == null)
                return Optional.empty();

            String appId = firstNonBlank(attribute(executable, "Id"), appxPackage.appId(), "App");
            Path contentRoot = configFile.getParent();
            Path installRoot = contentRoot == null ? null : contentRoot.getParent();
            String displayName = cleanDisplayName(
                    firstNonBlank(attribute(shellVisuals, "DefaultDisplayName"), appxPackage.displayName()),
                    humanizePackageName(identityName)
            );
            String description = firstNonBlank(attribute(shellVisuals, "Description"), appxPackage.description(), "");
            return Optional.of(new MicrosoftStoreInstallation(
                    displayName,
                    description,
                    appxPackage.packageFamilyName(),
                    appxPackage.packageFullName(),
                    text(document, "StoreId"),
                    firstNonBlank(text(document, "TitleId"), appxPackage.titleId()),
                    appxPackage.packageFamilyName() + "!" + appId,
                    installRoot,
                    contentRoot,
                    resolveAsset(contentRoot, attribute(shellVisuals, "Square44x44Logo")),
                    resolveAsset(contentRoot, firstNonBlank(
                            attribute(shellVisuals, "SplashScreenImage"),
                            attribute(shellVisuals, "Square150x150Logo")
                    )),
                    resolveAsset(contentRoot, firstNonBlank(
                            attribute(shellVisuals, "Square480x480Logo"),
                            attribute(shellVisuals, "StoreLogo"),
                            attribute(shellVisuals, "Square150x150Logo")
                    ))
            ));
        } catch (IOException | ParserConfigurationException | SAXException | RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to read MicrosoftGame.config at {}", configFile, exception);
            return Optional.empty();
        }
    }

    private static Optional<MicrosoftStoreInstallation> createAppxInstallation(AppxPackage appxPackage) {
        if (appxPackage.packageFamilyName() == null || appxPackage.launchId() == null)
            return Optional.empty();

        Path installLocation = toPath(appxPackage.installLocation());
        if (installLocation == null || !Files.isDirectory(installLocation))
            return Optional.empty();

        String displayName = cleanDisplayName(appxPackage.displayName(), humanizePackageName(appxPackage.name()));

        String description = firstNonBlank(appxPackage.description(), displayName);
        return Optional.of(new MicrosoftStoreInstallation(
                displayName,
                description,
                appxPackage.packageFamilyName(),
                appxPackage.packageFullName(),
                appxPackage.storeId(),
                appxPackage.titleId(),
                appxPackage.launchId(),
                installLocation,
                installLocation,
                resolveAsset(installLocation, appxPackage.square44x44Logo()),
                resolveAsset(installLocation, appxPackage.square150x150Logo()),
                resolveAsset(installLocation, appxPackage.square150x150Logo())
        ));
    }

    private static List<Path> discoverGameConfigFiles(List<Path> roots) {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root))
                continue;

            try (Stream<Path> stream = Files.walk(root, 4)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase("MicrosoftGame.config"))
                        .forEach(files::add);
            } catch (IOException exception) {
                GameDashboardApp.LOGGER.warn("Unable to scan XboxGames root {}", root, exception);
            }
        }

        return files;
    }

    private static Map<String, AppxPackage> readAppxPackages() {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return Map.of();

        try {
            Path helper = findWindowsPackageHelper().orElse(null);
            if (helper == null) {
                GameDashboardApp.LOGGER.warn("Unable to read AppX packages: Windows package helper was not found");
                return Map.of();
            }

            Set<String> gamingPackageFullNames = readGamingPackageFullNames();
            Process process = new ProcessBuilder(helper.toString()).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                GameDashboardApp.LOGGER.warn("Windows package helper failed, exitCode={}, output={}", exitCode, output);
                return Map.of();
            }

            Map<String, AppxPackage> packages = new HashMap<>();
            JsonElement root = GSON.fromJson(output, JsonElement.class);
            JsonArray rows = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
            if (root.isJsonObject()) {
                rows.add(root);
            }

            for (JsonElement rowElement : rows) {
                if (!rowElement.isJsonObject())
                    continue;

                InstalledPackage installedPackage = InstalledPackage.fromJson(rowElement.getAsJsonObject());
                for (AppxPackage appxPackage : readAppxManifest(installedPackage, gamingPackageFullNames)) {
                    if (appxPackage.name() != null) {
                        packages.put(appxPackage.name().toLowerCase(Locale.ROOT), appxPackage);
                    }
                }
            }

            return packages;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            GameDashboardApp.LOGGER.warn("Unable to read AppX packages", exception);
            return Map.of();
        }
    }

    private static List<AppxPackage> readAppxManifest(
            InstalledPackage installedPackage,
            Set<String> gamingPackageFullNames
    ) {
        if (installedPackage.name() == null || installedPackage.installLocation() == null)
            return List.of();

        Path installLocation = toPath(installedPackage.installLocation());
        if (installLocation == null)
            return List.of();

        Path manifestPath = installLocation.resolve("AppxManifest.xml");
        if (!Files.isRegularFile(manifestPath))
            return List.of();

        try {
            Document document = readXml(manifestPath);
            NodeList applications = document.getElementsByTagName("Application");
            List<AppxPackage> packages = new ArrayList<>();
            for (int index = 0; index < applications.getLength(); index++) {
                if (!(applications.item(index) instanceof Element application))
                    continue;

                String appId = attribute(application, "Id");
                String executable = attribute(application, "Executable");
                String startPage = attribute(application, "StartPage");
                if (appId == null || firstNonBlank(executable, startPage).isBlank())
                    continue;

                Element visualElements = firstChildElement(application, "VisualElements").orElse(null);
                packages.add(new AppxPackage(
                        installedPackage.name(),
                        installedPackage.packageFamilyName(),
                        installedPackage.packageFullName(),
                        installedPackage.installLocation(),
                        appId,
                        installedPackage.packageFamilyName() + "!" + appId,
                        executable,
                        attribute(visualElements, "DisplayName"),
                        attribute(visualElements, "Description"),
                        attribute(visualElements, "Square44x44Logo"),
                        attribute(visualElements, "Square150x150Logo"),
                        gamingPackageFullNames.contains(normalizePackageFullName(installedPackage.packageFullName())),
                        null,
                        null
                ));
            }

            return packages;
        } catch (IOException | ParserConfigurationException | SAXException | RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to read AppX manifest at {}", manifestPath, exception);
            return List.of();
        }
    }

    private static Set<String> readGamingPackageFullNames() {
        return OSUtils.readRegistryEntries(
                        "HKLM\\SOFTWARE\\Microsoft\\GamingServices\\PackageRepository\\Package",
                        false
                ).stream()
                .findFirst()
                .map(OSUtils.RegistryEntry::values)
                .map(Map::keySet)
                .map(values -> values.stream()
                        .map(MicrosoftStorePlatform::normalizePackageFullName)
                        .collect(Collectors.toCollection(HashSet::new)))
                .orElseGet(HashSet::new);
    }

    private static Optional<Path> findWindowsPackageHelper() {
        String configuredPath = System.getProperty("gamedashboard.windowsPackagesHelper");
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = toPath(configuredPath);
            if (isExecutable(path))
                return Optional.of(path);
        }

        String executableName = "GameDashboard.WindowsPackages.exe";
        for (Path directory : helperSearchDirectories()) {
            Path candidate = directory.resolve(executableName);
            if (isExecutable(candidate))
                return Optional.of(candidate);
        }

        return Optional.empty();
    }

    private static List<Path> helperSearchDirectories() {
        List<Path> directories = new ArrayList<>();
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        directories.add(workingDirectory.resolve("windows-helper"));
        directories.add(workingDirectory.resolve("build").resolve("windows-helper"));
        directories.add(workingDirectory.resolve("tools").resolve("GameDashboard.WindowsPackages")
                .resolve("bin").resolve("Release").resolve("net8.0-windows10.0.19041.0").resolve("win-x64"));
        directories.add(workingDirectory.resolve("tools").resolve("GameDashboard.WindowsPackages")
                .resolve("bin").resolve("Debug").resolve("net8.0-windows10.0.19041.0").resolve("win-x64"));

        try {
            Path codeSource = Path.of(MicrosoftStorePlatform.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path base = Files.isRegularFile(codeSource) ? codeSource.getParent() : codeSource;
            if (base != null) {
                directories.add(base.resolve("windows-helper"));
                if (base.getParent() != null) {
                    directories.add(base.getParent().resolve("windows-helper"));
                }
            }
        } catch (RuntimeException | URISyntaxException ignored) {
        }

        return directories;
    }

    private static boolean isExecutable(Path path) {
        return path != null && Files.isRegularFile(path) && Files.isExecutable(path);
    }

    private static APIConnector.GameResult findMetadata(String title) {
        try {
            return APIConnector.findBestFuzzyGameMatch(title, true, true, "PC (Microsoft Windows)", null)
                    .join()
                    .gameResult();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to load metadata for Microsoft Store/Xbox title '{}'", title, exception);
            return null;
        }
    }

    private static List<Path> defaultXboxGameRoots() {
        List<Path> roots = new ArrayList<>();
        for (Path root : FileSystemsHolder.roots()) {
            Path xboxGames = root.resolve("XboxGames");
            if (Files.isDirectory(xboxGames)) {
                roots.add(xboxGames);
            }
        }

        return roots;
    }

    private static boolean isReadableDirectory(String value) {
        if (value == null || value.isBlank())
            return false;

        try {
            Path path = Path.of(value);
            return Files.isDirectory(path) && Files.isReadable(path);
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static Document readXml(Path path) throws ParserConfigurationException, IOException, SAXException {
        var factory = DocumentBuilderFactory.newInstance();
        // These are SAX feature identifiers, not fetched URLs.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(Files.newInputStream(path));
    }

    private static Optional<Element> firstElement(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element))
            return Optional.empty();

        return Optional.of(element);
    }

    private static Optional<Element> firstChildElement(Element parent, String localName) {
        if (parent == null)
            return Optional.empty();

        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child
                    && elementNameEquals(child, localName)) {
                return Optional.of(child);
            }
        }

        return Optional.empty();
    }

    private static boolean elementNameEquals(Element element, String expectedLocalName) {
        String localName = element.getLocalName();
        if (expectedLocalName.equals(localName))
            return true;

        String nodeName = element.getNodeName();
        return expectedLocalName.equals(nodeName)
                || nodeName != null && nodeName.endsWith(":" + expectedLocalName);
    }

    private static String text(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0)
            return null;

        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String attribute(Element element, String attributeName) {
        if (element == null || !element.hasAttribute(attributeName))
            return null;

        String value = element.getAttribute(attributeName);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Path resolveAsset(Path root, String relativePath) {
        if (root == null || relativePath == null || relativePath.isBlank())
            return null;

        Path resolved = root.resolve(relativePath.replace('/', '\\')).normalize();
        return Files.isRegularFile(resolved) ? resolved : null;
    }

    private static String toImageUrl(Path imagePath) {
        return imagePath == null ? null : imagePath.toUri().toString();
    }

    private static Path toPath(String value) {
        if (value == null || value.isBlank())
            return null;

        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank())
                return value;
        }

        return "";
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && prefix != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String cleanDisplayName(String displayName, String fallback) {
        String name = isResourceReference(displayName) ? fallback : firstNonBlank(displayName, fallback);
        return splitJoinedWords(name)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isResourceReference(String value) {
        return value != null && value.regionMatches(true, 0, "ms-resource:", 0, "ms-resource:".length());
    }

    private static String splitJoinedWords(String value) {
        if (value == null || value.isBlank())
            return "";

        return value.replace('-', ' ')
                .replace('_', ' ')
                .replace('.', ' ')
                .replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ")
                .replaceAll("(?<=[A-Za-z])(?=\\d)|(?<=\\d)(?=[A-Za-z])", " ");
    }

    private static String normalizePackageFullName(String packageFullName) {
        return packageFullName == null ? "" : packageFullName.toLowerCase(Locale.ROOT);
    }

    private static String humanizePackageName(String packageName) {
        if (packageName == null || packageName.isBlank())
            return "";

        int separator = packageName.lastIndexOf('.');
        String name = separator < 0 ? packageName : packageName.substring(separator + 1);
        return splitJoinedWords(name)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record MicrosoftStoreInstallation(
            String displayName,
            String description,
            String packageFamilyName,
            String packageFullName,
            String storeId,
            String titleId,
            String launchId,
            Path installRoot,
            Path contentRoot,
            Path smallLogo,
            Path coverImage,
            Path largeLogo
    ) {
    }

    private record AppxPackage(
            String name,
            String packageFamilyName,
            String packageFullName,
            String installLocation,
            String appId,
            String launchId,
            String executable,
            String displayName,
            String description,
            String square44x44Logo,
            String square150x150Logo,
            boolean inGamingRepository,
            String storeId,
            String titleId
    ) {
    }

    private record InstalledPackage(
            String name,
            String packageFamilyName,
            String packageFullName,
            String installLocation
    ) {
        private static InstalledPackage fromJson(JsonObject object) {
            return new InstalledPackage(
                    string(object, "Name"),
                    string(object, "PackageFamilyName"),
                    string(object, "PackageFullName"),
                    string(object, "InstallLocation")
            );
        }
    }

    private static String string(JsonObject object, String memberName) {
        JsonElement value = object.get(memberName);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
