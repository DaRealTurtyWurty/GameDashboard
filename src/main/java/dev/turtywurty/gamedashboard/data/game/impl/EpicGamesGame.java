package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public final class EpicGamesGame extends Game {
    private final String epicManifestPath;

    public EpicGamesGame(String title, String description, String executionCommand, String thumbCoverImageURL, String coverImageURL, String nickname, Path epicManifestPath) {
        super(title, description, executionCommand, thumbCoverImageURL, coverImageURL, nickname, "epic_games");
        this.epicManifestPath = epicManifestPath.toString();
    }

    public EpicGamesGame(String title, String description, String executionCommand, Path epicManifestPath) {
        super(title, description, executionCommand);
        this.type = "epic_games";
        this.epicManifestPath = epicManifestPath.toString();
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        return game instanceof EpicGamesGame epicGamesGame && this.epicManifestPath.equals(epicGamesGame.epicManifestPath);
    }

    @Override
    public Optional<ProcessHandle> findPossibleProcess(List<ProcessHandle> processes) {
        return processes.stream()
                .filter(process -> !process.info().command().orElse("").contains("UnityCrashHandler"))
                .filter(process -> !process.info().command().orElse("").contains("EpicWebHelper"))
                .filter(process -> !process.info().command().orElse("").contains("EpicGamesLauncher"))
                .filter(process -> process.info().command().orElse("").toLowerCase().contains("epic"))
                .filter(process -> {
                    String[] pathSplit = process.info().command().orElse("").split("\\\\");
                    String executableName = pathSplit[pathSplit.length - 1].split("\\.")[0];
                    // check to see if it's similar to the game name
                    boolean isExecutableSimilar = executableName.toLowerCase().replace(" ", "")
                            .contains(getTitle().toLowerCase().replace(" ", ""));

                    // if it's not similar then check to see if the containing folder of the executable is similar
                    if (!isExecutableSimilar) {
                        String[] folderSplit = pathSplit[pathSplit.length - 2].split("\\.");
                        String folderName = folderSplit[folderSplit.length - 1];
                        isExecutableSimilar = folderName.toLowerCase().replace(" ", "")
                                .contains(getTitle().toLowerCase().replace(" ", ""));
                    }

                    return isExecutableSimilar;
                })
                .findFirst();
    }

    public static Builder builder(String title, String description, String executionCommand, Path epicManifestPath) {
        return new Builder(title, description, executionCommand, epicManifestPath);
    }

    public static class Builder {
        private final String title;
        private final String description;
        private final String executionCommand;
        private final Path epicManifestPath;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private Integer igdbGameId;
        private String nickname;

        public Builder(String title, String description, String executionCommand, Path epicManifestPath) {
            this.title = title;
            this.description = description;
            this.executionCommand = executionCommand;
            this.epicManifestPath = epicManifestPath;
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

        public Builder igdbGameId(Integer igdbGameId) {
            this.igdbGameId = igdbGameId;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public EpicGamesGame build() {
            var game = new EpicGamesGame(title, description, executionCommand, epicManifestPath);
            game.setThumbCoverImageURL(thumbCoverImageURL);
            game.setCoverImageURL(coverImageURL);
            game.setIgdbGameId(igdbGameId);
            game.setNickname(nickname);
            return game;
        }
    }
}
