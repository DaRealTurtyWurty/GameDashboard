package dev.turtywurty.gamedashboard.platform.impl;

import dev.turtywurty.gamedashboard.data.Database;
import dev.turtywurty.gamedashboard.data.SteamHandler;
import dev.turtywurty.gamedashboard.platform.ManualEntryForm;
import dev.turtywurty.gamedashboard.platform.Platform;
import dev.turtywurty.gamedashboard.util.OperatingSystem;
import dev.turtywurty.gamedashboard.util.ProgressMonitor;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class SteamPlatform implements Platform {
    @Override
    public Image getIcon() {
        return new Image(getClass().getResource("/images/platforms/steam.png").toExternalForm());
    }

    @Override
    public String getName() {
        return "Steam";
    }

    @Override
    public String getWebsite() {
        return "https://store.steampowered.com/";
    }

    @Override
    public String getDescription() {
        return "Steam is a digital distribution platform developed by Valve Corporation, offering a wide range of video games, software, and community features for gamers.";
    }

    @Override
    public Consumer<ProgressMonitor> performDiscovery() {
        return progressMonitor -> {
            progressMonitor.start("Finding Steam installation", 2);
            Optional<Path> steamLocation = SteamHandler.discoverSteamLocation();
            progressMonitor.worked(1);
            if (steamLocation.isEmpty()) {
                progressMonitor.done();
                return;
            }

            Optional<Path> steamLibraryFoldersLocation = SteamHandler.discoverSteamLibraryFoldersLocation(steamLocation.get());
            progressMonitor.worked(1);
            if (steamLibraryFoldersLocation.isEmpty()) {
                progressMonitor.done();
                return;
            }

            boolean configured = Database.getInstance().setSteamConfiguration(
                    steamLocation.get().toString(),
                    steamLibraryFoldersLocation.get().toString(),
                    progressMonitor
            );
            if (!configured) {
                progressMonitor.done();
            }
        };
    }

    @Override
    public ManualEntryView createManualEntryView() {
        var form = new ManualEntryForm(
                "Choose the Steam executable and libraryfolders.vdf file used to find your Steam library.",
                "steam",
                true,
                0
        );

        var executableField = form.addFileField(
                "Steam executable",
                Database.getInstance().getSteamExecutable(),
                switch (OperatingSystem.getCurrent()) {
                    case WINDOWS -> "C:\\Program Files (x86)\\Steam\\steam.exe";
                    case MACOS -> "/Applications/Steam.app/Contents/MacOS/steam_osx";
                    case LINUX -> "/usr/bin/steam";
                    default -> "";
                },
                "Select Steam Executable",
                null
        );

        var libraryFoldersField = form.addFileField(
                "Steam library folders file",
                Database.getInstance().getSteamLibraryFolders(),
                switch (OperatingSystem.getCurrent()) {
                    case WINDOWS -> "C:\\Program Files (x86)\\Steam\\steamapps\\libraryfolders.vdf";
                    case MACOS -> "/Applications/Steam.app/Contents/SteamApps/libraryfolders.vdf";
                    case LINUX -> "/usr/bin/steam/steamapps/libraryfolders.vdf";
                    default -> "";
                },
                "Select libraryfolders.vdf",
                new FileChooser.ExtensionFilter("Valve Data Format", "libraryfolders.vdf", "*.vdf")
        );

        return form.build(progressMonitor -> {
            String executable = executableField.getText();
            if (!form.validate(executableField, this::isValidSteamExecutable, "Select a valid Steam executable."))
                return;

            String libraryFolders = libraryFoldersField.getText();
            if (!form.validate(libraryFoldersField, this::isValidLibraryFolders, "Select a valid libraryfolders.vdf file."))
                return;

            form.hideError();
            progressMonitor.start("Saving Steam configuration", 1);
            ManualEntryForm.runAsync(progressMonitor, () -> {
                progressMonitor.worked(1);
                boolean configured = Database.getInstance().setSteamConfiguration(executable, libraryFolders, progressMonitor);
                if (!configured)
                    progressMonitor.done();
            });
        });
    }

    private boolean isValidSteamExecutable(String executable) {
        if (executable == null || executable.isBlank())
            return false;

        try {
            return SteamHandler.isSteamExecutableValid(Path.of(executable));
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private boolean isValidLibraryFolders(String libraryFolders) {
        if (libraryFolders == null || libraryFolders.isBlank())
            return false;

        try {
            return SteamHandler.isSteamLibraryFoldersValid(Path.of(libraryFolders));
        } catch (InvalidPathException exception) {
            return false;
        }
    }
}
