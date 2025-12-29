package com.example.mixin;

import com.example.effect.ModStatusEffects;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
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

@Mixin(EnderDragonPart.class)
public abstract class EnderDragonPartDuplicateOwnerMixin {
    @Unique
    private static final double MODID$DRAGON_DUPLICATION_SPAWN_SPREAD = 0.75D;

    @Inject(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("RETURN")
    )
    private void modid$duplicateDragonWhenHitByDuplicationArrow(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        Entity directSource = source.getSource();
        if (!(directSource instanceof ArrowEntity arrow)) {
            return;
        }

        ItemStack stack = arrow.getItemStack();
        PotionContentsComponent potionContents = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
        if (!modid$hasDuplicationEffect(potionContents)) {
            return;
        }

        EnderDragonPart part = (EnderDragonPart) (Object) this;
        EnderDragonEntity dragon = part.owner;
        if (!dragon.isAlive()) {
            return;
        }

        Entity copy = dragon.getType().create(world, SpawnReason.EVENT);
        if (copy == null) {
            return;
        }

        NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
        dragon.writeData(writeView);
        NbtCompound nbt = writeView.getNbt();
        nbt.remove("UUID");
        nbt.remove("UUIDMost");
        nbt.remove("UUIDLeast");
        nbt.remove("Passengers");
        nbt.remove("Vehicle");

        ReadView readView = NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt);
        copy.readData(readView);

        double offsetX = world.getRandom().nextTriangular(0.0D, MODID$DRAGON_DUPLICATION_SPAWN_SPREAD);
        double offsetZ = world.getRandom().nextTriangular(0.0D, MODID$DRAGON_DUPLICATION_SPAWN_SPREAD);
        copy.refreshPositionAndAngles(
                dragon.getX() + offsetX,
                dragon.getY(),
                dragon.getZ() + offsetZ,
                dragon.getYaw(),
                dragon.getPitch()
        );

        world.spawnEntity(copy);
    }

    @Unique
    private static boolean modid$hasDuplicationEffect(PotionContentsComponent potionContents) {
        for (StatusEffectInstance effect : potionContents.getEffects()) {
            if (effect.getEffectType().equals(ModStatusEffects.COPY_ON_HIT)) {
                return true;
            }
        }

        return false;
    }
}

