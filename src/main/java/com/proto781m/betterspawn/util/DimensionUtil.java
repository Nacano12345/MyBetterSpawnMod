package com.proto781m.betterspawn.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** 维度相关工具。 */
public final class DimensionUtil {

    private DimensionUtil() {
    }

    /** 把维度键转成字符串 id，如 {@code minecraft:overworld}。 */
    public static String id(ResourceKey<Level> key) {
        return key.location().toString();
    }

    /** 把字符串 id 解析成维度键。 */
    public static ResourceKey<Level> parse(String id) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(id));
    }

}
