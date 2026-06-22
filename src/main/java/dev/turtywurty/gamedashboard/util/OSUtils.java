package dev.turtywurty.gamedashboard.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import mslinks.ShellLink;
import mslinks.ShellLinkException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class OSUtils {
    private static final Gson GSON = new Gson();
    private static final Pattern REGISTRY_VALUE = Pattern.compile(
            "^\\s{4}(.+?)\\s+REG_[A-Z0-9_]+\\s+(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private OSUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Optional<Path> resolveShortcut(Path shortcutPath) {
        try {
            return Optional.ofNullable(new ShellLink(shortcutPath).resolveTarget())
                    .map(Path::of);
        } catch (IOException | ShellLinkException exception) {
            GameDashboardApp.LOGGER.error("Failed to resolve shortcut {}", shortcutPath, exception);
            return Optional.empty();
        }
    }

    public static String readRegistryValue(String key, @Nullable String valueName) {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return null;

        Optional<String> powershellValue = readRegistryValueWithPowerShell(key, valueName);
        if (powershellValue.isPresent())
            return powershellValue.get();

        try {
            Process process = new ProcessBuilder(
                    "reg",
                    "query",
                    key,
                    valueName != null ? "/v" : "/ve",
                    valueName != null ? valueName : ""
            ).start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.contains("REG_SZ") || line.contains("REG_EXPAND_SZ")) {
                    String[] parts = line.trim().split("\\s+REG_(?:SZ|EXPAND_SZ)\\s+", 2);
                    if (parts.length == 2)
                        return parts[1].trim();
                }
            }
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read registry value", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    public static List<String> readRegistrySubKeys(String key) {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return Collections.emptyList();

        List<String> powershellSubKeys = readRegistrySubKeysWithPowerShell(key);
        if (!powershellSubKeys.isEmpty())
            return powershellSubKeys;

        try {
            Process process = new ProcessBuilder(
                    "reg",
                    "query",
                    key
            ).start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            String[] lines = output.split("\n");
            return Stream.of(lines)
                    .map(String::trim)
                    .filter(line -> line.startsWith("HKEY"))
                    .distinct()
                    .toList();
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read registry subkeys", exception);
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    public static List<RegistryEntry> readRegistryEntries(String key, boolean recursive) {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return List.of();

        try {
            List<String> command = new ArrayList<>(List.of("reg.exe", "query", key));
            if (recursive)
                command.add("/s");

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), nativeCharset());
            return process.waitFor() == 0 ? parseRegistryOutput(output) : List.of();
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.debug("Unable to query registry key {}", key, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return List.of();
    }

    public static List<RegistryEntry> parseRegistryOutput(String output) {
        if (output == null || output.isBlank())
            return List.of();

        List<RegistryEntry> entries = new ArrayList<>();
        String currentKey = null;
        Map<String, String> currentValues = new LinkedHashMap<>();
        for (String line : output.lines().toList()) {
            if (line.startsWith("HKEY_")) {
                if (currentKey != null)
                    entries.add(new RegistryEntry(currentKey, currentValues));
                currentKey = line.trim();
                currentValues = new LinkedHashMap<>();
                continue;
            }

            Matcher matcher = REGISTRY_VALUE.matcher(line);
            if (currentKey != null && matcher.matches())
                currentValues.put(matcher.group(1).trim().toLowerCase(Locale.ROOT), matcher.group(2).trim());
        }
        if (currentKey != null)
            entries.add(new RegistryEntry(currentKey, currentValues));
        return List.copyOf(entries);
    }

    private static Charset nativeCharset() {
        String nativeEncoding = System.getProperty("native.encoding");
        return nativeEncoding == null || nativeEncoding.isBlank()
                ? Charset.defaultCharset()
                : Charset.forName(nativeEncoding);
    }

    public record RegistryEntry(String key, Map<String, String> values) {
        public RegistryEntry {
            values = new HashMap<>(values);
        }

        public String value(String name) {
            return values.get(name.toLowerCase(Locale.ROOT));
        }

        public String keyName() {
            int separator = key.lastIndexOf('\\');
            return separator < 0 ? key : key.substring(separator + 1);
        }
    }

    private static Optional<String> readRegistryValueWithPowerShell(String key, @Nullable String valueName) {
        String command = """
                $item = Get-Item -LiteralPath %s -ErrorAction Stop;
                $value = $item.GetValue(%s, $null, 'DoNotExpandEnvironmentNames');
                if ($null -ne $value) {
                    $bytes = [System.Text.Encoding]::UTF8.GetBytes($value.ToString());
                    [Console]::Out.Write([Convert]::ToBase64String($bytes));
                }
                """.formatted(quotePowerShellString("Registry::" + key), quotePowerShellString(valueName == null ? "" : valueName));

        Optional<String> output = runPowerShell(command);
        return output.filter(value -> !value.isBlank())
                .map(String::trim)
                .map(OSUtils::decodeBase64Utf8);
    }

    private static List<String> readRegistrySubKeysWithPowerShell(String key) {
        String command = """
                [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new();
                Get-ChildItem -LiteralPath %s -ErrorAction Stop | ForEach-Object { $_.Name }
                """.formatted(quotePowerShellString("Registry::" + key));

        Optional<String> output = runPowerShell(command);
        if (output.isEmpty() || output.get().isBlank())
            return List.of();

        List<String> subKeys = new ArrayList<>();
        for (String line : output.get().split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank())
                subKeys.add(trimmed);
        }

        return subKeys.stream().distinct().toList();
    }

    private static Optional<String> runPowerShell(String command) {
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    command
            ).start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0)
                return Optional.empty();

            return Optional.of(output);
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to run PowerShell registry command", exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String quotePowerShellString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String decodeBase64Utf8(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public static Optional<AppBundleMetadata> readAppBundleMetadata(Path propertyList) {
        if (OperatingSystem.CURRENT != OperatingSystem.MACOS || !Files.isRegularFile(propertyList))
            return Optional.empty();

        try {
            Process process = new ProcessBuilder(
                    "/usr/bin/plutil", "-convert", "json", "-o", "-", propertyList.toString()
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0 || output.isBlank())
                return Optional.empty();

            JsonObject propertyListData = GSON.fromJson(output, JsonObject.class);
            if (propertyListData == null)
                return Optional.empty();

            return Optional.of(new AppBundleMetadata(
                    getString(propertyListData, "CFBundleIdentifier"),
                    getString(propertyListData, "CFBundleName"),
                    getString(propertyListData, "CFBundleDisplayName"),
                    getString(propertyListData, "CFBundleShortVersionString"),
                    getString(propertyListData, "CFBundleDevelopmentRegion")
            ));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.error("Failed to read property list {}", propertyList, exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (JsonParseException | IllegalStateException exception) {
            GameDashboardApp.LOGGER.error("Failed to parse property list {}", propertyList, exception);
            return Optional.empty();
        }
    }

    private static String getString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive())
            return "";
        return object.get(key).getAsString();
    }

    public record AppBundleMetadata(String bundleIdentifier, String bundleName, String displayName,
                                    String version, String developmentRegion) {
    }

    public static Optional<Path> findProcessRunningPath(String processName, boolean contains) {
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

    public static Optional<Path> findCommandPath(String command) {
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

    public static Optional<Path> findLocationFromStartMenu(String name) {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            return Optional.empty();

        Path startMenuPath1 = Path.of(System.getenv("APPDATA"), "Microsoft", "Windows", "Start Menu", "Programs", name);
        Path startMenuPath2 = Path.of(System.getenv("PROGRAMDATA"), "Microsoft", "Windows", "Start Menu", "Programs", name);

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
                .map(OSUtils::resolveShortcut)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(Files::isRegularFile)
                .findFirst();
    }
}
