package com.example.block;

import com.example.ExampleMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {

    private static final Identifier SUPER_TNT_ID = Identifier.of(ExampleMod.MOD_ID, "super_tnt");
    private static final RegistryKey<Block> SUPER_TNT_KEY = RegistryKey.of(RegistryKeys.BLOCK, SUPER_TNT_ID);
    private static final RegistryKey<Item> SUPER_TNT_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, SUPER_TNT_ID);

    public static final Block SUPER_TNT = register(
            new SuperTntBlock(AbstractBlock.Settings.copy(Blocks.TNT).registryKey(SUPER_TNT_KEY)),
            SUPER_TNT_ID
    );

    public static final Item SUPER_TNT_ITEM = register(
            new BlockItem(SUPER_TNT, new Item.Settings().registryKey(SUPER_TNT_ITEM_KEY)),
            SUPER_TNT_ID
    );

    private ModBlocks() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE)
                .register(entries -> entries.add(SUPER_TNT_ITEM));
    }

    private static Block register(Block block, Identifier id) {
        return Registry.register(Registries.BLOCK, id, block);
    }

    private static Item register(Item item, Identifier id) {
        return Registry.register(Registries.ITEM, id, item);
    }
}
