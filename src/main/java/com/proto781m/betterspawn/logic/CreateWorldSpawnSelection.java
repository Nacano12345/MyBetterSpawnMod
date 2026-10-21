package com.proto781m.betterspawn.logic;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 创建世界界面的“出生维度”选择。
 *
 * <p>这是进程内的静态选择：客户端创建界面写入，单人/局域网的内置服务端在
 * {@code CreateSpawnPosition} 时读取。专用服务器没有创建界面，保持默认主世界。
 */
public final class CreateWorldSpawnSelection {

    private static final List<ResourceKey<Level>> OPTIONS = List.of(
            Level.OVERWORLD, Level.NETHER, Level.END);

    private static volatile ResourceKey<Level> selected = Level.OVERWORLD;

    private CreateWorldSpawnSelection() {
    }

    public static List<ResourceKey<Level>> options() {
        return OPTIONS;
    }

    public static ResourceKey<Level> get() {
        return selected;
    }

    public static void set(ResourceKey<Level> dimension) {
        selected = dimension == null ? Level.OVERWORLD : dimension;
    }

    public static ResourceKey<Level> cycle() {
        int index = OPTIONS.indexOf(selected);
        selected = OPTIONS.get((Math.max(index, 0) + 1) % OPTIONS.size());
        return selected;
    }

    /** 维度显示名，使用模组自己的 {@code betterspawn.dimension.*} 翻译键。 */
    public static Component displayName(ResourceKey<Level> dimension) {
        String key = "betterspawn.dimension." + dimension.location().getPath();
        return Component.translatableWithFallback(key, dimension.location().toString());
    }

    public static Component displayName() {
        return displayName(selected);
    }
}
