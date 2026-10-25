package com.proto781m.betterspawn.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shared Brigadier builders and argument helpers for BetterSpawn commands.
 *
 * <p>The setter commands all use the same optional {@code [pos] [angle] [dimension]}
 * chain. Keeping that chain here avoids separate implementations drifting apart when
 * the command surface or a Minecraft API changes.
 */
public final class CommandSupport {

    private CommandSupport() {
    }

    /** Runs a position command with resolved (or omitted) arguments. */
    @FunctionalInterface
    public interface PositionAction {
        int run(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                BlockPos pos, Float angle, ResourceKey<Level> dimension)
                throws CommandSyntaxException;
    }

    /** Resolves the target players for a command branch. */
    @FunctionalInterface
    private interface TargetResolver {
        Collection<ServerPlayer> resolve(CommandContext<CommandSourceStack> ctx)
                throws CommandSyntaxException;
    }

    /**
     * Builds a literal command with optional {@code [pos] [angle] [dimension]}.
     * The action receives {@code null} for omitted values.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> positionCommand(
            String name, PositionAction action) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(name)
                .executes(ctx -> action.run(ctx, List.of(), null, null, null));
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> position = positionArgument("pos");
        attachPositionAndDimension(position, action, ctx -> List.of());
        command.then(position);
        return command;
    }

    /**
     * Builds a command with a required {@code <targets>} argument, followed by optional
     * {@code [pos] [angle] [dimension]}.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> targetPositionCommand(
            String name, PositionAction action) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> position = positionArgument("pos");
        attachPositionAndDimension(position, action, CommandSupport::players);
        return Commands.literal(name)
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> action.run(ctx, players(ctx), null, null, null))
                        .then(position));
    }

    /** A position argument with the common {@code ~ ~ ~} suggestion. */
    public static RequiredArgumentBuilder<CommandSourceStack, Coordinates> positionArgument(String name) {
        return Commands.argument(name, Vec3Argument.vec3())
                .suggests((ctx, builder) -> {
                    builder.suggest("~ ~ ~");
                    return builder.buildFuture();
                });
    }

    /** A dimension argument with suggestions from the server dimension registry. */
    public static RequiredArgumentBuilder<CommandSourceStack, ResourceKey<Level>> dimensionArgument(
            String name) {
        return Commands.argument(name, ResourceKeyArgument.key(Registries.DIMENSION))
                .suggests((ctx, builder) -> suggestDimensions(ctx.getSource(), builder));
    }

    @SuppressWarnings("unchecked")
    public static ResourceKey<Level> dimension(CommandContext<CommandSourceStack> ctx, String name) {
        return (ResourceKey<Level>) ctx.getArgument(name, ResourceKey.class);
    }

    /** Resolves the {@code targets} argument. */
    public static Collection<ServerPlayer> players(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    /** Resolves the {@code pos} argument to block coordinates. */
    public static BlockPos vec(CommandContext<CommandSourceStack> ctx) {
        Vec3 value = Vec3Argument.getVec3(ctx, "pos");
        return BlockPos.containing(value.x, value.y, value.z);
    }

    /** Returns the source dimension. */
    public static ResourceKey<Level> currentDimension(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getLevel().dimension();
    }

    /** Returns the source position rounded to feet block coordinates. */
    public static BlockPos currentFeet(CommandContext<CommandSourceStack> ctx) {
        Vec3 position = ctx.getSource().getPosition();
        return BlockPos.containing(position.x, position.y, position.z);
    }

    /** Returns the source entity yaw, or zero for non-entity sources. */
    public static float currentYaw(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() != null
                ? ctx.getSource().getEntity().getYRot()
                : 0.0F;
    }

    /** Suggests all registered dimension IDs. */
    public static CompletableFuture<Suggestions> suggestDimensions(
            CommandSourceStack source, SuggestionsBuilder builder) {
        MinecraftServer server = source.getServer();
        if (server != null) {
            for (Identifier location : server.registryAccess()
                    .lookupOrThrow(Registries.DIMENSION).keySet()) {
                builder.suggest(location.toString());
            }
        }
        return builder.buildFuture();
    }

    private static void attachPositionAndDimension(
            RequiredArgumentBuilder<CommandSourceStack, Coordinates> position,
            PositionAction action, TargetResolver targets) {
        position.executes(ctx -> action.run(ctx, targets.resolve(ctx), vec(ctx), null, null));
        position.then(Commands.argument("angle", com.mojang.brigadier.arguments.FloatArgumentType.floatArg())
                .executes(ctx -> action.run(ctx, targets.resolve(ctx), vec(ctx),
                        com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "angle"), null))
                .then(dimensionArgument("dimension")
                        .executes(ctx -> action.run(ctx, targets.resolve(ctx), vec(ctx),
                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "angle"),
                                dimension(ctx, "dimension")))));
    }
}
