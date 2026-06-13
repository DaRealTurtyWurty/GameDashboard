package dev.turtywurty.gamedashboard.data.store;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonFileStore {
    private JsonFileStore() {
    }

    public static void writeAtomically(Path target, String content) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);

        Path temporaryFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporaryFile, content);
            try {
                Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
