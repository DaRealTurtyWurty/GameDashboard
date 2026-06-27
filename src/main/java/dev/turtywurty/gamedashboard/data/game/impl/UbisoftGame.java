package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.data.game.ExecutableLaunchTarget;
import dev.turtywurty.gamedashboard.data.game.LaunchTarget;
import dev.turtywurty.gamedashboard.data.game.UriLaunchTarget;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public final class UbisoftGame extends Game {
    private final int ubisoftGameId;
    private final String installLocation;
    private final String launcherExecutable;
    private final String language;

    public UbisoftGame(
            String title,
            String description,
            String executionCommand,
            String thumbCoverImageURL,
            String coverImageURL,
            String nickname,
            int ubisoftGameId,
            String installLocation,
            String launcherExecutable,
            String language
    ) {
        super(title, description, launchTarget(ubisoftGameId, launcherExecutable), thumbCoverImageURL, coverImageURL, nickname, "ubisoft");
        this.ubisoftGameId = ubisoftGameId;
        this.installLocation = installLocation;
        this.launcherExecutable = launcherExecutable;
        this.language = language;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game instanceof UbisoftGame ubisoftGame)
            return this.ubisoftGameId == ubisoftGame.ubisoftGameId;
        return super.matches(game);
    }

    private static LaunchTarget launchTarget(int ubisoftGameId, String launcherExecutable) {
        Path launcher = launcherExecutable == null || launcherExecutable.isBlank()
                ? null
                : Path.of(launcherExecutable);
        String launchUri = "uplay://launch/" + ubisoftGameId + "/0";
        if (launcher == null)
            return new UriLaunchTarget(launchUri);

        return new ExecutableLaunchTarget(launcher, List.of(launchUri), launcher.getParent());
    }

    public static Builder builder(
            String title,
            String description,
            int ubisoftGameId,
            String installLocation,
            String launcherExecutable,
            String language
    ) {
        return new Builder(title, description, ubisoftGameId, installLocation, launcherExecutable, language);
    }

    public static final class Builder {
        private final String title;
        private final String description;
        private final int ubisoftGameId;
        private final String installLocation;
        private final String launcherExecutable;
        private final String language;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private Integer igdbGameId;
        private String nickname;

        private Builder(
                String title,
                String description,
                int ubisoftGameId,
                String installLocation,
                String launcherExecutable,
                String language
        ) {
            this.title = title;
            this.description = description;
            this.ubisoftGameId = ubisoftGameId;
            this.installLocation = installLocation;
            this.launcherExecutable = launcherExecutable;
            this.language = language;
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

        public UbisoftGame build() {
            UbisoftGame game = new UbisoftGame(
                    title,
                    description,
                    "explorer.exe uplay://launch/" + ubisoftGameId + "/0",
                    thumbCoverImageURL,
                    coverImageURL,
                    nickname,
                    ubisoftGameId,
                    installLocation,
                    launcherExecutable,
                    language
            );
            game.setIgdbGameId(igdbGameId);
            return game;
        }
    }
}
