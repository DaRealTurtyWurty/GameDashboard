package dev.turtywurty.gamedashboard.data.game;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;

public final class LaunchTargetJsonAdapter implements JsonSerializer<LaunchTarget>, JsonDeserializer<LaunchTarget> {
    @Override
    public JsonElement serialize(LaunchTarget src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        switch (src) {
            case ExecutableLaunchTarget executable -> {
                object.addProperty("kind", "executable");
                object.addProperty("executable", executable.executable().toString());
                object.add("arguments", context.serialize(executable.arguments()));
                if (executable.workingDirectory() == null) {
                    object.add("workingDirectory", JsonNull.INSTANCE);
                } else {
                    object.addProperty("workingDirectory", executable.workingDirectory().toString());
                }
            }
            case UriLaunchTarget uri -> {
                object.addProperty("kind", "uri");
                object.addProperty("uri", uri.uri());
            }
            case WindowsAppLaunchTarget windowsApp -> {
                object.addProperty("kind", "windows_app");
                object.addProperty("appUserModelId", windowsApp.appUserModelId());
            }
        }

        return object;
    }

    @Override
    public LaunchTarget deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject object = json.getAsJsonObject();
        String kind = object.has("kind") && !object.get("kind").isJsonNull()
                ? object.get("kind").getAsString()
                : "";

        return switch (kind) {
            case "executable" -> new ExecutableLaunchTarget(
                    Path.of(requiredString(object, "executable")),
                    context.deserialize(object.get("arguments"), List.class),
                    optionalPath(object, "workingDirectory")
            );
            case "uri" -> new UriLaunchTarget(requiredString(object, "uri"));
            case "windows_app" -> new WindowsAppLaunchTarget(requiredString(object, "appUserModelId"));
            default -> throw new JsonParseException("Unknown launch target kind: " + kind);
        };
    }

    private static String requiredString(JsonObject object, String memberName) {
        if (!object.has(memberName) || object.get(memberName).isJsonNull())
            throw new JsonParseException("Missing launch target field: " + memberName);

        return object.get(memberName).getAsString();
    }

    private static Path optionalPath(JsonObject object, String memberName) {
        if (!object.has(memberName) || object.get(memberName).isJsonNull())
            return null;

        return Path.of(object.get(memberName).getAsString());
    }
}
