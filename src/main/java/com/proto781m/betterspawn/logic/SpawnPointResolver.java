package com.proto781m.betterspawn.logic;

import java.util.List;
import java.util.Optional;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.config.RespawnPolicy;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.resolver.SpawnMethodResolver;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnPoint;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.resolver.SpawnSafetyValidator;
import com.proto781m.betterspawn.template.SpawnRule;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 重生点优先级解析器：把三层数据 + JSON 模板 fallback 串成一个完整链。
 *
 * <p>优先级（可通过 {@link BetterSpawnConfig#RESPAWN_POLICY} 配置）：
 * <ul>
 *   <li>{@link RespawnPolicy#COMMAND_OVER_BED}：指令出生点 > 床/锚 > 玩家维度出生点 > 维度全局出生点 > 模板 fallback</li>
 *   <li>{@link RespawnPolicy#BED_OVER_COMMAND}：床/锚 > 指令出生点 > 玩家维度出生点 > 维度全局出生点 > 模板 fallback</li>
 * </ul>
 *
 * <p>返回的 {@code ResolveResult} 中的 {@code pos} 为最终站立坐标，角度由对应槽/配置决定。
 */
public final class SpawnPointResolver {

    private SpawnPointResolver() {
    }

    /**
     * 解析玩家在 {@code deathDimension} 死亡时的重生点。
     *
     * @param player           玩家（旧实体，即将被替换）
     * @param deathDimension   玩家死亡所在维度
     * @param server           服务器
     * @return 最终重生维度 + 坐标 + 角度，或 {@code null} 表示交还原版逻辑
     */
    public static ResolveResult resolveRespawn(ServerPlayer player, ResourceKey<Level> deathDimension, MinecraftServer server) {
        RespawnPolicy policy = BetterSpawnConfig.RESPAWN_POLICY.get();

        if (policy == RespawnPolicy.COMMAND_OVER_BED) {
            // 优先级：command > bed/anchor > player_dim > world_spawn > template
            // 1) COMMAND 槽
            Optional<SpawnSlot> cmd = PlayerSpawnData.getSlot(player, SpawnSlotType.COMMAND);
            if (cmd.isPresent() && cmd.get().valid()) {
                return toResult(cmd.get(), "command_slot");
            }
            // 2) BED / ANCHOR 槽
            if (checkBedAnchor(player, deathDimension, server)) {
                return getBedAnchorResult(player, deathDimension, server);
            }
        } else {
            // BED_OVER_COMMAND: bed/anchor > command > player_dim > world_spawn
            // 1) BED / ANCHOR 槽
            if (checkBedAnchor(player, deathDimension, server)) {
                return getBedAnchorResult(player, deathDimension, server);
            }
            // 2) COMMAND 槽
            Optional<SpawnSlot> cmd = PlayerSpawnData.getSlot(player, SpawnSlotType.COMMAND);
            if (cmd.isPresent() && cmd.get().valid()) {
                return toResult(cmd.get(), "command_slot");
            }
        }

        // 3) 玩家该维度个人世界出生点
        Optional<SpawnPoint> dimSpawn = PlayerSpawnData.getDimensionSpawn(player, deathDimension);
        if (dimSpawn.isPresent()) {
            return toResult(dimSpawn.get(), "player_dimension_spawn");
        }

        // 4) 维度全局默认出生点
        WorldSpawnData worldData = WorldSpawnData.get(server);
        Optional<SpawnPoint> global = worldData.get(deathDimension);
        if (global.isPresent()) {
            return toResult(global.get(), "world_spawn");
        }

        // 5) JSON 模板 fallback（只在首次生成或 /gotodim 调用；正常重生不跑模板）
        // 这里返回 EMPTY，让上层决定是否跑模板或用原版主世界出生点
        return ResolveResult.empty("template_fallback", ResolveResult.Status.EMPTY);
    }

    /**
     * 仅跑 JSON 模板解析（用于首次生成维度 / /gotodim）。
     * <p>
     * 此方法<strong>不</strong>读取玩家个人数据（command/bed/anchor 槽、玩家维度出生点），
     * 仅按 JSON 模板解析维度默认出生点，用于首次生成维度或 /gotodim 测试。
     */
    public static ResolveResult resolveFromTemplate(MinecraftServer server, ResourceKey<Level> dimension, boolean simulate) {
        Optional<SpawnRule> ruleOpt = SpawnTemplateManager.get(dimension.location());
        if (ruleOpt.isEmpty()) {
            // 无模板：用原版默认行为（主世界走 default，其他走 platform）
            return fallbackDefault(server, dimension, simulate);
        }
        SpawnRule rule = ruleOpt.get();
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return ResolveResult.empty("no_level", ResolveResult.Status.EMPTY);
        }
        SpawnContext ctx = new SpawnContext(
                level, rule, level.getSeed(), simulate,
                System.currentTimeMillis() + BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get() * 50L,
                new com.proto781m.betterspawn.api.SearchTrace());
        return SpawnMethodResolver.INSTANCE.resolve(ctx);
    }

    private static boolean checkBedAnchor(ServerPlayer player, ResourceKey<Level> deathDimension, MinecraftServer server) {
        // 先查 BED
        Optional<SpawnSlot> bed = PlayerSpawnData.getSlot(player, SpawnSlotType.BED);
        if (bed.isPresent()) {
            SpawnSlot slot = bed.get();
            if (!slot.dimension().equals(deathDimension)) return false;
            ServerLevel level = server.getLevel(slot.dimension());
            if (level == null) return false;
            // 验证床仍在且维度允许
            if (!net.minecraft.world.level.block.BedBlock.canSetSpawn(level)) return false;
            var bedState = level.getBlockState(slot.pos());
            if (!(bedState.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) return false;
            return true;
        }
        // 再查 ANCHOR
        Optional<SpawnSlot> anchor = PlayerSpawnData.getSlot(player, SpawnSlotType.ANCHOR);
        if (anchor.isPresent()) {
            SpawnSlot slot = anchor.get();
            if (!slot.dimension().equals(deathDimension)) return false;
            ServerLevel level = server.getLevel(slot.dimension());
            if (level == null) return false;
            if (!net.minecraft.world.level.block.RespawnAnchorBlock.canSetSpawn(level)) return false;
            var anchorState = level.getBlockState(slot.pos());
            if (anchorState.getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE) <= 0) return false;
            return true;
        }
        return false;
    }

    private static ResolveResult getBedAnchorResult(ServerPlayer player, ResourceKey<Level> deathDimension, MinecraftServer server) {
        // 优先 BED
        Optional<SpawnSlot> bed = PlayerSpawnData.getSlot(player, SpawnSlotType.BED);
        if (bed.isPresent()) {
            SpawnSlot slot = bed.get();
            ServerLevel level = server.getLevel(slot.dimension());
            // 围绕床找安全站立点
            var safe = com.proto781m.betterspawn.util.SafePosFinder.findSafeAround(level, slot.pos(), 3);
            if (safe != null) {
                return toResult(new SpawnPoint(safe, slot.dimension(), slot.angle()), "bed_slot");
            }
        }
        // 再 ANCHOR
        Optional<SpawnSlot> anchor = PlayerSpawnData.getSlot(player, SpawnSlotType.ANCHOR);
        if (anchor.isPresent()) {
            SpawnSlot slot = anchor.get();
            ServerLevel level = server.getLevel(slot.dimension());
            var safe = com.proto781m.betterspawn.util.SafePosFinder.findSafeAround(level, slot.pos(), 3);
            if (safe != null) {
                return toResult(new SpawnPoint(safe, slot.dimension(), slot.angle()), "anchor_slot");
            }
        }
        return ResolveResult.empty("bed_anchor_invalid", ResolveResult.Status.UNSAFE);
    }

    private static ResolveResult toResult(SpawnPoint point, String source) {
        return new ResolveResult(point.pos(), point.dimension(), point.angle(), source, false, 0, 0, 0,
                ResolveResult.Status.OK, List.of());
    }

    private static ResolveResult toResult(SpawnSlot slot, String source) {
        return new ResolveResult(slot.pos(), slot.dimension(), slot.angle(), source, false, 0, 0, 0,
                ResolveResult.Status.OK, List.of());
    }

    private static ResolveResult fallbackDefault(MinecraftServer server, ResourceKey<Level> dimension, boolean simulate) {
        // 兼容原版：主世界走 default，其他走 platform
        return ResolveResult.empty("fallback_default", ResolveResult.Status.EMPTY);
    }
}