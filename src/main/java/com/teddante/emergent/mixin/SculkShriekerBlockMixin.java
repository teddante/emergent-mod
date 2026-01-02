package com.teddante.emergent.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkShriekerBlock.class)
public class SculkShriekerBlockMixin {

    // Forces the block to be placed with CAN_SUMMON = true
    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void emergent$forceCanSummonOnPlace(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state != null && state.contains(SculkShriekerBlock.CAN_SUMMON)) {
            cir.setReturnValue(state.with(SculkShriekerBlock.CAN_SUMMON, true));
        }
    }
}
