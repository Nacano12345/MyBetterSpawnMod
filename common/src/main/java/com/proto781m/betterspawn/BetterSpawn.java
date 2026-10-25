package com.proto781m.betterspawn;

import com.mojang.logging.LogUtils;
import com.proto781m.betterspawn.api.SpawnMethodRegistry;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.method.CaveSpawnMethod;
import com.proto781m.betterspawn.method.DefaultSpawnMethod;
import com.proto781m.betterspawn.method.FixedSpawnMethod;
import com.proto781m.betterspawn.method.PortalTypeSpawnMethod;
import com.proto781m.betterspawn.method.FloatingIslandSpawnMethod;
import com.proto781m.betterspawn.method.PlatformSpawnMethod;
import com.proto781m.betterspawn.method.TopSpawnMethod;
import com.proto781m.betterspawn.method.WaterWorldSpawnMethod;
import com.proto781m.betterspawn.template.SpawnTemplateManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Main entry point for the BetterSpawn mod.
 *
 * <p>Mod ID: {@value #MODID}<br>
 * Display name: Proto781M's Better Spawn<br>
 * Loader: NeoForge 1.21+
 */
@Mod(BetterSpawn.MODID)
public class BetterSpawn {

    /** Mod ID. Must match the modId in neoforge.mods.toml. */
    public static final String MODID = "betterspawn";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public BetterSpawn(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, BetterSpawnConfig.SPEC);

        registerMethods();
        // Ensure built-in templates are readable before a datapack reload.
        SpawnTemplateManager.ensureLoaded();

        // Event listeners are registered automatically through @EventBusSubscriber:
        // CommandRegistrationHandler / ReloadListenerHandler / DimensionSpawnInitializer /
        // RespawnPositionHandler / PlayerSpawnSlotHandler / VerifyTaskManager
        LOGGER.info("BetterSpawn loaded (NeoForge 1.21)");
    }

    /** Whether diagnostic logging is enabled (debug.enableDiagnosticLogs). */
    public static boolean diagnosticsEnabled() {
        try {
            return BetterSpawnConfig.ENABLE_DIAGNOSTIC_LOGS.get();
        } catch (Exception ex) {
            return false;
        }
    }

    /** Emits output only when diagnostic logging is enabled to avoid noisy logs. */
    public static void diag(String message, Object... args) {
        if (diagnosticsEnabled()) {
            LOGGER.info("[diag] " + message, args);
        }
    }

    private void registerMethods() {
        SpawnMethodRegistry.register(new DefaultSpawnMethod());
        SpawnMethodRegistry.register(new CaveSpawnMethod());
        SpawnMethodRegistry.register(new TopSpawnMethod());
        SpawnMethodRegistry.register(new FixedSpawnMethod());
        SpawnMethodRegistry.register(new PortalTypeSpawnMethod());
        SpawnMethodRegistry.register(new FloatingIslandSpawnMethod());
        SpawnMethodRegistry.register(new WaterWorldSpawnMethod());
        SpawnMethodRegistry.register(PlatformSpawnMethod.INSTANCE);
        LOGGER.info("Registered {} spawn methods", SpawnMethodRegistry.getAllMethods().size());
    }
}
