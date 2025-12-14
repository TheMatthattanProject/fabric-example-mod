package com.example.item;

import com.example.ExampleMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
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

    public static final Item FLAMING_ARROW = register(
            new FlamingArrowItem(new Item.Settings().registryKey(FLAMING_ARROW_KEY)),
            FLAMING_ARROW_ID
    );

    public static final Item HUD_OVERLAY_TOGGLE = register(
            new HudOverlayToggleItem(new Item.Settings().registryKey(HUD_OVERLAY_TOGGLE_KEY)),
            HUD_OVERLAY_TOGGLE_ID
    );

    public static void initialize() {
        // Add our flaming arrow to the Combat ItemGroup (or whichever makes sense)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT)
                .register(entries -> entries.add(FLAMING_ARROW));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(HUD_OVERLAY_TOGGLE));
    }

    public static Item register(Item item, Identifier id) {
        return Registry.register(Registries.ITEM, id, item);
    }
}
