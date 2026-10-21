package com.proto781m.betterspawn.method;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.api.SpawnMethod;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.resolver.TerrainScanner;
import com.proto781m.betterspawn.util.PlatformBuilder;
import com.proto781m.betterspawn.util.SafePosFinder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 传送门选址方法（地狱推荐）：寻找一个能放下传送门内部空间的安全位置。
 *
 * <p>本方法只负责“选址”，不会实际放置黑曜石或点亮传送门。
 *
 * <p>判定一个位置“可建传送门”需要：
 * <ul>
 *   <li>第一列（玩家实际站立列）脚下是实体、非流体方块；</li>
 *   <li>传送门内部（默认 2 宽 × 3 高）全部是空气/可替换方块，且没有液体；</li>
 *   <li>玩家自身脚部/头部安全。</li>
 * </ul>
 *
 * <p>找不到天然位置时，可由 {@code build_platform=true} 生成一个带净空的平台，
 * 平台上方会清理出足够建造传送门的空间。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code center_x}, {@code center_z}：搜索中心，默认 0,0</li>
 *   <li>{@code min_y}, {@code max_y}：垂直搜索范围，默认 32..127</li>
 *   <li>{@code search_radius}：水平搜索半径，默认 128</li>
 *   <li>{@code ring_step}：环带步长，默认 32</li>
 *   <li>{@code portal_width}, {@code portal_height}：传送门内部尺寸，默认 2x3</li>
 *   <li>{@code avoid_lava}：是否避开岩浆，默认 true</li>
 *   <li>{@code build_platform}：找不到天然位置时是否生成平台，默认 true</li>
 *   <li>{@code platform_x/y/z}：平台中心地面坐标，默认搜索中心 + y=64</li>
 *   <li>{@code platform_size}：平台边长，默认 5（会自动调整为奇数）</li>
 *   <li>{@code platform_material}：平台方块，默认 {@code minecraft:netherrack}</li>
 *   <li>{@code max_chunk_loads}：本次最多加载多少个区块</li>
 * </ul>
 */
public class PortalTypeSpawnMethod implements SpawnMethod {

    private record Site(BlockPos feet, boolean alongX) {
    }

    @Override
    public String id() {
        return "portal_type";
    }

    @Override
    public ResolveResult resolve(SpawnContext ctx) {
        ServerLevel level = ctx.level();
        ResourceKey<Level> dimension = ctx.rule().dimensionKey();
        RandomSource rng = RandomSource.create(ctx.seed());

        int centerX = ctx.rule().getInt("center_x", 0);
        int centerZ = ctx.rule().getInt("center_z", 0);
        int minY = Math.max(level.getMinBuildHeight() + 1,
                ctx.rule().getInt("min_y", level.getMinBuildHeight() + 32));
        int maxY = Math.min(level.getMaxBuildHeight() - 2,
                ctx.rule().getInt("max_y", 127));
        int searchRadius = clamp(ctx.rule().getInt("search_radius", 128), 0, 4096);
        int ringStep = clamp(ctx.rule().getInt("ring_step", 32), 8, 512);
        int portalWidth = clamp(ctx.rule().getInt("portal_width", 2), 2, 4);
        int portalHeight = clamp(ctx.rule().getInt("portal_height", 3), 3, 5);
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
        Site center = findSite(level, scanner, centerX, centerZ, minY, maxY,
                portalWidth, portalHeight, avoidLava);
        if (center != null) {
            ctx.trace().ring(0, 1, 1);
            return naturalResult(level, dimension, center, angle, samples, 0, portalWidth, portalHeight);
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
                Site site = findSite(level, scanner, x, z, minY, maxY,
                        portalWidth, portalHeight, avoidLava);
                if (site != null) {
                    accepted++;
                    ctx.trace().ring(radius, count, accepted);
                    return naturalResult(level, dimension, site, angle, samples, radius,
                            portalWidth, portalHeight);
                }
            }
            ctx.trace().ring(radius, count, accepted);

            if (scanner.timedOut()) {
                return failed(dimension, samples, radiusReached, ResolveResult.Status.TIMEOUT,
                        List.of("Search timed out"));
            }
            if (scanner.budgetExhausted()) {
                break;
            }
        }

        // 3. 保底：生成一个带净空的平台
        if (ctx.rule().getBool("build_platform", true)) {
            return buildPlatform(ctx, level, dimension, angle, samples, radiusReached,
                    portalWidth, portalHeight, centerX, centerZ);
        }

        return failed(dimension, samples, radiusReached, ResolveResult.Status.UNSAFE,
                List.of("No portal site found within radius " + searchRadius));
    }

    // ------------------------------------------------------------------

    /**
     * 在 (x,z) 列从高到低寻找第一个能容纳传送门的安全位置。
     * 同一个 x/z 可能有多层平台，所以不能只取“第一个安全点”，要一直向下试。
     */
    private Site findSite(ServerLevel level, TerrainScanner scanner, int x, int z,
                          int minY, int maxY, int width, int height, boolean avoidLava) {
        if (!scanner.ensureChunk(x, z)) {
            return null;
        }

        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!SafePosFinder.isSafeStandingPosition(level, feet)) {
                continue;
            }
            if (avoidLava && level.getBlockState(feet.below()).getFluidState().is(
                    net.minecraft.tags.FluidTags.LAVA)) {
                continue;
            }

            // 先试 X 方向，再试 Z 方向
            if (hasPortalSpace(level, x, z, y, true, width, height)) {
                return new Site(feet, true);
            }
            if (hasPortalSpace(level, x, z, y, false, width, height)) {
                return new Site(feet, false);
            }
        }
        return null;
    }

    /**
     * 检查从 {@code (x, y, z)} 开始、沿 X 或 Z 方向延伸的传送门内部空间。
     * 内部底部就是玩家脚部 y，底部框架在 y-1。
     */
    private boolean hasPortalSpace(ServerLevel level, int x, int y, int z,
                                   boolean alongX, int width, int height) {
        for (int w = 0; w < width; w++) {
            int px = alongX ? x + w : x;
            int pz = alongX ? z : z + w;

            // 第一列是玩家实际站立/出生的一列，必须有实体地面；
            // 其余列只要求内部是空气，底部框架可以贴着旁边的地面放置或在空中放置。
            if (w == 0) {
                BlockPos ground = new BlockPos(px, y - 1, pz);
                if (!SafePosFinder.isSafeGround(level, ground)) {
                    return false;
                }
            }

            for (int h = 0; h < height; h++) {
                BlockPos inside = new BlockPos(px, y + h, pz);
                BlockState state = level.getBlockState(inside);
                if (!SafePosFinder.isPassable(state) || !state.getFluidState().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private ResolveResult naturalResult(ServerLevel level, ResourceKey<Level> dimension,
                                        Site site, float angle, int samples, int radius,
                                        int width, int height) {
        BlockPos feet = site.feet();
        List<String> warnings = new ArrayList<>(SpawnSafetyValidator.validate(level, feet));
        warnings.add("Portal bottom frame: " + feet.below()
                + ", interior " + width + "x" + height
                + ", facing " + (site.alongX() ? "X+" : "Z+"));
        warnings.add("Spawn point is inside the portal; step out before building");
        return new ResolveResult(feet, dimension, angle, id(), false, 0L,
                samples, radius, ResolveResult.Status.OK, warnings);
    }

    private ResolveResult buildPlatform(SpawnContext ctx, ServerLevel level,
                                        ResourceKey<Level> dimension, float angle,
                                        int samples, int radius, int portalWidth, int portalHeight,
                                        int centerX, int centerZ) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 3;

        int px = clamp(ctx.rule().getInt("platform_x", centerX), -30_000_000, 30_000_000);
        int py = clamp(ctx.rule().getInt("platform_y", 64), minY, maxY);
        int pz = clamp(ctx.rule().getInt("platform_z", centerZ), -30_000_000, 30_000_000);
        int size = clamp(ctx.rule().getInt("platform_size", 5), 3, 9);
        if (size % 2 == 0) {
            size++;
        }
        String material = ctx.rule().getString("platform_material", "minecraft:netherrack");
        int clearHeight = portalHeight + 1;

        BlockPos platformCenter = new BlockPos(px, py, pz);
        TerrainScanner platformScanner = new TerrainScanner(level, ctx.deadlineMillis(), 1);
        if (!platformScanner.ensureChunk(px, pz)) {
            return failed(dimension, samples, radius, ResolveResult.Status.UNSAFE,
                    List.of("Could not load platform chunk [" + (px >> 4) + ", " + (pz >> 4) + "]"));
        }

        BlockState state = PlatformBuilder.resolveState(material);
        if (!ctx.simulate()) {
            PlatformBuilder.build(level, platformCenter, size, state, clearHeight);
        }

        BlockPos feet = platformCenter.above();
        List<String> warnings = new ArrayList<>();
        warnings.add("No natural portal site; built a " + size + "x" + size + " platform at "
                + platformCenter);
        warnings.add("Cleared " + clearHeight + " blocks above the platform; fits a "
                + portalWidth + "x" + portalHeight + " portal");
        if (ctx.simulate()) {
            warnings.add("simulate mode: platform was not built");
        } else if (!SafePosFinder.isSafeStandingPosition(level, feet)) {
            warnings.add("Platform was built but the standing position is still unsafe; check height limits");
        }

        return new ResolveResult(feet, dimension, angle, id(), false, 0L,
                samples, radius, ResolveResult.Status.OK, warnings);
    }

    private static ResolveResult failed(ResourceKey<Level> dimension, int samples, int radius,
                                        ResolveResult.Status status, List<String> warnings) {
        return new ResolveResult(null, dimension, 0.0F, "portal_type", false, 0L,
                samples, radius, status, warnings);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
