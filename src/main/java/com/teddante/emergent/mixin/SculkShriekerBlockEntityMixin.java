package com.teddante.emergent.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.block.entity.SculkShriekerBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerBlockEntityMixin extends BlockEntity {

    public SculkShriekerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // Force-update the blockstate in the world if an inert shrieker activates.
    // This permanently fixes the block for future valid interactions.
    @Inject(method = "shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"))
    @SuppressWarnings("deprecation")
    private void emergent$ensureCanSummonState(ServerWorld world, ServerPlayerEntity player, CallbackInfo ci) {
        if (world != null) {
            BlockState state = this.getCachedState();
            if (state.contains(SculkShriekerBlock.CAN_SUMMON) && !state.get(SculkShriekerBlock.CAN_SUMMON)) {
                // Upgrade this shrieker to be capable of summoning
                BlockState newState = state.with(SculkShriekerBlock.CAN_SUMMON, true);
                world.setBlockState(this.pos, newState);
                // Force update cached state so subsequent logic in this very tick sees the
                // change
                // We use Deprecation suppression because we intentionally need to update the
                // cache
                // manually here for the fix to work in the same tick.

                this.setCachedState(newState);
            }
        }
    }

}
