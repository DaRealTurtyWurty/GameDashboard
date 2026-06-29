package dev.turtywurty.gamedashboard.platform.impl.individual;

import dev.turtywurty.gamedashboard.GameDashboardApp;
import dev.turtywurty.gamedashboard.data.APIConnector;
import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.game.ExecutableLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.FileSystemsHolder;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import dev.turtywurty.gamedashboard.util.Utils;
import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Wizard101Platform implements Platform {
    private static final int IGDB_ID = 47101;
    private static final String TITLE = "Wizard101";

    @Override
    public Image getIcon() {
        return new Image(Objects.requireNonNull(
                getClass().getResource("/images/platforms/individual/wizard101.png")
        ).toExternalForm());
    }

    @Override
    public String getName() {
        return TITLE;
    }

    @Override
    public String getWebsite() {
        return "https://www.wizard101.com/";
    }

    @Override
    public String getDescription() {
        return "Wizard101 is a massively multiplayer online role-playing game (MMORPG) developed and published by KingsIsle Entertainment. Players take on the role of students of the Ravenwood School of Magical Arts, where they learn spells, battle creatures, and explore various worlds within the game.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding Wizard101 installation", 1);

            Path found = null;
            for (Path root : FileSystemsHolder.roots()) {
                Path wizard101Path = root.resolve("ProgramData").resolve("KingsIsle Entertainment").resolve("Wizard101");
                if (Files.isDirectory(wizard101Path)) {
                    found = wizard101Path;
                    break;
                }
            }
            progressMonitor.worked(1);

            if (found == null) {
                progressMonitor.done();
                return;
            }

            addGame(progressMonitor, found);
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the Wizard101 installation directory. This is normally C:\\ProgramData\\KingsIsle Entertainment\\Wizard101.",
                "wizard101",
                false,
                0
        );

        var directoryField = form.addDirectoryField(
                "Wizard101 installation directory",
                "",
                "C:\\ProgramData\\KingsIsle Entertainment\\Wizard101",
                "Select Wizard101 Directory",
                false
        );

        return form.build(progressMonitor -> {
            if (!form.validate(directoryField, Utils::isReadableDirectory, "Select a readable Wizard101 directory."))
                return;

            form.hideError();
            ManualEntryForm.runAsync(progressMonitor, () -> addGame(progressMonitor, Path.of(directoryField.getText())));
        });
    }

    private static void addGame(ProgressMonitor progressMonitor, Path installDirectory) {
        progressMonitor.start("Fetching Wizard101 installation details", 2);
        Optional<Path> executable = findExecutable(installDirectory);
        progressMonitor.worked(1);
        if (executable.isEmpty()) {
            progressMonitor.done();
            return;
        }

        APIConnector.GameResult metadata = findMetadata();
        var game = getGame(metadata, executable);

        GameDashboardApp.LOGGER.info("Found Wizard101 installation at {}", installDirectory);
        Utils.runOnFxThread(() -> {
            if (!Database.getInstance().addGame(game)) {
                Database.getInstance().updateGame(game, game);
            }
        });
        progressMonitor.worked(1);
        progressMonitor.done();
    }

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType"})
    private static @NotNull Game getGame(APIConnector.GameResult metadata, Optional<Path> executable) {
        var game = new Game(
                TITLE,
                metadata == null || metadata.getSummary() == null ? "" : metadata.getSummary(),
                new ExecutableLaunchTarget(executable.get(), List.of(), executable.get().getParent())
        );
        game.setThumbCoverImageURL(metadata == null ? Utils.PLACEHOLDER_COVER_URL : metadata.getThumbCoverURL());
        game.setCoverImageURL(metadata == null ? Utils.PLACEHOLDER_COVER_URL : metadata.getCoverURL());
        game.setIgdbGameId(metadata == null ? IGDB_ID : metadata.getIgdbGameId());
        game.setNickname(TITLE);
        return game;
    }

    private static Optional<Path> findExecutable(Path installDirectory) {
        for (String relativePath : List.of("Bin/Wizard101.exe", "Wizard101.exe")) {
            Path executable = installDirectory.resolve(relativePath.replace('/', '\\'));
            if (Files.isRegularFile(executable))
                return Optional.of(executable);
        }

        try (Stream<Path> files = Files.walk(installDirectory, 4)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("Wizard101.exe"))
                    .findFirst();
        } catch (IOException exception) {
            GameDashboardApp.LOGGER.warn("Unable to scan Wizard101 installation at {}", installDirectory, exception);
            return Optional.empty();
        }
    }

    private static APIConnector.GameResult findMetadata() {
        try {
            return APIConnector.getGameByID(IGDB_ID, true, true).join();
        } catch (RuntimeException exception) {
            GameDashboardApp.LOGGER.warn("Unable to load Wizard101 metadata from IGDB id {}", IGDB_ID, exception);
            return null;
        }
    }
}
