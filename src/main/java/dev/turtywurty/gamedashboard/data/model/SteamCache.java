package dev.turtywurty.gamedashboard.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record SteamCache(
        String steamLocation,
        @SerializedName(value = "cache", alternate = "games")
        List<SteamGameCacheEntry> games
) {
}
