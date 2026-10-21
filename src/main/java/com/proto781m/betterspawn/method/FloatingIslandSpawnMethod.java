package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * 浮岛方法：从 {@code min_radius} 开始向外做同心环带搜索，寻找最近的有效陆地。
 *
 * <p>和默认方法的区别是搜索范围更大、样本更密，并且可以跳过中心区域，
 * 适合“中心是虚空/海洋、周围有浮岛”的自定义维度。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心，默认 0,0</li>
 *   <li>{@code min_radius}：起始半径，默认 64</li>
 *   <li>{@code max_radius}：最大半径，默认 2048</li>
 *   <li>{@code ring_step}：环带步长，默认 64</li>
 *   <li>{@code max_chunk_loads}：本次最多生成多少个区块</li>
 * </ul>
 */
public class FloatingIslandSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "floating_island";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int minRadius = clamp(ctx.rule().getInt("min_radius", 64), 0, 8192);
        int maxRadius = clamp(ctx.rule().getInt("max_radius", 2048), minRadius, 16384);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 64), 16, 1024);
        int maxChunkLoads = clamp(
                ctx.rule().getInt("max_chunk_loads", BetterSpawnConfig.MAX_CHUNK_LOADS.get()),
                1, 8192);
        int maxSurfaceDepth = clamp(ctx.rule().getInt("max_surface_depth", 32), 1, 128);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), maxChunkLoads);
        float angle = rng.nextFloat() * 360.0F;
        int radiusReached = 0;

        for (int radius = Math.max(minRadius, ringStep); radius <= maxRadius; radius += ringStep) {
            radiusReached = radius;
            int count = clamp(radius / 16, 16, 64);
            int accepted = 0;
            for (int i = 0; i < count; i++) {
                double radians = (Math.PI * 2.0) * (i + 0.5) / count;
                int x = centerX + (int) Math.round(radius * Math.cos(radians));
                int z = centerZ + (int) Math.round(radius * Math.sin(radians));
                if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
                    continue;
                }

                BlockPos feet = scanner.findSurface(x, z, TerrainScanner.LiquidPolicy.REJECT_LIQUID, maxSurfaceDepth);
                if (feet != null) {
                    accepted++;
                    ctx.trace().ring(radius, count, accepted);
                    return new ResolveResult(feet, dimension, angle, id(), false, 0L,
                            scanner.samples(), radius, ResolveResult.Status.OK,
                            SpawnSafetyValidator.validate(level, feet));
                }
            }
            ctx.trace().ring(radius, count, accepted);

            if (scanner.timedOut()) {
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.TIMEOUT,
                        List.of("搜索超时"));
            }
            if (scanner.budgetExhausted()) {
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                        List.of("区块加载预算耗尽（" + maxChunkLoads + "），未找到陆地"));
            }
        }

        return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                List.of("在半径 " + maxRadius + " 内未找到陆地"));
    }

    private static ResolveResult failed(ResourceKey<Level> dimension, TerrainScanner scanner,
                                        int radius, ResolveResult.Status status, List<String> warnings) {
        return new ResolveResult(null, dimension, 0.0F, "floating_island", false, 0L,
                scanner.samples(), radius, status, warnings);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
