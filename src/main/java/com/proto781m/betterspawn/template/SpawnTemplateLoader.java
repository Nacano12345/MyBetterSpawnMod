package com.proto781m.betterspawn.template;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 把 JSON 元素解析成 {@link SpawnRule}。
 *
 * <p>解析失败时返回 {@code null} 并打日志，不抛出异常（避免一个坏模板拖垮整个 reload）。
 */
public final class SpawnTemplateLoader {

    private SpawnTemplateLoader() {
    }

    public static SpawnRule load(ResourceLocation id, JsonElement element) {
        try {
            JsonObject obj = element.getAsJsonObject();
            ResourceLocation dimension = ResourceLocation.parse(obj.get("dimension").getAsString());
            String method = obj.get("method").getAsString();

            Map<String, Object> params = parseParams(obj.getAsJsonObject("parameters"));
            String fallback = obj.has("fallback") && !obj.get("fallback").isJsonNull()
                    ? obj.get("fallback").getAsString() : null;
            Map<String, Object> fallbackParams = obj.has("fallback_parameters")
                    ? parseParams(obj.getAsJsonObject("fallback_parameters"))
                    : Map.of();

            return new SpawnRule(dimension, method, params, fallback, fallbackParams);
        } catch (Exception ex) {
            NeoForge.EVENT_BUS.toString(); // no-op, keep import stable
            com.proto781m.betterspawn.BetterSpawn.LOGGER.error("Failed to load spawn template {}: {}", id, ex.getMessage());
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
            if (value.isJsonPrimitive()) {
                var prim = value.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    result.put(entry.getKey(), prim.getAsBoolean());
                } else if (prim.isNumber()) {
                    Number n = prim.getAsNumber();
                    // 保留整数/浮点语义
                    result.put(entry.getKey(), n.doubleValue() == Math.rint(n.doubleValue()) && !prim.getAsString().contains(".")
                            ? (Object) n.intValue() : (Object) n.doubleValue());
                } else {
                    result.put(entry.getKey(), prim.getAsString());
                }
            } else {
                result.put(entry.getKey(), value.toString());
            }
        }
        return result;
    }

    /** 便捷方法：直接把字符串解析为规则。 */
    public static SpawnRule parse(ResourceLocation id, String json) {
        return load(id, JsonParser.parseString(json));
    }
}
