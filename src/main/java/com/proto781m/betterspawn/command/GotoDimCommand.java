package com.proto781m.betterspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.api.ResolveResult;
import com.proto781m.betterspawn.api.SearchTrace;
import com.proto781m.betterspawn.api.SpawnMethodRegistry;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.data.WorldSpawnData;
import com.proto781m.betterspawn.logic.SpawnPointResolver;
import com.proto781m.betterspawn.logic.VerifyTaskManager;
import com.proto781m.betterspawn.template.SpawnRule;
import com.proto781m.betterspawn.template.SpawnTemplateLoader;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import com.proto781m.betterspawn.util.TeleportHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * {@code /gotodim} (also registered as {@code /betterspawn gotodim}): sandbox testing
 * command.
 *
 * <pre>
 * /gotodim [&lt;dimension&gt;] [simulate|teleport|verify] [--method &lt;id&gt;] [--params &lt;json&gt;]
 *          [--seed &lt;n&gt;] [--repeat &lt;n&gt;] [--timeout &lt;ticks&gt;] [--verbose] [--all]
 * </pre>
 *
 * <p>All arguments are optional. The dimension defaults to the current dimension and
 * the mode defaults to {@code simulate}. {@code --method} and {@code --params} apply
 * temporary rule overrides.
 *
 * <p>When a template sets {@code allow_position_overwrite=true} in {@code parameters},
 * the probe uses the dimension's current global spawn (Overworld: {@code level.dat};
 * other dimensions: {@code WorldSpawnData}), so {@code worldspawn global} takes effect
 * immediately. This is disabled by default. Search methods overwrite
 * {@code center_x/center_z}; {@code fixed} and {@code platform} overwrite their
 * coordinates; {@code portal_type} also updates its platform fallback. Explicit
 * position arguments in {@code --params} always take precedence for that run.
 */
public final class GotoDimCommand {

    private static final SimpleCommandExceptionType ERROR = new SimpleCommandExceptionType(
            Component.translatable("betterspawn.msg.gotodim.error"));

    private GotoDimCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot());
        dispatcher.register(Commands.literal("betterspawn").then(buildRoot()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        return Commands.literal("gotodim")
                .requires(src -> BetterSpawnConfig.ENABLE_GOTODIM.get())
                .executes(ctx -> run(ctx, null, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(GotoDimCommand::suggestArgs)
                        .executes(ctx -> dispatch(ctx, StringArgumentType.getString(ctx, "args"))));
    }

    // ------------------------------------------------------------------
    // Argument parsing
    // ------------------------------------------------------------------

    private static int dispatch(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        List<String> tokens = tokenize(raw == null ? "" : raw.trim());

        // The dimension may appear anywhere; prefer the last token (new syntax: test_arg... dim).
        ResourceKey<Level> dimension = null;
        List<String> options = new ArrayList<>(tokens);
        for (int i = tokens.size() - 1; i >= 0; i--) {
            ResourceKey<Level> key = dimensionKey(tokens.get(i), server);
            if (key != null) {
                dimension = key;
                options.remove(i);
                break;
            }
        }

        return run(ctx, dimension, String.join(" ", options));
    }

    private static ResourceKey<Level> dimensionKey(String token, MinecraftServer server) {
        if (token == null || token.isBlank()
                || token.startsWith("-")
                || token.startsWith("{") || token.startsWith("[")
                || token.startsWith("\"") || token.startsWith("'")) {
            return null;
        }
        Identifier id = Identifier.tryParse(token);
        if (id == null || server == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key) == null ? null : key;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> dimension,
                           String rawOptions) throws CommandSyntaxException {
        Options options = parseOptions(rawOptions);
        MinecraftServer server = ctx.getSource().getServer();

        if (options.all) {
            Options simulateOnly = options.copy();
            simulateOnly.mode = "simulate";
            int success = 0;
            for (ServerLevel level : server.getAllLevels()) {
                if (runOne(ctx, level, simulateOnly, false) > 0) {
                    success++;
                }
            }
            return success > 0 ? 1 : 0;
        }

        ResourceKey<Level> target = dimension != null
                ? dimension
                : ctx.getSource().getLevel().dimension();
        ServerLevel level = server.getLevel(target);
        if (level == null) {
            ctx.getSource().sendFailure(Component.translatable("betterspawn.msg.dimension_missing", target.identifier().toString()));
            return 0;
        }
        return runOne(ctx, level, options, true);
    }

    private static Options parseOptions(String raw) throws CommandSyntaxException {
        Options options = new Options();
        List<String> tokens = tokenize(raw);

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            switch (token) {
                case "simulate", "teleport", "verify" -> options.mode = token;
                case "--method" -> options.method = value(tokens, ++i, token);
                case "--params" -> options.params = value(tokens, ++i, token);
                case "--seed" -> options.seed = parseLong(value(tokens, ++i, token), token);
                case "--repeat" -> options.repeat = clamp(parseInt(value(tokens, ++i, token), token), 1, 100);
                case "--timeout" -> options.timeoutTicks = clamp(parseInt(value(tokens, ++i, token), token), 1, 2000);
                case "--verbose" -> options.verbose = true;
                case "--all" -> options.all = true;
                default -> throw new SimpleCommandExceptionType(
                        Component.translatable("betterspawn.msg.gotodim.unknown_option", token)).create();
            }
        }
        return options;
    }

    private static String value(List<String> tokens, int index, String option) throws CommandSyntaxException {
        if (index >= tokens.size()) {
            throw new SimpleCommandExceptionType(
                    Component.translatable("betterspawn.msg.gotodim.missing_value", option)).create();
        }
        return tokens.get(index);
    }

    private static long parseLong(String value, String option) throws CommandSyntaxException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new SimpleCommandExceptionType(
                    Component.translatable("betterspawn.msg.gotodim.invalid_number", option, value)).create();
        }
    }

    private static int parseInt(String value, String option) throws CommandSyntaxException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new SimpleCommandExceptionType(
                    Component.translatable("betterspawn.msg.gotodim.invalid_number", option, value)).create();
        }
    }

    /** Splits arguments on whitespace while preserving whitespace inside braces and quotes,
     * so {@code --params {"x": 1}} stays intact. */
    private static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int braces = 0;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                // Single quotes are only for shell-style argument wrapping and are not written into JSON.
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '{' || c == '[') {
                    braces++;
                } else if (c == '}' || c == ']') {
                    braces = Math.max(0, braces - 1);
                } else if (Character.isWhitespace(c) && braces == 0) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static CompletableFuture<Suggestions> suggestArgs(CommandContext<CommandSourceStack> ctx,
                                                              SuggestionsBuilder builder) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String remaining = builder.getRemaining();

        // For greedyString completion, narrow the range to the current token; otherwise the client
        // filters against the whole argument, so only the first parameter can be completed.
        int currentStart = currentTokenStart(remaining);
        String current = remaining.substring(currentStart);
        List<String> before = tokenize(remaining.substring(0, currentStart));
        String last = before.isEmpty() ? null : before.get(before.size() - 1);

        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + currentStart);

        // Completing the --method value
        if ("--method".equals(last)) {
            suggestValues(offset, current, SpawnMethodRegistry.getAllMethods().keySet());
            return offset.buildFuture();
        }

        // Completing the --params value: suggest common parameter JSON
        if ("--params".equals(last)) {
            if (current.isEmpty() || current.startsWith("{")) {
                suggestValues(offset, current, List.of(
                        "{\"max_radius\":512}",
                        "{\"search_radius\":128}",
                        "{\"ring_step\":64}",
                        "{\"min_y\":32,\"max_y\":127}",
                        "{\"center_x\":0,\"center_z\":0}",
                        "{\"portal_width\":2,\"portal_height\":3}",
                        "{\"build_platform\":true}",
                        "{\"roof_scan\":8,\"min_air_height\":2}",
                        "{\"max_chunk_loads\":64}"
                ));
            }
            return offset.buildFuture();
        }

        // Do not suggest numeric options to avoid misleading completions
        if ("--seed".equals(last) || "--repeat".equals(last) || "--timeout".equals(last)) {
            return offset.buildFuture();
        }

        Set<String> used = new HashSet<>(before);
        boolean hasDimension = before.stream().anyMatch(token -> dimensionKey(token, server) != null);

        List<String> flags = new ArrayList<>();
        for (String flag : List.of("--method", "--params", "--seed", "--repeat",
                "--timeout", "--verbose", "--all")) {
            if (!used.contains(flag)) {
                flags.add(flag);
            }
        }

        List<String> modes = new ArrayList<>();
        for (String mode : List.of("simulate", "teleport", "verify")) {
            if (!used.contains(mode)) {
                modes.add(mode);
            }
        }

        List<String> dimensions = new ArrayList<>();
        if (!hasDimension && server != null) {
            server.registryAccess().lookupOrThrow(Registries.DIMENSION).keySet().stream()
                    .map(Identifier::toString)
                    .forEach(dimensions::add);
        }

        // First token: mode or option. Dimensions go last in the new syntax; suggest them when the input contains a namespace.
        if (before.isEmpty()) {
            if (current.startsWith("--")) {
                suggestValues(offset, current, flags);
            } else if (current.contains(":")) {
                suggestValues(offset, current, dimensions);
            } else {
                suggestValues(offset, current, modes);
                suggestValues(offset, current, flags);
            }
            return offset.buildFuture();
        }

        // Later tokens: modes, options, or dimensions (the dimension may be last)
        if (current.startsWith("--")) {
            suggestValues(offset, current, flags);
        } else if (current.contains(":")) {
            suggestValues(offset, current, dimensions);
        } else {
            suggestValues(offset, current, modes);
            suggestValues(offset, current, flags);
            suggestValues(offset, current, dimensions);
        }
        return offset.buildFuture();
    }

    /** Returns the start index of the last token, respecting quotes and whitespace inside
     * braces. */
    private static int currentTokenStart(String raw) {
        int start = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int braces = 0;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '{' || c == '[') {
                    braces++;
                } else if (c == '}' || c == ']') {
                    braces = Math.max(0, braces - 1);
                } else if (Character.isWhitespace(c) && braces == 0) {
                    start = i + 1;
                }
            }
        }
        return start;
    }

    private static void suggestValues(SuggestionsBuilder builder, String current, Iterable<String> values) {
        for (String value : values) {
            if (current.isEmpty() || value.startsWith(current)) {
                builder.suggest(value);
            }
        }
    }

    // ------------------------------------------------------------------
    // Single or repeated execution
    // ------------------------------------------------------------------

    private static int runOne(CommandContext<CommandSourceStack> ctx, ServerLevel level,
                              Options options, boolean allowTeleport) throws CommandSyntaxException {
        boolean teleport = allowTeleport
                && ("teleport".equals(options.mode) || "verify".equals(options.mode));
        boolean verify = allowTeleport && "verify".equals(options.mode);

        if (teleport) {
            if (!ctx.getSource().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
                ctx.getSource().sendFailure(Component.translatable("betterspawn.msg.gotodim.no_permission"));
                return 0;
            }
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer)) {
                ctx.getSource().sendFailure(Component.translatable("betterspawn.msg.gotodim.player_only"));
                return 0;
            }
        }

        SpawnRule base = SpawnTemplateManager.get(level.dimension().identifier()).orElse(null);
        if (base == null) {
            ctx.getSource().sendFailure(Component.translatable("betterspawn.msg.gotodim.no_template",
                    level.dimension().identifier().toString()));
            return 0;
        }
        SpawnRule rule = applyOverrides(level, base, options);

        int total = Math.max(1, options.repeat);
        long totalTime = 0;
        long totalSamples = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        int successCount = 0;
        int fallbackCount = 0;
        int maxRadius = 0;

        for (int i = 0; i < total; i++) {
            long seed = (options.seed != null ? options.seed : level.getSeed()) + i;
            int timeoutTicks = options.timeoutTicks > 0
                    ? options.timeoutTicks
                    : Math.max(1, BetterSpawnConfig.SEARCH_TIMEOUT_TICKS.get());
            SearchTrace trace = new SearchTrace();
            long deadline = System.currentTimeMillis() + timeoutTicks * 50L;

            ResolveResult result = SpawnPointResolver.resolveRule(
                    level, rule, seed, true, deadline, trace);

            totalTime += result.elapsedMillis();
            totalSamples += result.samples();
            minTime = Math.min(minTime, result.elapsedMillis());
            maxTime = Math.max(maxTime, result.elapsedMillis());
            maxRadius = Math.max(maxRadius, result.maxRadiusReached());
            if (result.pos() != null) {
                successCount++;
            }
            if (result.fallbackUsed()) {
                fallbackCount++;
            }

            if (total == 1 || options.verbose) {
                printResult(ctx, level, result, trace, options.verbose);
            }

            if (result.pos() != null) {
                executeTeleport(ctx, level, result, teleport, verify);
            }
        }

        if (total > 1) {
            long avgTime = totalTime / total;
            long avgSamples = totalSamples / total;
            ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.gotodim.repeat_header",
                    level.dimension().identifier().toString(), String.valueOf(total)));
            ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.gotodim.success_rate",
                    String.format("%.1f%%", successCount * 100.0 / total),
                    String.format("%.1f%%", fallbackCount * 100.0 / total)));
            ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.gotodim.time_stats",
                    String.valueOf(minTime), String.valueOf(avgTime), String.valueOf(maxTime)));
            ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.gotodim.sample_stats",
                    String.valueOf(avgSamples), String.valueOf(maxRadius)));
        }

        return successCount > 0 ? 1 : 0;
    }

    private static SpawnRule applyOverrides(ServerLevel level, SpawnRule base, Options options)
            throws CommandSyntaxException {
        String method = options.method != null ? options.method : base.method();

        Map<String, Object> params = new HashMap<>(base.parameters());
        boolean positionSpecified = false;
        if (options.params != null) {
            try {
                JsonElement parsed = JsonParser.parseString(options.params);
                if (!parsed.isJsonObject()) {
                    throw new SimpleCommandExceptionType(
                            Component.translatable("betterspawn.msg.gotodim.params_object")).create();
                }
                var json = parsed.getAsJsonObject();
                positionSpecified = json.has("center_x") || json.has("center_z")
                        || json.has("x") || json.has("y") || json.has("z")
                        || json.has("platform_x") || json.has("platform_y")
                        || json.has("platform_z");
                params.putAll(SpawnTemplateLoader.parseParams(json));
            } catch (CommandSyntaxException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new SimpleCommandExceptionType(
                        Component.translatable("betterspawn.msg.gotodim.params_invalid", String.valueOf(ex.getMessage()))).create();
            }
        }

        // Overwrite the probe position with the global spawn only when the template explicitly allows it, so datapack configuration is preserved.
        // Explicit position arguments in --params always take precedence.
        boolean allowOverwrite = !positionSpecified
                && Boolean.TRUE.equals(params.get("allow_position_overwrite"));
        Optional<BlockPos> probeCenter = allowOverwrite
                ? currentProbeCenter(level)
                : Optional.empty();
        probeCenter.ifPresent(center -> applyGlobalPosition(params, method, center));

        Map<String, Object> fallbackParams = base.fallbackParameters();
        if (probeCenter.isPresent() && "platform".equals(base.fallback())) {
            BlockPos center = probeCenter.get();
            fallbackParams = new HashMap<>(fallbackParams);
            fallbackParams.put("x", center.getX());
            fallbackParams.put("y", center.getY() - 1);
            fallbackParams.put("z", center.getZ());
        }

        if (method.equals(base.method()) && options.params == null && probeCenter.isEmpty()) {
            return base;
        }
        return new SpawnRule(base.dimension(), method, params, base.fallback(), fallbackParams);
    }

    /**
     * Writes the global spawn into the position parameters used by the method.
     * The global spawn stores feet coordinates; a platform's {@code y} is the platform
     * block center, so it is offset by one.
     */
    private static void applyGlobalPosition(Map<String, Object> params, String method, BlockPos center) {
        switch (method) {
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
    }

    /**
     * Current probe center for the dimension. The Overworld uses {@code level.dat}
     * (vanilla {@code /setworldspawn} also updates it); other dimensions use
     * {@code WorldSpawnData}.
     */
    private static Optional<BlockPos> currentProbeCenter(ServerLevel level) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            return Optional.of(level.getLevelData().getRespawnData().pos());
        }
        return WorldSpawnData.get(level.getServer())
                .get(level.dimension())
                .map(point -> point.pos());
    }

    private static void executeTeleport(CommandContext<CommandSourceStack> ctx, ServerLevel level,
                                        ResolveResult result, boolean teleport, boolean verify) {
        if (!teleport || !(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel targetLevel = result.dimension() == null
                ? level
                : ctx.getSource().getServer().getLevel(result.dimension());
        if (targetLevel == null) {
            return;
        }

        BlockPos pos = result.pos();
        TeleportHelper.teleport(player, targetLevel,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                result.angle(), player.getXRot());
        ctx.getSource().sendSystemMessage(Component.translatable("betterspawn.msg.gotodim.teleported",
                pos.toString(), targetLevel.dimension().identifier().toString()));

        if (verify) {
            // After a cross-dimension teleport, the client needs time to load chunks; wait 3 seconds before verifying.
            VerifyTaskManager.schedule(player, targetLevel, pos, 60);
        }
    }

    private static void printResult(CommandContext<CommandSourceStack> ctx, ServerLevel level,
                                    ResolveResult result, SearchTrace trace, boolean verbose) {
        String color = switch (result.status()) {
            case OK -> "§a";
            case FALLBACK -> "§e";
            case TIMEOUT -> "§c";
            case UNSAFE -> "§6";
            default -> "§c";
        };

        ctx.getSource().sendSystemMessage(Component.translatable(
                "betterspawn.msg.gotodim.result.header", level.dimension().identifier().toString()));

        net.minecraft.network.chat.MutableComponent methodName =
                Component.literal(result.usedMethod());
        if (result.fallbackUsed()) {
            methodName.append(Component.translatable("betterspawn.msg.gotodim.result.fallback_suffix"));
        }
        ctx.getSource().sendSystemMessage(Component.translatable(
                "betterspawn.msg.gotodim.result.method", methodName));

        if (result.pos() != null) {
            ctx.getSource().sendSystemMessage(Component.translatable(
                    "betterspawn.msg.gotodim.result.pos",
                    result.pos().toString(), String.valueOf(result.pos().getY())));
        } else {
            ctx.getSource().sendSystemMessage(Component.translatable(
                    "betterspawn.msg.gotodim.result.no_pos"));
        }
        ctx.getSource().sendSystemMessage(Component.translatable(
                "betterspawn.msg.gotodim.result.elapsed", String.valueOf(result.elapsedMillis())));
        ctx.getSource().sendSystemMessage(Component.translatable(
                "betterspawn.msg.gotodim.result.samples",
                String.valueOf(result.samples()), String.valueOf(result.maxRadiusReached())));
        ctx.getSource().sendSystemMessage(Component.translatable(
                "betterspawn.msg.gotodim.result.status",
                color, Component.translatable("betterspawn.status."
                        + result.status().name().toLowerCase(java.util.Locale.ROOT))));

        for (String warning : result.warnings()) {
            ctx.getSource().sendSystemMessage(Component.translatable(
                    "betterspawn.msg.gotodim.result.warning", warning));
        }
        if (verbose && trace != null && !trace.rings().isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.translatable(
                    "betterspawn.msg.gotodim.result.trace"));
            for (SearchTrace.RingTrace ring : trace.rings()) {
                ctx.getSource().sendSystemMessage(Component.translatable(
                        "betterspawn.msg.gotodim.result.ring",
                        String.valueOf(ring.radius()), String.valueOf(ring.candidates()),
                        String.valueOf(ring.accepted())));
            }
        }
        BetterSpawn.diag("gotodim {} -> method={}, status={}, pos={}, elapsed={}ms, samples={}",
                level.dimension().identifier(), result.usedMethod(), result.status(),
                result.pos(), result.elapsedMillis(), result.samples());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Options {
        String mode = "simulate";
        String method;
        String params;
        Long seed;
        int repeat = 1;
        int timeoutTicks = 0;
        boolean verbose;
        boolean all;

        Options copy() {
            Options copy = new Options();
            copy.mode = mode;
            copy.method = method;
            copy.params = params;
            copy.seed = seed;
            copy.repeat = repeat;
            copy.timeoutTicks = timeoutTicks;
            copy.verbose = verbose;
            copy.all = all;
            return copy;
        }
    }
}
