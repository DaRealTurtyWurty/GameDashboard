package dev.turtywurty.gamedashboard.data.store;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonFileStore {
    private static final Object WRITE_LOCK = new Object();
    private static final int MAX_MOVE_ATTEMPTS = 5;
    private static final long MOVE_RETRY_DELAY_MILLIS = 50;

    private JsonFileStore() {
    }

    public static void writeAtomically(Path target, String content) throws IOException {
        synchronized (WRITE_LOCK) {
            Path parent = target.getParent();
            Files.createDirectories(parent);

            Path temporaryFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporaryFile, content);
                moveWithRetry(temporaryFile, target);
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private static void moveWithRetry(Path temporaryFile, Path target) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_MOVE_ATTEMPTS; attempt++) {
            try {
                moveAtomicallyIfSupported(temporaryFile, target);
                return;
            } catch (AccessDeniedException exception) {
                lastException = exception;
                if (attempt == MAX_MOVE_ATTEMPTS)
                    break;

                sleepBeforeRetry();
            }
        }

        throw lastException;
    }

    private static void moveAtomicallyIfSupported(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void sleepBeforeRetry() throws IOException {
        try {
            Thread.sleep(MOVE_RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while retrying JSON file write", exception);
        }
    }
}
