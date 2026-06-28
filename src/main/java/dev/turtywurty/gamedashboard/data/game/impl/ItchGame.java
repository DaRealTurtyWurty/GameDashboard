package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.data.game.LaunchTarget;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
public final class ItchGame extends Game {
    private final long gameId;
    private final String caveId;
    private final String url;
    private final String installPath;

    public ItchGame(
            String title,
            String description,
            LaunchTarget launchTarget,
            long gameId,
            String caveId,
            String url,
            String installPath
    ) {
        super(title, description, launchTarget);
        this.type = "itch";
        this.gameId = gameId;
        this.caveId = caveId;
        this.url = url;
        this.installPath = installPath;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        return game instanceof ItchGame itchGame && this.caveId.equals(itchGame.caveId);
    }

    public static Builder builder(
            String title,
            String description,
            LaunchTarget launchTarget,
            long gameId,
            String caveId,
            String url,
            String installPath
    ) {
        return new Builder(title, description, launchTarget, gameId, caveId, url, installPath);
    }

    public static class Builder {
        private final String title;
        private final String description;
        private final LaunchTarget launchTarget;
        private final long gameId;
        private final String caveId;
        private final String url;
        private final String installPath;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private Integer igdbGameId;
        private String nickname;

        public Builder(
                String title,
                String description,
                LaunchTarget launchTarget,
                long gameId,
                String caveId,
                String url,
                String installPath
        ) {
            this.title = title;
            this.description = description;
            this.launchTarget = launchTarget;
            this.gameId = gameId;
            this.caveId = caveId;
            this.url = url;
            this.installPath = installPath;
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

        public ItchGame build() {
            var game = new ItchGame(title, description, launchTarget, gameId, caveId, url, installPath);
            game.setThumbCoverImageURL(thumbCoverImageURL);
            game.setCoverImageURL(coverImageURL);
            game.setIgdbGameId(igdbGameId);
            game.setNickname(nickname);
            return game;
        }
    }
}
