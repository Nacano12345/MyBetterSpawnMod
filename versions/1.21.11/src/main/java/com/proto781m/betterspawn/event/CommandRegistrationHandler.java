package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.command.GotoDimCommand;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import com.proto781m.betterspawn.command.SetWorldSpawnCommand;
import com.proto781m.betterspawn.command.SpawnPointCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Registers all custom commands under {@code /betterspawn}. */
@EventBusSubscriber(modid = com.proto781m.betterspawn.BetterSpawn.MODID)
public final class CommandRegistrationHandler {

    private CommandRegistrationHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SetWorldSpawnCommand.register(event.getDispatcher());
        SpawnPointCommand.register(event.getDispatcher());
        GotoDimCommand.register(event.getDispatcher());

        if (BetterSpawnConfig.OVERRIDE_VANILLA_COMMANDS.get()) {
            SetWorldSpawnCommand.registerVanillaOverride(event.getDispatcher());
            SpawnPointCommand.registerVanillaOverride(event.getDispatcher());
        }
    }
}