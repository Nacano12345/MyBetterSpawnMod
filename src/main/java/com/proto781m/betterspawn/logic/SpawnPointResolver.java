package com.proto781m.betterspawn.logic;

import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SearchTrace;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.config.NoPersonalSpawnBehavior;
import com.proto781m.betterspawn.config.RespawnPolicy;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnPoint;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.resolver.SpawnMethodResolver;
import com.proto781m.betterspawn.template.SpawnRule;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import com.proto781m.betterspawn.util.SafePosFinder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 重生点优先级解析器：把三层数据串成完整链。
 *
 * <p>优先级（由 {@link BetterSpawnConfig#RESPAWN_POLICY} 控制）：
 * <ul>
 *   <li>{@link RespawnPolicy#COMMAND_OVER_BED}：指令出生点 &gt; 床/锚 &gt; 玩家维度出生点 &gt; 维度默认出生点</li>
 *   <li>{@link RespawnPolicy#BED_OVER_COMMAND}：床/锚 &gt; 指令出生点 &gt; 玩家维度出生点 &gt; 维度默认出生点</li>
 * </ul>
 *
 * <p>床/锚和指令出生点是“玩家自己的重生点”，可以跨维度生效；后面的两级按死亡维度查找。
 */
public final class SpawnPointResolver {

    private SpawnPointResolver() {
    }

    /**
     * 解析玩家死亡后的重生点。
     *
     * @param player         玩家（旧实体）
     * @param deathDimension 死亡所在维度
     * @return 最终重生点；{@code pos == null} 表示交还原版逻辑
     */
    public static ResolveResult resolveRespawn(ServerPlayer player, ResourceKey<Level> deathDimension,
                                               MinecraftServer server) {
        RespawnPolicy policy = BetterSpawnConfig.RESPAWN_POLICY.get();

        if (policy == RespawnPolicy.COMMAND_OVER_BED) {
            ResolveResult command = commandResult(player, server);
            if (command != null) {
                return command;
            }
            ResolveResult bed = bedAnchorResult(player, server);
            if (bed != null) {
                return bed;
            }
        } else {
            ResolveResult bed = bedAnchorResult(player, server);
            if (bed != null) {
                return bed;
            }
            ResolveResult command = commandResult(player, server);
            if (command != null) {
                return command;
            }
        }

        // 玩家在死亡维度的个人世界出生点
        Optional<SpawnPoint> personal = PlayerSpawnData.getDimensionSpawn(player, deathDimension);
        if (personal.isPresent()) {
            SpawnPoint point = personal.get();
            ServerLevel level = server.getLevel(point.dimension());
            BlockPos safe = findSafe(level, point.pos());
            if (safe != null) {
                return toResult(new SpawnPoint(safe, point.dimension(), point.angle()), "player_dimension_spawn");
            }
        }

        // 死亡维度的全局默认出生点
        ResolveResult world = worldSpawnResult(server, deathDimension);
        if (world != null) {
            return world;
        }

        // 没有任何个人/维度出生点：按配置决定是否回主世界
        if (!deathDimension.equals(Level.OVERWORLD)
                && BetterSpawnConfig.NO_PERSONAL_SPAWN.get() == NoPersonalSpawnBehavior.BACK_TO_OVERWORLD) {
            ResolveResult overworld = worldSpawnResult(server, Level.OVERWORLD);
            if (overworld != null) {
                return overworld;
            }
        }

        return ResolveResult.empty("vanilla", ResolveResult.Status.EMPTY);
    }

    /**
     * 只跑 JSON 模板解析（用于首次生成维度 / {@code /gotodim}）。
     * 不读取任何玩家个人数据。
     */
    public static ResolveResult resolveFromTemplate(MinecraftServer server, ResourceKey<Level> dimension,
                                                    boolean simulate) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return ResolveResult.empty("no_level", ResolveResult.Status.EMPTY);
        }
        Optional<SpawnRule> rule = SpawnTemplateManager.get(dimension.location());
        if (rule.isEmpty()) {
            return ResolveResult.empty("no_template", ResolveResult.Status.EMPTY);
        }
        long timeout = Math.max(1, BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get()) * 50L;
        return resolveRule(level, rule.get(), level.getSeed(), simulate,
                System.currentTimeMillis() + timeout, new SearchTrace());
    }

    /** 使用指定规则解析一次（供 {@code /gotodim} 应用临时覆盖）。 */
    public static ResolveResult resolveRule(ServerLevel level, SpawnRule rule, long seed, boolean simulate,
                                            long deadlineMillis, SearchTrace trace) {
        SpawnContext ctx = new SpawnContext(level, rule, seed, simulate, deadlineMillis, trace);
        return SpawnMethodResolver.INSTANCE.resolve(ctx);
    }

    // ------------------------------------------------------------------
    // 指令出生点 / 床 / 锚
    // ------------------------------------------------------------------

    private static ResolveResult commandResult(ServerPlayer player, MinecraftServer server) {
        Optional<SpawnSlot> slotOpt = PlayerSpawnData.getSlot(player, SpawnSlotType.COMMAND);
        if (slotOpt.isEmpty() || !slotOpt.get().valid()) {
            return null;
        }
        SpawnSlot slot = slotOpt.get();
        ServerLevel level = server.getLevel(slot.dimension());
        BlockPos safe = findSafe(level, slot.pos());
        if (safe == null) {
            return null; // 指令点已被破坏：继续走下一层，而不是直接中断整条链
        }
        return toResult(new SpawnPoint(safe, slot.dimension(), slot.angle()), "command_slot");
    }

    private static ResolveResult bedAnchorResult(ServerPlayer player, MinecraftServer server) {
        // 床
        Optional<SpawnSlot> bedOpt = PlayerSpawnData.getSlot(player, SpawnSlotType.BED);
        if (bedOpt.isPresent() && bedOpt.get().valid()) {
            SpawnSlot slot = bedOpt.get();
            ServerLevel level = server.getLevel(slot.dimension());
            if (level != null && BedBlock.canSetSpawn(level)
                    && level.getBlockState(slot.pos()).getBlock() instanceof BedBlock) {
                BlockPos safe = SafePosFinder.findSafeAround(level, slot.pos(), 3);
                if (safe != null) {
                    return toResult(new SpawnPoint(safe, slot.dimension(), slot.angle()), "bed_slot");
                }
            }
        }

        // 重生锚
        Optional<SpawnSlot> anchorOpt = PlayerSpawnData.getSlot(player, SpawnSlotType.ANCHOR);
        if (anchorOpt.isPresent() && anchorOpt.get().valid()) {
            SpawnSlot slot = anchorOpt.get();
            ServerLevel level = server.getLevel(slot.dimension());
            if (level != null && RespawnAnchorBlock.canSetSpawn(level)) {
                BlockState state = level.getBlockState(slot.pos());
                if (state.getBlock() instanceof RespawnAnchorBlock
                        && state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                    BlockPos safe = SafePosFinder.findSafeAround(level, slot.pos(), 3);
                    if (safe != null) {
                        return toResult(new SpawnPoint(safe, slot.dimension(), slot.angle()), "anchor_slot");
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 世界默认出生点
    // ------------------------------------------------------------------

    private static ResolveResult worldSpawnResult(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        WorldSpawnData data = WorldSpawnData.get(server);
        Optional<SpawnPoint> stored = data.get(dimension);

        SpawnPoint point;
        if (dimension.equals(Level.OVERWORLD) && level != null) {
            // /setworldspawn 直接改 level.dat；让原版数据优先，保证原版命令也能生效
            BlockPos vanillaPos = level.getLevelData().getSpawnPos();
            float vanillaAngle = level.getLevelData().getSpawnAngle();
            if (stored.isEmpty()
                    || !stored.get().pos().equals(vanillaPos)
                    || stored.get().angle() != vanillaAngle) {
                point = new SpawnPoint(vanillaPos, dimension, vanillaAngle);
            } else {
                point = stored.get();
            }
        } else {
            if (stored.isEmpty()) {
                return null;
            }
            point = stored.get();
        }

        BlockPos safe = findSafe(level, point.pos());
        if (safe == null) {
            safe = point.pos();
        }
        return toResult(new SpawnPoint(safe, dimension, point.angle()), "world_spawn");
    }

    private static BlockPos findSafe(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        if (SafePosFinder.isSafeStandingPosition(level, pos)) {
            return pos;
        }
        return SafePosFinder.findSafeAround(level, pos, 3);
    }

    private static ResolveResult toResult(SpawnPoint point, String source) {
        return new ResolveResult(point.pos(), point.dimension(), point.angle(), source, false,
                0L, 0, 0, ResolveResult.Status.OK, List.of());
    }
}
