package com.proto781m.betterspawn.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.proto781m.betterspawn.BetterSpawn;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * 把 JSON 元素解析成 {@link SpawnRule}。
 *
 * <p>解析失败时返回 {@code null} 并记录日志，不抛出异常，
 * 避免一个坏模板拖垮整个数据包 reload。
 */
public final class SpawnTemplateLoader {

    private SpawnTemplateLoader() {
    }

    public static SpawnRule load(ResourceLocation id, JsonElement element) {
        try {
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("dimension")) {
                throw new IllegalArgumentException("缺少 dimension 字段");
            }
            if (!obj.has("method")) {
                throw new IllegalArgumentException("缺少 method 字段");
            }

            ResourceLocation dimension = ResourceLocation.parse(obj.get("dimension").getAsString());
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

    /** 便捷方法：直接把字符串解析为规则。 */
    public static SpawnRule parse(ResourceLocation id, String json) {
        return load(id, JsonParser.parseString(json));
    }
}
