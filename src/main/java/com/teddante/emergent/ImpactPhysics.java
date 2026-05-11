package com.teddante.emergent;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ImpactPhysics {
    private static final double ENTITY_INJURY_ENERGY = 0.5;
    private static final double DAMAGE_PER_ENERGY = 4.0;
    private static final double KNOCKBACK_PER_MASS_RATIO = 0.12;

    private ImpactPhysics() {
    }

    public static void applyKineticImpacts(Entity mover) {
        if (!EmergentConfig.get().kineticImpacts || mover.level().isClientSide() || mover.isRemoved()
                || mover.noPhysics) {
            return;
        }

        Vec3 velocity = mover.getDeltaMovement();
        double speedSqr = velocity.lengthSqr();
        if (speedSqr <= 1.0E-6) {
            return;
        }

        double moverMass = estimateMass(mover);
        double kineticEnergy = 0.5 * moverMass * speedSqr;
        applyEntityImpacts(mover, velocity, moverMass, kineticEnergy);
        breakImpactedBrittleBlock(mover, velocity, kineticEnergy);
    }

    public static double estimateMass(Entity entity) {
        double volume = entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
        double mass = volume * 4.0;

        if (entity instanceof FallingBlockEntity fallingBlock) {
            mass = estimateBlockMass(fallingBlock.getBlockState());
        } else if (entity instanceof AbstractMinecart) {
            mass = 6.0;
        } else if (entity instanceof AbstractBoat) {
            mass = 8.0;
        } else if (entity instanceof LivingEntity) {
            mass = volume * 5.0;
        }

        for (Entity passenger : entity.getPassengers()) {
            mass += estimateMass(passenger);
        }

        if (entity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    mass += stack.getCount() * 0.03;
                }
            }
        }

        return mass;
    }

    private static double estimateBlockMass(BlockState state) {
        if (state.is(BlockTags.ANVIL)) {
            return 20.0;
        }

        return 4.0 + Math.sqrt(Math.max(0.0F, state.getBlock().getExplosionResistance()));
    }

    private static void applyEntityImpacts(Entity mover, Vec3 velocity, double moverMass, double kineticEnergy) {
        AABB sweptBox = mover.getBoundingBox().expandTowards(velocity).inflate(0.1);
        DamageSource damageSource = damageSourceFor(mover);

        for (Entity target : mover.level().getEntities(mover, sweptBox, EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
            if (!(target instanceof LivingEntity) || !target.isAlive() || target.noPhysics || target.isPassengerOfSameVehicle(mover)
                    || mover.hasPassenger(target) || target.hasPassenger(mover)) {
                continue;
            }

            Vec3 toTarget = target.position().subtract(mover.position());
            if (toTarget.lengthSqr() <= 1.0E-6) {
                toTarget = velocity;
            }

            Vec3 impactDirection = toTarget.normalize();
            Vec3 relativeVelocity = velocity.subtract(target.getDeltaMovement());
            double closingSpeed = relativeVelocity.dot(impactDirection);
            if (closingSpeed <= 0.0) {
                continue;
            }

            double impactEnergy = 0.5 * moverMass * closingSpeed * closingSpeed;
            if (impactEnergy <= ENTITY_INJURY_ENERGY) {
                continue;
            }

            target.hurt(damageSource, (float) ((impactEnergy - ENTITY_INJURY_ENERGY) * DAMAGE_PER_ENERGY));
            transferMomentum(moverMass, velocity, target);
        }
    }

    private static void transferMomentum(double moverMass, Vec3 velocity, Entity target) {
        double targetMass = estimateMass(target);
        if (targetMass <= 0.0) {
            return;
        }

        Vec3 impulse = velocity.scale(moverMass / targetMass * KNOCKBACK_PER_MASS_RATIO);
        target.push(impulse.x, impulse.y, impulse.z);
    }

    private static void breakImpactedBrittleBlock(Entity mover, Vec3 velocity, double kineticEnergy) {
        Level world = mover.level();
        Vec3[] starts = impactProbeStarts(mover);
        for (Vec3 start : starts) {
            BlockHitResult hit = world.clip(new ClipContext(start, start.add(velocity), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mover));
            if (hit.getType() == HitResult.Type.BLOCK && tryBreakBrittleBlock(mover, hit.getBlockPos(), kineticEnergy)) {
                return;
            }
        }
    }

    private static Vec3[] impactProbeStarts(Entity mover) {
        AABB box = mover.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double lowerY = box.minY + mover.getBbHeight() * 0.15;
        double centerY = (box.minY + box.maxY) * 0.5;
        double upperY = box.minY + mover.getBbHeight() * 0.85;
        double xInset = mover.getBbWidth() * 0.4;
        double zInset = mover.getBbWidth() * 0.4;

        return new Vec3[] {
                new Vec3(centerX, centerY, centerZ),
                new Vec3(centerX + xInset, centerY, centerZ),
                new Vec3(centerX - xInset, centerY, centerZ),
                new Vec3(centerX, centerY, centerZ + zInset),
                new Vec3(centerX, centerY, centerZ - zInset),
                new Vec3(centerX, lowerY, centerZ),
                new Vec3(centerX, upperY, centerZ)
        };
    }

    private static boolean tryBreakBrittleBlock(Entity mover, BlockPos pos, double kineticEnergy) {
        Level world = mover.level();
        BlockState state = world.getBlockState(pos);
        if (!state.is(MaterialReactionTags.BRITTLE) || state.getDestroySpeed(world, pos) < 0.0F) {
            return false;
        }

        double breakEnergy = state.getBlock().getExplosionResistance();
        if (kineticEnergy <= breakEnergy) {
            return false;
        }

        return world.destroyBlock(pos, true, mover, Block.UPDATE_LIMIT);
    }

    private static DamageSource damageSourceFor(Entity mover) {
        if (mover instanceof FallingBlockEntity fallingBlock) {
            return fallingBlock.getBlockState().is(BlockTags.ANVIL) ? mover.damageSources().anvil(mover)
                    : mover.damageSources().fallingBlock(mover);
        }

        if (mover instanceof Player player) {
            return mover.damageSources().playerAttack(player);
        }

        if (mover instanceof LivingEntity livingEntity) {
            return mover.damageSources().mobAttack(livingEntity);
        }

        return mover.damageSources().flyIntoWall();
    }
}
