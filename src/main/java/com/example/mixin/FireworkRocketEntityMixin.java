package com.example.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    @Unique
    private static final double MODID$ELYTRA_FIREWORK_ACCELERATION = 0.85D;

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V")
    )
    private void modid$uncapElytraFireworkBoost(LivingEntity livingEntity, Vec3d vanillaVelocity) {
        Vec3d lookDirection = livingEntity.getRotationVector();
        Vec3d newVelocity = livingEntity.getVelocity().add(lookDirection.multiply(MODID$ELYTRA_FIREWORK_ACCELERATION));
        livingEntity.setVelocity(newVelocity);
    }
}

