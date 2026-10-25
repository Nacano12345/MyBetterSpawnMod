package com.proto781m.betterspawn.template;

import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Spawn rule for one dimension, loaded from
 * {@code data/betterspawn/spawn_rules/&lt;dim&gt;.json}.
 *
 * <p>{@code parameters} and {@code fallbackParameters} contain the flattened key/value
 * pairs from the corresponding JSON objects. Values are converted to Java primitives
 * (Integer/Long/Double/Boolean/String) and read through typed getters.
 *
 * @param dimension          dimension ID
 * @param method             main method ID
 * @param parameters         main method parameters
 * @param fallback           fallback method ID; may be {@code null}
 * @param fallbackParameters fallback method parameters
 */
public record SpawnRule(
        ResourceLocation dimension,
        String method,
        Map<String, Object> parameters,
        String fallback,
        Map<String, Object> fallbackParameters
) {

    /** Converts the dimension ID to a {@code ResourceKey<Level>} for the resolver. */
    public ResourceKey<Level> dimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, dimension);
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
