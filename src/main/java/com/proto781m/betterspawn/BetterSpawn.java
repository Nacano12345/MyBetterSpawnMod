package com.proto781m.betterspawn;

import com.mojang.logging.LogUtils;
import com.proto781m.betterspawn.api.SpawnMethodRegistry;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.method.DefaultSpawnMethod;
import com.proto781m.betterspawn.method.FixedSpawnMethod;
import com.proto781m.betterspawn.method.FloatingIslandSpawnMethod;
import com.proto781m.betterspawn.method.PlatformSpawnMethod;
import com.proto781m.betterspawn.method.TopSpawnMethod;
import com.proto781m.betterspawn.method.WaterWorldSpawnMethod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * BetterSpawn 模组主入口。
 *
 * <p>模组 ID: {@value #MODID}<br>
 * 显示名: Proto781M's Better Spawn<br>
 * 加载器: NeoForge 1.21+
 */
@Mod(BetterSpawn.MODID)
public class BetterSpawn {

    /** 模组 ID，必须与 neoforge.mods.toml 中的 modId 一致。 */
    public static final String MODID = "betterspawn";

    /** 共享日志实例。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    public BetterSpawn(IEventBus modEventBus, ModContainer modContainer) {
        // 1. 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, BetterSpawnConfig.SPEC);

        // 2. 注册内置出生方法
        registerMethods();

        // 3. 事件总线监听器已通过 @EventBusSubscriber 自动注册
        //    （CommandRegistrationHandler、ReloadListenerHandler、ServerLifecycleHandler、
        //     DimensionSpawnInitializer、BedAnchorHandler）

        // 4. 标记模组加载完成
        LOGGER.info("BetterSpawn loaded (NeoForge 1.21)");
    }

    /** 注册所有内置出生点计算方法。 */
    private void registerMethods() {
        SpawnMethodRegistry.register(new DefaultSpawnMethod());
        SpawnMethodRegistry.register(new TopSpawnMethod());
        SpawnMethodRegistry.register(new FixedSpawnMethod());
        SpawnMethodRegistry.register(new FloatingIslandSpawnMethod());
        SpawnMethodRegistry.register(new WaterWorldSpawnMethod());
        SpawnMethodRegistry.register(PlatformSpawnMethod.INSTANCE);
        LOGGER.info("Registered {} spawn methods", SpawnMethodRegistry.getAllMethods().size());
    }
}