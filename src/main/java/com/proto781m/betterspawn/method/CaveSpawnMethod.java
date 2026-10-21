package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import com.proto781m.betterspawn.util.SafePosFinder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * 洞穴方法：不依赖天空/高度图，直接扫描“有地面 + 有低矮岩顶”的地下空腔。
 *
 * <p>这使它可以用于地狱、末地或任何没有天空的维度，也可以用于主世界的地下洞穴。
 * 判定一个位置是可用洞穴：
 * <ul>
 *   <li>脚下是实体、非流体方块；</li>
 *   <li>脚部/头部可站立，且上方至少有 {@code min_air_height} 格非流体空气；</li>
 *   <li>在 {@code roof_scan} 格以内能碰到实体方块作为“岩顶”；</li>
 *   <li>岩顶以下到脚部之间没有液体。</li>
 * </ul>
 *
 * <p>如果上方一直开阔到扫描上限都没有岩顶，就认为这是地表/开放空间，跳过。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心，默认 0,0</li>
 *   <li>{@code min_y}, {@code max_y}：垂直扫描范围</li>
 *   <li>{@code search_radius}：水平搜索半径，默认 128</li>
 *   <li>{@code ring_step}：环带步长，默认 32</li>
 *   <li>{@code min_air_height}：脚部上方最少空气高度，默认 2</li>
 *   <li>{@code roof_scan}：向上最多找多少格岩顶，默认 8</li>
 *   <li>{@code avoid_lava}：是否避开岩浆，默认 true</li>
 *   <li>{@code max_chunk_loads}：本次最多加载多少个区块</li>
 * </ul>
 */
public class CaveSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "cave";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int minY = Math.max(level.getMinBuildHeight() + 1,
                ctx.rule().getInt("min_y", level.getMinBuildHeight() + 1));
        int maxY = Math.min(level.getMaxBuildHeight() - 2,
                ctx.rule().getInt("max_y", level.getMaxBuildHeight() - 2));
        int searchRadius = clamp(ctx.rule().getInt("search_radius", 128), 0, 4096);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 32), 8, 512);
        int minAirHeight = clamp(ctx.rule().getInt("min_air_height", 2), 2, 8);
        int roofScan = clamp(ctx.rule().getInt("roof_scan", 8), 3, 32);
        boolean avoidLava = ctx.rule().getBool("avoid_lava", true);
        int maxChunkLoads = clamp(
                ctx.rule().getInt("max_chunk_loads", BetterSpawnConfig.MAX_CHUNK_LOADS.get()),
                1, 4096);

        TerrainScanner scanner = new TerrainScanner(level, ctx.deadlineMillis(), maxChunkLoads);
        float angle = rng.nextFloat() * 360.0F;
        int samples = 0;
        int radiusReached = 0;

        // 1. 中心点
        samples++;
        CaveSite center = findCave(level, scanner, centerX, centerZ, minY, maxY,
                minAirHeight, roofScan, avoidLava);
        if (center != null) {
            ctx.trace().ring(0, 1, 1);
            return result(level, dimension, center, angle, samples, 0);
        }

        // 2. 同心环带
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

                samples++;
                CaveSite site = findCave(level, scanner, x, z, minY, maxY,
                        minAirHeight, roofScan, avoidLava);
                if (site != null) {
                    accepted++;
                    ctx.trace().ring(radius, count, accepted);
                    return result(level, dimension, site, angle, samples, radius);
                }
            }
            ctx.trace().ring(radius, count, accepted);

            if (scanner.timedOut()) {
                return failed(dimension, samples, radiusReached, ResolveResult.Status.TIMEOUT,
                        List.of("Search timed out"));
            }
            if (scanner.budgetExhausted()) {
                return failed(dimension, samples, radiusReached, ResolveResult.Status.UNSAFE,
                        List.of("Chunk budget exhausted (" + maxChunkLoads + "); no cave found"));
            }
        }

        return failed(dimension, samples, radiusReached, ResolveResult.Status.UNSAFE,
                List.of("No cave with a roof found within radius " + searchRadius));
    }

    private record CaveSite(BlockPos feet, int airHeight, int roofY) {
    }

    /**
     * 在一列中从高到低寻找洞穴。找到的第一个满足“有地面、有净空、上方有岩顶”的位置即返回。
     */
    private CaveSite findCave(ServerLevel level, TerrainScanner scanner, int x, int z,
                              int minY, int maxY, int minAirHeight, int roofScan,
                              boolean avoidLava) {
        if (!scanner.ensureChunk(x, z)) {
            return null;
        }

        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!SafePosFinder.isSafeStandingPosition(level, feet)) {
                continue;
            }

            BlockState ground = level.getBlockState(feet.below());
            FluidState groundFluid = ground.getFluidState();
            if (avoidLava && groundFluid.is(net.minecraft.tags.FluidTags.LAVA)) {
                continue;
            }
            if (!groundFluid.isEmpty()) {
                continue;
            }

            int air = 0;
            boolean blockedByLiquid = false;
            for (int h = 0; h < roofScan; h++) {
                BlockPos above = feet.above(h);
                BlockState state = level.getBlockState(above);
                if (!state.getFluidState().isEmpty()) {
                    blockedByLiquid = true;
                    break;
                }
                if (SafePosFinder.isPassable(state)) {
                    air++;
                } else {
                    break; // 碰到岩顶
                }
            }

            if (blockedByLiquid || air < minAirHeight || air >= roofScan) {
                continue;
            }

            // 确认 air 位置确实是实体方块（而不是被 roofScan 截断）
            BlockState roof = level.getBlockState(feet.above(air));
            if (roof.getFluidState().isEmpty() && !SafePosFinder.isPassable(roof)) {
                return new CaveSite(feet, air, feet.getY() + air);
            }
        }
        return null;
    }

    private ResolveResult result(ServerLevel level, ResourceKey<Level> dimension, CaveSite site,
                                 float angle, int samples, int radius) {
        List<String> warnings = new ArrayList<>(SpawnSafetyValidator.validate(level, site.feet()));
        warnings.add("Cave roof y=" + site.roofY() + ", headroom " + site.airHeight() + " blocks");
        return new ResolveResult(site.feet(), dimension, angle, id(), false, 0L,
                samples, radius, ResolveResult.Status.OK, warnings);
    }

    private static ResolveResult failed(ResourceKey<Level> dimension, int samples, int radius,
                                        ResolveResult.Status status, List<String> warnings) {
        return new ResolveResult(null, dimension, 0.0F, "cave", false, 0L,
                samples, radius, status, warnings);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
