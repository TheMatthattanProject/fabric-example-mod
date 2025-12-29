package com.example.mixin;

import com.example.effect.ModStatusEffects;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArrowEntity.class)
public abstract class ArrowEntityDuplicateTargetMixin {
    @Unique
    private static final double MODID$ARROW_DUPLICATION_SPAWN_SPREAD = 0.75D;

    @Inject(method = "onHit(Lnet/minecraft/entity/LivingEntity;)V", at = @At("TAIL"))
    private void modid$duplicateTargetWhenHitByDuplicationArrow(LivingEntity target, CallbackInfo ci) {
        ArrowEntity arrow = (ArrowEntity) (Object) this;

        if (!(arrow.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        if (!(target instanceof MobEntity mobTarget)) {
            return;
        }

        ItemStack stack = arrow.getItemStack();
        PotionContentsComponent potionContents = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
        if (!modid$hasDuplicationEffect(potionContents)) {
            return;
        }

        Entity copy = mobTarget.getType().create(world, SpawnReason.EVENT);
        if (copy == null) {
            return;
        }

        NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
        mobTarget.writeData(writeView);
        NbtCompound nbt = writeView.getNbt();
        nbt.remove("UUID");
        nbt.remove("UUIDMost");
        nbt.remove("UUIDLeast");
        nbt.remove("Passengers");
        nbt.remove("Vehicle");

        ReadView readView = NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt);
        copy.readData(readView);

        double offsetX = world.getRandom().nextTriangular(0.0D, MODID$ARROW_DUPLICATION_SPAWN_SPREAD);
        double offsetZ = world.getRandom().nextTriangular(0.0D, MODID$ARROW_DUPLICATION_SPAWN_SPREAD);
        copy.refreshPositionAndAngles(
                mobTarget.getX() + offsetX,
                mobTarget.getY(),
                mobTarget.getZ() + offsetZ,
                mobTarget.getYaw(),
                mobTarget.getPitch()
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
