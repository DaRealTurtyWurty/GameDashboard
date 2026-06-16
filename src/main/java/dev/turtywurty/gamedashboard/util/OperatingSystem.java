package dev.turtywurty.gamedashboard.util;

public enum OperatingSystem {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN;

    public static final OperatingSystem CURRENT = getCurrent();

    public static OperatingSystem getCurrent() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("mac")) {
            return MACOS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return LINUX;
        } else {
            return UNKNOWN;
        }
    }
}
