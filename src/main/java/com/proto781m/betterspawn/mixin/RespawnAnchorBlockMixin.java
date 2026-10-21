package com.proto781m.betterspawn.mixin;

import com.proto781m.betterspawn.data.PlayerSpawnData;
import com.proto781m.betterspawn.data.SpawnSlot;
import com.proto781m.betterspawn.data.SpawnSlotType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 重生锚 Mixin：在成功设置重生点时记录到双槽。
 *
 * <p>目标方法签名：{@code protected InteractionResult useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)}
 */
@Mixin(RespawnAnchorBlock.class)
public abstract class RespawnAnchorBlockMixin {

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void betterspawn$onAnchorUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide() || player == null) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        InteractionResult result = cir.getReturnValue();
        if (result != InteractionResult.SUCCESS && result != InteractionResult.CONSUME) return;

        float angle = serverPlayer.getYRot();
        PlayerSpawnData.setSlot(serverPlayer, SpawnSlot.valid(SpawnSlotType.ANCHOR, serverLevel.dimension(), pos, angle));
    }
}