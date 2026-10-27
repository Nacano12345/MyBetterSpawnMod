package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;

/**
 * {@code /betterspawn spawnpoint} subcommands (slot-based variant).
 *
 * <p>Arguments may be omitted: the position defaults to the player's feet, the angle
 * defaults to the current yaw, and the dimension defaults to the player's current
 * dimension. Bed and anchor slots store the anchor block position; a safe standing
 * position is resolved around it on respawn.
 */
public final class SpawnPointCommand {

    private SpawnPointCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("spawnpoint")
                .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));

        // /betterspawn spawnpoint set <targets> [<pos>] [<angle>] [<dimension>]
        root.then(CommandSupport.targetPositionCommand("set",
                (ctx, targets, pos, angle, dimension) -> execSet(ctx, targets, pos, angle, dimension)));

        // /betterspawn spawnpoint bed <targets> [<pos>] [<angle>] [<dimension>]
        root.then(slotCommand("bed", SpawnSlotType.BED));

        // /betterspawn spawnpoint anchor <targets> [<pos>] [<angle>] [<dimension>]
        root.then(slotCommand("anchor", SpawnSlotType.ANCHOR));

        // /betterspawn spawnpoint clear <targets> [command|bed|anchor|all]
        root.then(Commands.literal("clear")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execClear(ctx, CommandSupport.players(ctx), "all"))
                        .then(Commands.literal("command").executes(ctx -> execClear(ctx, CommandSupport.players(ctx), "command")))
                        .then(Commands.literal("bed").executes(ctx -> execClear(ctx, CommandSupport.players(ctx), "bed")))
                        .then(Commands.literal("anchor").executes(ctx -> execClear(ctx, CommandSupport.players(ctx), "anchor")))
                        .then(Commands.literal("all").executes(ctx -> execClear(ctx, CommandSupport.players(ctx), "all")))));

        // /betterspawn spawnpoint query <target>
        root.then(Commands.literal("query")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> execQuery(ctx, EntityArgument.getPlayer(ctx, "target")))));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> slotCommand(String name, SpawnSlotType type) {
        return CommandSupport.targetPositionCommand(name,
                (ctx, targets, pos, angle, dimension) -> execSetSlot(
                        ctx, targets, pos, angle, dimension, type));
    }

    /** Registers vanilla-compatible /spawnpoint overrides when the experimental option is enabled. */
    public static void registerVanillaOverride(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnpoint")
                .requires(src -> src.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .executes(ctx -> setVanillaSpawnPoint(ctx.getSource(),
                        List.of(ctx.getSource().getPlayerOrException()),
                        BlockPos.containing(ctx.getSource().getPosition()),
                        WorldCoordinates.ZERO_ROTATION, null))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setVanillaSpawnPoint(ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets"),
                                BlockPos.containing(ctx.getSource().getPosition()),
                                WorldCoordinates.ZERO_ROTATION, null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> setVanillaSpawnPoint(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"),
                                        BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                        WorldCoordinates.ZERO_ROTATION, null))
                                .then(Commands.argument("rotation", RotationArgument.rotation())
                                        .executes(ctx -> setVanillaSpawnPoint(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                                RotationArgument.getRotation(ctx, "rotation"), null))
                                        .then(CommandSupport.dimensionArgument("dimension")
                                                .executes(ctx -> setVanillaSpawnPoint(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                                        RotationArgument.getRotation(ctx, "rotation"),
                                                        CommandSupport.dimension(ctx, "dimension")))))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> setVanillaSpawnPoint(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                                WorldCoordinates.absolute(new Vec2(0.0F,
                                                        FloatArgumentType.getFloat(ctx, "angle"))), null))
                                        .then(CommandSupport.dimensionArgument("dimension")
                                                .executes(ctx -> setVanillaSpawnPoint(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                                        WorldCoordinates.absolute(new Vec2(0.0F,
                                                                FloatArgumentType.getFloat(ctx, "angle"))),
                                                        CommandSupport.dimension(ctx, "dimension"))))))));
    }

    private static int setVanillaSpawnPoint(CommandSourceStack source, Collection<ServerPlayer> targets,
                                             BlockPos pos, Coordinates rotation, ResourceKey<Level> dimension) {
        Vec2 rot = rotation.getRotation(source);
        ResourceKey<Level> actualDimension = dimension != null
                ? dimension
                : source.getLevel().dimension();
        for (ServerPlayer target : targets) {
            PlayerSpawnData.setSlot(target, SpawnSlot.valid(SpawnSlotType.COMMAND, actualDimension, pos, rot.y));
            if (BetterSpawnConfig.SYNC_VANILLA_NBT.get()) {
                // Keep the vanilla NBT mirror in sync, then restore the explicit command angle.
                target.setRespawnPosition(new ServerPlayer.RespawnConfig(
                        net.minecraft.world.level.storage.LevelData.RespawnData.of(
                                actualDimension, pos, rot.y, rot.x), true), false);
                PlayerSpawnData.setSlot(target, SpawnSlot.valid(SpawnSlotType.COMMAND, actualDimension, pos, rot.y));
            }
        }
        Component message = Component.translatable("betterspawn.msg.spawnpoint.set",
                String.valueOf(targets.size()));
        source.sendSuccess(() -> message, true);
        return targets.size();
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    private static int execSet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                               BlockPos pos, Float angle, ResourceKey<Level> dimension) {
        int changed = 0;
        for (ServerPlayer target : targets) {
            BlockPos actualPos = pos != null
                    ? pos
                    : BlockPos.containing(target.getX(), target.getY(), target.getZ());
            float actualAngle = angle != null ? angle : target.getYRot();
            ResourceKey<Level> actualDimension = dimension != null
                    ? dimension
                    : target.level().dimension();

            PlayerSpawnData.setSlot(target, SpawnSlot.valid(
                    SpawnSlotType.COMMAND, actualDimension, actualPos, actualAngle));

            if (BetterSpawnConfig.SYNC_VANILLA_NBT.get()) {
                // forced=true so non-bed/non-anchor coordinates are also written to vanilla NBT
                target.setRespawnPosition(new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(actualDimension, actualPos, actualAngle, 0.0F), true), true);
                // setRespawnPosition triggers PlayerSpawnSlotHandler, which overwrites the slot with the player's current yaw;
                // Write it again here so an explicit angle takes effect.
                PlayerSpawnData.setSlot(target, SpawnSlot.valid(
                        SpawnSlotType.COMMAND, actualDimension, actualPos, actualAngle));
            }
            changed++;
        }

        Component message = Component.translatable("betterspawn.msg.spawnpoint.set",
                String.valueOf(changed));
        ctx.getSource().sendSuccess(() -> message, true);
        return changed > 0 ? 1 : 0;
    }

    private static int execSetSlot(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                   BlockPos pos, Float angle, ResourceKey<Level> dimension, SpawnSlotType type) {
        int changed = 0;
        int invalid = 0;

        for (ServerPlayer target : targets) {
            BlockPos actualPos = pos != null
                    ? pos
                    : BlockPos.containing(target.getX(), target.getY(), target.getZ());
            float actualAngle = angle != null ? angle : target.getYRot();
            ResourceKey<Level> actualDimension = dimension != null
                    ? dimension
                    : target.level().dimension();

            ServerLevel level = target.level().getServer() == null
                    ? null
                    : target.level().getServer().getLevel(actualDimension);
            if (level == null || !matchesSlotBlock(level, actualPos, type)) {
                invalid++;
                continue;
            }

            PlayerSpawnData.setSlot(target, SpawnSlot.valid(type, actualDimension, actualPos, actualAngle));
            changed++;
        }

        if (changed == 0) {
            Component hint = Component.translatable(type == SpawnSlotType.BED
                    ? "betterspawn.msg.spawnpoint.invalid.bed"
                    : "betterspawn.msg.spawnpoint.invalid.anchor");
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.spawnpoint.slot.invalid", String.valueOf(type), hint));
            return 0;
        }

        Component message = Component.translatable("betterspawn.msg.spawnpoint.slot.set",
                String.valueOf(changed), String.valueOf(type));
        if (invalid > 0) {
            message = message.copy().append(Component.translatable(
                    "betterspawn.msg.spawnpoint.slot.skipped", String.valueOf(invalid)));
        }
        Component finalMessage = message;
        ctx.getSource().sendSuccess(() -> finalMessage, true);
        return 1;
    }

    private static boolean matchesSlotBlock(ServerLevel level, BlockPos pos, SpawnSlotType type) {
        BlockState state = level.getBlockState(pos);
        if (type == SpawnSlotType.BED) {
            return level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos).canSetSpawn(level)
                    && state.getBlock() instanceof BedBlock;
        }
        if (type == SpawnSlotType.ANCHOR) {
            return RespawnAnchorBlock.canSetSpawn(level, pos)
                    && state.getBlock() instanceof RespawnAnchorBlock
                    && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
        }
        return true;
    }

    private static int execClear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                 String slotName) {
        for (ServerPlayer target : targets) {
            switch (slotName) {
                case "command" -> PlayerSpawnData.clearSlot(target, SpawnSlotType.COMMAND);
                case "bed" -> PlayerSpawnData.clearSlot(target, SpawnSlotType.BED);
                case "anchor" -> PlayerSpawnData.clearSlot(target, SpawnSlotType.ANCHOR);
                default -> {
                    for (SpawnSlotType type : SpawnSlotType.values()) {
                        PlayerSpawnData.clearSlot(target, type);
                    }
                }
            }
        }
        Component message = Component.translatable("betterspawn.msg.spawnpoint.clear",
                String.valueOf(targets.size()), slotName);
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execQuery(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        MutableComponent message = Component.translatable("betterspawn.msg.spawnpoint.query.header",
                target.getScoreboardName()).append(Component.literal("\n"));

        var dimensionSpawns = PlayerSpawnData.getAllDimensionSpawns(target);
        if (dimensionSpawns.isEmpty()) {
            message.append(Component.translatable("betterspawn.msg.spawnpoint.query.dim_none"))
                    .append(Component.literal("\n"));
        } else {
            dimensionSpawns.forEach((dimension, point) -> message
                    .append(Component.translatable("betterspawn.msg.spawnpoint.query.dim",
                            dimension.identifier().toString(), point.pos().toString(),
                            String.valueOf(point.angle())))
                    .append(Component.literal("\n")));
        }

        var slots = PlayerSpawnData.getSlots(target);
        for (SpawnSlotType type : SpawnSlotType.values()) {
            SpawnSlot slot = slots.get(type);
            if (slot == null) {
                message.append(Component.translatable("betterspawn.msg.spawnpoint.query.slot_none",
                                String.valueOf(type)))
                        .append(Component.literal("\n"));
            } else {
                message.append(Component.translatable("betterspawn.msg.spawnpoint.query.slot",
                                String.valueOf(type), slot.dimension().identifier().toString(),
                                slot.pos().toString(), String.valueOf(slot.angle()),
                                String.valueOf(slot.valid())))
                        .append(Component.literal("\n"));
            }
        }

        Component finalMessage = message;
        ctx.getSource().sendSuccess(() -> finalMessage, false);
        return 1;
    }
}
