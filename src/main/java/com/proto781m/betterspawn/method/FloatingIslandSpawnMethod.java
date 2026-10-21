package com.proto781m.betterspawn.method;

import java.util.List;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.resolver.ChunkSearchHelper;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 浮岛方法：从中心螺旋/环带向外扩展，直到找到有效陆地。
 *
 * <p>适合主世界孤岛/群岛地形，或自定义维度。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心（默认 0,0）</li>
 *   <li>{@code min_radius}：最小搜索半径（默认 512）</li>
 *   <li>{@code max_radius}：最大搜索半径（默认 8192）</li>
 *   <li>{@code ring_step}：环带步长（默认 128）</li>
 *   <li>{@code avoid_liquid}：是否避开水/岩浆（默认 true）</li>
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
        long deadline = ctx.deadlineMillis();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int minRadius = ctx.rule().getInt("min_radius", 512);
        int maxRadius = ctx.rule().getInt("max_radius", 8192);
        int ringStep = ctx.rule().getInt("ring_step", 128);
        boolean avoidLiquid = ctx.rule().getBool("avoid_liquid", true);

        int totalSamples = 0;
        int maxRadiusReached = 0;

        // 先在 minRadius 内用高度图快速采样（类似 default）
        for (int radius = minRadius; radius <= maxRadius; radius += ringStep) {
            if (System.currentTimeMillis() > deadline) {
                return ResolveResult.timeout(id());
            }
            maxRadiusReached = radius;
            int candidates = 0;
            int accepted = 0;

            // 螺旋扫描当前环带
            for (int angle = 0; angle < 360; angle += 360 / Math.max(1, radius / 16)) {
                if (System.currentTimeMillis() > deadline) {
                    return ResolveResult.timeout(id());
                }
                double rad = Math.toRadians(angle);
                int x = centerX + (int) (radius * Math.cos(rad));
                int z = centerZ + (int) (radius * Math.sin(rad));

                if (!ChunkSearchHelper.chunkExists(level, new BlockPos(x, level.getSeaLevel(), z))) {
                    continue;
                }
                candidates++;

                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos candidate = new BlockPos(x, topY + 1, z);

                if (!SpawnSafetyValidator.isSafe(level, candidate)) {
                    continue;
                }
                if (avoidLiquid && !level.getBlockState(candidate.below()).getFluidState().isEmpty()) {
                    continue;
                }

                accepted++;
                List<String> warnings = SpawnSafetyValidator.validate(level, candidate);
                return new ResolveResult(candidate, ctx.rule().dimensionKey(), rng.nextFloat() * 360.0F, id(), false,
                        0, totalSamples + candidates, maxRadiusReached,
                        ResolveResult.Status.OK, warnings);
            }
            totalSamples += candidates;
            ctx.trace().ring(radius, candidates, accepted);
        }

        return new ResolveResult(null, ctx.rule().dimensionKey(), 0, id(), false, 0, 0, 0,
                ResolveResult.Status.UNSAFE, List.of());
    }
}