package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
public final class BattleNetGame extends Game {
    private final String productCode;
    private final String uid;
    private final String installPath;

    public BattleNetGame(
            String title,
            String description,
            String executionCommand,
            String thumbCoverImageURL,
            String coverImageURL,
            String nickname,
            String productCode,
            String uid,
            String installPath
    ) {
        super(title, description, executionCommand, thumbCoverImageURL, coverImageURL, nickname, "battle_net");
        this.productCode = productCode;
        this.uid = uid;
        this.installPath = installPath;
    }

    public BattleNetGame(
            String title,
            String description,
            String executionCommand,
            String productCode,
            String uid,
            String installPath
    ) {
        super(title, description, executionCommand);
        this.type = "battle_net";
        this.productCode = productCode;
        this.uid = uid;
        this.installPath = installPath;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        return game instanceof BattleNetGame other &&
                this.productCode.equalsIgnoreCase(other.productCode);
    }

    public static Builder builder(
            String title,
            String description,
            String executionCommand,
            String productCode,
            String uid,
            String installPath
    ) {
        return new Builder(title, description, executionCommand, productCode, uid, installPath);
    }

    public static class Builder {
        private final String title;
        private final String description;
        private final String executionCommand;
        private final String productCode;
        private final String uid;
        private final String installPath;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private Integer igdbGameId;
        private String nickname;

        public Builder(
                String title,
                String description,
                String executionCommand,
                String productCode,
                String uid,
                String installPath
        ) {
            this.title = title;
            this.description = description;
            this.executionCommand = executionCommand;
            this.productCode = productCode;
            this.uid = uid;
            this.installPath = installPath;
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

        public BattleNetGame build() {
            var game = new BattleNetGame(
                    this.title,
                    this.description,
                    this.executionCommand,
                    this.productCode,
                    this.uid,
                    this.installPath
            );
            game.setThumbCoverImageURL(this.thumbCoverImageURL);
            game.setCoverImageURL(this.coverImageURL);
            game.setIgdbGameId(this.igdbGameId);
            game.setNickname(this.nickname);
            return game;
        }
    }
}
