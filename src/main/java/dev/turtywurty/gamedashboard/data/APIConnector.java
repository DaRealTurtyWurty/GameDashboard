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
import java.util.List;
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
}
