package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
public final class GOGGame extends Game {
    private final long productId;
    private final String url;
    private final String slug;

    public GOGGame(String title, String description, String executionCommand, String thumbCoverImageURL, String coverImageURL, String nickname, long productId, String url, String slug) {
        super(title, description, executionCommand, thumbCoverImageURL, coverImageURL, nickname, "gog");
        this.productId = productId;
        this.url = url;
        this.slug = slug;
    }

    public GOGGame(String title, String description, String executionCommand, long productId, String url, String slug) {
        super(title, description, executionCommand);
        this.type = "gog";
        this.productId = productId;
        this.url = url;
        this.slug = slug;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        return game instanceof GOGGame gogGame && this.productId == gogGame.productId;
    }

    public static Builder builder(String title, String description, String executionCommand, long productId, String url, String slug) {
        return new Builder(title, description, executionCommand, productId, url, slug);
    }

    public static class Builder {
        private final String title;
        private final String description;
        private final String executionCommand;
        private final long productId;
        private final String url;
        private final String slug;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private Integer igdbGameId;
        private String nickname;

        public Builder(String title, String description, String executionCommand, long productId, String url, String slug) {
            this.title = title;
            this.description = description;
            this.executionCommand = executionCommand;
            this.productId = productId;
            this.url = url;
            this.slug = slug;
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

        public GOGGame build() {
            var game = new GOGGame(title, description, executionCommand, productId, url, slug);
            game.setThumbCoverImageURL(thumbCoverImageURL);
            game.setCoverImageURL(coverImageURL);
            game.setIgdbGameId(igdbGameId);
            game.setNickname(nickname);
            return game;
        }
    }
}
