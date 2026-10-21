package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.logic.DimensionSpawnInitializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * 服务器启动相关：在维度创建前/后做初始化。
 */
@EventBusSubscriber(modid = com.proto781m.betterspawn.BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ServerLifecycleHandler {

    private ServerLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // 此时所有维度已创建，LevelEvent.Load 会随后触发
        // 这里可以做一些预热，如预加载模板
        DimensionSpawnInitializer.initAll(event.getServer());
    }
}