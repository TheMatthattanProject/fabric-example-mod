package com.example.hammer;

import com.example.ExampleMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class HammerEntities {
    private static final Identifier HAMMER_STRIKE_ID = Identifier.of(ExampleMod.MOD_ID, "hammer_strike");
    private static final RegistryKey<EntityType<?>> HAMMER_STRIKE_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, HAMMER_STRIKE_ID);

    public static final EntityType<HammerStrikeEntity> HAMMER_STRIKE = Registry.register(
            Registries.ENTITY_TYPE,
            HAMMER_STRIKE_ID,
            EntityType.Builder.create(HammerStrikeEntity::new, SpawnGroup.MISC)
                    .dimensions(0.1F, 0.1F)
                    .maxTrackingRange(512)
                    .trackingTickInterval(20)
                    .disableSaving()
                    .disableSummon()
                    .build(HAMMER_STRIKE_KEY)
    );

    private HammerEntities() {
    }

    public static void initialize() {
        // Static init does registration.
    }
}
