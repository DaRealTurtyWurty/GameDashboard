package dev.turtywurty.gamedashboard.util;

import dev.turtywurty.gamedashboard.GameDashboardApp;

import java.awt.*;
import java.net.URI;

public class WebUtils {
    public static void openWebpage(String url) {
        URI uri = URI.create(url);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            GameDashboardApp.getHostServicesInstance().showDocument(url);
        } catch (Exception ignored) {
        }

        try {
            switch (OperatingSystem.CURRENT) {
                case WINDOWS -> new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                case MACOS -> new ProcessBuilder("open", url).start();
                case LINUX -> new ProcessBuilder("xdg-open", url).start();
                default -> throw new UnsupportedOperationException("Unsupported operating system: " + OperatingSystem.CURRENT);
            }

            return;
        } catch (Exception ignored) {
        }

        GameDashboardApp.LOGGER.error("Failed to open webpage: {}", url);
    }
}

