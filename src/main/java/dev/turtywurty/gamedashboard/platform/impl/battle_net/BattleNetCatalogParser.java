package dev.turtywurty.gamedashboard.platform.impl.battle_net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class BattleNetCatalogParser {
    private static final Gson GSON = new Gson();

    private BattleNetCatalogParser() {
    }

    public static Catalog parse(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    public static Catalog parse(String json) {
        return GSON.fromJson(json, Catalog.class);
    }

    public record Catalog(
            @SerializedName("fragment_id")
            String fragmentId,

            Integer version,

            Map<String, Map<String, FileEntry>> files,

            Map<String, Install> installs,

            @SerializedName("presence_resources")
            List<PresenceResource> presenceResources,

            List<Product> products,

            @SerializedName("program_configuration")
            Map<String, ProgramConfiguration> programConfiguration,

            Map<String, Map<String, String>> strings,

            Types types
    ) {
    }

    public record FileEntry(
            String hash,
            String name
    ) {
    }

    public record Install(
            @SerializedName("tact_product")
            String tactProduct
    ) {
    }

    public record PresenceResource(
            @SerializedName("display_name")
            String displayName,

            @SerializedName("icon_16")
            String icon16,

            @SerializedName("icon_275")
            String icon275,

            @SerializedName("icon_32")
            String icon32,

            @SerializedName("icon_56")
            String icon56,

            @SerializedName("icon_svg")
            String iconSvg,

            @SerializedName("program_id")
            String programId
    ) {
    }

    public record Product(
            String id,
            BaseProduct base
    ) {
    }

    public record BaseProduct(
            @SerializedName("alt_content_url")
            String altContentUrl,

            String background,

            List<Badge> badges,

            @SerializedName("breaking_news_url")
            String breakingNewsUrl,

            @SerializedName("content_id")
            String contentId,

            @SerializedName("default_product_type")
            String defaultProductType,

            String genre,

            @SerializedName("handheld_feature_compatibility")
            List<HandheldFeatureCompatibility> handheldFeatureCompatibility,

            @SerializedName("handheld_status")
            List<String> handheldStatus,

            @SerializedName("heading_text")
            String headingText,

            @SerializedName("icon_medium")
            String iconMedium,

            @SerializedName("icon_small")
            String iconSmall,

            @SerializedName("icon_svg")
            String iconSvg,

            @SerializedName("icon_tiny")
            String iconTiny,

            @SerializedName("install_background")
            String installBackground,

            @SerializedName("key_art")
            String keyArt,

            String logo,

            Milestone milestone,

            @SerializedName("misc_flags")
            List<String> miscFlags,

            @SerializedName("mobile_promo_text")
            String mobilePromoText,

            @SerializedName("mobile_qr_code")
            String mobileQrCode,

            @SerializedName("mobile_qr_code_text")
            String mobileQrCodeText,

            String name,

            @SerializedName("program_id")
            String programId,

            @SerializedName("quick_links")
            List<QuickLink> quickLinks,

            @SerializedName("region_permission_flags")
            RegionPermissionFlags regionPermissionFlags,

            @SerializedName("starter_items")
            List<StarterItem> starterItems,

            @SerializedName("starter_mode_when_offline")
            Boolean starterModeWhenOffline,

            @SerializedName("supported_platforms")
            List<String> supportedPlatforms,

            @SerializedName("supported_regions")
            List<String> supportedRegions,

            @SerializedName("supports_starter_mode")
            Boolean supportsStarterMode,

            @SerializedName("tab_order")
            Integer tabOrder,

            @SerializedName("title_id")
            Integer titleId,

            Map<String, ProductType> types,

            @SerializedName("unsupported_platform_behavior")
            String unsupportedPlatformBehavior
    ) {
    }

    public record Milestone(
            String state
    ) {
    }

    public record Badge(
            String color,
            String description,
            Boolean hidden,
            String id,
            String image,
            String label,
            Integer rank
    ) {
    }

    public record HandheldFeatureCompatibility(
            String compatibility,
            String description
    ) {
    }

    public record QuickLink(
            Action action,
            String icon,
            String id,
            String label,
            Integer rank
    ) {
    }

    public record Action(
            String type,
            Boolean external,
            String target,
            String url,
            @SerializedName("custom_action_id")
            String customActionId
    ) {
    }

    public record RegionPermissionFlags(
            @SerializedName("default")
            String defaultValue,

            List<Map<String, String>> overrides
    ) {
    }

    public record StarterItem(
            String id,
            Integer rank,
            String type,
            String label,
            String style,
            @SerializedName("qr_body")
            String qrBody,
            @SerializedName("qr_image")
            String qrImage,
            @SerializedName("qr_title")
            String qrTitle,
            Action action
    ) {
    }

    public record ProductType(
            String uid,

            @SerializedName("selector_display_name")
            String selectorDisplayName,

            @SerializedName("supports_starter_mode")
            Boolean supportsStarterMode,

            @SerializedName("region_permission_flags")
            RegionPermissionFlags regionPermissionFlags
    ) {
    }

    public record ProgramConfiguration(
            @SerializedName("run_each_rule")
            List<RunEachRule> runEachRule
    ) {
    }

    public record RunEachRule(
            List<ProgramAction> actions,
            Match match
    ) {
    }

    public record ProgramAction(
            @SerializedName("add_product")
            AddProduct addProduct
    ) {
    }

    public record AddProduct(
            @SerializedName("product_id")
            ProductId productId
    ) {
    }

    public record ProductId(
            String id,
            String type
    ) {
    }

    public record Match(
            @SerializedName("game_account")
            GameAccount gameAccount,

            @SerializedName("license_id")
            JsonElement licenseId
    ) {
        public List<Integer> licenseIds() {
            if (this.licenseId == null || this.licenseId.isJsonNull())
                return Collections.emptyList();

            if (this.licenseId.isJsonArray()) {
                List<Integer> ids = new ArrayList<>();
                for (JsonElement element : this.licenseId.getAsJsonArray()) {
                    ids.add(element.getAsInt());
                }

                return ids;
            }

            return List.of(this.licenseId.getAsInt());
        }
    }

    public record GameAccount(
            @SerializedName("program_id")
            String programId
    ) {
    }

    public record Types(
            List<TypeDefinition> definitions
    ) {
    }

    public record TypeDefinition(
            String category,
            String id,

            @SerializedName("product_defaults")
            ProductDefaults productDefaults
    ) {
    }

    public record ProductDefaults(
            @SerializedName("name_style")
            String nameStyle,

            Integer rank,

            @SerializedName("supports_starter_mode")
            Boolean supportsStarterMode,

            @SerializedName("type_name")
            String typeName
    ) {
    }
}
