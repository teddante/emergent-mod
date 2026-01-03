package com.teddante.emergent.mixin;

import com.teddante.emergent.Emergent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.AutomaticItemPlacementContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
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
    private static final TagKey<Item> PLANTABLES = TagKey.of(RegistryKeys.ITEM,
            Identifier.of("emergent", "plantables"));

    @Shadow
    public abstract ItemStack getStack();

    @Shadow
    public abstract int getItemAge();

    @Inject(method = "tick", at = @At("TAIL"))
    private void tryAutoPlant(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        World world = self.getEntityWorld();

        // Only run server-side, when on ground, after 30 seconds
        if (world.isClient() || !self.isOnGround() || getItemAge() < 600) {
            return;
        }

        ItemStack stack = getStack();

        // Must be a plantable BlockItem
        if (!stack.isIn(PLANTABLES)) {
            return;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        BlockPos pos = self.getBlockPos();

        // Use vanilla's automatic placement context (same pattern as dispensers)
        AutomaticItemPlacementContext context = new AutomaticItemPlacementContext(
                world, pos, Direction.DOWN, stack, Direction.UP);

        // Delegate entirely to vanilla placement - handles validation, sounds, game
        // events, decrement
        if (blockItem.place(context).isAccepted()) {
            BlockState placed = world.getBlockState(pos);
            Emergent.LOGGER.debug("Auto-planted {} at {}", placed.getBlock(), pos);

            // Discard entity if stack is now empty
            if (stack.isEmpty()) {
                self.discard();
            }
        }
    }
}
