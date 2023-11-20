package domains.brighton.rg764.gamedashboard.data;

import com.google.gson.*;
import domains.brighton.rg764.gamedashboard.GameDashboardApp;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import proto.GameResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class APIConnector {
    private static final String BASE_URL = "https://api.turtywurty.dev/";

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setLenient()
            .setPrettyPrinting()
            .create();

    public static List<GameResult> search(String query) {
        String searchURL = BASE_URL + "games/search?apiKey=%s&fields=name,cover&query=%s"
                .formatted(GameDashboardApp.getAPIKey(), query);
        String coverImageURL = BASE_URL + "games/cover?apiKey=%s&imageSize=micro&fields=url"
                .formatted(GameDashboardApp.getAPIKey());

        Request searchRequest = new Request.Builder().url(searchURL).build();
        try (Response searchResponse = HTTP_CLIENT.newCall(searchRequest).execute()) {
            if (!searchResponse.isSuccessful())
                return List.of();

            ResponseBody searchResponseBody = searchResponse.body();
            if (searchResponseBody == null)
                return List.of();

            String searchResponseString = searchResponseBody.string();
            if (searchResponseString.isBlank())
                return List.of();

            JsonArray searchResponseArray = GSON.fromJson(searchResponseString, JsonArray.class);
            if (searchResponseArray == null)
                return List.of();

            List<GameResult> gameResults = new ArrayList<>();
            for (JsonElement jsonElement : searchResponseArray) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                if (jsonObject == null)
                    continue;

                String name = jsonObject.get("name").getAsString();
                int coverID;
                if(jsonObject.has("cover")) {
                    coverID = jsonObject.get("cover").getAsInt();
                } else {
                    gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35"));
                    continue;
                }

                Request coverRequest = new Request.Builder().url(coverImageURL + "&id=" + coverID).build();
                try (Response coverResponse = HTTP_CLIENT.newCall(coverRequest).execute()) {
                    if (!coverResponse.isSuccessful()) {
                        gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35"));
                        continue;
                    }

                    ResponseBody coverResponseBody = coverResponse.body();
                    if (coverResponseBody == null) {
                        gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35"));
                        continue;
                    }

                    String coverResponseString = coverResponseBody.string();
                    if (coverResponseString.isBlank()) {
                        gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35"));
                        continue;
                    }

                    JsonObject coverResponseObject = GSON.fromJson(coverResponseString, JsonObject.class);
                    if (coverResponseObject == null) {
                        gameResults.add(new GameResult(name, "https://fakeimg.pl/35x35"));
                        continue;
                    }

                    String coverURL = coverResponseObject.get("url").getAsString();
                    gameResults.add(new GameResult(name, coverURL));
                }
            }

            return gameResults;
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Replace with logger
            return List.of();
        }
    }

    @Data
    @AllArgsConstructor
    public static class GameResult {
        private String name;
        private String coverURL;
    }
}
