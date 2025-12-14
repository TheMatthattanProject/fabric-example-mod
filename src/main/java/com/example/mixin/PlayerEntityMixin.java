package com.example.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Unique
    private static final int MODID$ELYTRA_CRASH_EXPLOSION_COOLDOWN_TICKS = 20;

    @Unique
    private static final Map<UUID, Integer> MODID$LAST_ELYTRA_CRASH_EXPLOSION_TICK = new HashMap<>();

    @Inject(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("RETURN")
    )
    private void modid$explodeOnElytraCrash(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!source.isOf(DamageTypes.FLY_INTO_WALL)) {
            return;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;

        int tick = world.getServer().getTicks();
        UUID uuid = player.getUuid();
        Integer lastTick = MODID$LAST_ELYTRA_CRASH_EXPLOSION_TICK.get(uuid);
        if (lastTick != null && tick - lastTick < MODID$ELYTRA_CRASH_EXPLOSION_COOLDOWN_TICKS) {
            return;
        }
        MODID$LAST_ELYTRA_CRASH_EXPLOSION_TICK.put(uuid, tick);

        float speed = (float) player.getVelocity().length();
        float power = MathHelper.clamp(speed * 3.0F, 2.0F, 8.0F);

        world.createExplosion(
                player,
                player.getX(),
                player.getBodyY(0.5),
                player.getZ(),
                power,
                false,
                World.ExplosionSourceType.TNT
        );
    }
}
