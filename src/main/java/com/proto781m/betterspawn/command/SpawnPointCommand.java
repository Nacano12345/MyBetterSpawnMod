package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /betterspawn spawnpoint} 子命令（双槽版）。
 *
 * <p>参数可省略：坐标默认玩家脚部位置，角度默认当前朝向，维度默认玩家当前维度。
 * 床/锚槽记录的是锚点方块位置，重生时再围绕它寻找安全站立点。
 */
public final class SpawnPointCommand {

    private SpawnPointCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("spawnpoint")
                .requires(src -> src.hasPermission(2));

        // /betterspawn spawnpoint set <targets> [<pos>] [<angle>] [<dimension>]
        root.then(Commands.literal("set")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execSet(ctx, targets(ctx), null, null, null))
                        .then(Commands.argument("pos", Vec3Argument.vec3()).suggests((ctx, b) -> { b.suggest("~ ~ ~"); return b.buildFuture(); })
                                .executes(ctx -> execSet(ctx, targets(ctx), vec(ctx), null, null))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execSet(ctx, targets(ctx), vec(ctx),
                                                FloatArgumentType.getFloat(ctx, "angle"), null))
                                        .then(dimension("dimension")
                                                .executes(ctx -> execSet(ctx, targets(ctx), vec(ctx),
                                                        FloatArgumentType.getFloat(ctx, "angle"),
                                                        dimension(ctx, "dimension"))))))));

        // /betterspawn spawnpoint bed <targets> [<pos>] [<angle>] [<dimension>]
        root.then(slotCommand("bed", SpawnSlotType.BED));

        // /betterspawn spawnpoint anchor <targets> [<pos>] [<angle>] [<dimension>]
        root.then(slotCommand("anchor", SpawnSlotType.ANCHOR));

        // /betterspawn spawnpoint clear <targets> [command|bed|anchor|all]
        root.then(Commands.literal("clear")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execClear(ctx, targets(ctx), "all"))
                        .then(Commands.literal("command").executes(ctx -> execClear(ctx, targets(ctx), "command")))
                        .then(Commands.literal("bed").executes(ctx -> execClear(ctx, targets(ctx), "bed")))
                        .then(Commands.literal("anchor").executes(ctx -> execClear(ctx, targets(ctx), "anchor")))
                        .then(Commands.literal("all").executes(ctx -> execClear(ctx, targets(ctx), "all")))));

        // /betterspawn spawnpoint query <target>
        root.then(Commands.literal("query")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> execQuery(ctx, EntityArgument.getPlayer(ctx, "target")))));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> slotCommand(String name, SpawnSlotType type) {
        return Commands.literal(name)
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> execSetSlot(ctx, targets(ctx), null, null, null, type))
                        .then(Commands.argument("pos", Vec3Argument.vec3()).suggests((ctx, b) -> { b.suggest("~ ~ ~"); return b.buildFuture(); })
                                .executes(ctx -> execSetSlot(ctx, targets(ctx), vec(ctx), null, null, type))
                                .then(Commands.argument("angle", FloatArgumentType.floatArg())
                                        .executes(ctx -> execSetSlot(ctx, targets(ctx), vec(ctx),
                                                FloatArgumentType.getFloat(ctx, "angle"), null, type))
                                        .then(dimension("dimension")
                                                .executes(ctx -> execSetSlot(ctx, targets(ctx), vec(ctx),
                                                        FloatArgumentType.getFloat(ctx, "angle"),
                                                        dimension(ctx, "dimension"), type))))));
    }

    // ------------------------------------------------------------------
    // 参数辅助
    // ------------------------------------------------------------------

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceKey<Level>> dimension(String name) {
        return Commands.argument(name, ResourceKeyArgument.key(Registries.DIMENSION));
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> dimension(CommandContext<CommandSourceStack> ctx, String name) {
        return (ResourceKey<Level>) ctx.getArgument(name, ResourceKey.class);
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    private static BlockPos vec(CommandContext<CommandSourceStack> ctx) {
        Vec3 value = Vec3Argument.getVec3(ctx, "pos");
        return BlockPos.containing(value.x, value.y, value.z);
    }

    // ------------------------------------------------------------------
    // 执行
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
                // forced=true，确保非床/锚坐标也能写入原版 NBT
                target.setRespawnPosition(actualDimension, actualPos, actualAngle, true, true);
                // setRespawnPosition 会触发 PlayerSpawnSlotHandler，用玩家当前朝向覆盖槽；
                // 这里再写一次，确保显式 angle 生效
                PlayerSpawnData.setSlot(target, SpawnSlot.valid(
                        SpawnSlotType.COMMAND, actualDimension, actualPos, actualAngle));
            }
            changed++;
        }

        String message = "§a已设置 " + changed + " 个玩家的指令出生点";
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
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

            ServerLevel level = target.getServer() == null
                    ? null
                    : target.getServer().getLevel(actualDimension);
            if (level == null || !matchesSlotBlock(level, actualPos, type)) {
                invalid++;
                continue;
            }

            PlayerSpawnData.setSlot(target, SpawnSlot.valid(type, actualDimension, actualPos, actualAngle));
            changed++;
        }

        if (changed == 0) {
            String hint = type == SpawnSlotType.BED
                    ? "目标位置不是有效床方块"
                    : "目标位置不是有效重生锚";
            ctx.getSource().sendFailure(Component.literal("§c没有设置任何 " + type + " 槽：" + hint));
            return 0;
        }

        String message = "§a已设置 " + changed + " 个玩家的 " + type + " 槽"
                + (invalid > 0 ? "（跳过 " + invalid + " 个无效目标）" : "");
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static boolean matchesSlotBlock(ServerLevel level, BlockPos pos, SpawnSlotType type) {
        BlockState state = level.getBlockState(pos);
        if (type == SpawnSlotType.BED) {
            return BedBlock.canSetSpawn(level) && state.getBlock() instanceof BedBlock;
        }
        if (type == SpawnSlotType.ANCHOR) {
            return RespawnAnchorBlock.canSetSpawn(level)
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
        String message = "§a已清除 " + targets.size() + " 个玩家的 " + slotName + " 槽";
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int execQuery(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        StringBuilder sb = new StringBuilder("§e=== ").append(target.getScoreboardName()).append(" 的重生点 ===\n");

        var dimensionSpawns = PlayerSpawnData.getAllDimensionSpawns(target);
        if (dimensionSpawns.isEmpty()) {
            sb.append("  个人世界出生点: 无\n");
        } else {
            dimensionSpawns.forEach((dimension, point) -> sb.append("  个人世界出生点 [")
                    .append(dimension.location()).append("]: ").append(point.pos())
                    .append(" angle=").append(point.angle()).append("\n"));
        }

        var slots = PlayerSpawnData.getSlots(target);
        for (SpawnSlotType type : SpawnSlotType.values()) {
            SpawnSlot slot = slots.get(type);
            if (slot == null) {
                sb.append("  ").append(type).append(" 槽: 无\n");
            } else {
                sb.append("  ").append(type).append(" 槽 [").append(slot.dimension().location())
                        .append("]: ").append(slot.pos()).append(" angle=").append(slot.angle())
                        .append(" valid=").append(slot.valid()).append("\n");
            }
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}
