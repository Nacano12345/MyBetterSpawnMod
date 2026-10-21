package com.proto781m.betterspawn.event;

import com.proto781m.betterspawn.command.GotoDimCommand;
import com.proto781m.betterspawn.command.SetWorldSpawnCommand;
import com.proto781m.betterspawn.command.SpawnPointCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 注册所有自定义命令（挂在 /betterspawn 下）。
 */
@EventBusSubscriber(modid = com.proto781m.betterspawn.BetterSpawn.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CommandRegistrationHandler {

    private CommandRegistrationHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SetWorldSpawnCommand.register(event.getDispatcher());
        SpawnPointCommand.register(event.getDispatcher());
        GotoDimCommand.register(event.getDispatcher());
    }
}