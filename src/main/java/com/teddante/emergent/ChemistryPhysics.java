package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ChemistryPhysics {

    public static final TagKey<Item> FLAMMABLE_POWDERS = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:flammable_powders"));
    public static final TagKey<Item> OXIDIZERS = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:oxidizers"));
    public static final TagKey<Item> REACTIVE_METALS = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:reactive_metals"));
    public static final TagKey<Item> FERTILIZERS = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:fertilizers"));
    public static final TagKey<Item> WATER_SOLUBLE = TagKey.create(Registries.ITEM,
            Identifier.parse("emergent:water_soluble"));

    private ChemistryPhysics() {
    }

    /**
     * Called from ItemEntity tick. Returns true if the item entity should be
     * discarded (dissolved / consumed by a reaction).
     */
    public static boolean tickChemistry(ItemEntity item) {
        if (!EmergentConfig.get().chemistryReactions)
            return false;
        if (!(item.level() instanceof ServerLevel level))
            return false;
        ItemStack stack = item.getItem();
        if (stack.isEmpty())
            return false;

        boolean inWater = item.isInWater();
        boolean onFire = item.isOnFire() || item.isInLava();
        BlockPos pos = item.blockPosition();

        // Water-soluble: dissolve slowly in water.
        if (inWater && stack.is(WATER_SOLUBLE)) {
            if (item.tickCount % 40 == 0) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, item.getX(), item.getY() + 0.1,
                        item.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
                stack.shrink(1);
                // Fertilizer in water boosts nearby crop growth slightly.
                if (stack.is(FERTILIZERS)) {
                    tryBonemealNearby(level, pos);
                }
                if (stack.isEmpty()) {
                    item.discard();
                    return true;
                }
            }
        }

        // Reactive metals in water: slow fizz (visual) but don't consume.
        if (inWater && stack.is(REACTIVE_METALS)) {
            if (item.tickCount % 60 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE, item.getX(), item.getY() + 0.1,
                        item.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
            }
        }

        // Flammable powders near fire: tiny pop.
        if (onFire && stack.is(FLAMMABLE_POWDERS)) {
            level.sendParticles(ParticleTypes.FLAME, item.getX(), item.getY() + 0.2,
                    item.getZ(), 6, 0.3, 0.2, 0.3, 0.05);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.6f);
            stack.shrink(Math.min(stack.getCount(), 2));
            if (stack.isEmpty()) {
                item.discard();
                return true;
            }
        }

        // Fertilizers lying on grass/dirt: fertilize surrounding plants occasionally.
        if (!inWater && stack.is(FERTILIZERS) && item.onGround() && item.tickCount % 120 == 0) {
            BlockState below = level.getBlockState(pos.below());
            if (below.is(BlockTags.DIRT) || below.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
                if (tryBonemealNearby(level, pos)) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        item.discard();
                        return true;
                    }
                }
            }
        }

        // Oxidizers in water: briefly sizzle — no other effect here.
        if (inWater && stack.is(OXIDIZERS) && item.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.3,
                    item.getZ(), 2, 0.15, 0.15, 0.15, 0.01);
        }

        return false;
    }

    private static boolean tryBonemealNearby(ServerLevel level, BlockPos origin) {
        for (int attempt = 0; attempt < 4; attempt++) {
            int dx = level.getRandom().nextInt(5) - 2;
            int dz = level.getRandom().nextInt(5) - 2;
            int dy = level.getRandom().nextInt(3) - 1;
            BlockPos target = origin.offset(dx, dy, dz);
            BlockState state = level.getBlockState(target);
            if (state.getBlock() instanceof BonemealableBlock bm) {
                if (bm.isValidBonemealTarget(level, target, state)) {
                    if (bm.isBonemealSuccess(level, level.getRandom(), target, state)) {
                        bm.performBonemeal(level, level.getRandom(), target, state);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                                6, 0.3, 0.3, 0.3, 0.02);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
