package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.GameDashboardApp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EAArtworkCache {
    private static final Pattern IMAGE_URL = Pattern.compile(
            "https://app-images\\.ea\\.com/[A-Za-z0-9._~:/?#@!$&()*+,;=%\\-]+"
    );
    private static final long MAX_CACHE_FILE_SIZE = 8L * 1024L * 1024L;

    private final List<String> imageUrls;

    private EAArtworkCache(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public static EAArtworkCache loadDefault() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank())
            return new EAArtworkCache(List.of());

        Path cacheData = Path.of(
                localAppData,
                "Electronic Arts",
                "EA Desktop",
                "CEF",
                "BrowserCache",
                "EADesktop",
                "Cache",
                "Cache_Data"
        );
        return load(cacheData);
    }

    public static EAArtworkCache load(Path cacheData) {
        if (!Files.isDirectory(cacheData))
            return new EAArtworkCache(List.of());

        Set<String> urls = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(cacheData, 2)) {
            files.filter(Files::isRegularFile).forEach(path -> readUrls(path, urls));
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.debug("Unable to scan EA artwork cache at {}", cacheData, exception);
        }
        return new EAArtworkCache(List.copyOf(urls));
    }

    private static void readUrls(Path path, Set<String> urls) {
        try {
            long size = Files.size(path);
            if (size <= 0 || size > MAX_CACHE_FILE_SIZE)
                return;

            String contents = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
            Matcher matcher = IMAGE_URL.matcher(contents);
            while (matcher.find())
                urls.add(matcher.group());
        } catch (IOException ignored) {
            // Chromium may rotate or lock a cache file while discovery is running.
        }
    }

    private static int titleScore(String title, String url) {
        String normalizedTitle = EAPlatform.normalize(title);
        String normalizedUrl = EAPlatform.normalize(url.substring(url.lastIndexOf('/') + 1));
        if (normalizedTitle.isBlank() || normalizedUrl.isBlank())
            return 0;

        String compactTitle = normalizedTitle.replace(" ", "");
        String compactUrl = normalizedUrl.replace(" ", "");
        if (compactUrl.contains(compactTitle))
            return 100;

        Set<String> tokens = new LinkedHashSet<>(List.of(normalizedTitle.split(" ")));
        tokens.removeIf(token -> token.length() < 2 || token.equals("and") || token.equals("the"));
        long matches = tokens.stream().filter(token -> normalizedUrl.contains(token)).count();

        int score = (int) matches * 15;
        if (normalizedTitle.contains("sims 4") && normalizedUrl.contains("ts4"))
            score += 55;
        if (normalizedTitle.contains("sims 4") && normalizedUrl.contains("base game"))
            score += 50;
        return matches >= 2 || score >= 55 ? score : 0;
    }

    private static boolean isSquareArt(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("1x1") && !lower.contains("promo-hero");
    }

    private static boolean isPortraitArt(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("packart") && lower.contains("9x16");
    }

    private static boolean isLogoArt(String url) {
        return url.toLowerCase(Locale.ROOT).contains("keyart-logo");
    }

    private static int squareTypeScore(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lower.contains("game-art-1x1") || lower.contains("gameart"))
            score += 35;
        if (lower.contains("keyart-1x1"))
            score += 25;
        if (lower.contains("base-game") || lower.contains("fullgame"))
            score += 30;
        if (isAddOnArt(lower))
            score -= 100;
        return score;
    }

    private static boolean isAddOnArt(String lowerUrl) {
        return lowerUrl.contains("dlc")
                || lowerUrl.contains("add-on")
                || lowerUrl.contains("addon")
                || lowerUrl.contains("currency")
                || lowerUrl.contains("virtual-currency")
                || lowerUrl.contains("points-pack");
    }

    private static int portraitTypeScore(String url) {
        return url.toLowerCase(Locale.ROOT).contains("fullgame") ? 20 : 0;
    }

    private static int logoTypeScore(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return isAddOnArt(lower) ? -100 : 25;
    }

    private static String resize(String url, int width) {
        if (url == null || url.isBlank())
            return null;
        int queryStart = url.indexOf('?');
        String base = queryStart < 0 ? url : url.substring(0, queryStart);
        return base + "?impolicy=dynamic&w=" + width;
    }

    public Artwork find(String title) {
        List<ScoredUrl> candidates = new ArrayList<>();
        for (String url : this.imageUrls) {
            int titleScore = titleScore(title, url);
            if (titleScore > 0)
                candidates.add(new ScoredUrl(url, titleScore));
        }

        String square = candidates.stream()
                .filter(candidate -> isSquareArt(candidate.url()))
                .max(Comparator.comparingInt(candidate -> candidate.score() + squareTypeScore(candidate.url())))
                .map(ScoredUrl::url)
                .orElse(null);
        String portrait = candidates.stream()
                .filter(candidate -> isPortraitArt(candidate.url()))
                .max(Comparator.comparingInt(candidate -> candidate.score() + portraitTypeScore(candidate.url())))
                .map(ScoredUrl::url)
                .orElse(null);
        String logo = candidates.stream()
                .filter(candidate -> isLogoArt(candidate.url()))
                .max(Comparator.comparingInt(candidate -> candidate.score() + logoTypeScore(candidate.url())))
                .map(ScoredUrl::url)
                .orElse(null);

        return new Artwork(resize(square, 300), resize(portrait, 400), resize(logo, 320));
    }

    public record Artwork(String squareUrl, String portraitUrl, String logoUrl) {
        private boolean isEmpty() {
            return this.squareUrl == null && this.portraitUrl == null && this.logoUrl == null;
        }
    }

    private record ScoredUrl(String url, int score) {
    }
}
