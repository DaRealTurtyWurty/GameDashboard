package dev.turtywurty.gamedashboard.data.game;

import dev.turtywurty.gamedashboard.util.OperatingSystem;

import java.io.IOException;

public record UriLaunchTarget(String uri) implements LaunchTarget {
    public UriLaunchTarget {
        if (uri == null || uri.isBlank())
            throw new IllegalArgumentException("URI cannot be null or blank");

        uri = uri.trim();
    }

    @Override
    public Process launch() throws IOException {
        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> new ProcessBuilder(this.uri).start();
            case MACOS -> new ProcessBuilder("open", this.uri).start();
            case LINUX -> new ProcessBuilder("xdg-open", this.uri).start();
            default -> throw new UnsupportedOperationException(
                    "Unsupported operating system for URI launch: " + OperatingSystem.CURRENT
            );
        };
    }
}
