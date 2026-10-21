package com.proto781m.betterspawn.template;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * 模板管理器：缓存所有已加载的 {@link SpawnRule}，支持运行时 reload。
 *
 * <p>资源路径约定：{@code data/<namespace>/spawn_rules/<path>.json}
 * 经过 {@code SimpleJsonResourceReloadListener} 后，按 dimension id 索引。
 *
 * <p>内置原版三个维度的默认模板作为兜底，数据包可覆盖。
 */
public final class SpawnTemplateManager {

    private static final Map<ResourceLocation, SpawnRule> RULES = new HashMap<>();
    private static final Gson GSON = new Gson();

    /** 内置原版三个维度的默认模板（兜底，数据包可覆盖）。 */
    private static final Map<ResourceLocation, SpawnRule> BUILTIN_DEFAULTS = createBuiltinDefaults();

    private SpawnTemplateManager() {
    }

    private static Map<ResourceLocation, SpawnRule> createBuiltinDefaults() {
        Map<ResourceLocation, SpawnRule> defaults = new HashMap<>();

        // 主世界：default 方法
        defaults.put(ResourceLocation.parse("minecraft:overworld"), new SpawnRule(
                ResourceLocation.parse("minecraft:overworld"),
                "default",
                Map.of(
                        "center_x", 0,
                        "center_z", 0,
                        "max_radius", 8192,
                        "ring_step", 512,
                        "sample_step", 64
                ),
                "platform",
                Map.of(
                        "x", 0, "y", 64, "z", 0,
                        "size", 3,
                        "material", "minecraft:stone"
                )
        ));

        // 地狱：top 方法
        defaults.put(ResourceLocation.parse("minecraft:the_nether"), new SpawnRule(
                ResourceLocation.parse("minecraft:the_nether"),
                "top",
                Map.of(
                        "min_y", 32,
                        "max_y", 127,
                        "center_x", 0,
                        "center_z", 0,
                        "search_radius", 512,
                        "avoid_lava", true
                ),
                "platform",
                Map.of(
                        "x", 0, "y", 64, "z", 0,
                        "size", 3,
                        "material", "minecraft:netherrack"
                )
        ));

        // 末地：fixed 方法
        defaults.put(ResourceLocation.parse("minecraft:the_end"), new SpawnRule(
                ResourceLocation.parse("minecraft:the_end"),
                "fixed",
                Map.of(
                        "x", 100, "y", 64, "z", 0,
                        "angle", 90.0,
                        "safe_check", true
                ),
                "platform",
                Map.of(
                        "x", 100, "y", 64, "z", 0,
                        "size", 5,
                        "material", "minecraft:end_stone"
                )
        ));

        return defaults;
    }

    /** 由 reload listener 调用，整体替换规则表（数据包优先，内置兜底）。 */
    public static void reload(Map<ResourceLocation, JsonElement> data) {
        Map<ResourceLocation, SpawnRule> parsed = new HashMap<>();

        // 1. 先加载内置默认值
        parsed.putAll(BUILTIN_DEFAULTS);

        // 2. 数据包加载的模板覆盖内置值
        for (Map.Entry<ResourceLocation, JsonElement> entry : data.entrySet()) {
            SpawnRule rule = SpawnTemplateLoader.load(entry.getKey(), entry.getValue());
            if (rule != null) {
                // 用 rule.dimension() (ResourceLocation) 作为 key
                parsed.put(rule.dimension(), rule);
            }
        }

        RULES.clear();
        RULES.putAll(parsed);
        com.proto781m.betterspawn.BetterSpawn.LOGGER.info("Loaded {} spawn template(s) (including {} builtin)", RULES.size(), BUILTIN_DEFAULTS.size());
    }

    public static Optional<SpawnRule> get(ResourceLocation dimension) {
        return Optional.ofNullable(RULES.get(dimension));
    }

    public static Optional<SpawnRule> get(String dimension) {
        return get(ResourceLocation.parse(dimension));
    }

    public static Optional<SpawnRule> get(ResourceKey<Level> dimension) {
        return get(dimension.location());
    }

    public static Collection<SpawnRule> all() {
        return RULES.values();
    }

    public static boolean isEmpty() {
        return RULES.isEmpty();
    }
}