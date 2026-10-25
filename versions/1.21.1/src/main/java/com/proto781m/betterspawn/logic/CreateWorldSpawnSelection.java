package com.proto781m.betterspawn.logic;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * "Spawn dimension" selection for the create-world screen.
 *
 * <p>This is a process-wide static selection. The client create screen writes it, and
 * the integrated server reads it during {@code CreateSpawnPosition}. Dedicated servers
 * have no create screen and keep the Overworld default.
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

    /** Dimension display name, using the mod's {@code betterspawn.dimension.*} translation keys. */
    public static Component displayName(ResourceKey<Level> dimension) {
        String key = "betterspawn.dimension." + dimension.location().getPath();
        return Component.translatableWithFallback(key, dimension.location().toString());
    }

    public static Component displayName() {
        return displayName(selected);
    }
}
