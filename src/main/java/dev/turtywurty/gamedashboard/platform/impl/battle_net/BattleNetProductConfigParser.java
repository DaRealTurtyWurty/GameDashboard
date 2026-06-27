package dev.turtywurty.gamedashboard.platform.impl.battle_net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class BattleNetProductConfigParser {
    private static final Gson GSON = new Gson();

    public static Map<String, Section> parse(Path path) throws IOException {
        Type type = new TypeToken<Map<String, Section>>() {
        }.getType();
        return GSON.fromJson(Files.readString(path), type);
    }

    public record ProductConfig(
            Map<String, Section> entries
    ) {
    }

    public record Section(
            Config config,
            Map<String, Section> platform
    ) {
    }

    public record Config(
            @SerializedName("data_dir")
            String dataDir,

            @SerializedName("display_locales")
            List<String> displayLocales,

            Form form,

            String product,

            @SerializedName("supported_locales")
            List<String> supportedLocales,

            @SerializedName("supports_multibox")
            Boolean supportsMultibox,

            @SerializedName("supports_offline")
            Boolean supportsOffline,

            @SerializedName("title_info")
            TitleInfo titleInfo,

            @SerializedName("update_method")
            String updateMethod,

            List<JsonObject> install,

            Binaries binaries,

            @SerializedName("binary_launch_path")
            String binaryLaunchPath,

            @SerializedName("binary_version_path")
            String binaryVersionPath,

            @SerializedName("min_spec")
            MinSpec minSpec,

            List<String> tags,

            List<JsonObject> uninstall
    ) {
    }

    public record Form(
            @SerializedName("game_dir")
            GameDir gameDir
    ) {
    }

    public record GameDir(
            String dirname,

            @SerializedName("default")
            String defaultValue,

            @SerializedName("required_space")
            Long requiredSpace
    ) {
    }

    public record TitleInfo(
            @SerializedName("title_id")
            String titleId
    ) {
    }

    public record Binaries(
            Binary game
    ) {
    }

    public record Binary(
            @SerializedName("launch_arguments")
            List<String> launchArguments,

            @SerializedName("relative_path")
            String relativePath,

            Boolean switcher
    ) {
    }

    public record MinSpec(
            @SerializedName("default_required_cpu_cores")
            Integer defaultRequiredCpuCores,

            @SerializedName("default_required_cpu_speed")
            Integer defaultRequiredCpuSpeed,

            @SerializedName("default_required_ram")
            Integer defaultRequiredRam,

            @SerializedName("default_requires_64_bit")
            Boolean defaultRequires64Bit,

            @SerializedName("required_osspecs")
            Map<String, RequiredOsSpec> requiredOsSpecs
    ) {
    }

    public record RequiredOsSpec(
            @SerializedName("required_subversion")
            Integer requiredSubversion
    ) {
    }
}
