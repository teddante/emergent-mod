package com.teddante.emergent.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
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
    private void emergent$ensureCanSummonState(ServerWorld world, ServerPlayerEntity player, CallbackInfo ci) {
        if (world != null) {
            BlockState state = this.getCachedState();
            if (state.contains(SculkShriekerBlock.CAN_SUMMON) && !state.get(SculkShriekerBlock.CAN_SUMMON)) {
                // Upgrade this shrieker to be capable of summoning
                world.setBlockState(this.pos, state.with(SculkShriekerBlock.CAN_SUMMON, true));
            }
        }
    }

    // Redirect the checking of the CAN_SUMMON property in the shriek method.
    // This forces the shrieker to behave as if it can summon, regardless of its
    // actual state.
    // Redirect the checking of the CAN_SUMMON property in the shriek method to
    // always return true.
    // This effectively tricks the game into thinking the shrieker can always summon
    // a warden.
    // Redirect the property check to ensure the CURRENT execution behaves correctly
    // even before the state update might take effect or if cached state is used.
    @Redirect(method = "shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;get(Lnet/minecraft/state/property/Property;)Ljava/lang/Comparable;"))
    private Comparable<?> emergent$alwaysCanSummon(BlockState instance, Property<?> property) {
        if (property == SculkShriekerBlock.CAN_SUMMON || "can_summon".equals(property.getName())) {
            return true;
        }
        return instance.get(property);
    }
}
