package dev.turtywurty.gamedashboard.platform.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EAArtworkCacheTest {
    @Test
    void selectsSquareAndPortraitArtForTheSameTitle(@TempDir Path cacheDirectory) throws IOException {
        Files.writeString(cacheDirectory.resolve("data_1"), """
                https://app-images.ea.com/example/battlefield-5-ce-m-keyart-1x1-en.jpg?impolicy=dynamic&w=250\0
                https://app-images.ea.com/example/battlefield-5-ce-m-packart-9x16-en.jpg?impolicy=dynamic&w=213\0
                https://app-images.ea.com/example/battlefield-1-ce-m-keyart-1x1-en.jpg?impolicy=dynamic&w=250\0
                """);

        EAArtworkCache.Artwork artwork = EAArtworkCache.load(cacheDirectory).find("Battlefield V");

        assertEquals(
                "https://app-images.ea.com/example/battlefield-5-ce-m-keyart-1x1-en.jpg?impolicy=dynamic&w=300",
                artwork.squareUrl()
        );
        assertEquals(
                "https://app-images.ea.com/example/battlefield-5-ce-m-packart-9x16-en.jpg?impolicy=dynamic&w=400",
                artwork.portraitUrl()
        );
    }
}
