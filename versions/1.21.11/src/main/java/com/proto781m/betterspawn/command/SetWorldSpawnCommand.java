package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.logic.DimensionSpawnInitializer;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

/**
 * {@code /betterspawn worldspawn} subcommands (an extended {@code setworldspawn}).
 *
 * <p>Arguments may be omitted: the dimension defaults to the current dimension, the
 * position defaults to the player's feet, and the angle defaults to the current yaw.
 *
 * <pre>
 * /betterspawn worldspawn global [&lt;pos&gt; | &lt;dimension&gt;] [&lt;pos&gt;] [&lt;angle&gt;]
 * /betterspawn worldspawn player &lt;targets&gt; [&lt;pos&gt; | &lt;dimension&gt;] [&lt;pos&gt;] [&lt;angle&gt;]
 * /betterspawn worldspawn clear [&lt;dimension&gt;] [&lt;targets&gt;]
 * /betterspawn worldspawn query [&lt;dimension&gt;] [&lt;target&gt;]
 * /betterspawn worldspawn regenerate [&lt;dimension&gt;]
 * /betterspawn worldspawn list
 * /betterspawn worldspawn reload
 * </pre>
 */
public final class SetWorldSpawnCommand {

    private SetWorldSpawnCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("worldspawn")
                .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));

        // /betterspawn worldspawn global [<pos>] [<angle>] [<dimension>]
        root.then(CommandSupport.positionCommand("global",
                (ctx, targets, pos, angle, dimension) -> execGlobal(ctx,
                        dimension != null ? dimension : CommandSupport.currentDimension(ctx),
                        pos != null ? pos : CommandSupport.currentFeet(ctx),
                        angle != null ? angle : CommandSupport.currentYaw(ctx))));

        // /betterspawn worldspawn player <targets> [<pos>] [<angle>] [<dimension>]
        root.then(CommandSupport.targetPositionCommand("player",
                (ctx, targets, pos, angle, dimension) -> execPlayer(ctx, targets, dimension, pos, angle)));

        // clear [dimension] [targets]
        root.then(Commands.literal("clear")
                .executes(ctx -> execClearGlobal(ctx, CommandSupport.currentDimension(ctx)))
                .then(CommandSupport.dimensionArgument("dimension")
                        .executes(ctx -> execClearGlobal(ctx, CommandSupport.dimension(ctx, "dimension")))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> execClearPlayer(ctx, CommandSupport.dimension(ctx, "dimension"),
                                        CommandSupport.players(ctx))))));

        // query [dimension] [target]
        root.then(Commands.literal("query")
                .executes(ctx -> execQueryGlobal(ctx, CommandSupport.currentDimension(ctx)))
                .then(CommandSupport.dimensionArgument("dimension")
                        .executes(ctx -> execQueryGlobal(ctx, CommandSupport.dimension(ctx, "dimension")))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> execQueryPlayer(ctx, CommandSupport.dimension(ctx, "dimension"),
                                        EntityArgument.getPlayer(ctx, "target"))))));

        // regenerate [dimension]
        root.then(Commands.literal("regenerate")
                .executes(ctx -> execRegenerate(ctx, CommandSupport.currentDimension(ctx)))
                .then(CommandSupport.dimensionArgument("dimension")
                        .executes(ctx -> execRegenerate(ctx, CommandSupport.dimension(ctx, "dimension")))));

        // spawn_dimension [dimension] -- experimental: target dimension for a new player's first join
        root.then(Commands.literal("spawn_dimension")
                .executes(ctx -> execQuerySpawnDimension(ctx))
                .then(CommandSupport.dimensionArgument("dimension")
                        .executes(ctx -> execSetSpawnDimension(ctx, CommandSupport.dimension(ctx, "dimension")))));

        // list
        root.then(Commands.literal("list").executes(ctx -> {
            var templates = SpawnTemplateManager.all();
            if (templates.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable("betterspawn.msg.templates.empty"));
                return 0;
            }
            ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.templates.header"));
            for (var rule : templates) {
                ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.templates.entry",
                        rule.dimension().toString(), rule.method(), String.valueOf(rule.fallback())));
            }
            return 1;
        }));

        // reload (triggers a full datapack reload)
        root.then(Commands.literal("reload").executes(ctx -> {
            MinecraftServer server = ctx.getSource().getServer();
            server.reloadResources(server.getPackRepository().getSelectedIds());
            ctx.getSource().sendSuccess(() -> Component.translatable("betterspawn.msg.reloaded"), true);
            return 1;
        }));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    /** Registers vanilla-compatible /setworldspawn overrides when the experimental option is enabled. */
    public static void registerVanillaOverride(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setworldspawn")
                .requires(src -> src.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .executes(ctx -> execGlobal(ctx, CommandSupport.currentDimension(ctx),
                        CommandSupport.currentFeet(ctx), 0.0F))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> execGlobal(ctx, CommandSupport.currentDimension(ctx),
                                BlockPosArgument.getSpawnablePos(ctx, "pos"), 0.0F))
                        .then(Commands.argument("rotation", RotationArgument.rotation())
                                .executes(ctx -> {
                                    Vec2 rotation = RotationArgument.getRotation(ctx, "rotation")
                                            .getRotation(ctx.getSource());
                                    return execGlobal(ctx, CommandSupport.currentDimension(ctx),
                                            BlockPosArgument.getSpawnablePos(ctx, "pos"), rotation.y);
                                })
                                .then(CommandSupport.dimensionArgument("dimension")
                                        .executes(ctx -> {
                                            Vec2 rotation = RotationArgument.getRotation(ctx, "rotation")
                                                    .getRotation(ctx.getSource());
                                            return execGlobal(ctx, CommandSupport.dimension(ctx, "dimension"),
                                                    BlockPosArgument.getSpawnablePos(ctx, "pos"), rotation.y);
                                        })))
                        .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                .executes(ctx -> execGlobal(ctx, CommandSupport.currentDimension(ctx),
                                        BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                        FloatArgumentType.getFloat(ctx, "angle")))
                                .then(CommandSupport.dimensionArgument("dimension")
                                        .executes(ctx -> execGlobal(ctx,
                                                CommandSupport.dimension(ctx, "dimension"),
                                                BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                                FloatArgumentType.getFloat(ctx, "angle")))))));
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    private static int execGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                  BlockPos pos, float angle) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.dimension_missing", dimension.identifier().toString()));
            return 0;
        }

        WorldSpawnData.get(server).set(dimension, pos, angle, level);
        Component message = Component.translatable("betterspawn.msg.worldspawn.set",
                dimension.identifier().toString(), pos.toString(), String.valueOf(angle));
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execPlayer(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                  ResourceKey<Level> dimension, BlockPos pos, Float angle) {
        int changed = 0;
        for (ServerPlayer target : targets) {
            ResourceKey<Level> actualDimension = dimension != null
                    ? dimension
                    : target.level().dimension();
            BlockPos actualPos = pos != null
                    ? pos
                    : BlockPos.containing(target.getX(), target.getY(), target.getZ());
            float actualAngle = angle != null ? angle : target.getYRot();

            PlayerSpawnData.setDimensionSpawn(target, actualDimension, actualPos, actualAngle);
            changed++;
        }

        Component dimensionName = dimension != null
                ? Component.literal(dimension.identifier().toString())
                : Component.translatable("betterspawn.msg.current_dimension");
        Component message = Component.translatable("betterspawn.msg.worldspawn.player.set",
                String.valueOf(changed), dimensionName);
        ctx.getSource().sendSuccess(() -> message, true);
        return changed > 0 ? 1 : 0;
    }

    private static int execClearGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        WorldSpawnData.get(ctx.getSource().getServer()).clear(dimension);
        Component message = Component.translatable("betterspawn.msg.worldspawn.global.clear",
                dimension.identifier().toString());
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execClearPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       Collection<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            PlayerSpawnData.clearDimensionSpawn(target, dimension);
        }
        Component message = Component.translatable("betterspawn.msg.worldspawn.player.clear",
                String.valueOf(targets.size()), dimension.identifier().toString());
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execQueryGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.getLevel(dimension);
        var entry = WorldSpawnData.get(server).get(dimension);

        // level.dat is authoritative for the Overworld (/setworldspawn writes there); queries use it.
        BlockPos pos;
        float angle;
        if (dimension.equals(Level.OVERWORLD) && level != null) {
            pos = level.getLevelData().getRespawnData().pos();
            angle = level.getLevelData().getRespawnData().yaw();
        } else if (entry.isPresent()) {
            pos = entry.get().pos();
            angle = entry.get().angle();
        } else {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.worldspawn.global.none", dimension.identifier().toString()));
            return 0;
        }

        BlockPos finalPos = pos;
        float finalAngle = angle;
        Component message = Component.translatable("betterspawn.msg.worldspawn.global.query",
                dimension.identifier().toString(), finalPos.toString(), String.valueOf(finalAngle));
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int execQueryPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       ServerPlayer target) {
        var entry = PlayerSpawnData.getDimensionSpawn(target, dimension);
        if (entry.isEmpty()) {
            Component message = Component.translatable("betterspawn.msg.worldspawn.player.none",
                    target.getScoreboardName(), dimension.identifier().toString());
            ctx.getSource().sendSuccess(() -> message, false);
            return 0;
        }
        var point = entry.get();
        Component message = Component.translatable("betterspawn.msg.worldspawn.player.query",
                target.getScoreboardName(), dimension.identifier().toString(),
                point.pos().toString(), String.valueOf(point.angle()));
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int execRegenerate(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        boolean ok = DimensionSpawnInitializer.regenerate(ctx.getSource().getServer(), dimension);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.worldspawn.regenerate.fail", dimension.identifier().toString()));
            return 0;
        }
        var point = WorldSpawnData.get(ctx.getSource().getServer()).get(dimension);
        Component message = point.map(p -> (Component) Component.translatable(
                        "betterspawn.msg.worldspawn.regenerate.ok",
                        dimension.identifier().toString(), p.pos().toString(), String.valueOf(p.angle())))
                .orElseGet(() -> Component.translatable("betterspawn.msg.worldspawn.regenerate.ok_no_pos",
                        dimension.identifier().toString()));
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execSetSpawnDimension(CommandContext<CommandSourceStack> ctx,
                                             ResourceKey<Level> dimension) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server.getLevel(dimension) == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.dimension_missing", dimension.identifier().toString()));
            return 0;
        }
        WorldSpawnData.get(server).setSpawnDimension(dimension);
        Component message = Component.translatable("betterspawn.msg.spawn_dimension.set",
                dimension.identifier().toString());
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execQuerySpawnDimension(CommandContext<CommandSourceStack> ctx) {
        ResourceKey<Level> dimension = WorldSpawnData.get(ctx.getSource().getServer()).spawnDimension();
        Component message = Component.translatable("betterspawn.msg.spawn_dimension.query",
                dimension.identifier().toString());
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }
}
