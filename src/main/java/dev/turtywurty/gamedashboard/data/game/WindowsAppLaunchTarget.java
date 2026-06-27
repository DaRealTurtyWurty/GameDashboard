package dev.turtywurty.gamedashboard.data.game;

import dev.turtywurty.gamedashboard.util.OperatingSystem;

import java.io.IOException;

public record WindowsAppLaunchTarget(String appUserModelId) implements LaunchTarget {
    public WindowsAppLaunchTarget {
        if (appUserModelId == null || appUserModelId.isBlank())
            throw new IllegalArgumentException("AppUserModelID cannot be null or blank");

        appUserModelId = appUserModelId.trim();
    }

    @Override
    public Process launch() throws IOException {
        if (OperatingSystem.CURRENT != OperatingSystem.WINDOWS)
            throw new UnsupportedOperationException("Windows app launch targets are only supported on Windows");

        return new ProcessBuilder(
                "explorer.exe",
                "shell:AppsFolder\\" + this.appUserModelId
        ).start();
    }
}
