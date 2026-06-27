package dev.turtywurty.gamedashboard.data.game;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.turtywurty.gamedashboard.data.game.impl.EAAppGame;
import dev.turtywurty.gamedashboard.data.game.impl.EpicGamesGame;
import dev.turtywurty.gamedashboard.data.game.impl.GOGGame;
import dev.turtywurty.gamedashboard.data.game.impl.RiotGame;
import dev.turtywurty.gamedashboard.data.game.impl.SteamGame;
import dev.turtywurty.gamedashboard.data.game.impl.UbisoftGame;

import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;

public final class GameDeserializer implements JsonDeserializer<Game> {
    private static final Map<String, Class<? extends Game>> TYPES = Map.of(
            "steam", SteamGame.class,
            "epic_games", EpicGamesGame.class,
            "gog", GOGGame.class,
            "ea_app", EAAppGame.class,
            "ubisoft", UbisoftGame.class,
            "riot", RiotGame.class
    );

    @Override
    public Game deserialize(
            JsonElement json,
            Type typeOfT,
            JsonDeserializationContext context
    ) throws JsonParseException {
        JsonObject object = json.getAsJsonObject();
        String type = object.has("type") && object.get("type").isJsonPrimitive()
                ? object.get("type").getAsString().toLowerCase(Locale.ROOT)
                : "manual";
        Class<? extends Game> gameClass = TYPES.get(type);
        if (gameClass != null)
            return context.deserialize(object, gameClass);

        var game = new Game(
                stringValue(object, "title"),
                stringValue(object, "description"),
                context.deserialize(object.get("launchTarget"), LaunchTarget.class),
                stringValue(object, "thumbCoverImageURL"),
                stringValue(object, "coverImageURL"),
                stringValue(object, "nickname"),
                type
        );
        game.setCoverLogoImageURL(stringValue(object, "coverLogoImageURL"));
        if (object.has("igdbGameId") && !object.get("igdbGameId").isJsonNull()) {
            game.setIgdbGameId(object.get("igdbGameId").getAsInt());
        }

        return game;
    }

    private static String stringValue(JsonObject object, String memberName) {
        return object.has(memberName) && !object.get(memberName).isJsonNull()
                ? object.get(memberName).getAsString()
                : null;
    }
}
