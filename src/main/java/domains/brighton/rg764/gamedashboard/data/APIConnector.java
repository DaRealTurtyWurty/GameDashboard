package domains.brighton.rg764.gamedashboard.data;

import domains.brighton.rg764.gamedashboard.GameDashboardApp;
import lombok.Data;

import java.util.List;

public class APIConnector {
    private static final String BASE_URL = "https://api.turtywurty.dev/";

    public static List<GameResult> search(String query) {
        String searchURL = BASE_URL + "games/search?apiKey=%s&fields=name,cover".formatted(GameDashboardApp.getAPIKey());
        String coverImageURL = BASE_URL + "games/cover?apiKey=%s&imageSize=hd".formatted(GameDashboardApp.getAPIKey());


    }

    @Data
    public static class GameResult {
        private String name;
        private String coverURL;
    }
}
