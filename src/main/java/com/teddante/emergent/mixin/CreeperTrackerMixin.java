package com.teddante.emergent.mixin;

import com.teddante.emergent.ReactiveCreeperTracker;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Creeper.class)
public class CreeperTrackerMixin implements ReactiveCreeperTracker {
    @Unique
    private boolean isReacting = false;

    @Override
    public boolean emergent$isReacting() {
        return this.isReacting;
    }

    @Override
    public void emergent$setReacting(boolean reacting) {
        this.isReacting = reacting;
    }
}
