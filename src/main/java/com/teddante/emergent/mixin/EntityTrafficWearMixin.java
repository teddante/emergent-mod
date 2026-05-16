package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.TrafficWearPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityTrafficWearMixin {
    @Shadow
    public abstract Level level();

    @Shadow
    public abstract boolean onGround();

    @Shadow
    public abstract boolean isPassenger();

    @Shadow
    public abstract BlockPos getOnPos();

    @Shadow
    public abstract Vec3 getKnownMovement();

    @Shadow
    public abstract float getBbWidth();

    @Shadow
    public abstract float getBbHeight();

    @Inject(method = "applyEffectsFromBlocks()V", at = @At("TAIL"))
    private void emergent$applyTrafficWear(CallbackInfo ci) {
        if (!EmergentConfig.get().materialReactions || !(this.level() instanceof ServerLevel world)
                || !this.onGround() || this.isPassenger()) {
            return;
        }

        BlockPos pos = this.getOnPos();
        BlockState state = world.getBlockState(pos);
        TrafficWearPhysics.applyEntityTraffic(world, pos, state, (Entity) (Object) this);
    }
}
