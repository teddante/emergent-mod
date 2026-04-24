package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkShriekerBlock.class)
public class SculkShriekerBlockMixin {

    // Forces the block to be placed with CAN_SUMMON = true
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void emergent$forceCanSummonOnPlace(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (EmergentConfig.get().universalWardenSummoning && state != null && state.hasProperty(SculkShriekerBlock.CAN_SUMMON)) {
            cir.setReturnValue(state.setValue(SculkShriekerBlock.CAN_SUMMON, true));
        }
    }
}
