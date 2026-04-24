package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerBlockEntityMixin extends BlockEntity {

    public SculkShriekerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // Force-update the blockstate in the world if an inert shrieker activates.
    // This permanently fixes the block for future valid interactions.
    @Inject(method = "tryShriek", at = @At("HEAD"))
    @SuppressWarnings("deprecation")
    private void emergent$ensureCanSummonState(ServerLevel world, ServerPlayer player, CallbackInfo ci) {
        if (EmergentConfig.get().universalWardenSummoning && world != null) {
            BlockState state = this.getBlockState();
            if (state.hasProperty(SculkShriekerBlock.CAN_SUMMON) && !state.getValue(SculkShriekerBlock.CAN_SUMMON)) {
                // Upgrade this shrieker to be capable of summoning
                BlockState newState = state.setValue(SculkShriekerBlock.CAN_SUMMON, true);
                world.setBlock(this.worldPosition, newState, 3);
                // Force update cached state so subsequent logic in this very tick sees the
                // change
                // We use Deprecation suppression because we intentionally need to update the
                // cache
                // manually here for the fix to work in the same tick.

                this.setBlockState(newState);
            }
        }
    }

}
