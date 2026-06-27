package dev.turtywurty.gamedashboard.data.game;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ExecutableLaunchTarget(
        Path executable,
        List<String> arguments,
        @Nullable Path workingDirectory
) implements LaunchTarget {
    public ExecutableLaunchTarget {
        if (executable == null)
            throw new IllegalArgumentException("Executable path cannot be null");

        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public ExecutableLaunchTarget(Path executable) {
        this(executable, List.of(), null);
    }

    public ExecutableLaunchTarget(Path executable, List<String> arguments) {
        this(executable, arguments, null);
    }

    public ExecutableLaunchTarget(String executable) {
        this(Path.of(executable));
    }

    public ExecutableLaunchTarget(String executable, List<String> arguments) {
        this(Path.of(executable), arguments, null);
    }

    public static ExecutableLaunchTarget fromCommand(String command) {
        if (command == null || command.isBlank())
            throw new IllegalArgumentException("Command cannot be null or blank");

        Optional<ExecutableLaunchTarget> executableWithExistingPath = fromExistingPath(command.trim());
        if (executableWithExistingPath.isPresent())
            return executableWithExistingPath.get();

        List<String> tokens = tokenizeCommand(command.trim());
        if (tokens.isEmpty())
            throw new IllegalArgumentException("Command cannot be blank");

        Path executable = Path.of(tokens.getFirst());
        return new ExecutableLaunchTarget(
                executable,
                tokens.subList(1, tokens.size()),
                Files.isRegularFile(executable) ? executable.getParent() : null
        );
    }

    @Override
    public Process launch() throws IOException {
        List<String> command = new ArrayList<>(1 + this.arguments.size());
        command.add(this.executable.toString());
        command.addAll(this.arguments);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (this.workingDirectory != null)
            processBuilder.directory(this.workingDirectory.toFile());

        return processBuilder.start();
    }

    private static Optional<ExecutableLaunchTarget> fromExistingPath(String command) {
        List<Integer> splitPoints = new ArrayList<>();
        splitPoints.add(command.length());
        for (int index = command.length() - 1; index >= 0; index--) {
            if (Character.isWhitespace(command.charAt(index)))
                splitPoints.add(index);
        }

        for (int splitPoint : splitPoints) {
            String candidateExecutable = stripWrappingQuotes(command.substring(0, splitPoint).trim());
            if (candidateExecutable.isBlank())
                continue;

            try {
                Path executable = Path.of(candidateExecutable);
                if (!Files.isRegularFile(executable))
                    continue;

                String argumentString = splitPoint >= command.length()
                        ? ""
                        : command.substring(splitPoint).trim();
                return Optional.of(new ExecutableLaunchTarget(
                        executable,
                        tokenizeCommand(argumentString),
                        executable.getParent()
                ));
            } catch (InvalidPathException ignored) {
                // Try the next split point.
            }
        }

        return Optional.empty();
    }

    private static List<String> tokenizeCommand(String command) {
        if (command == null || command.isBlank())
            return List.of();

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < command.length(); index++) {
            char character = command.charAt(index);
            if (character == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(character) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(character);
        }

        if (!current.isEmpty())
            tokens.add(current.toString());

        return tokens;
    }

    private static String stripWrappingQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
            return value.substring(1, value.length() - 1);

        return value;
    }
}
