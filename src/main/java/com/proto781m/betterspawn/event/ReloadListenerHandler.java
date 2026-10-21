package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.template.SpawnTemplateReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * 注册数据包 reload listener（监听 spawn_rules 目录变化）。
 */
@EventBusSubscriber(modid = com.proto781m.betterspawn.BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ReloadListenerHandler {

    private ReloadListenerHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpawnTemplateReloadListener());
    }
}