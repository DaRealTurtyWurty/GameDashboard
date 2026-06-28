package dev.turtywurty.gamedashboard.data.game.impl;

import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.data.game.WindowsAppLaunchTarget;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
public final class MicrosoftStoreGame extends Game {
    private final String packageFamilyName;
    private final String packageFullName;
    private final String storeId;
    private final String titleId;
    private final String installRoot;
    private final String contentRoot;

    public MicrosoftStoreGame(
            String title,
            String description,
            String appUserModelId,
            String packageFamilyName,
            String packageFullName,
            String storeId,
            String titleId,
            String installRoot,
            String contentRoot
    ) {
        super(title, description, new WindowsAppLaunchTarget(appUserModelId));
        this.type = "microsoft_store";
        this.packageFamilyName = packageFamilyName;
        this.packageFullName = packageFullName;
        this.storeId = storeId;
        this.titleId = titleId;
        this.installRoot = installRoot;
        this.contentRoot = contentRoot;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        if (!(game instanceof MicrosoftStoreGame microsoftStoreGame))
            return super.matches(game);

        if (this.packageFamilyName != null && microsoftStoreGame.packageFamilyName != null)
            return this.packageFamilyName.equalsIgnoreCase(microsoftStoreGame.packageFamilyName);

        return super.matches(game);
    }

    public static Builder builder(
            String title,
            String description,
            String appUserModelId,
            String packageFamilyName,
            String packageFullName
    ) {
        return new Builder(title, description, appUserModelId, packageFamilyName, packageFullName);
    }

    public static final class Builder {
        private final String title;
        private final String description;
        private final String appUserModelId;
        private final String packageFamilyName;
        private final String packageFullName;

        private String storeId;
        private String titleId;
        private String installRoot;
        private String contentRoot;
        private String thumbCoverImageURL;
        private String coverImageURL;
        private String coverLogoImageURL;
        private Integer igdbGameId;
        private String nickname;

        private Builder(
                String title,
                String description,
                String appUserModelId,
                String packageFamilyName,
                String packageFullName
        ) {
            this.title = title;
            this.description = description;
            this.appUserModelId = appUserModelId;
            this.packageFamilyName = packageFamilyName;
            this.packageFullName = packageFullName;
        }

        public Builder storeId(String storeId) {
            this.storeId = storeId;
            return this;
        }

        public Builder titleId(String titleId) {
            this.titleId = titleId;
            return this;
        }

        public Builder installRoot(String installRoot) {
            this.installRoot = installRoot;
            return this;
        }

        public Builder contentRoot(String contentRoot) {
            this.contentRoot = contentRoot;
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

        public MicrosoftStoreGame build() {
            var game = new MicrosoftStoreGame(
                    this.title,
                    this.description,
                    this.appUserModelId,
                    this.packageFamilyName,
                    this.packageFullName,
                    this.storeId,
                    this.titleId,
                    this.installRoot,
                    this.contentRoot
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
