package com.teddante.emergent.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SculkShriekerBlockEntity.class)
public class SculkShriekerBlockEntityMixin {

    // Redirect the checking of the CAN_SUMMON property in the shriek method.
    // This forces the shrieker to behave as if it can summon, regardless of its
    // actual state.
    @Redirect(method = "shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;get(Lnet/minecraft/state/property/Property;)Ljava/lang/Comparable;"))
    private Comparable<?> emergent$alwaysCanSummon(BlockState instance, Property<?> property) {
        if (property == SculkShriekerBlock.CAN_SUMMON) {
            return true;
        }
        return instance.get(property);
    }
}
