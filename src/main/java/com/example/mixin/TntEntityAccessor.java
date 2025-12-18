package com.example.mixin;

import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TntEntity.class)
public interface TntEntityAccessor {
    @Accessor("explosionPower")
    void modid$setExplosionPower(float explosionPower);
}
