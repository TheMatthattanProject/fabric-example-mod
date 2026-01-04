package com.example.enchantment;

import com.example.ExampleMod;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEnchantments {
    public static final Identifier BARRAGE_ID = Identifier.of(ExampleMod.MOD_ID, "barrage");
    public static final RegistryKey<Enchantment> BARRAGE_KEY = RegistryKey.of(RegistryKeys.ENCHANTMENT, BARRAGE_ID);

    private ModEnchantments() {
    }
}
