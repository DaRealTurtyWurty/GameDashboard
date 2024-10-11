package dev.turtywurty.gamedashboard.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class Game {
    private final String title;
    private final String description;
    private final String executionCommand;

    private String thumbCoverImageURL;
    private String coverImageURL;
    private String nickname;
    private int steamAppId = -1;

    public boolean isSteam() {
        return this.steamAppId != -1;
    }
}
