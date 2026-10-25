package com.proto781m.betterspawn.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Dimension helpers. */
public final class DimensionUtil {

    private DimensionUtil() {
    }

    /** Converts a dimension key to its string ID, such as {@code minecraft:overworld}. */
    public static String id(ResourceKey<Level> key) {
        return key.location().toString();
    }

    /** Parses a string ID into a dimension key. */
    public static ResourceKey<Level> parse(String id) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(id));
    }

}
