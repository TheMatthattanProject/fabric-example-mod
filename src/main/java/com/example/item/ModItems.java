package com.example.item;

import com.example.ExampleMod;
import com.example.hammer.HammerDesignatorItem;
import com.example.hammer.HammerConfig;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;


public class ModItems {

    private static final Identifier FLAMING_ARROW_ID = Identifier.of(ExampleMod.MOD_ID, "flaming_arrow");
    private static final RegistryKey<Item> FLAMING_ARROW_KEY = RegistryKey.of(RegistryKeys.ITEM, FLAMING_ARROW_ID);

    private static final Identifier HUD_OVERLAY_TOGGLE_ID = Identifier.of(ExampleMod.MOD_ID, "hud_overlay_toggle");
    private static final RegistryKey<Item> HUD_OVERLAY_TOGGLE_KEY = RegistryKey.of(RegistryKeys.ITEM, HUD_OVERLAY_TOGGLE_ID);

    private static final Identifier HAMMER_DESIGNATOR_ID = Identifier.of(ExampleMod.MOD_ID, "hammer_designator");
    private static final RegistryKey<Item> HAMMER_DESIGNATOR_KEY = RegistryKey.of(RegistryKeys.ITEM, HAMMER_DESIGNATOR_ID);
    private static final Identifier HAMMER_DESIGNATOR_SUBTLE_ID = Identifier.of(ExampleMod.MOD_ID, "hammer_designator_subtle");
    private static final RegistryKey<Item> HAMMER_DESIGNATOR_SUBTLE_KEY = RegistryKey.of(RegistryKeys.ITEM, HAMMER_DESIGNATOR_SUBTLE_ID);
    private static final Identifier HAMMER_DESIGNATOR_CINEMATIC_ID = Identifier.of(ExampleMod.MOD_ID, "hammer_designator_cinematic");
    private static final RegistryKey<Item> HAMMER_DESIGNATOR_CINEMATIC_KEY = RegistryKey.of(RegistryKeys.ITEM, HAMMER_DESIGNATOR_CINEMATIC_ID);
    private static final Identifier HAMMER_DESIGNATOR_APOCALYPTIC_ID = Identifier.of(ExampleMod.MOD_ID, "hammer_designator_apocalyptic");
    private static final RegistryKey<Item> HAMMER_DESIGNATOR_APOCALYPTIC_KEY = RegistryKey.of(RegistryKeys.ITEM, HAMMER_DESIGNATOR_APOCALYPTIC_ID);

    public static final Item FLAMING_ARROW = register(
            new FlamingArrowItem(new Item.Settings().registryKey(FLAMING_ARROW_KEY)),
            FLAMING_ARROW_ID
    );

    public static final Item HUD_OVERLAY_TOGGLE = register(
            new HudOverlayToggleItem(new Item.Settings().registryKey(HUD_OVERLAY_TOGGLE_KEY)),
            HUD_OVERLAY_TOGGLE_ID
    );

    public static final Item HAMMER_DESIGNATOR = register(
            new HammerDesignatorItem(new Item.Settings().registryKey(HAMMER_DESIGNATOR_KEY).maxCount(1)),
            HAMMER_DESIGNATOR_ID
    );

    public static final Item HAMMER_DESIGNATOR_SUBTLE = register(
            new HammerDesignatorItem(new Item.Settings().registryKey(HAMMER_DESIGNATOR_SUBTLE_KEY).maxCount(1), HammerConfig.ClientFxPreset.SUBTLE),
            HAMMER_DESIGNATOR_SUBTLE_ID
    );

    public static final Item HAMMER_DESIGNATOR_CINEMATIC = register(
            new HammerDesignatorItem(new Item.Settings().registryKey(HAMMER_DESIGNATOR_CINEMATIC_KEY).maxCount(1), HammerConfig.ClientFxPreset.CINEMATIC),
            HAMMER_DESIGNATOR_CINEMATIC_ID
    );

    public static final Item HAMMER_DESIGNATOR_APOCALYPTIC = register(
            new HammerDesignatorItem(new Item.Settings().registryKey(HAMMER_DESIGNATOR_APOCALYPTIC_KEY).maxCount(1), HammerConfig.ClientFxPreset.APOCALYPTIC),
            HAMMER_DESIGNATOR_APOCALYPTIC_ID
    );

    public static void initialize() {
        // Add our flaming arrow to the Combat ItemGroup (or whichever makes sense)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT)
                .register(entries -> entries.add(FLAMING_ARROW));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(HUD_OVERLAY_TOGGLE));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(HAMMER_DESIGNATOR));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(HAMMER_DESIGNATOR_SUBTLE));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(HAMMER_DESIGNATOR_CINEMATIC));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(HAMMER_DESIGNATOR_APOCALYPTIC));
    }

    public static Item register(Item item, Identifier id) {
        return Registry.register(Registries.ITEM, id, item);
    }

    public static boolean isHammerDesignator(ItemStack stack) {
        return stack.isOf(HAMMER_DESIGNATOR)
                || stack.isOf(HAMMER_DESIGNATOR_SUBTLE)
                || stack.isOf(HAMMER_DESIGNATOR_CINEMATIC)
                || stack.isOf(HAMMER_DESIGNATOR_APOCALYPTIC);
    }
}
