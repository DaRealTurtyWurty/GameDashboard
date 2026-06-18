package dev.turtywurty.gamedashboard.data.game;

import lombok.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Game {
    protected final String title;
    protected final String description;
    protected final String executionCommand;

    protected String thumbCoverImageURL;
    protected String coverImageURL;
    protected String nickname;
    @Setter(AccessLevel.NONE)
    protected String type = "manual";

    public Process launch() throws IOException {
        return Runtime.getRuntime().exec(this.executionCommand);
    }

    public boolean matches(@Nullable Game game) {
        if (game == null)
            return false;

        String normalizedCommand = normalizeExecutionCommand(executionCommand);
        if (normalizedCommand.isEmpty())
            return false;

        String otherNormalizedCommand = normalizeExecutionCommand(game.getExecutionCommand());
        return normalizedCommand.equals(otherNormalizedCommand);
    }

    public Optional<ProcessHandle> findPossibleProcess(List<ProcessHandle> processes) {
        String normalizedCommand = normalizeExecutionCommand(executionCommand);
        if (normalizedCommand.isEmpty())
            return Optional.empty();

        for (ProcessHandle process : processes) {
            String command = process.info().command().orElse("");
            String normalizedProcessCommand = normalizeExecutionCommand(command);
            if (normalizedProcessCommand.contains(normalizedCommand))
                return Optional.of(process);
        }

        return Optional.empty();
    }

    private static String normalizeExecutionCommand(String executionCommand) {
        return executionCommand != null
                ? executionCommand.trim().toLowerCase(Locale.ROOT)
                : "";
    }
}
