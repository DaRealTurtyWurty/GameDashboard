package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import lombok.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public final class SteamGame extends Game {
    private final int steamAppId;

    public SteamGame(String title, String description, String executionCommand, int steamAppId, String thumbCoverImageURL, String coverImageURL, String nickname) {
        super(title, description, executionCommand, thumbCoverImageURL, coverImageURL, nickname, "steam");
        this.steamAppId = steamAppId;
    }

    public SteamGame(String title, String description, String executionCommand, int steamAppId) {
        super(title, description, executionCommand);
        this.type = "steam";
        this.steamAppId = steamAppId;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        return game instanceof SteamGame steamGame && this.steamAppId == steamGame.steamAppId;
    }

    @Override
    public Optional<ProcessHandle> findPossibleProcess(List<ProcessHandle> processes) {
        return processes.stream()
                .filter(process -> !process.info().command().orElse("").contains("UnityCrashHandler"))
                .filter(process -> !process.info().command().orElse("").contains("steamwebhelper"))
                .filter(process -> !process.info().command().orElse("").contains("steamerrorreporter"))
                .filter(process -> !process.info().command().orElse("").contains("GameOverlayUI"))
                .filter(process -> process.info().command().orElse("").toLowerCase(Locale.ROOT).contains("steam"))
                .filter(process -> {
                    String[] pathSplit = process.info().command().orElse("").split("\\\\");
                    String executableName = pathSplit[pathSplit.length - 1].split("\\.")[0];
                    // check to see if it's similar to the game name
                    boolean isExecutableSimilar = executableName.toLowerCase(Locale.ROOT).replace(" ", "")
                            .contains(getTitle().toLowerCase(Locale.ROOT).replace(" ", ""));

                    // if it's not similar then check to see if the containing folder of the executable is similar
                    if (!isExecutableSimilar) {
                        String[] folderSplit = pathSplit[pathSplit.length - 2].split("\\.");
                        String folderName = folderSplit[folderSplit.length - 1];
                        isExecutableSimilar = folderName.toLowerCase(Locale.ROOT).replace(" ", "")
                                .contains(getTitle().toLowerCase(Locale.ROOT).replace(" ", ""));
                    }

                    return isExecutableSimilar;
                })
                .findFirst()
                .or(() -> super.findPossibleProcess(processes));
    }

    public static Builder builder(String title, String description, String executionCommand, int steamAppId) {
        return new Builder(title, description, executionCommand, steamAppId);
    }

    public static class Builder {
        private final String title;
        private final String description;
        private final String executionCommand;
        private final int steamAppId;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private String nickname;

        public Builder(String title, String description, String executionCommand, int steamAppId) {
            this.title = title;
            this.description = description;
            this.executionCommand = executionCommand;
            this.steamAppId = steamAppId;
        }

        public Builder thumbCoverImageURL(String thumbCoverImageURL) {
            this.thumbCoverImageURL = thumbCoverImageURL;
            return this;
        }

        public Builder coverImageURL(String coverImageURL) {
            this.coverImageURL = coverImageURL;
            return this;
        }

        public Builder images(String thumbCoverImageURL, String coverImageURL) {
            this.thumbCoverImageURL = thumbCoverImageURL;
            this.coverImageURL = coverImageURL;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public SteamGame build() {
            var game = new SteamGame(title, description, executionCommand, steamAppId);
            game.setThumbCoverImageURL(thumbCoverImageURL);
            game.setCoverImageURL(coverImageURL);
            game.setNickname(nickname);
            return game;
        }
    }
}
