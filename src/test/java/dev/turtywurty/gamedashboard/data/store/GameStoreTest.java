package dev.turtywurty.gamedashboard.data.store;

import com.google.gson.Gson;
import dev.turtywurty.gamedashboard.data.game.Game;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameStoreTest {
    @Test
    void preservesCoverLogoUrl(@TempDir Path appDataDirectory) {
        var game = new Game("Example", "", "example.exe");
        game.setCoverLogoImageURL("https://app-images.ea.com/example/keyart-logo-en.png");

        var store = new GameStore(appDataDirectory, new Gson());
        store.save(List.of(game));

        assertEquals(game.getCoverLogoImageURL(), store.load().getFirst().getCoverLogoImageURL());
    }
}
