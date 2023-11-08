package domains.brighton.rg764.gamedashboard.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Database {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Database INSTANCE = new Database();
    private final Path path = Path.of(System.getProperty("user.home"), "gamedashboard", "games.json");
    private final ObservableList<Game> games = FXCollections.observableArrayList();

    private Database() {
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                Files.writeString(path, "[]");
            } catch (IOException exception) {
                exception.printStackTrace(); // TODO: Create a logger
            }
        }
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public static Gson getGson() {
        return GSON;
    }

    public void readGames() throws IOException {
        String json = Files.readString(path);
        JsonArray array = GSON.fromJson(json, JsonArray.class);
        List<Game> games = new ArrayList<>();
        for (JsonElement element : array) {
            games.add(GSON.fromJson(element, Game.class));
        }

        this.games.clear();
        this.games.addAll(games);
    }

    public void writeGames() throws IOException {
        JsonArray array = new JsonArray();
        for (Game game : games) {
            array.add(GSON.toJson(game));
        }

        Files.writeString(path, GSON.toJson(array));
    }

    public void addGame(Game game) {
        games.add(game);

        try {
            writeGames();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        }
    }

    public void removeGame(Game game) {
        games.remove(game);

        try {
            writeGames();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        }
    }

    public void updateGame(Game game) {
        Game matching = this.games.stream()
                .filter(g -> g.getTitle().equals(game.getTitle()))
                .findFirst()
                .orElse(null);

        if (matching == null)
            return;

        int index = this.games.indexOf(matching);
        this.games.set(index, game);

        try {
            writeGames();
        } catch (IOException exception) {
            exception.printStackTrace(); // TODO: Create a logger
        }
    }

    public Game getGame(String title) {
        return this.games.stream()
                .filter(game -> game.getTitle().equals(title))
                .findFirst()
                .orElse(null);
    }

    public boolean containsGame(String title) {
        return this.games.stream()
                .anyMatch(game -> game.getTitle().equals(title));
    }
}
