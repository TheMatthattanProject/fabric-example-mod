package com.example.mixin;

import com.example.config.BarrageConfig;
import com.example.enchantment.ModEnchantments;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Unit;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(RangedWeaponItem.class)
public abstract class RangedWeaponItemBarrageMixin {
    @Unique
    private static final ThreadLocal<int[]> MODID$BARRAGE_CONTEXT = new ThreadLocal<>();

    @Shadow
    protected abstract ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical);

    @Inject(
            method = "shootAll(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD")
    )
    private void modid$beginBarrageShot(
            ServerWorld world,
            LivingEntity shooter,
            Hand hand,
            ItemStack weaponStack,
            List<ItemStack> projectiles,
            float speed,
            float divergence,
            boolean critical,
            @Nullable LivingEntity target,
            CallbackInfo ci
    ) {
        MODID$BARRAGE_CONTEXT.set(new int[]{0});
    }

    @Inject(
            method = "shootAll(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/entity/LivingEntity;)V",
            at = @At("RETURN")
    )
    private void modid$endBarrageShot(
            ServerWorld world,
            LivingEntity shooter,
            Hand hand,
            ItemStack weaponStack,
            List<ItemStack> projectiles,
            float speed,
            float divergence,
            boolean critical,
            @Nullable LivingEntity target,
            CallbackInfo ci
    ) {
        MODID$BARRAGE_CONTEXT.remove();
    }

    @Redirect(
            method = "shootAll(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileEntity;spawn(Lnet/minecraft/entity/projectile/ProjectileEntity;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/entity/projectile/ProjectileEntity;"
            )
    )
    private ProjectileEntity modid$spawnWithBarrage(
            ProjectileEntity projectile,
            ServerWorld world,
            ItemStack projectileStack,
            Consumer<ProjectileEntity> beforeSpawn,
            ServerWorld worldArg,
            LivingEntity shooter,
            Hand hand,
            ItemStack weaponStack,
            List<ItemStack> projectiles,
            float speed,
            float divergence,
            boolean critical,
            @Nullable LivingEntity target
    ) {
        ProjectileEntity spawnedProjectile = ProjectileEntity.spawn(projectile, world, projectileStack, beforeSpawn);

        int[] context = MODID$BARRAGE_CONTEXT.get();
        if (context == null) {
            context = new int[]{0};
            MODID$BARRAGE_CONTEXT.set(context);
        }
        if (context[0]++ > 0) {
            return spawnedProjectile;
        }

        Registry<Enchantment> enchantmentRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);

        int barrageLevel = EnchantmentHelper.getLevel(enchantmentRegistry.getOrThrow(ModEnchantments.BARRAGE_KEY), weaponStack);
        barrageLevel = Math.min(barrageLevel, BarrageConfig.maxLevel());
        if (barrageLevel <= 0) {
            return spawnedProjectile;
        }

        int multishotPresent = EnchantmentHelper.getLevel(enchantmentRegistry.getOrThrow(Enchantments.MULTISHOT), weaponStack) > 0 ? 1 : 0;

        int vanillaProjectileCount = projectiles.size();
        long desiredTotal = (long)(3 * multishotPresent + 1) * (long)(64 * barrageLevel + 1);
        int totalArrowCount = (int)Math.min(desiredTotal, (long)BarrageConfig.maxTotalArrowsCap());
        totalArrowCount = Math.max(totalArrowCount, vanillaProjectileCount);

        int extrasToSpawn = totalArrowCount - vanillaProjectileCount;
        if (extrasToSpawn <= 0) {
            return spawnedProjectile;
        }

        Random random = world.getRandom();
        boolean addShooterVelocity = !(((Object)this) instanceof CrossbowItem);

        for (int i = 0; i < extrasToSpawn; i++) {
            ItemStack extraProjectileStack = projectileStack.copyWithCount(1);
            extraProjectileStack.set(DataComponentTypes.INTANGIBLE_PROJECTILE, Unit.INSTANCE);

            ProjectileEntity extraProjectile = this.createArrowEntity(world, shooter, weaponStack, extraProjectileStack, critical);

            float yawOffset = randomOffsetDegrees(random, BarrageConfig.horizontalSpreadDegrees());
            float pitchOffset = randomOffsetDegrees(random, BarrageConfig.verticalSpreadDegrees());
            float variedSpeed = applySpeedVariance(random, speed, BarrageConfig.speedVariance());

            modid$spawnExtraProjectile(
                    world,
                    shooter,
                    extraProjectile,
                    extraProjectileStack,
                    variedSpeed,
                    divergence,
                    yawOffset,
                    pitchOffset,
                    addShooterVelocity
            );
        }

        return spawnedProjectile;
    }

    private static void modid$spawnExtraProjectile(
            ServerWorld world,
            LivingEntity shooter,
            ProjectileEntity projectile,
            ItemStack projectileStack,
            float speed,
            float divergence,
            float yawOffset,
            float pitchOffset,
            boolean addShooterVelocity
    ) {
        Vec3d look = shooter.getRotationVec(1.0F);
        Vec3d forward = look.lengthSquared() > 1.0E-7 ? look.normalize() : Vec3d.ZERO;

        double yOffset = projectileStack.isOf(Items.FIREWORK_ROCKET) ? 0.15D : 0.1D;
        Vec3d origin = new Vec3d(shooter.getX(), shooter.getEyeY() - yOffset, shooter.getZ())
                .add(forward.multiply(BarrageConfig.spawnForwardOffset()));

        projectile.setPosition(origin.x, origin.y, origin.z);

        float pitch = shooter.getPitch() + pitchOffset;
        float yaw = shooter.getYaw() + yawOffset;

        if (addShooterVelocity) {
            projectile.setVelocity(shooter, pitch, yaw, 0.0F, speed, divergence);
        } else {
            modid$setVelocityNoShooterMovement(projectile, pitch, yaw, speed, divergence);
        }

        if (world.spawnEntity(projectile)) {
            projectile.triggerProjectileSpawned(world, projectileStack);
        }
    }

    private static void modid$setVelocityNoShooterMovement(ProjectileEntity projectile, float pitch, float yaw, float speed, float divergence) {
        float yawRad = yaw * ((float)Math.PI / 180.0F);
        float pitchRad = pitch * ((float)Math.PI / 180.0F);
        float x = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float y = -MathHelper.sin(pitchRad);
        float z = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        projectile.setVelocity(x, y, z, speed, divergence);
    }

    private static float randomOffsetDegrees(Random random, float maxAbsDegrees) {
        if (maxAbsDegrees <= 0.0F) {
            return 0.0F;
        }
        return (random.nextFloat() * 2.0F - 1.0F) * maxAbsDegrees;
    }

    private static float applySpeedVariance(Random random, float baseSpeed, float variance) {
        if (variance <= 0.0F || baseSpeed <= 0.0F) {
            return baseSpeed;
        }
        float speedMultiplier = 1.0F + (random.nextFloat() * 2.0F - 1.0F) * variance;
        return Math.max(0.01F, baseSpeed * speedMultiplier);
    }

}
