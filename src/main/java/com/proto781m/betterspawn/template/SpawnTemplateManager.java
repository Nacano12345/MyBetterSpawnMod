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
 * Template manager: caches all loaded {@link SpawnRule} instances and supports runtime
 * reloads.
 *
 * <p>Resources live in JSON files under {@code data/<namespace>/spawn_rules/...}.
 * Datapack rules are indexed by their {@code dimension} field and override built-in
 * templates for the same dimension.
 *
 * <p>Built-in templates are installed when the class loads, so
 * {@link #get(ResourceLocation)} never returns empty before the server finishes a
 * datapack reload. Missing built-ins were another cause of the early platform fallback
 * during {@code ServerAboutToStartEvent}.
 *
 * <p>The rule table is replaced as a whole through a {@code volatile} reference, so
 * reload and resolution threads never share a mutable HashMap.
 */
public final class SpawnTemplateManager {

    /** Built-in default templates for the three vanilla dimensions; datapacks may override them. */
    private static final Map<ResourceLocation, SpawnRule> BUILTIN_DEFAULTS =
            Map.copyOf(createBuiltinDefaults());

    /** Currently active rules: built-ins plus datapack overrides. Replaced atomically for safe reads. */
    private static volatile Map<ResourceLocation, SpawnRule> rules = BUILTIN_DEFAULTS;

    private SpawnTemplateManager() {
    }

    private static Map<ResourceLocation, SpawnRule> createBuiltinDefaults() {
        Map<ResourceLocation, SpawnRule> defaults = new HashMap<>();

        // Overworld: default method
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

        // Nether: portal_type method (find a portal site; build a platform on failure)
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

        // End: fixed method (a fixed point outside the main island, with a platform fallback)
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
     * Called by the reload listener: replaces the whole rule table. Datapack rules take
     * precedence over built-ins.
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

    /** Ensures built-in templates are installed; normally this already happened at class load. */
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
