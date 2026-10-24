package com.proto781m.betterspawn.client;

import com.proto781m.betterspawn.logic.CreateWorldSpawnSelection;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Factory for the "Spawn Dimension" button on the create-world "More" tab.
 *
 * <p>{@code CreateWorldScreenMoreTabMixin} inserts the button into the MoreTab layout,
 * below the Game Rules, Experimental, and Data Packs buttons.
 */
public final class CreateWorldSpawnDimensionButton {

    private CreateWorldSpawnDimensionButton() {
    }

    public static Button create() {
        Button button = Button.builder(label(), pressed -> {
                    CreateWorldSpawnSelection.cycle();
                    pressed.setMessage(label());
                })
                .width(210)
                .build();
        button.setTooltip(Tooltip.create(
                Component.translatable("betterspawn.create_world.spawn_dimension.tooltip")));
        return button;
    }

    private static Component label() {
        return Component.translatable("betterspawn.create_world.spawn_dimension",
                CreateWorldSpawnSelection.displayName());
    }
}
