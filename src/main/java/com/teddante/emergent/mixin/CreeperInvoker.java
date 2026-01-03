package com.teddante.emergent.mixin;

import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreeperEntity.class)
public interface CreeperInvoker {
    @Invoker("explode")
    void invokeExplode();
}
