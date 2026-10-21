package com.proto781m.betterspawn.mixin;

import com.proto781m.betterspawn.client.CreateWorldSpawnDimensionButton;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在创建世界界面“更多”页的三个按钮下方插入“出生维度”按钮。
 *
 * <p>原版 MoreTab 的布局是 1 列 3 行；这里在同一列第 4 行添加按钮，
 * 所以位置会自然对齐在“游戏规则 / 实验性内容 / 数据包”下面。
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
public abstract class CreateWorldScreenMoreTabMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void betterspawn$addSpawnDimensionButton(CallbackInfo ci) {
        if (!BetterSpawnConfig.ENABLE_SPAWN_DIMENSION_SELECTOR.get()) {
            return;
        }
        GridLayout layout = ((GridLayoutTabAccessor) (Object) this).betterspawn$getLayout();
        layout.addChild(CreateWorldSpawnDimensionButton.create(), 3, 0);
    }
}
