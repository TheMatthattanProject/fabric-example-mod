package com.example.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class CopyOnHitStatusEffect extends StatusEffect {
    private static final int MODID$COLOR = 0x6B38FF;

    public CopyOnHitStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, MODID$COLOR);
    }
}
