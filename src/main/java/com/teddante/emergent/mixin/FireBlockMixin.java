package com.teddante.emergent.mixin;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.FireWetness;
import com.teddante.emergent.MaterialReactions;
import com.teddante.emergent.VolatileExplosionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void emergent$keepFireAliveOnReactiveMaterials(BlockState state, ServerLevel world, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (!EmergentConfig.get().materialReactions || !emergent$hasReactiveFireContext(world, pos)) {
            return;
        }

        int age = state.hasProperty(FireBlock.AGE) ? state.getValue(FireBlock.AGE) : 0;
        if (age <= 2) {
            return;
        }

        emergent$reactAroundFire(world, pos, random, age);

        float keepAliveChance = 0.75f * (1.0f - FireWetness.getWetness(world, pos));
        if (random.nextFloat() < keepAliveChance) {
            world.setBlock(pos, state.setValue(FireBlock.AGE, 0), 3);
            world.scheduleTick(pos, (Block) (Object) this, 30 + random.nextInt(10));
            ci.cancel();
        }
    }

    @Inject(method = "onPlace", at = @At("TAIL"))
    private void emergent$reactWhenFireIsPlaced(BlockState state, Level world, BlockPos pos, BlockState oldState,
            boolean movedByPiston, CallbackInfo ci) {
        if (!EmergentConfig.get().materialReactions || !(world instanceof ServerLevel serverWorld)) {
            return;
        }

        int age = state.hasProperty(FireBlock.AGE) ? state.getValue(FireBlock.AGE) : 0;
        emergent$reactAroundFire(serverWorld, pos, serverWorld.getRandom(), Math.max(age, 5));
    }

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void checkVolatileDestruction(Level world, BlockPos pos, int spreadChance, RandomSource random, int age,
            CallbackInfo ci) {
        // If we are about to spread fire TO a block, check if that block is a volatile
        // container.
        // We only care if it's a server world (explosions are server-side).
        if (world instanceof ServerLevel serverWorld && EmergentConfig.get().materialReactions
                && MaterialReactions.tryReactToFire(serverWorld, pos, world.getBlockState(pos), random)) {
            ci.cancel();
            return;
        }

        if (EmergentConfig.get().volatileContainers && world instanceof ServerLevel) {

            // Check if the target block is a volatile container
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof Container container) {
                if (VolatileExplosionUtils.tryExplodeVolatileContainer(world, container, pos)) {
                    // We destroyed the block with an explosion, cancel the fire spread
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void emergent$reactWithNearbyMaterials(BlockState state, ServerLevel world, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (!EmergentConfig.get().materialReactions) {
            return;
        }

        int age = state.hasProperty(FireBlock.AGE) ? state.getValue(FireBlock.AGE) : 0;
        if (age > 2 && emergent$hasReactiveFireContext(world, pos)) {
            return;
        }

        emergent$reactAroundFire(world, pos, random, age);
    }

    @Unique
    private void emergent$reactAroundFire(ServerLevel world, BlockPos pos, RandomSource random, int age) {
        float heat = 0.75f + age * 0.06f;

        emergent$tryReactNearFire(world, pos.below(), random, heat * 1.35f);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            emergent$tryReactNearFire(world, side, random, heat * 0.8f);
            emergent$tryIgniteReactiveSurface(world, side, random, Math.min(0.45f, heat * 0.24f));
        }

        emergent$tryReactNearFire(world, pos.above(), random, heat * 0.45f);
    }

    @Unique
    private boolean emergent$hasReactiveFireContext(ServerLevel world, BlockPos pos) {
        if (MaterialReactions.canReactToFire(world.getBlockState(pos.below()))) {
            return true;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (MaterialReactions.canReactToFire(world.getBlockState(pos.relative(direction)))
                    || MaterialReactions.canReactToFire(world.getBlockState(pos.relative(direction).below()))) {
                return true;
            }
        }

        return MaterialReactions.canReactToFire(world.getBlockState(pos.above()));
    }

    @Unique
    private void emergent$tryReactNearFire(ServerLevel world, BlockPos targetPos, RandomSource random, float heat) {
        if (heat <= 0.0f) {
            return;
        }

        MaterialReactions.exposeToFire(world, targetPos, world.getBlockState(targetPos), heat, random);
    }

    @Unique
    private void emergent$tryIgniteReactiveSurface(ServerLevel world, BlockPos firePos, RandomSource random, float chance) {
        if (random.nextFloat() > chance || !world.getBlockState(firePos).isAir()) {
            return;
        }

        BlockPos supportPos = firePos.below();
        if (!MaterialReactions.canReactToFire(world.getBlockState(supportPos))) {
            return;
        }

        BlockState fireState = BaseFireBlock.getState(world, firePos);
        if (fireState.canSurvive(world, firePos)) {
            world.setBlock(firePos, fireState, 3);
        }
    }

    // Use @ModifyArg instead of @Redirect for better mod compatibility.
    // This only modifies the 'age' parameter (index 4) to always be 0,
    // allowing fire to spread indefinitely regardless of its current age.
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FireBlock;checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V"), index = 4)
    private int emergent$modifyFireAge(int age) {
        return EmergentConfig.get().infiniteFireSpread ? 0 : age;
    }
}
