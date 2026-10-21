package com.proto781m.betterspawn.template;

import com.google.gson.JsonElement;
import com.proto781m.betterspawn.BetterSpawn;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 模板管理器：缓存所有已加载的 {@link SpawnRule}，支持运行时 reload。
 *
 * <p>资源路径约定：{@code data/<namespace>/spawn_rules/...} 下的 JSON 文件。
 * 数据包规则按 JSON 里的 {@code dimension} 字段索引，并覆盖同名内置模板。
 *
 * <p>内置模板在类加载时就放进规则表，因此即使服务器还没完成数据包 reload，
 * {@link #get(ResourceLocation)} 也不会返回空——这是早期版本在
 * {@code ServerAboutToStartEvent} 阶段全部回退到平台方法的另一个原因。
 *
 * <p>规则表使用 {@code volatile} 整体替换，避免客户端/服务端 reload 线程与解析线程
 * 同时读写同一个 HashMap。
 */
public final class SpawnTemplateManager {

    /** 内置原版三个维度的默认模板（兜底，数据包可覆盖）。 */
    private static final Map<ResourceLocation, SpawnRule> BUILTIN_DEFAULTS =
            Map.copyOf(createBuiltinDefaults());

    /** 当前生效的规则：内置 + 数据包覆盖。整体替换，保证读线程安全。 */
    private static volatile Map<ResourceLocation, SpawnRule> rules = BUILTIN_DEFAULTS;

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
                        "max_radius", 256,
                        "ring_step", 64
                ),
                "platform",
                Map.of(
                        "x", 0, "y", 64, "z", 0,
                        "size", 3,
                        "material", "minecraft:stone"
                )
        ));

        // 地狱：portal_type 方法（寻找可建传送门的位置，失败则生成平台）
        defaults.put(ResourceLocation.parse("minecraft:the_nether"), new SpawnRule(
                ResourceLocation.parse("minecraft:the_nether"),
                "portal_type",
                Map.ofEntries(
                        Map.entry("center_x", 0),
                        Map.entry("center_z", 0),
                        Map.entry("min_y", 32),
                        Map.entry("max_y", 127),
                        Map.entry("search_radius", 128),
                        Map.entry("ring_step", 32),
                        Map.entry("avoid_lava", true),
                        Map.entry("portal_width", 2),
                        Map.entry("portal_height", 3),
                        Map.entry("build_platform", true),
                        Map.entry("platform_x", 0),
                        Map.entry("platform_y", 64),
                        Map.entry("platform_z", 0),
                        Map.entry("platform_size", 5),
                        Map.entry("platform_material", "minecraft:netherrack")
                ),
                "platform",
                Map.of(
                        "x", 0, "y", 64, "z", 0,
                        "size", 5,
                        "material", "minecraft:netherrack"
                )
        ));

        // 末地：fixed 方法（主岛外的定点坐标，由平台方法保底搭台）
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

    /**
     * 由 reload listener 调用：整体替换规则表。数据包规则覆盖内置规则。
     */
    public static void reload(Map<ResourceLocation, JsonElement> data) {
        Map<ResourceLocation, SpawnRule> parsed = new HashMap<>(BUILTIN_DEFAULTS);
        int fromPack = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : data.entrySet()) {
            SpawnRule rule = SpawnTemplateLoader.load(entry.getKey(), entry.getValue());
            if (rule != null) {
                parsed.put(rule.dimension(), rule);
                fromPack++;
            }
        }

        rules = Map.copyOf(parsed);
        BetterSpawn.LOGGER.info("Loaded {} spawn template(s): {} from datapack, {} builtin base",
                rules.size(), fromPack, BUILTIN_DEFAULTS.size());
    }

    /** 确保内置模板已就位（正常情况类加载时已完成，这里只做防御）。 */
    public static void ensureLoaded() {
        if (rules.isEmpty()) {
            rules = BUILTIN_DEFAULTS;
        }
    }

    public static Optional<SpawnRule> get(ResourceLocation dimension) {
        return Optional.ofNullable(rules.get(dimension));
    }

    public static Optional<SpawnRule> get(String dimension) {
        return get(ResourceLocation.parse(dimension));
    }

    public static Optional<SpawnRule> get(ResourceKey<Level> dimension) {
        return get(dimension.location());
    }

    public static Collection<SpawnRule> all() {
        return List.copyOf(rules.values());
    }

}
