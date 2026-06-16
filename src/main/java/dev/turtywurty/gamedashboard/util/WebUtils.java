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
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if(os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if(os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
            }

            return;
        } catch (Exception ignored) {
        }

        GameDashboardApp.LOGGER.error("Failed to open webpage: {}", url);
    }
}

