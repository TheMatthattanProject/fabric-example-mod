package com.example.effect;

import com.example.ExampleMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class ModStatusEffects {
    public static final RegistryEntry<StatusEffect> COPY_ON_HIT = Registry.registerReference(
            Registries.STATUS_EFFECT,
            Identifier.of(ExampleMod.MOD_ID, "copy_on_hit"),
            new CopyOnHitStatusEffect()
    );

    public static void initialize() {
    }

    private ModStatusEffects() {
    }
}

