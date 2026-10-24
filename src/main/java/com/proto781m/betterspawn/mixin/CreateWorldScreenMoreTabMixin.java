package com.proto781m.betterspawn.mixin;

import com.proto781m.betterspawn.BetterSpawn;
import com.proto781m.betterspawn.client.CreateWorldSpawnDimensionButton;
import com.proto781m.betterspawn.config.BetterSpawnConfig;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inserts the "Spawn Dimension" button below the three buttons on the create-world
 * "More" tab.
 *
 * <p>Vanilla's MoreTab uses a 1-column, 3-row layout. This adds the button on row 4
 * of the same column, aligning it below Game Rules, Experimental, and Data Packs.
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
        BetterSpawn.diag("Added spawn dimension selector button to CreateWorldScreen MoreTab");
    }
}
