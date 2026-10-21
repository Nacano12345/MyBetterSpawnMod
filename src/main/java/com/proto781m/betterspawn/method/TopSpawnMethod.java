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
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 顶部方法（地狱）：从维度顶部向下扫描，找第一个“下方固体、上方空气、非流体”的位置。
 *
 * <p>关键点：地狱有基岩天花板，不能直接用高度图顶部当出生点；
 * 这里从高度图顶部下方开始向下扫描，避免站到基岩屋顶上。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心，默认 0,0</li>
 *   <li>{@code min_y}：最低搜索高度，默认 {@code minBuildHeight + 1}</li>
 *   <li>{@code max_y}：最高搜索高度，默认 {@code maxBuildHeight - 2}</li>
 *   <li>{@code search_radius}：水平搜索半径，默认 128</li>
 *   <li>{@code ring_step}：环带步长，默认 32</li>
 *   <li>{@code avoid_lava}：是否避开岩浆，默认 true</li>
 *   <li>{@code max_chunk_loads}：本次最多生成多少个区块</li>
 * </ul>
 */
public class TopSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "top";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int minY = Math.max(level.getMinBuildHeight() + 1, ctx.rule().getInt("min_y", level.getMinBuildHeight() + 1));
        int maxY = Math.min(level.getMaxBuildHeight() - 2, ctx.rule().getInt("max_y", level.getMaxBuildHeight() - 2));
        int searchRadius = clamp(ctx.rule().getInt("search_radius", 128), 0, 4096);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 32), 8, 512);
        boolean avoidLava = ctx.rule().getBool("avoid_lava", true);
        int maxChunkLoads = clamp(
                ctx.rule().getInt("max_chunk_loads", BetterSpawnConfig.MAX_CHUNK_LOADS.get()),
                1, 4096);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), maxChunkLoads);
        TerrainScanner.LiquidPolicy policy = avoidLava
                ? TerrainScanner.LiquidPolicy.REJECT_LIQUID
                : TerrainScanner.LiquidPolicy.ALLOW_LIQUID;
        float angle = rng.nextFloat() * 360.0F;
        int radiusReached = 0;

        BlockPos center = scanColumn(level, scanner, centerX, centerZ, minY, maxY, policy);
        if (center != null) {
            ctx.trace().ring(0, 1, 1);
            return result(level, dimension, center, angle, scanner, 0);
        }

        for (int radius = ringStep; radius <= searchRadius; radius += ringStep) {
            radiusReached = radius;
            int count = clamp(radius / 16, 8, 32);
            int accepted = 0;
            for (int i = 0; i < count; i++) {
                double radians = (Math.PI * 2.0) * (i + 0.5) / count;
                int x = centerX + (int) Math.round(radius * Math.cos(radians));
                int z = centerZ + (int) Math.round(radius * Math.sin(radians));
                if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
                    continue;
                }
                BlockPos feet = scanColumn(level, scanner, x, z, minY, maxY, policy);
                if (feet != null) {
                    accepted++;
                    ctx.trace().ring(radius, count, accepted);
                    return result(level, dimension, feet, angle, scanner, radius);
                }
            }
            ctx.trace().ring(radius, count, accepted);

            if (scanner.timedOut()) {
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.TIMEOUT,
                        List.of("搜索超时"));
            }
            if (scanner.budgetExhausted()) {
                return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                        List.of("区块加载预算耗尽（" + maxChunkLoads + "），未找到安全点"));
            }
        }

        return failed(dimension, scanner, radiusReached, ResolveResult.Status.UNSAFE,
                List.of("在半径 " + searchRadius + " 内未找到安全点"));
    }

    private static BlockPos scanColumn(ServerLevel level, TerrainScanner scanner,
                                       int x, int z, int minY, int maxY,
                                       TerrainScanner.LiquidPolicy policy) {
        // 必须先确保区块加载，否则 getHeight 会返回 minBuildHeight，被误判为虚空
        if (!scanner.ensureChunk(x, z)) {
            return null;
        }
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (topY <= level.getMinBuildHeight() + 1) {
            return null;
        }
        int fromY = Math.min(maxY, topY - 1);
        return scanner.findBelow(x, z, fromY, minY, policy);
    }

    private static ResolveResult result(ServerLevel level, ResourceKey<Level> dimension, BlockPos pos,
                                        float angle, TerrainScanner scanner, int radius) {
        return new ResolveResult(pos, dimension, angle, "top", false, 0L,
                scanner.samples(), radius, ResolveResult.Status.OK,
                SpawnSafetyValidator.validate(level, pos));
    }

    private static ResolveResult failed(ResourceKey<Level> dimension, TerrainScanner scanner,
                                        int radius, ResolveResult.Status status, List<String> warnings) {
        return new ResolveResult(null, dimension, 0.0F, "top", false, 0L,
                scanner.samples(), radius, status, warnings);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
