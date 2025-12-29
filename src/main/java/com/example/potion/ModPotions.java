package com.example.potion;

import com.example.ExampleMod;
import com.example.effect.ModStatusEffects;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class ModPotions {
    private static final int MODID$COPY_ON_HIT_DURATION_TICKS = 20 * 30;

    public static final RegistryEntry<Potion> COPY_ON_HIT = Registry.registerReference(
            Registries.POTION,
            Identifier.of(ExampleMod.MOD_ID, "copy_on_hit"),
            new Potion(
                    "copy_on_hit",
                    new StatusEffectInstance(ModStatusEffects.COPY_ON_HIT, MODID$COPY_ON_HIT_DURATION_TICKS)
            )
    );

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
            entries.add(PotionContentsComponent.createStack(Items.POTION, COPY_ON_HIT));
            entries.add(PotionContentsComponent.createStack(Items.SPLASH_POTION, COPY_ON_HIT));
            entries.add(PotionContentsComponent.createStack(Items.LINGERING_POTION, COPY_ON_HIT));
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries ->
                entries.add(PotionContentsComponent.createStack(Items.TIPPED_ARROW, COPY_ON_HIT))
        );

        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            FabricBrewingRecipeRegistryBuilder registry = (FabricBrewingRecipeRegistryBuilder) builder;
            registry.registerPotionRecipe(Potions.AWKWARD, Ingredient.ofItems(Items.SLIME_BALL), COPY_ON_HIT);
        });
    }

    private ModPotions() {
    }
}
