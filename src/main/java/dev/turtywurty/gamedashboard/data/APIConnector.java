package dev.turtywurty.gamedashboard.data;

import com.google.gson.*;
import dev.turtywurty.gamedashboard.GameDashboardApp;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class APIConnector {
    private static final String BASE_URL = "https://api.turtywurty.dev/";

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setLenient()
            .setPrettyPrinting()
            .create();

    public static CompletableFuture<List<GameResult>> search(String query, boolean includeSummary) {
        return search(query, includeSummary, true);
    }

    public static CompletableFuture<List<GameResult>> search(String query) {
        return search(query, false, true);
    }

    public static CompletableFuture<List<GameResult>> search(String query, boolean includeSummary, boolean includeCover) {
        return CompletableFuture.supplyAsync(() -> {
            String searchURL = (BASE_URL + "games/search?apiKey=%s&query=%s&fields=name" + (includeSummary ? ",summary" : "") + (includeCover ? ",cover" : ""))
                    .formatted(GameDashboardApp.getAPIKey(), query);
            String coverImageURL = BASE_URL + "games/cover?apiKey=%s&fields=url"
                    .formatted(GameDashboardApp.getAPIKey());

            Request searchRequest = new Request.Builder().url(searchURL).build();
            try (Response searchResponse = HTTP_CLIENT.newCall(searchRequest).execute()) {
                if (!searchResponse.isSuccessful()) {
                    GameDashboardApp.LOGGER.warn("Failed to search for games: {}", searchResponse.code());
                    return Collections.emptyList();
                }

                ResponseBody searchResponseBody = searchResponse.body();
                if (searchResponseBody == null) {
                    GameDashboardApp.LOGGER.warn("Failed to search for games: Response body is null");
                    return Collections.emptyList();
                }

                String searchResponseString = searchResponseBody.string();
                if (searchResponseString.isBlank()) {
                    GameDashboardApp.LOGGER.warn("Failed to search for games: Response body is blank");
                    return Collections.emptyList();
                }

                JsonArray searchResponseArray = GSON.fromJson(searchResponseString, JsonArray.class);
                if (searchResponseArray == null) {
                    GameDashboardApp.LOGGER.warn("Failed to search for games: Response body is not a JSON array");
                    return Collections.emptyList();
                }

                List<GameResult> gameResults = new ArrayList<>();
                for (JsonElement jsonElement : searchResponseArray) {
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    if (jsonObject == null)
                        continue;

                    String name = jsonObject.get("name").getAsString();
                    String summary = includeSummary ? jsonObject.has("summary") ? jsonObject.get("summary").getAsString() : null : null;
                    if (includeCover) {
                        int coverID;
                        if (jsonObject.has("cover")) {
                            coverID = jsonObject.get("cover").getAsInt();
                        } else {
                            gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35", "https://fakeimg.pl/35x35", summary));
                            continue;
                        }

                        Request coverRequest = new Request.Builder().url(coverImageURL + "&id=" + coverID).build();
                        try (Response coverResponse = HTTP_CLIENT.newCall(coverRequest).execute()) {
                            if (!coverResponse.isSuccessful()) {
                                gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35", "https://fakeimg.pl/35x35", summary));
                                continue;
                            }

                            ResponseBody coverResponseBody = coverResponse.body();
                            if (coverResponseBody == null) {
                                gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35", "https://fakeimg.pl/35x35", summary));
                                continue;
                            }

                            String coverResponseString = coverResponseBody.string();
                            if (coverResponseString.isBlank()) {
                                gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35", "https://fakeimg.pl/35x35", summary));
                                continue;
                            }

                            JsonObject coverResponseObject = GSON.fromJson(coverResponseString, JsonObject.class);
                            if (coverResponseObject == null) {
                                gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35", "https://fakeimg.pl/35x35", summary));
                                continue;
                            }

                            String thumbCoverURL = coverResponseObject.get("url").getAsString();
                            String coverURL = thumbCoverURL.replace("t_thumb", "t_cover_big");
                            gameResults.add(new GameResult(name, thumbCoverURL, coverURL, summary));
                        }
                    } else {
                        gameResults.add(new GameResult(name, null, null, summary));
                    }
                }

                return gameResults;
            } catch (IOException exception) {
                GameDashboardApp.LOGGER.error("Failed to search for games", exception);
                return Collections.emptyList();
            }
        });
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
