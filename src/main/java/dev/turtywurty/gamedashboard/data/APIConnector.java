package dev.turtywurty.gamedashboard.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class APIConnector {
    private static final String BASE_URL = "https://api.turtywurty.dev/";
    private static final String PLACEHOLDER_COVER_URL = "https://fakeimg.pl/35x35";
    private static final int MAX_RATE_LIMIT_RETRIES = 5;
    private static final long MAX_RATE_LIMIT_DELAY_MILLIS = 60_000;
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 250;
    private static final Object REQUEST_THROTTLE_LOCK = new Object();
    private static final AtomicLong RATE_LIMITED_UNTIL = new AtomicLong();

    private static long nextRequestTimeMillis;

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setLenient()
            .setPrettyPrinting()
            .create();

    private APIConnector() {
    }

    public static CompletableFuture<List<GameResult>> search(String query, boolean includeSummary) {
        return search(query, includeSummary, true);
    }

    public static CompletableFuture<List<GameResult>> search(String query) {
        return search(query, false, true);
    }

    public static CompletableFuture<List<GameResult>> search(
            String query,
            boolean includeSummary,
            boolean includeCover
    ) {
        return CompletableFuture.supplyAsync(() -> {
            HttpUrl searchUrl = urlBuilder("games/search")
                    .addQueryParameter("apiKey", GameDashboardApp.getAPIKey())
                    .addQueryParameter("query", query)
                    .addQueryParameter("fields", gameFields(includeSummary, includeCover))
                    .build();

            JsonArray searchResults;
            try {
                searchResults = executeJson(new Request.Builder().url(searchUrl).build(), "Search for games")
                        .getAsJsonArray();
            } catch (APIException exception) {
                if (exception.isNotFound())
                    return List.of();

                throw exception;
            } catch (IllegalStateException exception) {
                throw invalidResponse("Search for games", "Expected a JSON array", exception);
            }

            List<GameResult> gameResults = new ArrayList<>();
            for (JsonElement resultElement : searchResults) {
                if (!resultElement.isJsonObject())
                    continue;

                JsonObject game = resultElement.getAsJsonObject();
                String name = getRequiredString(game, "name", "Search for games");
                String summary = includeSummary ? getOptionalString(game, "summary") : null;

                if (!includeCover) {
                    gameResults.add(new GameResult(name, null, null, summary));
                    continue;
                }

                Integer coverId = getOptionalInteger(game, "cover");
                CoverUrls coverUrls = coverId == null ? null : getCoverUrls(coverId);
                gameResults.add(createGameResult(name, summary, coverUrls));
            }

            return gameResults;
        });
    }

    public static CompletableFuture<IGDBGameMatcher.MatchResult> findBestFuzzyGameMatch(
            String title,
            boolean includeSummary,
            boolean includeCover
    ) {
        return findBestFuzzyGameMatch(title, includeSummary, includeCover, null, null);
    }

    public static CompletableFuture<GameResult> findBestFastGameMatch(
            String title,
            boolean includeSummary,
            boolean includeCover
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Map<Integer, IGDBGameCandidate> candidatesById = new LinkedHashMap<>();
            for (String query : generateFastMatchQueries(title)) {
                List<IGDBGameCandidate> candidates = searchGameCandidates(query, includeSummary, false, 10);
                for (IGDBGameCandidate candidate : candidates) {
                    candidatesById.putIfAbsent(candidate.id(), candidate);
                }
            }

            IGDBGameMatcher.MatchResult match = IGDBGameMatcher.findBestMatch(
                    title,
                    List.copyOf(candidatesById.values())
            );
            logFuzzyMatchDebug(title, generateFastMatchQueries(title), match);
            if (match.winner() == null)
                return null;

            IGDBGameCandidate candidate = match.winner().candidate();
            if (includeCover && candidate.coverId() != null) {
                CoverUrls coverUrls = getCoverUrls(candidate.coverId());
                if (coverUrls != null) {
                    return new GameResult(
                            candidate.name(),
                            coverUrls.thumbnailUrl(),
                            coverUrls.coverUrl(),
                            candidate.summary()
                    );
                }
            }

            if (includeCover && candidate.id() > 0) {
                GameResult detailedResult = getGameByID(candidate.id(), includeSummary, true).join();
                if (detailedResult != null)
                    return detailedResult;
            }

            return candidate.toGameResult();
        });
    }

    public static CompletableFuture<IGDBGameMatcher.MatchResult> findBestFuzzyGameMatch(
            String title,
            boolean includeSummary,
            boolean includeCover,
            String platform,
            Integer releaseYear
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> queries = IGDBGameMatcher.generateQueries(title);
            Map<Integer, IGDBGameCandidate> candidatesById = new LinkedHashMap<>();
            for (String query : queries) {
                List<IGDBGameCandidate> candidates = searchGameCandidates(query, includeSummary, false, 15);
                if (candidates.isEmpty()) {
                    candidates = search(query, includeSummary, false).join().stream()
                            .map(APIConnector::createFallbackCandidate)
                            .toList();
                }

                for (IGDBGameCandidate candidate : candidates) {
                    candidatesById.putIfAbsent(candidate.id(), candidate);
                }
            }

            IGDBGameMatcher.MatchResult match = IGDBGameMatcher.findBestMatch(
                    title,
                    List.copyOf(candidatesById.values()),
                    platform,
                    releaseYear
            );
            logFuzzyMatchDebug(title, queries, match);
            if (!includeCover || match.winner() == null || match.winner().candidate().id() <= 0)
                return addWinnerCoverDetails(match, includeSummary, includeCover);

            GameResult detailedResult = getGameByID(match.winner().candidate().id(), includeSummary, true).join();
            if (detailedResult == null)
                return addWinnerCoverDetails(match, includeSummary, includeCover);

            IGDBGameCandidate detailedCandidate = new IGDBGameCandidate(
                    match.winner().candidate().id(),
                    detailedResult.getName(),
                    match.winner().candidate().slug(),
                    match.winner().candidate().alternativeNames(),
                    match.winner().candidate().platforms(),
                    match.winner().candidate().firstReleaseDate(),
                    match.winner().candidate().category(),
                    match.winner().candidate().parentGame(),
                    match.winner().candidate().versionParent(),
                    match.winner().candidate().coverId(),
                    detailedResult.getSummary(),
                    detailedResult.getThumbCoverURL(),
                    detailedResult.getCoverURL()
            );
            return new IGDBGameMatcher.MatchResult(
                    new IGDBGameMatcher.ScoredCandidate(
                            detailedCandidate,
                            match.winner().score(),
                            match.winner().reasons()
                    ),
                    match.ambiguous(),
                    match.candidates(),
                    match.reason()
            );
        });
    }

    private static IGDBGameMatcher.MatchResult addWinnerCoverDetails(
            IGDBGameMatcher.MatchResult match,
            boolean includeSummary,
            boolean includeCover
    ) {
        if (!includeCover || match.winner() == null || match.winner().candidate().coverId() == null)
            return match;

        CoverUrls coverUrls = getCoverUrls(match.winner().candidate().coverId());
        if (coverUrls == null)
            return match;

        IGDBGameCandidate candidate = match.winner().candidate();
        IGDBGameCandidate detailedCandidate = new IGDBGameCandidate(
                candidate.id(),
                candidate.name(),
                candidate.slug(),
                candidate.alternativeNames(),
                candidate.platforms(),
                candidate.firstReleaseDate(),
                candidate.category(),
                candidate.parentGame(),
                candidate.versionParent(),
                candidate.coverId(),
                includeSummary ? candidate.summary() : null,
                coverUrls.thumbnailUrl(),
                coverUrls.coverUrl()
        );
        return new IGDBGameMatcher.MatchResult(
                new IGDBGameMatcher.ScoredCandidate(
                        detailedCandidate,
                        match.winner().score(),
                        match.winner().reasons()
                ),
                match.ambiguous(),
                match.candidates(),
                match.reason()
        );
    }

    private static List<String> generateFastMatchQueries(String title) {
        IGDBGameMatcher.TitleParts normalizedTitle = IGDBGameMatcher.normalizeTitle(title);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addSearchQuery(queries, title);
        addSearchQuery(queries, normalizedTitle.normalized());
        addSearchQuery(queries, normalizedTitle.normalizedNoAnd());
        return List.copyOf(queries);
    }

    private static void addSearchQuery(LinkedHashSet<String> queries, String query) {
        if (query == null)
            return;

        String normalizedQuery = query.trim();
        if (!normalizedQuery.isBlank())
            queries.add(normalizedQuery);
    }

    private static List<IGDBGameCandidate> searchGameCandidates(
            String query,
            boolean includeSummary,
            boolean includeCover,
            int limit
    ) {
        HttpUrl searchUrl = urlBuilder("games/search")
                .addQueryParameter("apiKey", GameDashboardApp.getAPIKey())
                .addQueryParameter("query", query)
                .addQueryParameter("fields", gameCandidateFields(includeSummary, includeCover))
                .addQueryParameter("limit", Integer.toString(limit))
                .build();

        JsonArray searchResults;
        try {
            searchResults = executeJson(new Request.Builder().url(searchUrl).build(), "Search for game candidates")
                    .getAsJsonArray();
        } catch (APIException exception) {
            if (exception.isNotFound())
                return List.of();

            throw exception;
        } catch (IllegalStateException exception) {
            throw invalidResponse("Search for game candidates", "Expected a JSON array", exception);
        }

        List<IGDBGameCandidate> gameResults = new ArrayList<>();
        for (JsonElement resultElement : searchResults) {
            if (!resultElement.isJsonObject())
                continue;

            JsonObject game = resultElement.getAsJsonObject();
            Integer id = getOptionalInteger(game, "id");
            String name = getOptionalString(game, "name");
            if (name == null)
                continue;
            if (id == null)
                id = fallbackCandidateId(name);

            String summary = includeSummary ? getOptionalString(game, "summary") : null;
            Integer coverId = getOptionalInteger(game, "cover");
            CoverUrls coverUrls = includeCover && coverId != null ? getCoverUrls(coverId) : null;

            gameResults.add(new IGDBGameCandidate(
                    id,
                    name,
                    getOptionalString(game, "slug"),
                    getAlternativeNames(game),
                    getPlatforms(game),
                    getOptionalLong(game, "first_release_date"),
                    getOptionalInteger(game, "category"),
                    getOptionalInteger(game, "parent_game"),
                    getOptionalInteger(game, "version_parent"),
                    coverId,
                    summary,
                    coverUrls == null ? PLACEHOLDER_COVER_URL : coverUrls.thumbnailUrl(),
                    coverUrls == null ? PLACEHOLDER_COVER_URL : coverUrls.coverUrl()
            ));
        }

        return gameResults;
    }

    private static IGDBGameCandidate createFallbackCandidate(GameResult gameResult) {
        return new IGDBGameCandidate(
                fallbackCandidateId(gameResult.getName()),
                gameResult.getName(),
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                gameResult.getSummary(),
                gameResult.getThumbCoverURL(),
                gameResult.getCoverURL()
        );
    }

    private static int fallbackCandidateId(String name) {
        String normalizedName = IGDBGameMatcher.normalizeTitle(name).normalizedNoAnd();
        return -Math.abs(normalizedName.hashCode());
    }

    public static CompletableFuture<Integer> getGameIdFromExternalId(
            ExternalPlatform platform,
            String externalId
    ) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(externalId, "externalId");

        return CompletableFuture.supplyAsync(() -> {
            HttpUrl searchUrl = urlBuilder("games/external")
                    .addQueryParameter("apiKey", GameDashboardApp.getAPIKey())
                    .addQueryParameter("platform", platform.getApiName())
                    .addQueryParameter("externalId", externalId)
                    .build();

            JsonObject game;
            try {
                game = executeJson(
                        new Request.Builder().url(searchUrl).build(),
                        "Get game ID from external ID"
                ).getAsJsonObject();
            } catch (APIException exception) {
                if (exception.isNotFound())
                    return null;

                throw exception;
            } catch (IllegalStateException exception) {
                throw invalidResponse(
                        "Get game ID from external ID",
                        "Expected a JSON object",
                        exception
                );
            }

            return getRequiredInteger(game, "id", "Get game ID from external ID");
        });
    }

    public static CompletableFuture<GameResult> getGameByID(
            int id,
            boolean includeSummary,
            boolean includeCover
    ) {
        return CompletableFuture.supplyAsync(() -> {
            HttpUrl gameUrl = urlBuilder("games")
                    .addQueryParameter("apiKey", GameDashboardApp.getAPIKey())
                    .addQueryParameter("id", Integer.toString(id))
                    .addQueryParameter("fields", gameFields(includeSummary, includeCover))
                    .build();

            JsonObject game;
            try {
                game = executeJson(new Request.Builder().url(gameUrl).build(), "Get game by ID")
                        .getAsJsonObject();
            } catch (APIException exception) {
                if (exception.isNotFound())
                    return null;

                throw exception;
            } catch (IllegalStateException exception) {
                throw invalidResponse("Get game by ID", "Expected a JSON object", exception);
            }

            String name = getRequiredString(game, "name", "Get game by ID");
            String summary = includeSummary ? getOptionalString(game, "summary") : null;
            if (!includeCover)
                return new GameResult(name, null, null, summary);

            Integer coverId = getOptionalInteger(game, "cover");
            CoverUrls coverUrls = coverId == null ? null : getCoverUrls(coverId);
            return createGameResult(name, summary, coverUrls);
        });
    }

    private static CoverUrls getCoverUrls(int coverId) {
        HttpUrl coverUrl = urlBuilder("games/cover")
                .addQueryParameter("apiKey", GameDashboardApp.getAPIKey())
                .addQueryParameter("fields", "url")
                .addQueryParameter("id", Integer.toString(coverId))
                .build();

        JsonObject cover;
        try {
            cover = executeJson(new Request.Builder().url(coverUrl).build(), "Get game cover")
                    .getAsJsonObject();
        } catch (APIException exception) {
            if (exception.isNotFound())
                return null;

            throw exception;
        } catch (IllegalStateException exception) {
            throw invalidResponse("Get game cover", "Expected a JSON object", exception);
        }

        String thumbnailUrl = getRequiredString(cover, "url", "Get game cover");
        return new CoverUrls(thumbnailUrl, thumbnailUrl.replace("t_thumb", "t_cover_big"));
    }

    private static JsonElement executeJson(Request request, String operation) {
        for (int attempt = 0; attempt <= MAX_RATE_LIMIT_RETRIES; attempt++) {
            waitForRateLimit(operation);
            waitForRequestSlot(operation);

            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                String responseText = responseBody == null ? "" : responseBody.string();

                if (!response.isSuccessful()) {
                    APIException exception = parseErrorResponse(
                            response.code(),
                            response.message(),
                            responseText,
                            operation
                    );
                    if (exception.isRateLimited() && attempt < MAX_RATE_LIMIT_RETRIES) {
                        long delayMillis = getRateLimitDelayMillis(response, attempt);
                        extendRateLimit(delayMillis);
                        GameDashboardApp.LOGGER.info(
                                "{} was rate limited; retrying in {} ms ({}/{})",
                                operation,
                                delayMillis,
                                attempt + 1,
                                MAX_RATE_LIMIT_RETRIES
                        );
                        continue;
                    }

                    throw exception;
                }

                if (responseText.isBlank())
                    throw invalidResponse(operation, "Response body is empty", null);

                try {
                    JsonElement json = GSON.fromJson(responseText, JsonElement.class);
                    if (json == null || json.isJsonNull())
                        throw invalidResponse(operation, "Response body contains null JSON", null);

                    return json;
                } catch (JsonParseException exception) {
                    throw invalidResponse(operation, "Response body contains invalid JSON", exception);
                }
            } catch (SocketTimeoutException exception) {
                throw new APIException(
                        504,
                        "timeout",
                        operation + " timed out",
                        null,
                        exception
                );
            } catch (IOException exception) {
                throw new APIException(
                        503,
                        "service_unavailable",
                        operation + " could not reach the API",
                        null,
                        exception
                );
            }
        }

        throw new IllegalStateException("Rate limit retry loop completed unexpectedly");
    }

    private static void waitForRequestSlot(String operation) {
        synchronized (REQUEST_THROTTLE_LOCK) {
            while (true) {
                long now = System.currentTimeMillis();
                long delayMillis = nextRequestTimeMillis - now;
                if (delayMillis <= 0) {
                    nextRequestTimeMillis = now + MIN_REQUEST_INTERVAL_MILLIS;
                    return;
                }

                try {
                    REQUEST_THROTTLE_LOCK.wait(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new APIException(
                            503,
                            "interrupted",
                            operation + " was interrupted while waiting for the API request buffer",
                            null,
                            exception
                    );
                }
            }
        }
    }

    private static void waitForRateLimit(String operation) {
        while (true) {
            long delayMillis = RATE_LIMITED_UNTIL.get() - System.currentTimeMillis();
            if (delayMillis <= 0)
                return;

            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new APIException(
                        503,
                        "interrupted",
                        operation + " was interrupted while waiting for the API rate limit",
                        null,
                        exception
                );
            }
        }
    }

    private static void extendRateLimit(long delayMillis) {
        long limitedUntil = System.currentTimeMillis() + delayMillis;
        RATE_LIMITED_UNTIL.accumulateAndGet(limitedUntil, Math::max);
    }

    private static long getRateLimitDelayMillis(Response response, int attempt) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                return clampRateLimitDelay(seconds * 1_000);
            } catch (NumberFormatException ignored) {
                try {
                    long delayMillis = ZonedDateTime.parse(
                            retryAfter,
                            DateTimeFormatter.RFC_1123_DATE_TIME
                    ).toInstant().toEpochMilli() - System.currentTimeMillis();
                    return clampRateLimitDelay(delayMillis);
                } catch (DateTimeParseException ignoredDate) {
                    // Fall through to exponential backoff.
                }
            }
        }

        long exponentialDelay = 1_000L << attempt;
        long jitter = ThreadLocalRandom.current().nextLong(250, 751);
        return clampRateLimitDelay(exponentialDelay + jitter);
    }

    private static long clampRateLimitDelay(long delayMillis) {
        return Math.clamp(delayMillis, 0, MAX_RATE_LIMIT_DELAY_MILLIS);
    }

    private static APIException parseErrorResponse(
            int statusCode,
            String responseMessage,
            String responseText,
            String operation
    ) {
        String error = defaultErrorCode(statusCode);
        String message = responseText.isBlank()
                ? operation + " failed: " + responseMessage
                : responseText;
        Integer upstreamStatus = null;

        if (!responseText.isBlank()) {
            try {
                JsonElement json = GSON.fromJson(responseText, JsonElement.class);
                if (json != null && json.isJsonObject()) {
                    JsonObject errorResponse = json.getAsJsonObject();
                    error = getOptionalString(errorResponse, "error", error);
                    message = getOptionalString(errorResponse, "message", message);
                    upstreamStatus = getOptionalInteger(errorResponse, "upstreamStatus");
                }
            } catch (JsonParseException | IllegalStateException ignored) {
                // Preserve the HTTP status and raw response when an older server returns non-JSON errors.
            }
        }

        if (statusCode == 404) {
            GameDashboardApp.LOGGER.debug(
                    "{} returned no result: error={}, message={}",
                    operation,
                    error,
                    message
            );
        } else {
            GameDashboardApp.LOGGER.warn(
                    "{} failed: status={}, error={}, message={}, upstreamStatus={}",
                    operation,
                    statusCode,
                    error,
                    message,
                    upstreamStatus
            );
        }
        return new APIException(statusCode, error, message, upstreamStatus);
    }

    private static APIException invalidResponse(String operation, String detail, Throwable cause) {
        return new APIException(
                502,
                "invalid_response",
                operation + " failed: " + detail,
                null,
                cause
        );
    }

    private static HttpUrl.Builder urlBuilder(String path) {
        HttpUrl baseUrl = HttpUrl.get(BASE_URL);
        HttpUrl.Builder builder = baseUrl.newBuilder();
        for (String pathSegment : path.split("/")) {
            builder.addPathSegment(pathSegment);
        }

        return builder;
    }

    private static String gameFields(boolean includeSummary, boolean includeCover) {
        List<String> fields = new ArrayList<>(List.of("name"));
        if (includeSummary)
            fields.add("summary");
        if (includeCover)
            fields.add("cover");
        return String.join(",", fields);
    }

    private static String gameCandidateFields(boolean includeSummary, boolean includeCover) {
        List<String> fields = new ArrayList<>(List.of(
                "id",
                "name",
                "slug",
                "alternative_names.name",
                "platforms.id",
                "platforms.name",
                "first_release_date",
                "category",
                "parent_game",
                "version_parent",
                "cover"
        ));
        if (includeSummary)
            fields.add("summary");
        if (includeCover)
            fields.add("cover");
        return String.join(",", fields);
    }

    private static List<String> getAlternativeNames(JsonObject game) {
        JsonArray alternatives = getOptionalArray(game, "alternative_names");
        if (alternatives == null)
            return List.of();

        List<String> names = new ArrayList<>();
        for (JsonElement alternative : alternatives) {
            if (!alternative.isJsonObject())
                continue;

            String name = getOptionalString(alternative.getAsJsonObject(), "name");
            if (name != null && !name.isBlank())
                names.add(name);
        }

        return List.copyOf(names);
    }

    private static List<IGDBPlatform> getPlatforms(JsonObject game) {
        JsonArray platforms = getOptionalArray(game, "platforms");
        if (platforms == null)
            return List.of();

        List<IGDBPlatform> result = new ArrayList<>();
        for (JsonElement platformElement : platforms) {
            if (!platformElement.isJsonObject())
                continue;

            JsonObject platform = platformElement.getAsJsonObject();
            Integer id = getOptionalInteger(platform, "id");
            String name = getOptionalString(platform, "name");
            if (id != null && name != null)
                result.add(new IGDBPlatform(id, name));
        }

        return List.copyOf(result);
    }

    private static void logFuzzyMatchDebug(
            String title,
            List<String> queries,
            IGDBGameMatcher.MatchResult match
    ) {
        List<String> topCandidates = match.candidates().stream()
                .limit(5)
                .map(candidate -> candidate.candidate().name()
                        + "=" + String.format(Locale.ROOT, "%.1f", candidate.score())
                        + " " + candidate.reasons())
                .toList();
        GameDashboardApp.LOGGER.info(
                "IGDB fuzzy match for '{}': queries={}, result={}, topCandidates={}",
                title,
                queries,
                match.reason(),
                topCandidates
        );
    }

    private static GameResult createGameResult(String name, String summary, CoverUrls coverUrls) {
        if (coverUrls == null)
            return new GameResult(name, PLACEHOLDER_COVER_URL, PLACEHOLDER_COVER_URL, summary);

        return new GameResult(name, coverUrls.thumbnailUrl(), coverUrls.coverUrl(), summary);
    }

    private static String getRequiredString(JsonObject object, String property, String operation) {
        String value = getOptionalString(object, property);
        if (value == null)
            throw invalidResponse(operation, "Missing string property '" + property + "'", null);
        return value;
    }

    private static int getRequiredInteger(JsonObject object, String property, String operation) {
        Integer value = getOptionalInteger(object, property);
        if (value == null)
            throw invalidResponse(operation, "Missing integer property '" + property + "'", null);
        return value;
    }

    private static String getOptionalString(JsonObject object, String property) {
        return getOptionalString(object, property, null);
    }

    private static String getOptionalString(JsonObject object, String property, String fallback) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
            return fallback;

        try {
            return value.getAsString();
        } catch (UnsupportedOperationException | IllegalStateException exception) {
            return fallback;
        }
    }

    private static Integer getOptionalInteger(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
            return null;

        try {
            return value.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException exception) {
            return null;
        }
    }

    private static Long getOptionalLong(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
            return null;

        try {
            return value.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException exception) {
            return null;
        }
    }

    private static JsonArray getOptionalArray(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonArray())
            return null;

        return value.getAsJsonArray();
    }

    private static String defaultErrorCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> "invalid_request";
            case 404 -> "not_found";
            case 429 -> "rate_limited";
            case 502 -> "upstream_failure";
            case 503 -> "service_unavailable";
            case 504 -> "timeout";
            default -> "http_error";
        };
    }

    private record CoverUrls(String thumbnailUrl, String coverUrl) {
    }

    @Getter
    @AllArgsConstructor
    public enum ExternalPlatform {
        STEAM("steam"),
        GOG("gog"),
        YOUTUBE("youtube"),
        MICROSOFT("microsoft"),
        APPLE("apple"),
        TWITCH("twitch"),
        ANDROID("android"),
        AMAZON_ASIN("amazon_asin"),
        AMAZON_LUNA("amazon_luna"),
        AMAZON_ADG("amazon_adg"),
        EPIC_GAME_STORE("epic_game_store"),
        OCULUS("oculus"),
        UTOMIK("utomik"),
        ITCH_IO("itch_io"),
        XBOX_MARKETPLACE("xbox_marketplace"),
        KARTRIDGE("kartridge"),
        PLAYSTATION_STORE_US("playstation_store_us"),
        FOCUS_ENTERTAINMENT("focus_entertainment"),
        XBOX_GAME_PASS_ULTIMATE_CLOUD("xbox_game_pass_ultimate_cloud"),
        GAMEJOLT("gamejolt");

        private final String apiName;
    }

    @Getter
    public static final class APIException extends RuntimeException {
        private final int statusCode;
        private final String error;
        private final Integer upstreamStatus;

        public APIException(int statusCode, String error, String message, Integer upstreamStatus) {
            this(statusCode, error, message, upstreamStatus, null);
        }

        public APIException(
                int statusCode,
                String error,
                String message,
                Integer upstreamStatus,
                Throwable cause
        ) {
            super(message, cause);
            this.statusCode = statusCode;
            this.error = error;
            this.upstreamStatus = upstreamStatus;
        }

        public boolean isNotFound() {
            return this.statusCode == 404;
        }

        public boolean isRateLimited() {
            return this.statusCode == 429 || this.upstreamStatus != null && this.upstreamStatus == 429;
        }
    }

    @Data
    @AllArgsConstructor
    public static class GameResult {
        private String name;
        private String thumbCoverURL;
        private String coverURL;
        private String summary;
    }

    public record IGDBPlatform(int id, String name) {
    }

    public record IGDBGameCandidate(
            int id,
            String name,
            String slug,
            List<String> alternativeNames,
            List<IGDBPlatform> platforms,
            Long firstReleaseDate,
            Integer category,
            Integer parentGame,
            Integer versionParent,
            Integer coverId,
            String summary,
            String thumbCoverURL,
            String coverURL
    ) {
        public Integer releaseYear() {
            return IGDBGameMatcher.releaseYear(this.firstReleaseDate);
        }

        public boolean hasPlatform(String platformName) {
            String normalizedPlatform = IGDBGameMatcher.normalizeTitle(platformName).normalizedNoAnd();
            return this.platforms.stream()
                    .map(IGDBPlatform::name)
                    .map(name -> IGDBGameMatcher.normalizeTitle(name).normalizedNoAnd())
                    .anyMatch(normalizedPlatform::equals);
        }

        public boolean looksLikeExpansionOrEdition() {
            if (this.parentGame != null || this.versionParent != null)
                return true;

            return this.category != null && switch (this.category) {
                case 1, 2, 3, 6, 7, 13, 14 -> true;
                default -> false;
            };
        }

        public GameResult toGameResult() {
            return new GameResult(
                    this.name,
                    this.thumbCoverURL == null ? PLACEHOLDER_COVER_URL : this.thumbCoverURL,
                    this.coverURL == null ? PLACEHOLDER_COVER_URL : this.coverURL,
                    this.summary
            );
        }
    }
}
