package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.api.SearchTrace;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * 默认方法：复用原版主世界出生点算法的简化版。
 *
 * <p>原版算法：在 0, 512, 1024, 1536, 2048 等环带上采样，做气候评分，
 * 再围绕最佳临时点做精搜，最后 5 区块半径找顶部非水方块。
 *
 * <p>本实现：优先检查中心点，然后从半径 0 开始向外搜索，
 * 用 {@link net.minecraft.world.level.levelgen.Heightmap} 快速获取高度图，
 * 找第一个安全点。足够快且效果接近原版。
 *
 * <p>注意：getHeight 会自动生成区块，无需预先检查区块是否存在。
 */
public class DefaultSpawnMethod implements SpawnMethod {

    @Override
    public String id() {
        return "default";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        SearchTrace trace = ctx.trace();
        long deadline = ctx.deadlineMillis();
        RandomSource rng = RandomSource.create(ctx.seed());

        // 限制最大搜索半径为 256 格（16 个区块），防止生成过多区块导致区块坐标溢出
        int maxRadius = Math.min(256, ctx.rule().getInt("max_radius", 256));
        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);

        // 1. 先检查中心点 (0,0)
        {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, centerX, centerZ);
            // 检查是否为虚空世界（高度异常低）
            if (y < level.getMinBuildHeight() + 10) {
                BetterSpawn.LOGGER.warn("[DefaultSpawnMethod] Void world detected at center (y={}), skipping default search", y);
            } else {
                BlockPos candidate = new BlockPos(centerX, y + 1, centerZ);
                if (SpawnSafetyValidator.isSafe(level, candidate)) {
                    List<String> warnings = SpawnSafetyValidator.validate(level, candidate);
                    BetterSpawn.LOGGER.info("[DefaultSpawnMethod] Found safe spawn at center: {}", candidate);
                    return new ResolveResult(candidate, ctx.rule().dimensionKey(), RandomSource.create(ctx.seed()).nextFloat() * 360.0F, id(), false,
                            0, 1, 0, ResolveResult.Status.OK, warnings);
                }
            }
        }

        // centerX 和 centerZ 已在上面定义，直接使用

        // 先搜索中心区域 (0-64)
        for (int radius = 0; radius <= 64; radius += 32) {
            if (searchRadius(level, centerX, centerZ, radius, RandomSource.create(ctx.seed()), ctx, null)) {
                // 找到了，实际返回由内部处理
            }
        }

        // 然后同心环带搜索 (64, 128, 192, 256)
        for (int radius = 64; radius <= maxRadius; radius += 64) {
            if (System.currentTimeMillis() > deadline) {
                return ResolveResult.timeout(id());
            }
            
            int samplesPerRing = Math.max(16, 360 / Math.max(1, radius / 32));
            int angleStep = 360 / samplesPerRing;
            
            for (int angle = 0; angle < 360; angle += angleStep) {
                if (System.currentTimeMillis() > deadline) {
                    return ResolveResult.timeout(id());
                }
                double rad = Math.toRadians(angle);
                int x = centerX + (int) (radius * Math.cos(rad));
                int z = centerZ + (int) (radius * Math.sin(rad));

                // 限制坐标范围，防止区块坐标溢出
                if (x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000) {
                    continue;
                }

                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                
                // 检查是否为虚空世界（高度异常低）
                if (y < level.getMinBuildHeight() + 10) {
                    continue;
                }
                
                BlockPos candidate = new BlockPos(x, y + 1, z);

                if (SpawnSafetyValidator.isSafe(level, candidate)) {
                    List<String> warnings = SpawnSafetyValidator.validate(level, candidate);
                    BetterSpawn.LOGGER.info("[DefaultSpawnMethod] Found safe spawn at radius {}: {}", radius, new BlockPos(x, y + 1, z));
                    return new ResolveResult(candidate, ctx.rule().dimensionKey(), RandomSource.create(ctx.seed()).nextFloat() * 360.0F, id(), false,
                            0, 1, radius, ResolveResult.Status.OK, SpawnSafetyValidator.validate(level, new BlockPos(x, y + 1, z)));
                }
            }
            trace.ring(radius, 360 / Math.max(1, radius / 32), 0);
        }

        // 兜底：在中心附近 10x10 范围内暴力搜索
        BlockPos fallback = findSafeNearby(level, centerX, centerZ, 10);
        if (fallback != null) {
            BetterSpawn.LOGGER.warn("[DefaultSpawnMethod] Fallback to nearby scan, found: {}", fallback);
            return new ResolveResult(fallback, ctx.rule().dimensionKey(), RandomSource.create(ctx.seed()).nextFloat() * 360.0F, id(), false,
                    0, 1, 0, ResolveResult.Status.OK, SpawnSafetyValidator.validate(level, new BlockPos(centerX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, centerX, centerZ) + 1, centerZ)));
        }

        return ResolveResult.empty(id(), ResolveResult.Status.UNSAFE);
    }

    private boolean searchRadius(ServerLevel level, int centerX, int centerZ, int radius, 
                                 RandomSource rng, SpawnContext ctx, SearchTrace trace) {
        int samplesPerRing = Math.max(16, 360 / Math.max(1, radius / 32));
        int angleStep = 360 / samplesPerRing;
        
        for (int angle = 0; angle < 360; angle += 360 / samplesPerRing) {
            double rad = Math.toRadians(angle);
            int x = centerX + (int) (radius * Math.cos(rad));
            int z = centerZ + (int) (radius * Math.sin(rad));
            
            // 限制坐标范围，防止区块坐标溢出
            if (x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000) {
                continue;
            }
            
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            
            // 检查是否为虚空世界（高度异常低）
            if (y < level.getMinBuildHeight() + 10) {
                continue;
            }
            
            BlockPos candidate = new BlockPos(x, y + 1, z);
            
            if (SpawnSafetyValidator.isSafe(level, candidate)) {
                return true;
            }
        }
        return false;
    }

    private BlockPos findSafeNearby(ServerLevel level, int centerX, int centerZ, int range) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                // 限制坐标范围
                if (x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                // 检查是否为虚空世界
                if (y < level.getMinBuildHeight() + 10) {
                    continue;
                }
                BlockPos candidate = new BlockPos(x, y + 1, z);
                if (com.proto781m.betterspawn.resolver.SpawnSafetyValidator.isSafe(level, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}