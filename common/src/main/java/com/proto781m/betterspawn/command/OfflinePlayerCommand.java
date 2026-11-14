package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.proto781m.betterspawn.data.OfflinePlayerData;
import com.proto781m.betterspawn.data.OfflinePlayerRef;
import com.proto781m.betterspawn.data.SpawnPoint;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import com.proto781m.betterspawn.util.DimensionUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Offline player spawn editing: {@code /betterspawn offline ...}.
 *
 * <p>The player argument is resolved through {@code playerdata} and suggestions enumerate only
 * players that already have a BetterSpawn root. Editing a player who is currently online is
 * rejected so the live player data is never overwritten.
 */
public final class OfflinePlayerCommand {

    private OfflinePlayerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("offline")
                .requires(src -> CommandSupport.hasPermission(src, 2));

        root.then(Commands.literal("list").executes(OfflinePlayerCommand::listPlayers));

        root.then(Commands.argument("player", StringArgumentType.word())
                .suggests(OfflinePlayerCommand::suggestPlayers)
                .executes(OfflinePlayerCommand::query)
                .then(Commands.literal("query").executes(OfflinePlayerCommand::query))
                .then(worldSpawnCommand())
                .then(spawnPointCommand()));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> worldSpawnCommand() {
        return Commands.literal("worldspawn")
                .executes(ctx -> setWorldSpawn(ctx, CommandSupport.currentDimension(ctx),
                        CommandSupport.currentFeet(ctx), CommandSupport.currentYaw(ctx)))
                .then(CommandSupport.positionArgument("pos")
                        .executes(ctx -> setWorldSpawn(ctx, CommandSupport.currentDimension(ctx),
                                CommandSupport.vec(ctx), CommandSupport.currentYaw(ctx)))
                        .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                .executes(ctx -> setWorldSpawn(ctx, CommandSupport.currentDimension(ctx),
                                        CommandSupport.vec(ctx),
                                        FloatArgumentType.getFloat(ctx, "angle")))
                                .then(CommandSupport.dimensionArgument("dimension")
                                        .executes(ctx -> setWorldSpawn(ctx,
                                                CommandSupport.dimension(ctx, "dimension"),
                                                CommandSupport.vec(ctx),
                                                FloatArgumentType.getFloat(ctx, "angle"))))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clearWorldSpawn(ctx, CommandSupport.currentDimension(ctx)))
                        .then(CommandSupport.dimensionArgument("dimension")
                                .executes(ctx -> clearWorldSpawn(ctx,
                                        CommandSupport.dimension(ctx, "dimension")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnPointCommand() {
        return Commands.literal("spawnpoint")
                .then(slotSetCommand("set", SpawnSlotType.COMMAND))
                .then(slotSetCommand("bed", SpawnSlotType.BED))
                .then(slotSetCommand("anchor", SpawnSlotType.ANCHOR))
                .then(Commands.literal("clear")
                        .executes(ctx -> clearSlot(ctx, "all"))
                        .then(Commands.literal("command").executes(ctx -> clearSlot(ctx, "command")))
                        .then(Commands.literal("bed").executes(ctx -> clearSlot(ctx, "bed")))
                        .then(Commands.literal("anchor").executes(ctx -> clearSlot(ctx, "anchor")))
                        .then(Commands.literal("all").executes(ctx -> clearSlot(ctx, "all"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> slotSetCommand(
            String name, SpawnSlotType type) {
        return Commands.literal(name)
                .executes(ctx -> setSlot(ctx, type, CommandSupport.currentDimension(ctx),
                        CommandSupport.currentFeet(ctx), CommandSupport.currentYaw(ctx)))
                .then(CommandSupport.positionArgument("pos")
                        .executes(ctx -> setSlot(ctx, type, CommandSupport.currentDimension(ctx),
                                CommandSupport.vec(ctx), CommandSupport.currentYaw(ctx)))
                        .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                .executes(ctx -> setSlot(ctx, type, CommandSupport.currentDimension(ctx),
                                        CommandSupport.vec(ctx),
                                        FloatArgumentType.getFloat(ctx, "angle")))
                                .then(CommandSupport.dimensionArgument("dimension")
                                        .executes(ctx -> setSlot(ctx, type,
                                                CommandSupport.dimension(ctx, "dimension"),
                                                CommandSupport.vec(ctx),
                                                FloatArgumentType.getFloat(ctx, "angle"))))));
    }

    private static int listPlayers(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<OfflinePlayerRef> players = OfflinePlayerData.list(server);
        if (players.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("betterspawn.msg.offline.list.none"));
            return 0;
        }
        ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.offline.list.header"));
        for (OfflinePlayerRef player : players) {
            ctx.getSource().sendSystemMessage(Component.translatable(
                    "betterspawn.msg.offline.list.entry", player.displayName(), player.id().toString()));
        }
        return players.size();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (OfflinePlayerRef player : OfflinePlayerData.list(ctx.getSource().getServer())) {
            builder.suggest(player.displayName());
        }
        return builder.buildFuture();
    }

    private static int query(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        OfflinePlayerData data = load(ctx, playerName);
        if (data == null) {
            return 0;
        }

        ctx.getSource().sendSystemMessage(Component.translatable(
                "betterspawn.msg.offline.query.header", data.ref().displayName()));
        Map<ResourceKey<Level>, SpawnPoint> dimensions = data.dimensionSpawns();
        Map<SpawnSlotType, SpawnSlot> slots = data.slots();
        if (dimensions.isEmpty() && slots.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.offline.query.none"));
            return 1;
        }

        for (Map.Entry<ResourceKey<Level>, SpawnPoint> entry : dimensions.entrySet()) {
            SpawnPoint point = entry.getValue();
            ctx.getSource().sendSystemMessage(Component.translatable(
                    "betterspawn.msg.offline.query.dimension",
                    DimensionUtil.id(entry.getKey()), point.pos().toString(),
                    String.valueOf(point.angle())));
        }
        for (Map.Entry<SpawnSlotType, SpawnSlot> entry : slots.entrySet()) {
            SpawnSlot slot = entry.getValue();
            ctx.getSource().sendSystemMessage(Component.translatable(
                    "betterspawn.msg.offline.query.slot", entry.getKey().name(),
                    DimensionUtil.id(slot.dimension()), slot.pos().toString(),
                    String.valueOf(slot.angle())));
        }
        return 1;
    }

    private static int setWorldSpawn(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                     BlockPos pos, float angle) {
        String playerName = StringArgumentType.getString(ctx, "player");
        OfflinePlayerData data = load(ctx, playerName);
        if (data == null) {
            return 0;
        }
        data.setDimensionSpawn(dimension, pos, angle);
        if (!save(ctx, data)) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "betterspawn.msg.offline.worldspawn.set", data.ref().displayName(),
                DimensionUtil.id(dimension), pos.toString(), String.valueOf(angle)), true);
        return 1;
    }

    private static int clearWorldSpawn(CommandContext<CommandSourceStack> ctx,
                                       ResourceKey<Level> dimension) {
        String playerName = StringArgumentType.getString(ctx, "player");
        OfflinePlayerData data = load(ctx, playerName);
        if (data == null) {
            return 0;
        }
        data.clearDimensionSpawn(dimension);
        if (!save(ctx, data)) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "betterspawn.msg.offline.worldspawn.clear", data.ref().displayName(),
                DimensionUtil.id(dimension)), true);
        return 1;
    }

    private static int setSlot(CommandContext<CommandSourceStack> ctx, SpawnSlotType type,
                               ResourceKey<Level> dimension, BlockPos pos, float angle) {
        String playerName = StringArgumentType.getString(ctx, "player");
        OfflinePlayerData data = load(ctx, playerName);
        if (data == null) {
            return 0;
        }
        data.setSlot(SpawnSlot.valid(type, dimension, pos, angle));
        if (!save(ctx, data)) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "betterspawn.msg.offline.spawnpoint.set", data.ref().displayName(),
                type.name(), DimensionUtil.id(dimension), pos.toString(),
                String.valueOf(angle)), true);
        return 1;
    }

    private static int clearSlot(CommandContext<CommandSourceStack> ctx, String slotName) {
        String playerName = StringArgumentType.getString(ctx, "player");
        OfflinePlayerData data = load(ctx, playerName);
        if (data == null) {
            return 0;
        }
        switch (slotName) {
            case "command" -> data.clearSlot(SpawnSlotType.COMMAND);
            case "bed" -> data.clearSlot(SpawnSlotType.BED);
            case "anchor" -> data.clearSlot(SpawnSlotType.ANCHOR);
            default -> {
                for (SpawnSlotType type : SpawnSlotType.values()) {
                    data.clearSlot(type);
                }
            }
        }
        if (!save(ctx, data)) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "betterspawn.msg.offline.spawnpoint.clear", data.ref().displayName(), slotName), true);
        return 1;
    }

    private static OfflinePlayerData load(CommandContext<CommandSourceStack> ctx, String playerName) {
        MinecraftServer server = ctx.getSource().getServer();
        Optional<OfflinePlayerRef> ref = OfflinePlayerData.resolve(server, playerName);
        if (ref.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.offline.not_found", playerName));
            return null;
        }
        if (OfflinePlayerData.isOnline(server, ref.get())) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.offline.online", ref.get().displayName()));
            return null;
        }
        Optional<OfflinePlayerData> data = OfflinePlayerData.load(server, ref.get());
        if (data.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.offline.load_failed", ref.get().displayName()));
            return null;
        }
        return data.get();
    }

    private static boolean save(CommandContext<CommandSourceStack> ctx, OfflinePlayerData data) {
        try {
            data.save();
            return true;
        } catch (IOException ex) {
            ctx.getSource().sendFailure(Component.translatable(
                    "betterspawn.msg.offline.save_failed", data.ref().displayName(), ex.toString()));
            return false;
        }
    }
}
