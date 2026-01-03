package com.teddante.emergent.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    /**
     * @author Antigravity
     * @reason Ensure falling water level matches its origin level to conserve
     *         volume.
     */
    @ModifyVariable(method = "flow", at = @At("HEAD"), argsOnly = true)
    private FluidState conserveVolumeInFlow(FluidState targetNewState, WorldAccess world, BlockPos pos,
            BlockState state, Direction direction) {
        if (!(world instanceof ServerWorld))
            return targetNewState;
        if (!((Object) this instanceof WaterFluid))
            return targetNewState;

        BlockPos originPos = pos.offset(direction.getOpposite());
        FluidState originState = world.getFluidState(originPos);

        if (originState.getFluid().matchesType((Fluid) (Object) this)) {
            int originLevel = originState.getLevel();

            // If flowing down, the target should take the FULL volume of the origin (up to
            // 8).
            // If flowing horizontally, vanilla level calculation (origin - 1) is mostly
            // okay,
            // but we must ensure we don't take more than we have.

            int maxPossibleLevel = direction == Direction.DOWN ? originLevel
                    : Math.min(targetNewState.getLevel(), originLevel);

            if (targetNewState.getLevel() != maxPossibleLevel) {
                return this.getFlowing(maxPossibleLevel, targetNewState.get(FlowableFluid.FALLING));
            }
        }
        return targetNewState;
    }

    /**
     * @author Antigravity
     * @reason Subtract the volume pushed to the target from the origin.
     */
    @Inject(method = "flow", at = @At("TAIL"))
    protected void postFlowSubtract(WorldAccess world, BlockPos pos, BlockState state, Direction direction,
            FluidState fluidState, CallbackInfo ci) {
        if (!(world instanceof ServerWorld))
            return;
        if (!((Object) this instanceof WaterFluid))
            return;

        BlockPos originPos = pos.offset(direction.getOpposite());
        FluidState originState = world.getFluidState(originPos);

        if (originState.getFluid().matchesType((Fluid) (Object) this)) {
            int pushedLevel = fluidState.getLevel();
            int currentOriginLevel = originState.getLevel();

            int nextLevel = Math.max(0, currentOriginLevel - pushedLevel);

            if (nextLevel == 0) {
                world.setBlockState(originPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            } else {
                FluidState newState = this.getFlowing(nextLevel,
                        originState.contains(FlowableFluid.FALLING) ? originState.get(FlowableFluid.FALLING) : false);
                world.setBlockState(originPos, newState.getBlockState(), Block.NOTIFY_ALL);
            }
        }
    }

    /**
     * @author Antigravity
     * @reason Relax hole-finding bias for high-pressure (high level) fluids.
     *         This allows source blocks and high-level water to spread sideways
     *         even if a drop-off is nearby.
     */
    @Redirect(method = "getSpread", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"))
    private void conditionalClear(Map<Direction, FluidState> instance, ServerWorld world, BlockPos pos,
            BlockState state) {
        FluidState fluidState = world.getFluidState(pos);

        // Pressure-Based Spread:
        // If it's a source (8) or high level (>=6), don't clear the map.
        // This lets it spread to all valid neighbors, not just the "shortest path to
        // hole".
        if (fluidState.getLevel() < 6 && !fluidState.isStill()) {
            instance.clear();
        }
    }
}
