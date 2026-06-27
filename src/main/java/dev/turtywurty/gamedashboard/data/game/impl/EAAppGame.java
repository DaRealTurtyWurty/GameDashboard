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
public final class EAAppGame extends Game {
    private final String softwareId;
    private final String installDataPath;

    public EAAppGame(
            String title,
            String description,
            String executionCommand,
            String thumbCoverImageURL,
            String coverImageURL,
            String nickname,
            String softwareId,
            String installDataPath
    ) {
        super(title, description, executableTarget(executionCommand), thumbCoverImageURL, coverImageURL, nickname, "ea_app");
        this.softwareId = softwareId;
        this.installDataPath = installDataPath;
    }

    public EAAppGame(
            String title,
            String description,
            String executionCommand,
            String softwareId,
            String installDataPath
    ) {
        super(title, description, executableTarget(executionCommand));
        this.type = "ea_app";
        this.softwareId = softwareId;
        this.installDataPath = installDataPath;
    }

    @Override
    public boolean matches(@Nullable Game game) {
        if (!(game instanceof EAAppGame eaAppGame))
            return super.matches(game);

        if (this.softwareId != null && !this.softwareId.isBlank())
            return this.softwareId.equalsIgnoreCase(eaAppGame.softwareId);

        return this.installDataPath.equalsIgnoreCase(eaAppGame.installDataPath);
    }

    private static LaunchTarget executableTarget(String executionCommand) {
        Path executable = Path.of(executionCommand);
        return new ExecutableLaunchTarget(executable, List.of(), executable.getParent());
    }

    public static Builder builder(
            String title,
            String description,
            String executionCommand,
            String softwareId,
            String installDataPath
    ) {
        return new Builder(title, description, executionCommand, softwareId, installDataPath);
    }

    public static final class Builder {
        private final String title;
        private final String description;
        private final String executionCommand;
        private final String softwareId;
        private final String installDataPath;

        private String thumbCoverImageURL;
        private String coverImageURL;
        private String coverLogoImageURL;
        private Integer igdbGameId;
        private String nickname;

        private Builder(
                String title,
                String description,
                String executionCommand,
                String softwareId,
                String installDataPath
        ) {
            this.title = title;
            this.description = description;
            this.executionCommand = executionCommand;
            this.softwareId = softwareId;
            this.installDataPath = installDataPath;
        }

        public Builder images(String thumbCoverImageURL, String coverImageURL) {
            this.thumbCoverImageURL = thumbCoverImageURL;
            this.coverImageURL = coverImageURL;
            return this;
        }

        public Builder coverLogo(String coverLogoImageURL) {
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

        public EAAppGame build() {
            EAAppGame game = new EAAppGame(
                    this.title,
                    this.description,
                    this.executionCommand,
                    this.thumbCoverImageURL,
                    this.coverImageURL,
                    this.nickname,
                    this.softwareId,
                    this.installDataPath
            );
            game.setCoverLogoImageURL(this.coverLogoImageURL);
            game.setIgdbGameId(this.igdbGameId);
            return game;
        }
    }
}
