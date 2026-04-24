package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.SmokeSystem;
import com.teddante.emergent.StructuralStress;
import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {

    @Shadow
    public abstract ServerLevel level();

    @Shadow
    public abstract Vec3 center();

    @Shadow
    public abstract float radius();

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void checkVolatileBlocks(List<BlockPos> affectedBlocks, CallbackInfo ci) {
        if (!EmergentConfig.get().volatileContainers) {
            return;
        }

        ServerLevel world = this.level();

        // Create a copy of the list to prevent ConcurrentModificationException
        // if the recursive explosion modifies the original list within the same tick.
        List<BlockPos> affectedBlocksCopy = new ArrayList<>(affectedBlocks);

        for (BlockPos pos : affectedBlocksCopy) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof RandomizableContainerBlockEntity container) {
                VolatileExplosionUtils.tryExplodeVolatileContainer(world, container, pos);
            }
        }
    }

    @Inject(method = "interactWithBlocks", at = @At("TAIL"))
    private void emergent$postBlastEffects(List<BlockPos> affectedBlocks, CallbackInfo ci) {
        ServerLevel world = this.level();
        Vec3 c = this.center();

        if (EmergentConfig.get().smokeAndFumes) {
            SmokeSystem.emitExplosionSmoke(world, c.x, c.y, c.z, this.radius());
        }

        if (EmergentConfig.get().structuralStress && !affectedBlocks.isEmpty()) {
            List<BlockPos> copy = new ArrayList<>(affectedBlocks);
            StructuralStress.applyAfterExplosion(world, copy);
        }
    }
}
