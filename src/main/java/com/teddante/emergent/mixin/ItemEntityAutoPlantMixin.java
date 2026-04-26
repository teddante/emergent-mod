package com.teddante.emergent.mixin;

import com.teddante.emergent.Emergent;
import com.teddante.emergent.EmergentConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enables auto-planting for dropped plantable items (saplings, seeds,
 * mushrooms, berries).
 * 
 * When a plantable item has been on valid ground for ~30 seconds (600 ticks),
 * it attempts to plant itself using vanilla's placement mechanics.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityAutoPlantMixin {

    @Unique
    private static final TagKey<Item> PLANTABLES = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:plantables"));
    @Unique
    private static final int PLANTING_START_AGE = 600;
    @Unique
    private static final int PLANTING_RETRY_INTERVAL = 100;

    @Shadow
    public abstract ItemStack getItem();

    @Shadow
    public abstract int getAge();

    @Inject(method = "tick", at = @At("TAIL"))
    private void tryAutoPlant(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        Level world = self.level();

        // Only run server-side, when on ground, after 30 seconds
        int age = getAge();
        if (!EmergentConfig.get().autoPlanting
                || world.isClientSide()
                || !self.onGround()
                || age < PLANTING_START_AGE
                || (age - PLANTING_START_AGE) % PLANTING_RETRY_INTERVAL != 0) {
            return;
        }

        ItemStack stack = getItem();

        // Must be a plantable BlockItem
        if (!stack.is(PLANTABLES)) {
            return;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        BlockPos pos = self.blockPosition();

        // Use vanilla's automatic placement context (same pattern as dispensers)
        DirectionalPlaceContext context = new DirectionalPlaceContext(
                world, pos, Direction.DOWN, stack, Direction.UP);

        // Delegate entirely to vanilla placement - handles validation, sounds, game
        // events, decrement
        if (blockItem.place(context).consumesAction()) {
            BlockState placed = world.getBlockState(pos);
            Emergent.LOGGER.debug("Auto-planted {} at {}", placed.getBlock(), pos);

            // Discard entity if stack is now empty
            if (stack.isEmpty()) {
                self.discard();
            }
        }
    }
}
