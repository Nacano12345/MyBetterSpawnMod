package com.proto781m.betterspawn.logic;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SearchTrace;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.config.CommandSpawnMode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Respawn priority resolver. Combines the three data layers into one chain.
 *
 * <p>Priority (controlled by {@link BetterSpawnConfig#RESPAWN_POLICY}):
 * <ul>
 *   <li>{@link RespawnPolicy#COMMAND_OVER_BED}: command spawn &gt; bed/anchor &gt; player dimension spawn &gt; dimension default spawn</li>
 *   <li>{@link RespawnPolicy#BED_OVER_COMMAND}: bed/anchor &gt; command spawn &gt; player dimension spawn &gt; dimension default spawn</li>
 * </ul>
 *
 * <p>Bed/anchor and command spawns are player-specific and can work across dimensions;
 * the last two layers are looked up in the death dimension.
 */
public final class SpawnPointResolver {

    private SpawnPointResolver() {
    }

    /**
     * Resolves the respawn point after a player dies.
     *
     * @param player         the old player entity
     * @param deathDimension dimension where the player died
     * @return final respawn point; {@code pos == null} means vanilla logic should handle it
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

        // Player's personal world spawn in the death dimension
        Optional<SpawnPoint> personal = PlayerSpawnData.getDimensionSpawn(player, deathDimension);
        if (personal.isPresent()) {
            SpawnPoint point = personal.get();
            ServerLevel level = server.getLevel(point.dimension());
            BlockPos safe = findSafe(level, point.pos());
            BetterSpawn.diag("Respawn chain {}: personal spawn {} -> safe {}",
                    player.getScoreboardName(), point.pos(), safe);
            if (safe != null) {
                return toResult(new SpawnPoint(safe, point.dimension(), point.angle()), "player_dimension_spawn");
            }
            BetterSpawn.diag("Respawn chain {}: personal spawn {} is unsafe; falling through",
                    player.getScoreboardName(), point.pos());
        } else {
            BetterSpawn.diag("Respawn chain {}: no personal spawn for {}",
                    player.getScoreboardName(), deathDimension.identifier());
        }

        // Global default spawn of the death dimension
        ResolveResult world = worldSpawnResult(server, deathDimension);
        if (world != null) {
            BetterSpawn.diag("Respawn chain {}: world spawn {} at {}",
                    player.getScoreboardName(), deathDimension.identifier(), world.pos());
            return world;
        }

        // No personal or dimension spawn exists: the config decides whether to return to the Overworld.
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
     * Runs only the JSON template resolution (used for initial dimension creation and
     * {@code /gotodim}). It does not read any player-specific data.
     */
    public static ResolveResult resolveFromTemplate(MinecraftServer server, ResourceKey<Level> dimension,
                                                    boolean simulate) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return ResolveResult.empty("no_level", ResolveResult.Status.EMPTY);
        }
        Optional<SpawnRule> rule = SpawnTemplateManager.get(dimension.identifier());
        if (rule.isEmpty()) {
            return ResolveResult.empty("no_template", ResolveResult.Status.EMPTY);
        }
        long timeout = Math.max(1, BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get()) * 50L;
        return resolveRule(level, rule.get(), level.getSeed(), simulate,
                System.currentTimeMillis() + timeout, new SearchTrace());
    }

    /** Resolves once using the given rule (used by {@code /gotodim} for temporary overrides). */
    public static ResolveResult resolveRule(ServerLevel level, SpawnRule rule, long seed, boolean simulate,
                                            long deadlineMillis, SearchTrace trace) {
        SpawnContext ctx = new SpawnContext(level, rule, seed, simulate, deadlineMillis, trace);
        return SpawnMethodResolver.INSTANCE.resolve(ctx);
    }

    // ------------------------------------------------------------------
    // Command spawn / bed / anchor
    // ------------------------------------------------------------------

    private static ResolveResult commandResult(ServerPlayer player, MinecraftServer server) {
        Optional<SpawnSlot> slotOpt = PlayerSpawnData.getSlot(player, SpawnSlotType.COMMAND);
        if (slotOpt.isEmpty() || !slotOpt.get().valid()) {
            return null;
        }
        SpawnSlot slot = slotOpt.get();
        ServerLevel level = server.getLevel(slot.dimension());
        if (level == null) {
            return null;
        }

        CommandSpawnMode mode = BetterSpawnConfig.COMMAND_SPAWN_MODE.get();
        return switch (mode) {
            case DEFAULT -> toResult(new SpawnPoint(slot.pos(), slot.dimension(), slot.angle()),
                    "command_slot");
            case BED_ANCHOR -> commandBedAnchorSearch(level, slot);
            case WORLDSPAWN -> commandWorldSpawnSearch(level, slot);
        };
    }

    private static ResolveResult commandBedAnchorSearch(ServerLevel level, SpawnSlot slot) {
        BlockPos safe = SafePosFinder.findSafeAround(level, slot.pos(), 3);
        if (safe == null) {
            return null;
        }
        return toResult(new SpawnPoint(safe, slot.dimension(), slot.angle()),
                "command_slot_bed_anchor");
    }

    private static ResolveResult commandWorldSpawnSearch(ServerLevel level, SpawnSlot slot) {
        Optional<SpawnRule> baseOpt = SpawnTemplateManager.get(slot.dimension());
        if (baseOpt.isEmpty()) {
            return null;
        }
        SpawnRule base = baseOpt.get();
        SpawnRule centered = centeredRule(base, slot.pos());
        long timeout = Math.max(1, BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get()) * 50L;
        ResolveResult result = resolveRule(level, centered, level.getSeed(), false,
                System.currentTimeMillis() + timeout, new SearchTrace());
        if (result.pos() == null || result.status() == ResolveResult.Status.UNSAFE) {
            return null;
        }
        return new ResolveResult(result.pos(), slot.dimension(), slot.angle(),
                "command_slot_worldspawn", result.fallbackUsed(), result.elapsedMillis(),
                result.samples(), result.maxRadiusReached(), result.status(), result.warnings());
    }

    private static SpawnRule centeredRule(SpawnRule base, BlockPos center) {
        Map<String, Object> params = new HashMap<>(base.parameters());
        switch (base.method()) {
            case "fixed" -> {
                params.put("x", center.getX());
                params.put("y", center.getY());
                params.put("z", center.getZ());
            }
            case "platform" -> {
                params.put("x", center.getX());
                params.put("y", center.getY() - 1);
                params.put("z", center.getZ());
            }
            case "portal_type" -> {
                params.put("center_x", center.getX());
                params.put("center_z", center.getZ());
                params.put("platform_x", center.getX());
                params.put("platform_y", center.getY() - 1);
                params.put("platform_z", center.getZ());
            }
            default -> {
                params.put("center_x", center.getX());
                params.put("center_z", center.getZ());
            }
        }

        Map<String, Object> fallbackParams = new HashMap<>(base.fallbackParameters());
        if ("platform".equals(base.fallback())) {
            fallbackParams.put("x", center.getX());
            fallbackParams.put("y", center.getY() - 1);
            fallbackParams.put("z", center.getZ());
        }

        return new SpawnRule(base.dimension(), base.method(), params, base.fallback(), fallbackParams);
    }

    private static ResolveResult bedAnchorResult(ServerPlayer player, MinecraftServer server) {
        // Bed
        Optional<SpawnSlot> bedOpt = PlayerSpawnData.getSlot(player, SpawnSlotType.BED);
        if (bedOpt.isPresent() && bedOpt.get().valid()) {
            SpawnSlot slot = bedOpt.get();
            ServerLevel level = server.getLevel(slot.dimension());
            if (level != null && level.environmentAttributes()
                    .getValue(EnvironmentAttributes.BED_RULE, slot.pos()).canSetSpawn(level)
                    && level.getBlockState(slot.pos()).getBlock() instanceof BedBlock) {
                BlockPos safe = SafePosFinder.findSafeAround(level, slot.pos(), 3);
                if (safe != null) {
                    return toResult(new SpawnPoint(safe, slot.dimension(), slot.angle()), "bed_slot");
                }
            }
        }

        // Respawn anchor
        Optional<SpawnSlot> anchorOpt = PlayerSpawnData.getSlot(player, SpawnSlotType.ANCHOR);
        if (anchorOpt.isPresent() && anchorOpt.get().valid()) {
            SpawnSlot slot = anchorOpt.get();
            ServerLevel level = server.getLevel(slot.dimension());
            if (level != null && RespawnAnchorBlock.canSetSpawn(level, slot.pos())) {
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
    // World default spawn
    // ------------------------------------------------------------------

    private static ResolveResult worldSpawnResult(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        WorldSpawnData data = WorldSpawnData.get(server);
        Optional<SpawnPoint> stored = data.get(dimension);

        SpawnPoint point;
        if (dimension.equals(Level.OVERWORLD) && level != null) {
            // /setworldspawn writes level.dat directly; prefer vanilla data so the vanilla command also works.
            BlockPos vanillaPos = level.getLevelData().getRespawnData().pos();
            float vanillaAngle = level.getLevelData().getRespawnData().yaw();
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
