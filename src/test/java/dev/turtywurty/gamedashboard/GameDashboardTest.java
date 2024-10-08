package dev.turtywurty.gamedashboard;

import dev.turtywurty.gamedashboard.data.SteamHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GameDashboardTest {
    @Test
    public void testNormalizeSteamName() {
        assertEquals("test", SteamHandler.normalizeSteamName("Test Game"));
        assertEquals("star wars squadrons", SteamHandler.normalizeSteamName("STAR WARS™: Squadrons"));
    }
}
