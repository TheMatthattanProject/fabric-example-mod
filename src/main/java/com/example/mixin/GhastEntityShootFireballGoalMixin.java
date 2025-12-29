package com.example.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.entity.mob.GhastEntity$ShootFireballGoal")
public abstract class GhastEntityShootFireballGoalMixin {
    @Unique
    private static final int MODID$GHAST_FIREBALL_VOLLEY_SIZE = 32;

    @Unique
    private static final double MODID$GHAST_FIREBALL_VOLLEY_SPREAD = 0.15D;

    @Unique
    private static final int MODID$GHAST_FIREBALL_VOLLEY_DELAY_TICKS = 2;

    @Unique
    private static final double MODID$GHAST_FIREBALL_SPEED_MULTIPLIER = 2.0D;

    @Unique
    private static final double MODID$GHAST_FIREBALL_SPAWN_OFFSET = 4.0D;

    @Unique
    private int modid$volleyShotsRemaining;

    @Unique
    private int modid$volleyTicksUntilNextShot;

    @Shadow
    @Final
    private GhastEntity ghast;

    @Unique
    private static void modid$makeFireballFaster(ExplosiveProjectileEntity projectile) {
        double accelerationPower = ExplosiveProjectileEntity.DEFAULT_ACCELERATION_POWER * MODID$GHAST_FIREBALL_SPEED_MULTIPLIER;
        projectile.accelerationPower = accelerationPower;

        Vec3d velocity = projectile.getVelocity();
        if (velocity.lengthSquared() > 0.0D) {
            projectile.setVelocity(velocity.normalize().multiply(accelerationPower));
        }
    }

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void modid$shootRemainingVolleyShotsWithDelay(CallbackInfo ci) {
        if (modid$volleyShotsRemaining <= 0) {
            return;
        }

        World world = ghast.getEntityWorld();
        if (world.isClient()) {
            return;
        }

        if (modid$volleyTicksUntilNextShot > 0) {
            modid$volleyTicksUntilNextShot--;
            if (modid$volleyTicksUntilNextShot > 0) {
                return;
            }
        }

        LivingEntity target = ghast.getTarget();
        if (target == null) {
            modid$volleyShotsRemaining = 0;
            return;
        }

        if (target.squaredDistanceTo(ghast) > 4096.0D || !ghast.canSee(target)) {
            modid$volleyShotsRemaining = 0;
            return;
        }

        Vec3d rotation = ghast.getRotationVec(1.0F);
        double spawnX = ghast.getX() + rotation.x * MODID$GHAST_FIREBALL_SPAWN_OFFSET;
        double spawnY = ghast.getBodyY(0.5D) + 0.5D;
        double spawnZ = ghast.getZ() + rotation.z * MODID$GHAST_FIREBALL_SPAWN_OFFSET;

        Vec3d baseDirection = new Vec3d(
                target.getX() - spawnX,
                target.getBodyY(0.5D) - spawnY,
                target.getZ() - spawnZ
        ).normalize();

        Vec3d spreadDirection = baseDirection.add(
                world.getRandom().nextTriangular(0.0D, MODID$GHAST_FIREBALL_VOLLEY_SPREAD),
                world.getRandom().nextTriangular(0.0D, MODID$GHAST_FIREBALL_VOLLEY_SPREAD),
                world.getRandom().nextTriangular(0.0D, MODID$GHAST_FIREBALL_VOLLEY_SPREAD)
        ).normalize();

        FireballEntity fireball = new FireballEntity(world, ghast, spreadDirection, ghast.getFireballStrength());
        modid$makeFireballFaster(fireball);
        fireball.setPosition(spawnX, spawnY, spawnZ);
        if (!ghast.isSilent()) {
            world.syncWorldEvent(null, 1016, ghast.getBlockPos(), 0);
        }
        world.spawnEntity(fireball);

        modid$volleyShotsRemaining--;
        if (modid$volleyShotsRemaining > 0) {
            modid$volleyTicksUntilNextShot = MODID$GHAST_FIREBALL_VOLLEY_DELAY_TICKS;
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean modid$spawnFasterGhastFireballAndPrimeVolley(World world, Entity entity) {
        if (entity instanceof FireballEntity fireball) {
            modid$makeFireballFaster(fireball);
        }

        boolean spawned = world.spawnEntity(entity);

        if (!world.isClient() && spawned && MODID$GHAST_FIREBALL_VOLLEY_SIZE > 1) {
            modid$volleyShotsRemaining = MODID$GHAST_FIREBALL_VOLLEY_SIZE - 1;
            modid$volleyTicksUntilNextShot = MODID$GHAST_FIREBALL_VOLLEY_DELAY_TICKS;
        }

        return spawned;
    }

    @Inject(method = "start", at = @At("HEAD"))
    private void modid$resetVolleyOnStart(CallbackInfo ci) {
        modid$volleyShotsRemaining = 0;
        modid$volleyTicksUntilNextShot = 0;
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void modid$resetVolleyOnStop(CallbackInfo ci) {
        modid$volleyShotsRemaining = 0;
        modid$volleyTicksUntilNextShot = 0;
    }
}
