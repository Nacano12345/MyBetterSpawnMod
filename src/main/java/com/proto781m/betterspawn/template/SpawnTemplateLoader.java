package com.proto781m.betterspawn.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.proto781m.betterspawn.BetterSpawn;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Parses a JSON element into a {@link SpawnRule}.
 *
 * <p>On parse failure, returns {@code null} and logs the error instead of throwing, so
 * one bad template cannot break the entire datapack reload.
 */
public final class SpawnTemplateLoader {

    private SpawnTemplateLoader() {
    }

    public static SpawnRule load(Identifier id, JsonElement element) {
        try {
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("dimension")) {
                throw new IllegalArgumentException("Missing dimension field");
            }
            if (!obj.has("method")) {
                throw new IllegalArgumentException("Missing method field");
            }

            Identifier dimension = Identifier.parse(obj.get("dimension").getAsString());
            String method = obj.get("method").getAsString();
            Map<String, Object> params = parseParams(obj.getAsJsonObject("parameters"));
            String fallback = obj.has("fallback") && !obj.get("fallback").isJsonNull()
                    ? obj.get("fallback").getAsString()
                    : null;
            Map<String, Object> fallbackParams = obj.has("fallback_parameters")
                    ? parseParams(obj.getAsJsonObject("fallback_parameters"))
                    : Map.of();

            return new SpawnRule(dimension, method, params, fallback, fallbackParams);
        } catch (Exception ex) {
            BetterSpawn.LOGGER.error("Failed to load spawn template {}: {}", id, ex.getMessage());
            return null;
        }
    }

    public static Map<String, Object> parseParams(JsonObject obj) {
        Map<String, Object> result = new HashMap<>();
        if (obj == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) {
                result.put(entry.getKey(), value.toString());
                continue;
            }

            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                result.put(entry.getKey(), primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                String raw = primitive.getAsString();
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    result.put(entry.getKey(), primitive.getAsDouble());
                } else {
                    result.put(entry.getKey(), primitive.getAsInt());
                }
            } else {
                result.put(entry.getKey(), primitive.getAsString());
            }
        }
        return result;
    }

}
