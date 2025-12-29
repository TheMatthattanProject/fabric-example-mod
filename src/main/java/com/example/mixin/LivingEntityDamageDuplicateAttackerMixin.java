package com.example.mixin;

import com.example.effect.ModStatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageDuplicateAttackerMixin {
    @Unique
    private static final double MODID$ATTACKER_COPY_SPAWN_SPREAD = 0.75D;

    @Inject(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("RETURN")
    )
    private void modid$duplicateAttackerOnDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        Entity attacker = source.getAttacker();
        if (!(attacker instanceof MobEntity mobAttacker)) {
            return;
        }

        if (!mobAttacker.hasStatusEffect(ModStatusEffects.COPY_ON_HIT)) {
            return;
        }

        Entity copy = mobAttacker.getType().create(world, SpawnReason.EVENT);
        if (copy == null) {
            return;
        }

        NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
        mobAttacker.writeData(writeView);
        NbtCompound nbt = writeView.getNbt();
        nbt.remove("UUID");
        nbt.remove("UUIDMost");
        nbt.remove("UUIDLeast");
        nbt.remove("Passengers");
        nbt.remove("Vehicle");

        ReadView readView = NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt);
        copy.readData(readView);

        double offsetX = world.getRandom().nextTriangular(0.0D, MODID$ATTACKER_COPY_SPAWN_SPREAD);
        double offsetZ = world.getRandom().nextTriangular(0.0D, MODID$ATTACKER_COPY_SPAWN_SPREAD);
        copy.refreshPositionAndAngles(
                mobAttacker.getX() + offsetX,
                mobAttacker.getY(),
                mobAttacker.getZ() + offsetZ,
                mobAttacker.getYaw(),
                mobAttacker.getPitch()
        );

        world.spawnEntity(copy);
    }
}

