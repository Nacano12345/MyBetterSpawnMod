package com.proto781m.betterspawn.client;

import com.proto781m.betterspawn.logic.CreateWorldSpawnSelection;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * 创建世界界面“更多”页的“出生维度”按钮工厂。
 *
 * <p>按钮由 {@code CreateWorldScreenMoreTabMixin} 插入到 MoreTab 的布局中，
 * 位置在“游戏规则 / 实验性内容 / 数据包”三个按钮下方。
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
