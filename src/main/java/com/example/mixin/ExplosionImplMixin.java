package com.example.mixin;

import com.example.explosion.ExplosionCarver;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionImplMixin {
    @Shadow
    protected abstract List<BlockPos> getBlocksToDestroy();

    @Redirect(
            method = "explode",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/explosion/ExplosionImpl;getBlocksToDestroy()Ljava/util/List;")
    )
    private List<BlockPos> modid$useWavefrontBlockCarvingForHighPowerExplosions(ExplosionImpl explosion) {
        if (explosion.getPower() < ExplosionCarver.HIGH_POWER_THRESHOLD) {
            return getBlocksToDestroy();
        }

        if (explosion.getDestructionType() == Explosion.DestructionType.KEEP) {
            return getBlocksToDestroy();
        }

        ServerWorld world = explosion.getWorld();
        long seed = world.random.nextLong();
        ExplosionCarver.schedule(world, explosion, seed);
        return List.of();
    }
}

