package com.proto781m.betterspawn.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(id));
    }

    /** 获取服务器上所有已加载的维度。 */
    public static Collection<ServerLevel> allLevels(MinecraftServer server) {
        if (server.getAllLevels() == null) {
            return List.of();
        }
        List<ServerLevel> list = new ArrayList<>();
        server.getAllLevels().forEach(list::add);
        return list;
    }

    /** 按维度键获取维度，不存在返回 null。 */
    public static ServerLevel level(MinecraftServer server, ResourceKey<Level> key) {
        return server.getLevel(key);
    }
}
