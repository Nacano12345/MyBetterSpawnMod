package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import com.proto781m.betterspawn.util.PlatformBuilder;
import com.proto781m.betterspawn.util.SafePosFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Platform method: builds a platform at the given coordinates as a last-resort
 * spawn point.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code x}, {@code y}, {@code z}: platform center ground coordinates; defaults to 0,64,0</li>
 *   <li>{@code size}: platform side length (odd values are symmetric); defaults to 3</li>
 *   <li>{@code material}: block ID; defaults to {@code minecraft:stone}</li>
 * </ul>
 *
 * <p>When {@code simulate=true}, the method returns coordinates without modifying the world.
 */
public class PlatformSpawnMethod implements SpawnMethod {

    /** Singleton used by the dimension initializer when the template fails completely. */
    public static final PlatformSpawnMethod INSTANCE = new PlatformSpawnMethod();

    private PlatformSpawnMethod() {
    }

    @Override
    public String id() {
        return "platform";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = level.dimension();
        RandomSource rng = RandomSource.create(ctx.seed());

        // Work even without a template: build a default platform rule instead of throwing an NPE.
        var rule = ctx.rule() != null ? ctx.rule() : defaultRule(level);

        int x = rule.getInt("x", 0);
        int y = rule.getInt("y", 64);
        int z = rule.getInt("z", 0);
        int size = Math.max(1, rule.getInt("size", 3));
        if (size % 2 == 0) {
            size++; // Keep the platform center-symmetric
        }
        String materialId = rule.getString("material", "minecraft:stone");

        int minY = level.getMinY() + 1;
        int maxY = level.getMaxY() - 3;
        BlockPos center = new BlockPos(x, Math.max(minY, Math.min(maxY, y)), z);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), 1);
        if (!scanner.ensureChunk(center.getX(), center.getZ())) {
            return new ResolveResult(null, dimension, 0.0F, id(), false, 0L,
                    scanner.samples(), 0, ResolveResult.Status.UNSAFE,
                    List.of("Could not load platform chunk [" + (center.getX() >> 4) + ", " + (center.getZ() >> 4) + "]"));
        }

        BlockState state = PlatformBuilder.resolveState(materialId);
        List<String> warnings = new ArrayList<>();
        warnings.add("Platform: material=" + materialId + ", size=" + size);

        if (ctx.simulate()) {
            warnings.add("simulate mode: platform was not built");
        } else {
            PlatformBuilder.build(level, center, size, state);
            if (!SafePosFinder.isSafeStandingPosition(level, center.above())) {
                warnings.add("Platform was built but the standing position is still unsafe; check the dimension height limits");
            }
        }

        BlockPos standPos = center.above();
        return new ResolveResult(standPos, dimension, rng.nextFloat() * 360.0F, id(), false, 0L,
                scanner.samples(), 0, ResolveResult.Status.OK, warnings);
    }

    private static com.proto781m.betterspawn.template.SpawnRule defaultRule(ServerLevel level) {
        return new com.proto781m.betterspawn.template.SpawnRule(
                level.dimension().location(), "platform",
                Map.of("x", 0, "y", 64, "z", 0, "size", 3, "material", "minecraft:stone"),
                null, Map.of());
    }
}
