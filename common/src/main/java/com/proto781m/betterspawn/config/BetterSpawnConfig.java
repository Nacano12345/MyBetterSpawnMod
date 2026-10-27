package com.proto781m.betterspawn.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Global BetterSpawn configuration (COMMON type; server-authoritative). */
public final class BetterSpawnConfig {

    private BetterSpawnConfig() {
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<RespawnPolicy> RESPAWN_POLICY;
    public static final ModConfigSpec.EnumValue<CommandSpawnMode> COMMAND_SPAWN_MODE;
    public static final ModConfigSpec.EnumValue<NoPersonalSpawnBehavior> NO_PERSONAL_SPAWN;
    public static final ModConfigSpec.BooleanValue SYNC_VANILLA_NBT;
    public static final ModConfigSpec.IntValue FALLBACK_MAX_DEPTH;
    public static final ModConfigSpec.IntValue SEARCH_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue MAX_CHUNK_LOADS;
    public static final ModConfigSpec.BooleanValue ENABLE_GOTODIM;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_DIMENSION_SELECTOR;
    public static final ModConfigSpec.BooleanValue OVERRIDE_VANILLA_COMMANDS;
    public static final ModConfigSpec.BooleanValue ENABLE_DIAGNOSTIC_LOGS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        RESPAWN_POLICY = b
                .comment("Respawn priority: COMMAND_OVER_BED = command spawn before bed; BED_OVER_COMMAND = bed/anchor first.")
                .defineEnum("respawnPolicy", RespawnPolicy.COMMAND_OVER_BED);
        COMMAND_SPAWN_MODE = b
                .comment("Command spawn handling: DEFAULT = exact /spawnpoint position; BED_ANCHOR = search a small safe area like a bed/anchor; WORLDSPAWN = run the dimension spawn template centered on the command point.")
                .defineEnum("commandSpawnMode", CommandSpawnMode.DEFAULT);
        NO_PERSONAL_SPAWN = b
                .comment("Final fallback when the death dimension has no global spawn either: BACK_TO_OVERWORLD = return to the Overworld; STAY_IN_DEATH_DIMENSION = stay in the death dimension or use vanilla behavior.")
                .defineEnum("noPersonalSpawnBehavior", NoPersonalSpawnBehavior.BACK_TO_OVERWORLD);
        SYNC_VANILLA_NBT = b
                .comment("When running /betterspawn spawnpoint set, also write the vanilla respawn NBT for compatibility with other mods.")
                .define("syncVanillaNbt", true);
        b.pop();

        b.push("search");
        FALLBACK_MAX_DEPTH = b
                .comment("Maximum recursion depth of the fallback chain, preventing infinite loops between methods.")
                .defineInRange("fallbackMaxDepth", 3, 1, 10);
        SEARCH_TIMEOUT_TICKS = b
                .comment("Timeout in ticks for one spawn-point resolution.")
                .defineInRange("searchTimeoutTicks", 200, 1, 2000);
        MAX_CHUNK_LOADS = b
                .comment("Maximum chunks loaded or generated synchronously per resolution. Large values can cause first-join lag or save I/O pressure.")
                .defineInRange("maxChunkLoads", 64, 1, 8192);
        b.pop();

        b.push("debug");
        ENABLE_GOTODIM = b
                .comment("Enable the /gotodim and /betterspawn gotodim test commands.")
                .define("enableGotoDim", true);
        ENABLE_DIAGNOSTIC_LOGS = b
                .comment("Emit detailed diagnostic logs for the respawn chain, personal spawn writes, command parsing, verify results, and more. Disabled by default.")
                .define("enableDiagnosticLogs", false);
        b.pop();

        b.push("experimental");
        ENABLE_SPAWN_DIMENSION_SELECTOR = b
                .comment("Experimental: show the Spawn Dimension button on the single-player/LAN create-world screen. Dedicated servers do not have this screen.")
                .define("enableSpawnDimensionSelector", true);
        OVERRIDE_VANILLA_COMMANDS = b
                .comment("Experimental: override the vanilla /spawnpoint and /setworldspawn command roots with BetterSpawn handlers. An optional trailing dimension is accepted. Requires a server restart; disabled by default.")
                .define("overrideVanillaCommands", false);
        b.pop();

        SPEC = b.build();
    }
}
