package dev.turtywurty.gamedashboard.data.model;

public record SteamGameCacheEntry(
        int appId,
        String name,
        String executionPath,
        String thumbCoverImageURL,
        String coverImageURL
) {
}
