package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.TrafficWearPhysics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
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

    @Inject(method = "move", at = @At("TAIL"))
    private void emergent$applyTrafficWear(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        if (!EmergentConfig.get().materialReactions || !(this.level() instanceof ServerLevel world)
                || !this.onGround() || this.isPassenger() || delta.horizontalDistance() < 0.015) {
            return;
        }

        Entity entity = (Entity) (Object) this;
        TrafficWearPhysics.applyContactPatchTraffic(
                world,
                entity.getBoundingBox(),
                Math.max(0.35, entity.getBbHeight()),
                delta.horizontalDistance());
    }
}
