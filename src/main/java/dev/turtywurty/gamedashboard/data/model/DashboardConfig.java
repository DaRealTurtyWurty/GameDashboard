package dev.turtywurty.gamedashboard.data.model;

import com.google.gson.annotations.SerializedName;
import dev.turtywurty.gamedashboard.data.Game;

import java.util.List;

public record DashboardConfig(
        List<Game> games,
        @SerializedName(value = "steamExecutable", alternate = "steamLocation")
        String steamExecutable,
        String steamLibraryFolders,
        @SerializedName(value = "epicGamesInstallLocations", alternate = "epicInstallLocations")
        List<String> epicInstallLocations
) {
}
