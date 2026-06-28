package dev.turtywurty.gamedashboard.util;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FileSystemsHolder {
    private FileSystemsHolder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static List<Path> roots() {
        List<Path> roots = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            roots.add(root);
        }

        return List.copyOf(roots);
    }
}
