package domains.brighton.rg764.gamedashboard.data;

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

    private String coverImageURL;
    private String nickname;
}
