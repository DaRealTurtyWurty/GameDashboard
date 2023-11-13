package domains.brighton.rg764.gamedashboard.view.general;

import domains.brighton.rg764.gamedashboard.data.Game;
import domains.brighton.rg764.gamedashboard.util.ImageCache;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.scene.effect.Glow;
import javafx.scene.text.TextAlignment;
import lombok.Getter;

@Getter
public class GridGameEntry {
    private final Tile tile;
    private final Game game;

    public GridGameEntry(Game game) {
        this.game = game;

        this.tile = TileBuilder.create()
                .skinType(Tile.SkinType.CUSTOM)
                .prefSize(150, 200)
                .textAlignment(TextAlignment.CENTER)
                .text(game.getTitle())
                .textSize(Tile.TextSize.BIGGER)
                .roundedCorners(true)
                .backgroundImage(ImageCache.getImage(game.getCoverImageURL()))
                .backgroundImageOpacity(1)
                .build();

        this.tile.setOnMouseClicked(event -> {
            // TODO: Switch main content pane to game info view
        });

        this.tile.setOnMouseEntered(event -> this.tile.setEffect(new Glow()));
        this.tile.setOnMouseExited(event -> this.tile.setEffect(null));
    }
}
