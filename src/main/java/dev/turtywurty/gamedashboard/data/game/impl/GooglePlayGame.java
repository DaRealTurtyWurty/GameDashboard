package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.data.game.LaunchTarget;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
public final class GooglePlayGame extends Game {
    private final String packageName;
    private final String launchUri;
    private final String shortcutPath;
    private final String appIconPath;
    private final String backgroundPath;
    private final String logoPath;

    public GooglePlayGame(
            String title,
            String description,
            LaunchTarget launchTarget,
            String packageName,
            String launchUri,
            String shortcutPath,
            String appIconPath,
            String backgroundPath,
            String logoPath
    ) {
        super(title, description, launchTarget);
        this.type = "google_play";
        this.packageName = packageName;
        this.launchUri = launchUri;
        this.shortcutPath = shortcutPath;
        this.appIconPath = appIconPath;
        this.backgroundPath = backgroundPath;
        this.logoPath = logoPath;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        return game instanceof GooglePlayGame googlePlayGame
                && this.packageName.equalsIgnoreCase(googlePlayGame.packageName);
    }

    public static Builder builder(
            String title,
            String description,
            LaunchTarget launchTarget,
            String packageName,
            String launchUri
    ) {
        return new Builder(title, description, launchTarget, packageName, launchUri);
    }

    public static final class Builder {
        private final String title;
        private final String description;
        private final LaunchTarget launchTarget;
        private final String packageName;
        private final String launchUri;

        private String shortcutPath;
        private String appIconPath;
        private String backgroundPath;
        private String logoPath;
        private String thumbCoverImageURL;
        private String coverImageURL;
        private String coverLogoImageURL;
        private Integer igdbGameId;
        private String nickname;

        private Builder(
                String title,
                String description,
                LaunchTarget launchTarget,
                String packageName,
                String launchUri
        ) {
            this.title = title;
            this.description = description;
            this.launchTarget = launchTarget;
            this.packageName = packageName;
            this.launchUri = launchUri;
        }

        public Builder shortcutPath(String shortcutPath) {
            this.shortcutPath = shortcutPath;
            return this;
        }

        public Builder localAssets(String appIconPath, String backgroundPath, String logoPath) {
            this.appIconPath = appIconPath;
            this.backgroundPath = backgroundPath;
            this.logoPath = logoPath;
            return this;
        }

        public Builder images(String thumbCoverImageURL, String coverImageURL, String coverLogoImageURL) {
            this.thumbCoverImageURL = thumbCoverImageURL;
            this.coverImageURL = coverImageURL;
            this.coverLogoImageURL = coverLogoImageURL;
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

        public GooglePlayGame build() {
            var game = new GooglePlayGame(
                    this.title,
                    this.description,
                    this.launchTarget,
                    this.packageName,
                    this.launchUri,
                    this.shortcutPath,
                    this.appIconPath,
                    this.backgroundPath,
                    this.logoPath
            );
            game.setThumbCoverImageURL(this.thumbCoverImageURL);
            game.setCoverImageURL(this.coverImageURL);
            game.setCoverLogoImageURL(this.coverLogoImageURL);
            game.setIgdbGameId(this.igdbGameId);
            game.setNickname(this.nickname);
            return game;
        }
    }
}
