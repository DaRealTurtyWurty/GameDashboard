package dev.turtywurty.gamedashboard.data.game;

import lombok.Data;
import lombok.Setter;
import lombok.AccessLevel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Data
public class Game {
    protected final String title;
    protected final String description;
    protected final LaunchTarget launchTarget;

    protected String thumbCoverImageURL;
    protected String coverImageURL;
    protected String coverLogoImageURL;
    protected Integer igdbGameId;
    protected String nickname;
    @Setter(AccessLevel.NONE)
    protected String type = "manual";

    public Game(String title, String description, LaunchTarget launchTarget) {
        this.title = title;
        this.description = description;
        this.launchTarget = launchTarget;
    }

    public Game(String title, String description, LaunchTarget launchTarget, String type) {
        this(title, description, launchTarget);
        this.type = type;
    }

    public Game(
            String title,
            String description,
            LaunchTarget launchTarget,
            String thumbCoverImageURL,
            String coverImageURL,
            String nickname,
            String type
    ) {
        this(title, description, launchTarget);
        this.thumbCoverImageURL = thumbCoverImageURL;
        this.coverImageURL = coverImageURL;
        this.nickname = nickname;
        this.type = type;
    }

    public Process launch() throws IOException {
        return this.launchTarget.launch();
    }

    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        if (this.launchTarget == null)
            return false;

        return this.launchTarget.equals(game.getLaunchTarget());
    }

    public Optional<ProcessHandle> findPossibleProcess(List<ProcessHandle> processes) {
        if (!(this.launchTarget instanceof ExecutableLaunchTarget executableLaunchTarget))
            return Optional.empty();

        String executable = executableLaunchTarget.executable().toString().toLowerCase();
        for (ProcessHandle process : processes) {
            String command = process.info().command().orElse("");
            if (command.toLowerCase().contains(executable))
                return Optional.of(process);
        }

        return Optional.empty();
    }
}
