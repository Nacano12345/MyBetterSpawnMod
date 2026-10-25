package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.template.SpawnTemplateReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/** Registers the datapack reload listener for the {@code spawn_rules} directory. */
@EventBusSubscriber(modid = BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ReloadListenerHandler {

    private static final ResourceLocation LISTENER_ID =
            ResourceLocation.fromNamespaceAndPath(BetterSpawn.MODID, "spawn_rules");

    private ReloadListenerHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(LISTENER_ID, new SpawnTemplateReloadListener());
    }
}
