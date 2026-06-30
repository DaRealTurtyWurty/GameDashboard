package dev.turtywurty.gamedashboard.platform;

import dev.turtywurty.gamedashboard.platform.impl.*;
import dev.turtywurty.gamedashboard.platform.impl.battle_net.BattleNetPlatform;
import dev.turtywurty.gamedashboard.platform.impl.ea.EAPlatform;
import dev.turtywurty.gamedashboard.platform.impl.individual.RobloxPlatform;
import dev.turtywurty.gamedashboard.platform.impl.individual.Wizard101Platform;

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
        registerPlatform(new ItchPlatform());
        registerPlatform(new UbisoftPlatform());
        registerPlatform(new RiotPlatform());
        registerPlatform(new BattleNetPlatform());
        registerPlatform(new MicrosoftStorePlatform());
        registerPlatform(new GooglePlayGamesPlatform());
        registerPlatform(new Wizard101Platform());
        registerPlatform(new RobloxPlatform());
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
