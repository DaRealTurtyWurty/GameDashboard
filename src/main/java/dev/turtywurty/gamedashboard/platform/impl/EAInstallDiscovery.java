package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.GameDashboardApp;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class EAInstallDiscovery {
    private static final List<String> REGISTRY_ROOTS = List.of(
            "HKLM\\SOFTWARE\\EA Games",
            "HKLM\\SOFTWARE\\WOW6432Node\\EA Games",
            "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
    );
    private static final Pattern REGISTRY_VALUE = Pattern.compile(
            "^\\s{4}(.+?)\\s+REG_[A-Z0-9_]+\\s+(.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> EXECUTABLE_EXCLUSIONS = Set.of(
            "cleanup", "unins", "uninstall", "installer", "update", "updater", "crash", "reporter"
    );

    private EAInstallDiscovery() {
    }

    static List<EAInstallation> discover(Path eaDesktopDataDirectory) {
        Path installData = eaDesktopDataDirectory.resolve("InstallData");
        if (!Files.isDirectory(installData))
            return List.of();

        List<RegistryEntry> registryEntries = readRegistryEntries();
        try (Stream<Path> children = Files.list(installData)) {
            return children
                    .filter(Files::isDirectory)
                    .map(path -> createInstallation(path, registryEntries))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(EAInstallation::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read EA install data at {}", installData, exception);
            return List.of();
        }
    }

    static boolean isValidDataDirectory(Path path) {
        return path != null && Files.isDirectory(path.resolve("InstallData"));
    }

    private static Optional<EAInstallation> createInstallation(
            Path installDataPath,
            List<RegistryEntry> registryEntries
    ) {
        String folderName = installDataPath.getFileName().toString();
        RegistryEntry registryEntry = registryEntries.stream()
                .max(Comparator.comparingInt(entry -> matchScore(folderName, entry)))
                .filter(entry -> matchScore(folderName, entry) >= 45)
                .orElse(null);

        // reg.exe transliterates symbols such as the trademark sign to a literal "T"
        // when its output is redirected. InstallData directory names are the stable,
        // uncorrupted product names maintained by the EA app itself.
        String title = cleanTitle(folderName);
        Path installLocation = registryEntry == null ? null : getInstallLocation(registryEntry);
        if (installLocation == null || !Files.isDirectory(installLocation)) {
            GameDashboardApp.LOGGER.warn("EA install '{}' has no readable installation directory", title);
            return Optional.empty();
        }

        Path executable = registryEntry == null ? null : getDisplayIcon(registryEntry);
        if (executable == null || !Files.isRegularFile(executable))
            executable = findLikelyExecutable(installLocation, title).orElse(null);
        if (executable == null) {
            GameDashboardApp.LOGGER.warn("EA install '{}' has no launch executable", title);
            return Optional.empty();
        }

        return Optional.of(new EAInstallation(
                title,
                findSoftwareId(installDataPath).orElse(normalize(folderName)),
                installDataPath,
                installLocation,
                executable
        ));
    }

    private static List<RegistryEntry> readRegistryEntries() {
        List<RegistryEntry> entries = new ArrayList<>();
        for (String root : REGISTRY_ROOTS) {
            try {
                Process process = new ProcessBuilder("reg.exe", "query", root, "/s")
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), nativeCharset());
                int exitCode = process.waitFor();
                if (exitCode == 0)
                    entries.addAll(parseRegistryOutput(output));
            } catch (IOException exception) {
                GameDashboardApp.LOGGER.debug("Unable to query EA registry root {}", root, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return entries;
            }
        }
        return entries;
    }

    static List<RegistryEntry> parseRegistryOutput(String output) {
        List<RegistryEntry> entries = new ArrayList<>();
        String currentKey = null;
        Map<String, String> currentValues = new LinkedHashMap<>();

        for (String line : output.lines().toList()) {
            if (line.startsWith("HKEY_")) {
                if (currentKey != null)
                    entries.add(new RegistryEntry(currentKey, Map.copyOf(currentValues)));
                currentKey = line.trim();
                currentValues.clear();
                continue;
            }

            Matcher matcher = REGISTRY_VALUE.matcher(line);
            if (currentKey != null && matcher.matches())
                currentValues.put(matcher.group(1).trim().toLowerCase(Locale.ROOT), matcher.group(2).trim());
        }
        if (currentKey != null)
            entries.add(new RegistryEntry(currentKey, Map.copyOf(currentValues)));

        return entries;
    }

    private static Charset nativeCharset() {
        String nativeEncoding = System.getProperty("native.encoding");
        return nativeEncoding == null || nativeEncoding.isBlank()
                ? Charset.defaultCharset()
                : Charset.forName(nativeEncoding);
    }

    private static int matchScore(String folderName, RegistryEntry entry) {
        String target = normalize(folderName);
        int score = nameScore(target, normalize(entry.keyName()));
        score = Math.max(score, nameScore(target, normalize(entry.value("DisplayName"))));

        Path installLocation = getInstallLocation(entry);
        if (installLocation != null && installLocation.getFileName() != null)
            score = Math.max(score, nameScore(target, normalize(installLocation.getFileName().toString())) + 20);
        if (getDisplayIcon(entry) != null)
            score += 5;
        return score;
    }

    private static int nameScore(String left, String right) {
        if (left.isBlank() || right.isBlank())
            return 0;
        if (left.equals(right))
            return 100;
        if (left.contains(right) || right.contains(left))
            return 75;

        Set<String> leftTokens = new LinkedHashSet<>(List.of(left.split(" ")));
        Set<String> rightTokens = new LinkedHashSet<>(List.of(right.split(" ")));
        leftTokens.removeIf(String::isBlank);
        rightTokens.removeIf(String::isBlank);
        if (leftTokens.isEmpty() || rightTokens.isEmpty())
            return 0;

        long common = leftTokens.stream().filter(rightTokens::contains).count();
        return (int) Math.round(common * 70.0 / Math.max(leftTokens.size(), rightTokens.size()));
    }

    private static Optional<String> findSoftwareId(Path installDataPath) {
        try (Stream<Path> children = Files.list(installDataPath)) {
            return children
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("base-") && name.length() > 5)
                    .map(name -> name.substring(5))
                    .findFirst();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static Path getInstallLocation(RegistryEntry entry) {
        String value = firstNonBlank(entry.value("InstallLocation"), entry.value("Install Dir"));
        return toPath(value);
    }

    private static Path getDisplayIcon(RegistryEntry entry) {
        String value = entry.value("DisplayIcon");
        if (value == null || value.isBlank())
            return null;

        value = value.trim();
        if (value.startsWith("\"") && value.indexOf('"', 1) > 1)
            value = value.substring(1, value.indexOf('"', 1));
        else if (value.lastIndexOf(',') > 2)
            value = value.substring(0, value.lastIndexOf(','));
        return toPath(value);
    }

    private static Path toPath(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return Path.of(value.replace("\"", "").trim()).normalize();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Optional<Path> findLikelyExecutable(Path installLocation, String title) {
        String normalizedTitle = normalize(title).replace(" ", "");
        try (Stream<Path> files = Files.walk(installLocation, 4)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe"))
                    .filter(path -> EXECUTABLE_EXCLUSIONS.stream().noneMatch(exclusion ->
                            path.getFileName().toString().toLowerCase(Locale.ROOT).contains(exclusion)))
                    .max(Comparator.comparingInt(path -> executableScore(path, normalizedTitle)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static int executableScore(Path path, String normalizedTitle) {
        String fileName = normalize(path.getFileName().toString().replaceFirst("(?i)\\.exe$", ""))
                .replace(" ", "");
        int score = normalizedTitle.contains(fileName) || fileName.contains(normalizedTitle) ? 50 : 0;
        String fullPath = path.toString().toLowerCase(Locale.ROOT);
        if (fullPath.contains("\\game\\bin\\"))
            score += 20;
        if (fileName.contains("launcher"))
            score += 5;
        return score;
    }

    static String normalize(String value) {
        if (value == null)
            return "";
        return value.toLowerCase(Locale.ROOT)
                .replace("™", "")
                .replace("®", "")
                .replace("(tm)", "")
                .replace("&", " and ")
                .replaceAll("\\biii\\b", "3")
                .replaceAll("\\bii\\b", "2")
                .replaceAll("\\biv\\b", "4")
                .replaceAll("\\bv\\b", "5")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    static String cleanTitle(String value) {
        if (value == null)
            return "";
        return value
                .replaceAll("(?i)\\(TM\\)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    record RegistryEntry(String key, Map<String, String> values) {
        RegistryEntry {
            values = new HashMap<>(values);
        }

        String value(String name) {
            return this.values.get(name.toLowerCase(Locale.ROOT));
        }

        String keyName() {
            int separator = this.key.lastIndexOf('\\');
            return separator < 0 ? this.key : this.key.substring(separator + 1);
        }
    }

    record EAInstallation(
            String title,
            String softwareId,
            Path installDataPath,
            Path installLocation,
            Path executable
    ) {
    }
}
