package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * 水世界方法：把水/岩浆等流体视为“空气”，只寻找非流体的固体方块。
 *
 * <p>搜索时优先返回干燥的落脚点；只有在找不到干燥位置、且
 * {@code allow_submerged=true} 时，才会返回水下/液体中的固体地面。
 * 这样既能服务海洋世界，也不会在普通世界里把玩家优先丢进海里。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心，默认 0,0</li>
 *   <li>{@code max_radius}：最大半径，默认 2048</li>
 *   <li>{@code ring_step}：环带步长，默认 64</li>
 *   <li>{@code allow_submerged}：是否允许水下落脚，默认 true</li>
 *   <li>{@code max_chunk_loads}：本次最多生成多少个区块</li>
 * </ul>
 */
public class WaterWorldSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "water_world";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int maxRadius = clamp(ctx.rule().getInt("max_radius", 2048), 16, 16384);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 64), 16, 1024);
        boolean allowSubmerged = ctx.rule().getBool("allow_submerged", true);
        int maxChunkLoads = clamp(
                ctx.rule().getInt("max_chunk_loads", BetterSpawnConfig.MAX_CHUNK_LOADS.get()),
                1, 8192);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), maxChunkLoads);
        float angle = rng.nextFloat() * 360.0F;
        int radiusReached = 0;

        BlockPos submerged = null;
        int submergedRadius = 0;

        for (int radius = ringStep; radius <= maxRadius; radius += ringStep) {
            radiusReached = radius;
            int count = clamp(radius / 16, 12, 48);
            int accepted = 0;
            for (int i = 0; i < count; i++) {
                double radians = (Math.PI * 2.0) * (i + 0.5) / count;
                int x = centerX + (int) Math.round(radius * Math.cos(radians));
                int z = centerZ + (int) Math.round(radius * Math.sin(radians));
                if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
                    continue;
                }

                BlockPos feet = scanner.findSurface(x, z, TerrainScanner.LiquidPolicy.ALLOW_LIQUID);
                if (feet == null) {
                    continue;
                }
                accepted++;

                boolean dry = level.getBlockState(feet).getFluidState().isEmpty()
                        && level.getBlockState(feet.above()).getFluidState().isEmpty();
                if (dry) {
                    ctx.trace().ring(radius, count, accepted);
                    return result(level, dimension, feet, angle, scanner, radius, List.of());
                }
                if (allowSubmerged && submerged == null) {
                    submerged = feet;
                    submergedRadius = radius;
                }
            }
            ctx.trace().ring(radius, count, accepted);

            if (scanner.timedOut()) {
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.TIMEOUT,
                        List.of("Search timed out"));
            }
            if (scanner.budgetExhausted()) {
                // 预算用尽时，如果已经找到水下候选点，就返回它
                if (submerged != null) {
                    return result(level, dimension, submerged, angle, scanner, submergedRadius,
                            List.of("Chunk budget reached; returning the submerged/liquid candidate"));
                }
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                        List.of("Chunk budget exhausted (" + maxChunkLoads + "); no solid ground found"));
            }
        }

        if (submerged != null) {
            List<String> warnings = new ArrayList<>(
                    SpawnSafetyValidator.validate(level, submerged));
            warnings.add("No dry land found; returning submerged solid ground");
            return result(level, dimension, submerged, angle, scanner, submergedRadius, warnings);
        }

        return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                List.of("No dry land or solid ground found within radius " + maxRadius));
    }

    private static ResolveResult result(ServerLevel level, ResourceKey<Level> dimension, BlockPos pos,
                                        float angle, TerrainScanner scanner, int radius,
                                        List<String> extraWarnings) {
        List<String> warnings = new ArrayList<>(SpawnSafetyValidator.validate(level, pos));
        warnings.addAll(extraWarnings);
        return new ResolveResult(pos, dimension, angle, "water_world", false, 0L,
                scanner.samples(), radius, ResolveResult.Status.OK, warnings);
    }

    private static ResolveResult failed(ResourceKey<Level> dimension, TerrainScanner scanner,
                                        int radius, ResolveResult.Status status, List<String> warnings) {
        return new ResolveResult(null, dimension, 0.0F, "water_world", false, 0L,
                scanner.samples(), radius, status, warnings);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
