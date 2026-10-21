package com.proto781m.betterspawn.template;

import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 单个维度的出生点规则（来自 {@code data/betterspawn/spawn_rules/&lt;dim&gt;.json}）。
 *
 * <p>{@code parameters} / {@code fallbackParameters} 是 JSON 中对应对象的扁平键值，
 * 值统一转成 Java 基本类型（Integer/Long/Double/Boolean/String），用 typed getter 读取。
 *
 * @param dimension          维度 id
 * @param method             主方法 id
 * @param parameters         主方法参数
 * @param fallback           回退方法 id（可为 null）
 * @param fallbackParameters 回退方法参数
 */
public record SpawnRule(
        ResourceLocation dimension,
        String method,
        Map<String, Object> parameters,
        String fallback,
        Map<String, Object> fallbackParameters
) {

    /** 将 dimension 转换为 ResourceKey<Level>，供解析器使用。 */
    public ResourceKey<Level> dimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, dimension);
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public Map<String, Object> fallbackParameters() {
        return fallbackParameters;
    }

    public int getInt(String key, int def) {
        Object v = parameters.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public double getDouble(String key, double def) {
        Object v = parameters.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public boolean getBool(String key, boolean def) {
        Object v = parameters.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public String getString(String key, String def) {
        Object v = parameters.get(key);
        return v instanceof String s ? s : def;
    }
}
