package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.template.SpawnTemplateReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Registers the datapack reload listener for the {@code spawn_rules} directory. */
@EventBusSubscriber(modid = com.proto781m.betterspawn.BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ReloadListenerHandler {

    private ReloadListenerHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpawnTemplateReloadListener());
    }
}