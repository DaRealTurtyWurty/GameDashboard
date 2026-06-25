package dev.turtywurty.gamedashboard.platform;

import dev.turtywurty.gamedashboard.platform.impl.EpicGamesPlatform;
import dev.turtywurty.gamedashboard.platform.impl.EAPlatform;
import dev.turtywurty.gamedashboard.platform.impl.GOGPlatform;
import dev.turtywurty.gamedashboard.platform.impl.RiotPlatform;
import dev.turtywurty.gamedashboard.platform.impl.SteamPlatform;
import dev.turtywurty.gamedashboard.platform.impl.UbisoftPlatform;

import java.util.ArrayList;
import java.util.List;

public final class Platforms {
    private Platforms() {
    }

    private static final List<Platform> PLATFORMS = new ArrayList<>();

    static {
        registerPlatform(new SteamPlatform());
        registerPlatform(new EpicGamesPlatform());
        registerPlatform(new EAPlatform());
        registerPlatform(new GOGPlatform());
        registerPlatform(new UbisoftPlatform());
        registerPlatform(new RiotPlatform());
    }

    public static void registerPlatform(Platform platform) {
        if (!PLATFORMS.contains(platform)) {
            PLATFORMS.add(platform);
        } else {
            throw new IllegalArgumentException("Platform already registered: " + platform.getName());
        }
    }

    public static List<Platform> getPlatforms() {
        return List.copyOf(PLATFORMS);
    }
}
