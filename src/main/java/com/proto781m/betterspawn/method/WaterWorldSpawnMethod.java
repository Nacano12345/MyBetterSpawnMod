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
 * 水世界方法：把水/岩浆等流体视为“空气”，只找非流体固体方块上方的位置。
 *
 * <p>适合海洋世界、岩浆世界等全液体维度。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心（默认 0,0）</li>
 *   <li>{@code max_radius}：最大搜索半径（默认 4096）</li>
 *   <li>{@code ring_step}：环带步长（默认 128）</li>
 *   <li>{@code treat_liquid_as_air}：把流体当空气处理（默认 true，本方法名义即为此）</li>
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
        long deadline = ctx.deadlineMillis();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int maxRadius = ctx.rule().getInt("max_radius", 4096);
        int ringStep = ctx.rule().getInt("ring_step", 128);

        int totalSamples = 0;
        int maxRadiusReached = 0;

        for (int radius = ringStep; radius <= maxRadius; radius += ringStep) {
            if (System.currentTimeMillis() > deadline) {
                return ResolveResult.timeout(id());
            }
            maxRadiusReached = radius;
            int candidates = 0;
            int accepted = 0;

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

                // 不用 MOTION_BLOCKING（它把流体当实体），改用 OCEAN_FLOOR 或手动找第一个非流体实体方块
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                BlockPos ground = new BlockPos(x, y, z);
                BlockPos candidate = ground.above();

                // 关键：下方必须是固体且非流体，当前/上方必须是空气或流体
                if (!level.getBlockState(ground).getFluidState().isEmpty()) {
                    continue; // 下方是流体，跳过
                }
                if (!SpawnSafetyValidator.isSafe(level, candidate)) {
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