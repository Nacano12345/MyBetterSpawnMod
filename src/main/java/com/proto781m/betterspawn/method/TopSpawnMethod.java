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
 * 顶部方法（地狱）：从维度最高建筑高度向下扫描，找第一个“下方固体、上方空气、非流体”的位置。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code min_y}：最低搜索高度（默认 32）</li>
 *   <li>{@code max_y}：最高搜索高度（默认 256，NeoForge 1.21 通常 320）</li>
 *   <li>{@code avoid_lava}：true 时排除脚下为岩浆的位置（默认 true）</li>
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
        long deadline = ctx.deadlineMillis();
        RandomSource rng = RandomSource.create(ctx.seed());

        int minY = ctx.rule().getInt("min_y", level.getMinBuildHeight() + 1);
        int maxY = ctx.rule().getInt("max_y", level.getMaxBuildHeight() - 1);
        boolean avoidLava = ctx.rule().getBool("avoid_lava", true);

        // 遍历 X/Z，按距离中心排序（可选，简单起见从 0,0 开始螺旋）
        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int searchRadius = ctx.rule().getInt("search_radius", 512);

        int samples = 0;
        for (int radius = 0; radius <= searchRadius; radius++) {
            if (System.currentTimeMillis() > deadline) {
                return ResolveResult.timeout(id());
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;

                    if (!ChunkSearchHelper.chunkExists(level, new BlockPos(x, minY, z))) {
                        continue;
                    }
                    samples++;

                    // 用高度图拿该柱顶部非空气 Y
                    int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    for (int y = Math.min(topY, maxY); y >= minY; y--) {
                        BlockPos ground = new BlockPos(x, y, z);
                        BlockPos stand = ground.above();
                        if (SpawnSafetyValidator.isSafe(level, stand)) {
                            if (avoidLava && level.getBlockState(ground).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                                continue;
                            }
                            List<String> warnings = SpawnSafetyValidator.validate(level, stand);
                            return new ResolveResult(stand, ctx.rule().dimensionKey(), rng.nextFloat() * 360.0F, id(), false,
                                    0, samples, radius, ResolveResult.Status.OK, warnings);
                        }
                    }
                }
            }
        }

        return new ResolveResult(null, ctx.rule().dimensionKey(), 0, id(), false, 0, 0, 0,
                ResolveResult.Status.UNSAFE, List.of());
    }
}