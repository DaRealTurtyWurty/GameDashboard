package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.data.game.ExecutableLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.LaunchTarget;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public final class RiotGame extends Game {
    private final String productId;
    private final String patchline;
    private final String installLocation;
    private final String launcherExecutable;

    public RiotGame(
            String title,
            String description,
            String executionCommand,
            String thumbCoverImageURL,
            String coverImageURL,
            String nickname,
            String productId,
            String patchline,
            String installLocation,
            String launcherExecutable
    ) {
        super(title, description, launchTarget(launcherExecutable, productId, patchline), thumbCoverImageURL, coverImageURL, nickname, "riot");
        this.productId = productId;
        this.patchline = patchline;
        this.installLocation = installLocation;
        this.launcherExecutable = launcherExecutable;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game instanceof RiotGame riotGame)
            return this.productId.equalsIgnoreCase(riotGame.productId)
                    && this.patchline.equalsIgnoreCase(riotGame.patchline);
        return super.matches(game);
    }

    private static LaunchTarget launchTarget(String launcherExecutable, String productId, String patchline) {
        Path launcher = Path.of(launcherExecutable);
        return new ExecutableLaunchTarget(
                launcher,
                List.of(
                "--launch-product=" + productId,
                "--launch-patchline=" + patchline
                ),
                launcher.getParent()
        );
    }

    public static Builder builder(
            String title,
            String description,
            String productId,
            String patchline,
            String installLocation,
            String launcherExecutable
    ) {
        return new Builder(title, description, productId, patchline, installLocation, launcherExecutable);
    }

    public static final class Builder {
        private final String title;
        private final String description;
        private final String productId;
        private final String patchline;
        private final String installLocation;
        private final String launcherExecutable;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private Integer igdbGameId;
        private String nickname;

        private Builder(
                String title,
                String description,
                String productId,
                String patchline,
                String installLocation,
                String launcherExecutable
        ) {
            this.title = title;
            this.description = description;
            this.productId = productId;
            this.patchline = patchline;
            this.installLocation = installLocation;
            this.launcherExecutable = launcherExecutable;
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

        public RiotGame build() {
            String executionCommand = '"' + this.launcherExecutable + '"'
                    + " --launch-product=" + this.productId
                    + " --launch-patchline=" + this.patchline;
            RiotGame game = new RiotGame(
                    this.title,
                    this.description,
                    executionCommand,
                    this.thumbCoverImageURL,
                    this.coverImageURL,
                    this.nickname,
                    this.productId,
                    this.patchline,
                    this.installLocation,
                    this.launcherExecutable
            );
            game.setIgdbGameId(this.igdbGameId);
            return game;
        }
    }
}
