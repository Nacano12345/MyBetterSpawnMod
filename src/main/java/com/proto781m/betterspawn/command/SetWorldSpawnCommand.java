package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.logic.DimensionSpawnInitializer;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
                .requires(src -> src.hasPermission(2));

        // /betterspawn worldspawn global [<pos>] [<angle>] [<dimension>]
        root.then(Commands.literal("global")
                .executes(ctx -> execGlobal(ctx, currentDimension(ctx), currentFeet(ctx), currentYaw(ctx)))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .suggests((ctx, b) -> {
                            b.suggest("~ ~ ~");
                            return b.buildFuture();
                        })
                        .executes(ctx -> execGlobal(ctx, currentDimension(ctx), vec(ctx), currentYaw(ctx)))
                        .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                .executes(ctx -> execGlobal(ctx, currentDimension(ctx), vec(ctx),
                                        FloatArgumentType.getFloat(ctx, "angle")))
                                .then(argumentDimension("dimension")
                                        .executes(ctx -> execGlobal(ctx, dimension(ctx, "dimension"),
                                                vec(ctx), FloatArgumentType.getFloat(ctx, "angle")))))));

        // /betterspawn worldspawn player <targets> [<pos>] [<angle>] [<dimension>]
        root.then(Commands.literal("player")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execPlayer(ctx, targets(ctx), null, null, null))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .suggests((ctx, b) -> {
                                    b.suggest("~ ~ ~");
                                    return b.buildFuture();
                                })
                                .executes(ctx -> execPlayer(ctx, targets(ctx), null, vec(ctx), null))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execPlayer(ctx, targets(ctx), null, vec(ctx),
                                                FloatArgumentType.getFloat(ctx, "angle")))
                                        .then(argumentDimension("dimension")
                                                .executes(ctx -> execPlayer(ctx, targets(ctx),
                                                        dimension(ctx, "dimension"), vec(ctx),
                                                        FloatArgumentType.getFloat(ctx, "angle"))))))));

        // clear [dimension] [targets]
        root.then(Commands.literal("clear")
                .executes(ctx -> execClearGlobal(ctx, currentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execClearGlobal(ctx, dimension(ctx, "dimension")))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> execClearPlayer(ctx, dimension(ctx, "dimension"),
                                        targets(ctx))))));

        // query [dimension] [target]
        root.then(Commands.literal("query")
                .executes(ctx -> execQueryGlobal(ctx, currentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execQueryGlobal(ctx, dimension(ctx, "dimension")))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> execQueryPlayer(ctx, dimension(ctx, "dimension"),
                                        EntityArgument.getPlayer(ctx, "target"))))));

        // regenerate [dimension]
        root.then(Commands.literal("regenerate")
                .executes(ctx -> execRegenerate(ctx, currentDimension(ctx)))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execRegenerate(ctx, dimension(ctx, "dimension")))));

        // spawn_dimension [dimension] -- experimental: target dimension for a new player's first join
        root.then(Commands.literal("spawn_dimension")
                .executes(ctx -> execQuerySpawnDimension(ctx))
                .then(argumentDimension("dimension")
                        .executes(ctx -> execSetSpawnDimension(ctx, dimension(ctx, "dimension")))));

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

    // ------------------------------------------------------------------
    // Argument helpers
    // ------------------------------------------------------------------

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceKey<Level>> argumentDimension(String name) {
        return Commands.argument(name, ResourceKeyArgument.key(Registries.DIMENSION))
                .suggests((ctx, builder) -> suggestDimensions(ctx.getSource(), builder));
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> dimension(CommandContext<CommandSourceStack> ctx, String name) {
        return (ResourceKey<Level>) ctx.getArgument(name, ResourceKey.class);
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    private static BlockPos vec(CommandContext<CommandSourceStack> ctx) {
        Vec3 value = Vec3Argument.getVec3(ctx, "pos");
        return BlockPos.containing(value.x, value.y, value.z);
    }

    private static ResourceKey<Level> currentDimension(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getLevel().dimension();
    }

    private static BlockPos currentFeet(CommandContext<CommandSourceStack> ctx) {
        Vec3 position = ctx.getSource().getPosition();
        return BlockPos.containing(position.x, position.y, position.z);
    }

    private static float currentYaw(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() != null ? ctx.getSource().getEntity().getYRot() : 0.0F;
    }

    private static CompletableFuture<Suggestions> suggestDimensions(CommandSourceStack src, SuggestionsBuilder builder) {
        MinecraftServer server = src.getServer();
        if (server != null) {
            for (ResourceLocation location : server.registryAccess()
                    .registryOrThrow(Registries.DIMENSION).keySet()) {
                builder.suggest(location.toString());
            }
        }
        return builder.buildFuture();
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
                    "betterspawn.msg.dimension_missing", dimension.location().toString()));
            return 0;
        }

        WorldSpawnData.get(server).set(dimension, pos, angle, level);
        Component message = Component.translatable("betterspawn.msg.worldspawn.set",
                dimension.location().toString(), pos.toString(), String.valueOf(angle));
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
                ? Component.literal(dimension.location().toString())
                : Component.translatable("betterspawn.msg.current_dimension");
        Component message = Component.translatable("betterspawn.msg.worldspawn.player.set",
                String.valueOf(changed), dimensionName);
        ctx.getSource().sendSuccess(() -> message, true);
        return changed > 0 ? 1 : 0;
    }

    private static int execClearGlobal(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        WorldSpawnData.get(ctx.getSource().getServer()).clear(dimension);
        Component message = Component.translatable("betterspawn.msg.worldspawn.global.clear",
                dimension.location().toString());
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execClearPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       Collection<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            PlayerSpawnData.clearDimensionSpawn(target, dimension);
        }
        Component message = Component.translatable("betterspawn.msg.worldspawn.player.clear",
                String.valueOf(targets.size()), dimension.location().toString());
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
            pos = level.getLevelData().getSpawnPos();
            angle = level.getLevelData().getSpawnAngle();
        } else if (entry.isPresent()) {
            pos = entry.get().pos();
            angle = entry.get().angle();
        } else {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.worldspawn.global.none", dimension.location().toString()));
            return 0;
        }

        BlockPos finalPos = pos;
        float finalAngle = angle;
        Component message = Component.translatable("betterspawn.msg.worldspawn.global.query",
                dimension.location().toString(), finalPos.toString(), String.valueOf(finalAngle));
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int execQueryPlayer(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                       ServerPlayer target) {
        var entry = PlayerSpawnData.getDimensionSpawn(target, dimension);
        if (entry.isEmpty()) {
            Component message = Component.translatable("betterspawn.msg.worldspawn.player.none",
                    target.getScoreboardName(), dimension.location().toString());
            ctx.getSource().sendSuccess(() -> message, false);
            return 0;
        }
        var point = entry.get();
        Component message = Component.translatable("betterspawn.msg.worldspawn.player.query",
                target.getScoreboardName(), dimension.location().toString(),
                point.pos().toString(), String.valueOf(point.angle()));
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int execRegenerate(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension) {
        boolean ok = DimensionSpawnInitializer.regenerate(ctx.getSource().getServer(), dimension);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.worldspawn.regenerate.fail", dimension.location().toString()));
            return 0;
        }
        var point = WorldSpawnData.get(ctx.getSource().getServer()).get(dimension);
        Component message = point.map(p -> (Component) Component.translatable(
                        "betterspawn.msg.worldspawn.regenerate.ok",
                        dimension.location().toString(), p.pos().toString(), String.valueOf(p.angle())))
                .orElseGet(() -> Component.translatable("betterspawn.msg.worldspawn.regenerate.ok_no_pos",
                        dimension.location().toString()));
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execSetSpawnDimension(CommandContext<CommandSourceStack> ctx,
                                             ResourceKey<Level> dimension) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server.getLevel(dimension) == null) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.dimension_missing", dimension.location().toString()));
            return 0;
        }
        WorldSpawnData.get(server).setSpawnDimension(dimension);
        Component message = Component.translatable("betterspawn.msg.spawn_dimension.set",
                dimension.location().toString());
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int execQuerySpawnDimension(CommandContext<CommandSourceStack> ctx) {
        ResourceKey<Level> dimension = WorldSpawnData.get(ctx.getSource().getServer()).spawnDimension();
        Component message = Component.translatable("betterspawn.msg.spawn_dimension.query",
                dimension.location().toString());
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }
}
