package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SpawnContext;
import com.proto781m.betterspawn.logic.SpawnPointResolver;
import com.proto781m.betterspawn.resolver.SpawnMethodResolver;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /betterspawn gotodim 测试指令：沙盒模拟维度默认出生点解析。
 *
 * <p>模式：
 * <ul>
 *   <li>{@code simulate}：只跑解析链，打印报告，不传送，不落库（默认，权限 0）</li>
 *   <li>{@code teleport}：解析后传送玩家到结果点（权限 2）</li>
 *   <li>{@code verify}：传送 + 等待若干 tick 后校验落地/液体/虚空（权限 2）</li>
 * </ul>
 *
 * <p>选项：
 * <ul>
 *   <li>{@code --method} 临时覆盖方法</li>
 *   <li>{@code --params} 临时覆盖参数（JSON）</li>
 *   <li>{@code --seed} 覆盖种子</li>
 *   <li>{@code --repeat} 重复 N 次统计</li>
 *   <li>{@code --timeout} 超时 tick</li>
 *   <li>{@code --verbose} 打印详细 trace</li>
 *   <li>{@code --all} 对所有已注册维度各跑一次</li>
 * </ul>
 * </p>
 */
public final class GotoDimCommand {

    private GotoDimCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gotodim")
                .requires(src -> BetterSpawnConfig.ENABLE_GOTODIM.get() && src.hasPermission(0))
                .then(Commands.argument("dimension", ResourceKeyArgument.key(Registries.DIMENSION))
                        .suggests((ctx, builder) -> suggestDimensions(ctx.getSource(), builder))
                        .executes(ctx -> exec(ctx, ctx.getArgument("dimension", ResourceKey.class), "simulate"))
                        .then(Commands.literal("simulate").executes(ctx -> exec(ctx, ctx.getArgument("dimension", ResourceKey.class), "simulate")))
                        .then(Commands.literal("teleport").executes(ctx -> exec(ctx, ctx.getArgument("dimension", ResourceKey.class), "teleport")))
                        .then(Commands.literal("verify").executes(ctx -> exec(ctx, ctx.getArgument("dimension", ResourceKey.class), "verify")))
                        .then(Commands.literal("--method")
                                .then(Commands.argument("method", StringArgumentType.string())
                                        .suggests((ctx, builder) -> suggestMethods(builder))
                                        .executes(ctx -> execWithOverride(ctx, ctx.getArgument("dimension", ResourceKey.class), "method"))))
                        .then(Commands.literal("--params")
                                .then(Commands.argument("params", StringArgumentType.greedyString())
                                        .executes(ctx -> execWithOverride(ctx, ctx.getArgument("dimension", ResourceKey.class), "params"))))
                        .then(Commands.literal("--seed")
                                .then(Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(ctx -> execWithOverride(ctx, ctx.getArgument("dimension", ResourceKey.class), "seed"))))
                        .then(Commands.literal("--repeat")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> execWithOverride(ctx, ctx.getArgument("dimension", ResourceKey.class), "repeat"))))
                        .then(Commands.literal("--timeout")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 2000))
                                        .executes(ctx -> execWithOverride(ctx, ctx.getArgument("dimension", ResourceKey.class), "timeout"))))
                        .then(Commands.literal("--verbose")
                                .executes(ctx -> execWithOverride(ctx, ctx.getArgument("dimension", ResourceKey.class), "verbose")))
                        .then(Commands.literal("--all")
                                .executes(ctx -> execWithOverride(ctx, ctx.getArgument("dimension", ResourceKey.class), "all"))));

        dispatcher.register(Commands.literal("betterspawn").then(root));
    }

    private static CompletableFuture<Suggestions> suggestDimensions(CommandSourceStack src, SuggestionsBuilder builder) {
        MinecraftServer server = src.getServer();
        if (server != null) {
            for (ResourceLocation loc : server.registryAccess().registryOrThrow(Registries.DIMENSION).keySet()) {
                builder.suggest(loc.toString());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMethods(SuggestionsBuilder builder) {
        for (String id : com.proto781m.betterspawn.api.SpawnMethodRegistry.getAllMethods().keySet()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    }

    private static int exec(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension, String mode) throws CommandSyntaxException {
        GotoDimOptions opts = new GotoDimOptions(mode);
        return runGotoDim(ctx, dimension, opts);
    }

    private static int execWithOverride(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension, String optName) throws CommandSyntaxException {
        GotoDimOptions opts = new GotoDimOptions("simulate");
        String raw = ctx.getInput().substring(ctx.getInput().indexOf(optName));
        if (raw.startsWith("--method ")) opts.methodOverride = raw.substring(9).split(" ")[0];
        if (raw.startsWith("--params ")) opts.paramsOverride = raw.substring(9);
        if (raw.startsWith("--seed ")) opts.seedOverride = Long.parseLong(raw.substring(7).split(" ")[0]);
        if (raw.startsWith("--repeat ")) opts.repeat = Integer.parseInt(raw.substring(9).split(" ")[0]);
        if (raw.startsWith("--timeout ")) opts.timeoutTicks = Integer.parseInt(raw.substring(10).split(" ")[0]);
        if (raw.contains("--verbose")) opts.verbose = true;
        if (raw.contains("--all")) opts.all = true;
        return runGotoDim(ctx, dimension, opts);
    }

    private static int runGotoDim(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension, GotoDimOptions opts) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("§c维度不存在: " + dimension.location()));
            return 0;
        }

        if (opts.all) {
            // 对所有维度跑一次
            for (ResourceLocation loc : server.registryAccess().registryOrThrow(Registries.DIMENSION).keySet()) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, loc);
                GotoDimOptions copy = new GotoDimOptions("simulate");
                copy.repeat = opts.repeat;
                copy.timeoutTicks = opts.timeoutTicks;
                copy.verbose = opts.verbose;
                runSingle(ctx, dim, server, copy);
            }
            return 1;
        }

        return runSingle(ctx, dimension, server, opts);
    }

    private static int runSingle(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                 MinecraftServer server, GotoDimOptions opts) throws CommandSyntaxException {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("§c维度不存在: " + dimension.location()));
            return 0;
        }

        boolean isSimulate = "simulate".equals(opts.mode);
        boolean isTeleport = "teleport".equals(opts.mode) || "verify".equals(opts.mode);
        boolean isVerify = "verify".equals(opts.mode);

        if (isTeleport && ctx.getSource().getEntity() instanceof ServerPlayer player) {
            if (!ctx.getSource().hasPermission(2)) {
                ctx.getSource().sendFailure(Component.literal("§cteleport/verify 需要权限等级 2"));
                return 0;
            }
        }

        // 统计累计器
        int total = opts.repeat > 0 ? opts.repeat : 1;
        long totalTime = 0;
        int totalSamples = 0;
        int successCount = 0;
        int fallbackCount = 0;

        ResolveResult lastResult = null;

        for (int i = 0; i < total; i++) {
            if (opts.repeat > 0 && i > 0) {
                ctx.getSource().sendSystemMessage(Component.literal("§7第 " + (i + 1) + "/" + total + " 次..."));
            }

            // 构造临时规则（覆盖 method/params）
            var ruleOpt = SpawnTemplateManager.get(dimension.location());
            if (ruleOpt.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("§c该维度无模板，跳过"));
                return 0;
            }
            var rule = ruleOpt.get();

            // 应用覆盖
            if (opts.methodOverride != null) {
                rule = new com.proto781m.betterspawn.template.SpawnRule(
                        rule.dimension(), opts.methodOverride, rule.parameters(),
                        rule.fallback(), rule.fallbackParameters());
            }
            if (opts.paramsOverride != null) {
                try {
                    JsonElement params = JsonParser.parseString(opts.paramsOverride);
                    if (params.isJsonObject()) {
                        rule = new com.proto781m.betterspawn.template.SpawnRule(
                                rule.dimension(), rule.method(),
                                com.proto781m.betterspawn.template.SpawnTemplateLoader.parseParams(params.getAsJsonObject()),
                                rule.fallback(), rule.fallbackParameters());
                    }
                } catch (Exception ex) {
                    ctx.getSource().sendFailure(Component.literal("§cparams JSON 解析失败: " + ex.getMessage()));
                    return 0;
                }
            }

            long seed = opts.seedOverride != null ? opts.seedOverride : level.getSeed();
            long deadline = System.currentTimeMillis() + (opts.timeoutTicks > 0 ? opts.timeoutTicks : BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get()) * 50L;

            SpawnContext spawnCtx = new SpawnContext(
                    level, rule, seed, true, // simulate=true：不落库
                    deadline, new com.proto781m.betterspawn.api.SearchTrace());

            ResolveResult result = SpawnPointResolver.resolveFromTemplate(server, dimension, true);

            lastResult = result;

            totalTime += result.elapsedMillis();
            totalSamples += result.samples();
            if (result.status() == ResolveResult.Status.OK) successCount++;
            if (result.fallbackUsed()) fallbackCount++;

            // 单次输出
            if (opts.repeat <= 1 || opts.verbose) {
                printResult(ctx, dimension, result, spawnCtx.trace(), opts.verbose);
            }

            // teleport / verify
            if (isTeleport && lastResult != null && lastResult.pos() != null && ctx.getSource().getEntity() instanceof ServerPlayer player) {
                com.proto781m.betterspawn.util.TeleportHelper.teleport(player, level, lastResult.pos().getX() + 0.5, lastResult.pos().getY(), lastResult.pos().getZ() + 0.5, lastResult.angle(), player.getXRot());
                ctx.getSource().sendSystemMessage(Component.literal("§a已传送到 " + lastResult.pos()));

                if (isVerify) {
                    // 简单轮询验证：20 tick 后检查
                    server.execute(() -> {
                        try {
                            Thread.sleep(1000); // 20 tick ≈ 1s
                        } catch (InterruptedException ignored) {}
                        if (player.isAlive()) {
                            boolean onGround = player.onGround();
                            boolean inLiquid = player.isInFluidType();
                            boolean inVoid = player.getY() < level.getMinBuildHeight();
                            String msg = "§e[verify] 落地校验: " + (onGround ? "站在方块上" : "未落地") +
                                    (inLiquid ? " §c在液体中" : "") +
                                    (inVoid ? " §c掉入虚空" : "");
                            player.sendSystemMessage(Component.literal(msg));
                        }
                    });
                }
            }

            // 汇总统计
            if (total > 1) {
                ctx.getSource().sendSystemMessage(Component.literal(
                        "§e=== " + dimension.location() + " " + total + " 次重复测试 ==="));
                ctx.getSource().sendSystemMessage(Component.literal(
                        "  成功率: " + String.format("%.1f%%", successCount * 100.0 / total) +
                                "  fallback率: " + String.format("%.1f%%", fallbackCount * 100.0 / total)));
                ctx.getSource().sendSystemMessage(Component.literal(
                        "  耗时 min/avg/max: N/A / " + (totalTime / total) + " / N/A ms" +
                                "  平均采样: " + (totalSamples / total) +
                                "  最大半径: " + (lastResult != null ? lastResult.maxRadiusReached() : 0)));
            }
        }

        return 1;
    }

    private static void printResult(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                                    ResolveResult result, com.proto781m.betterspawn.api.SearchTrace trace, boolean verbose) {
        String statusColor = switch (result.status()) {
            case OK -> "§a";
            case FALLBACK -> "§e";
            case TIMEOUT -> "§c";
            case UNSAFE -> "§6";
            default -> "§c";
        };
        ctx.getSource().sendSystemMessage(Component.literal(
                "§e[BetterSpawn] 维度 " + dimension.location() + " 解析结果"));
        ctx.getSource().sendSystemMessage(Component.literal(
                "  方法: " + result.usedMethod() + (result.fallbackUsed() ? " (fallback)" : "")));
        if (result.pos() != null) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "  坐标: " + result.pos() + " [点击传送]".replace("[点击传送]", "")));
        }
        ctx.getSource().sendSystemMessage(Component.literal(
                "  耗时: " + result.elapsedMillis() + " ms"));
        ctx.getSource().sendSystemMessage(Component.literal(
                "  采样: " + result.samples() + "  最大半径: " + result.maxRadiusReached()));
        ctx.getSource().sendSystemMessage(Component.literal(
                "  状态: " + statusColor + result.status()));
        for (String w : result.warnings()) {
            ctx.getSource().sendSystemMessage(Component.literal("  ⚠ " + w));
        }
        if (verbose && trace != null && !trace.rings().isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("  详细轨迹:"));
            for (var r : trace.rings()) {
                ctx.getSource().sendSystemMessage(Component.literal(
                        "    半径 " + r.radius() + ": 候选 " + r.candidates() + ", 接受 " + r.accepted()));
            }
        }
    }

    /** 选项容器，简化版。生产建议用完整的 brigadier 参数树。 */
    private static class GotoDimOptions {
        String mode;
        String methodOverride;
        String paramsOverride;
        Long seedOverride;
        int repeat = 1;
        int timeoutTicks = 0;
        boolean verbose = false;
        boolean all = false;

        // 用于累计的字段
        long totalTime = 0;
        int totalSamples = 0;
        int successCount = 0;
        int fallbackCount = 0;
        ResolveResult lastResult = null;

        GotoDimOptions(String mode) {
            this.mode = mode;
        }
    }
}