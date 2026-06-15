package dev.turtywurty.gamedashboard.data.model;

import com.google.gson.annotations.SerializedName;
import dev.turtywurty.gamedashboard.data.game.Game;

import java.util.List;

public record DashboardConfig(
        @SerializedName(value = "steamExecutable", alternate = "steamLocation")
        String steamExecutable,
        String steamLibraryFolders,
        @SerializedName(value = "epicGamesInstallLocations", alternate = "epicInstallLocations")
        List<String> epicInstallLocations
) {
}
