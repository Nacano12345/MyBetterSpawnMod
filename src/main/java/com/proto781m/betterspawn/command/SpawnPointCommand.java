package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * /betterspawn spawnpoint 子命令（双槽版）。
 *
 * <p>原版 {@code /spawnpoint} 保持兼容（写入 COMMAND 槽）；
 * 扩展功能挂在 {@code /betterspawn spawnpoint} 下。
 */
public final class SpawnPointCommand {

    private SpawnPointCommand() {
    }

    private static BlockPos toFeetPos(Vec3 vec) {
        return BlockPos.containing(vec.x, vec.y, vec.z);
    }

    private static BlockPos toFeetPos(BlockPos blockPos) {
        return blockPos.above();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("spawnpoint")
                .requires(src -> src.hasPermission(2));

        // /betterspawn spawnpoint set <targets> <pos> [angle] [dimension]
        root.then(Commands.literal("set")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> execSet(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                        toFeetPos(ctx.getArgument("pos", Vec3.class)), 0, null))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execSet(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                toFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                FloatArgumentType.getFloat(ctx, "angle"), null))
                                        .then(Commands.argument("dimension", ResourceKeyArgument.key(Registries.DIMENSION))
                                                .executes(ctx -> execSet(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                        toFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                        FloatArgumentType.getFloat(ctx, "angle"),
                                                        ctx.getArgument("dimension", ResourceKey.class))))))));

        // /betterspawn spawnpoint bed <targets> <pos> [angle] [dimension]  (手动写床槽，调试用)
        root.then(Commands.literal("bed")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> execSetSlot(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                        toFeetPos(ctx.getArgument("pos", Vec3.class)), 0, null, SpawnSlotType.BED))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execSetSlot(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                toFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                FloatArgumentType.getFloat(ctx, "angle"), null, SpawnSlotType.BED))
                                        .then(Commands.argument("dimension", ResourceKeyArgument.key(Registries.DIMENSION))
                                                .executes(ctx -> execSetSlot(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                        toFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                        FloatArgumentType.getFloat(ctx, "angle"),
                                                        ctx.getArgument("dimension", ResourceKey.class), SpawnSlotType.BED)))))));

        // /betterspawn spawnpoint anchor <targets> <pos> [angle] [dimension] (手动写锚槽，调试用)
        root.then(Commands.literal("anchor")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> execSetSlot(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                        toFeetPos(ctx.getArgument("pos", Vec3.class)), 0, null, SpawnSlotType.ANCHOR))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execSetSlot(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                toFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                FloatArgumentType.getFloat(ctx, "angle"), null, SpawnSlotType.ANCHOR))
                                        .then(Commands.argument("dimension", ResourceKeyArgument.key(Registries.DIMENSION))
                                                .executes(ctx -> execSetSlot(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                        toFeetPos(ctx.getArgument("pos", Vec3.class)),
                                                        FloatArgumentType.getFloat(ctx, "angle"),
                                                        ctx.getArgument("dimension", ResourceKey.class), SpawnSlotType.ANCHOR)))))));

        // /betterspawn spawnpoint clear <targets> [command|bed|anchor|all]
        root.then(Commands.literal("clear")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execClear(ctx, EntityArgument.getPlayers(ctx, "targets"), "all"))
                        .then(Commands.literal("command")
                                .executes(ctx -> execClear(ctx, EntityArgument.getPlayers(ctx, "targets"), "command")))
                        .then(Commands.literal("bed")
                                .executes(ctx -> execClear(ctx, EntityArgument.getPlayers(ctx, "targets"), "bed")))
                        .then(Commands.literal("anchor")
                                .executes(ctx -> execClear(ctx, EntityArgument.getPlayers(ctx, "targets"), "anchor")))
                        .then(Commands.literal("all")
                                .executes(ctx -> execClear(ctx, EntityArgument.getPlayers(ctx, "targets"), "all")))));

        // /betterspawn spawnpoint query <target>
        root.then(Commands.literal("query")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> execQuery(ctx, EntityArgument.getPlayer(ctx, "target")))));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    private static int execSet(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                               BlockPos pos, float angle, ResourceKey<Level> dimension) throws CommandSyntaxException {
        for (ServerPlayer target : targets) {
            ResourceKey<Level> dim = (dimension != null) ? dimension : target.level().dimension();
            PlayerSpawnData.setSlot(target, new com.proto781m.betterspawn.data.SpawnSlot(
                    com.proto781m.betterspawn.data.SpawnSlotType.COMMAND, dim, pos, angle, true));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已设置 " + targets.size() + " 个玩家的指令出生点: " + pos + " angle=" + angle), true);
        return 1;
    }

    private static int execSetSlot(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                   BlockPos pos, float angle, ResourceKey<Level> dimension, SpawnSlotType type) throws CommandSyntaxException {
        for (ServerPlayer target : targets) {
            ResourceKey<Level> dim = (dimension != null) ? dimension : target.level().dimension();
            com.proto781m.betterspawn.data.PlayerSpawnData.setSlot(target,
                    com.proto781m.betterspawn.data.SpawnSlot.valid(type, dim, pos, angle));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已手动设置 " + targets.size() + " 个玩家的 " + type + " 槽: " + pos), true);
        return 1;
    }

    private static int execClear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String slotStr) throws CommandSyntaxException {
        for (ServerPlayer target : targets) {
            switch (slotStr) {
                case "command" -> com.proto781m.betterspawn.data.PlayerSpawnData.clearSlot(target, com.proto781m.betterspawn.data.SpawnSlotType.COMMAND);
                case "bed" -> com.proto781m.betterspawn.data.PlayerSpawnData.clearSlot(target, com.proto781m.betterspawn.data.SpawnSlotType.BED);
                case "anchor" -> com.proto781m.betterspawn.data.PlayerSpawnData.clearSlot(target, com.proto781m.betterspawn.data.SpawnSlotType.ANCHOR);
                case "all" -> {
                    for (com.proto781m.betterspawn.data.SpawnSlotType t : com.proto781m.betterspawn.data.SpawnSlotType.values()) {
                        com.proto781m.betterspawn.data.PlayerSpawnData.clearSlot(target, t);
                    }
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清除 " + targets.size() + " 个玩家的 " + slotStr + " 槽"), true);
        return 1;
    }

    private static int execQuery(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        var slots = com.proto781m.betterspawn.data.PlayerSpawnData.getSlots(target);
        var dimSpawns = target.getPersistentData().getCompound(com.proto781m.betterspawn.data.PlayerSpawnData.ROOT)
                .getList(com.proto781m.betterspawn.data.PlayerSpawnData.KEY_DIM_SPAWNS, net.minecraft.nbt.Tag.TAG_COMPOUND);

        StringBuilder sb = new StringBuilder("§e=== ").append(target.getScoreboardName()).append(" 的重生点 ===\n");
        if (dimSpawns.isEmpty()) {
            sb.append("  个人世界出生点: 无\n");
        } else {
            for (int i = 0; i < dimSpawns.size(); i++) {
                var t = dimSpawns.getCompound(i);
                sb.append("  个人世界出生点 [").append(t.getString("dim"))
                        .append("]: ").append(t.getInt("x")).append(",").append(t.getInt("y")).append(",").append(t.getInt("z"))
                        .append(" angle=").append(t.getFloat("angle")).append("\n");
            }
        }
        for (com.proto781m.betterspawn.data.SpawnSlotType type : com.proto781m.betterspawn.data.SpawnSlotType.values()) {
            var slot = slots.get(type);
            if (slot != null) {
                sb.append("  ").append(type).append(" 槽 [").append(slot.dimension().location())
                        .append("]: ").append(slot.pos()).append(" angle=").append(slot.angle())
                        .append(" valid=").append(slot.valid()).append("\n");
            } else {
                sb.append("  ").append(type).append(" 槽: 无\n");
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}