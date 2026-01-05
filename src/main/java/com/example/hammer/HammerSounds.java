package com.example.hammer;

import com.example.ExampleMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class HammerSounds {
    public static final SoundEvent HAMMER_TARGET_ACQUIRE = register("hammer_target_acquire");
    public static final SoundEvent HAMMER_ALIGNMENT_HUM = register("hammer_alignment_hum");
    public static final SoundEvent HAMMER_WARNING = register("hammer_warning");
    public static final SoundEvent HAMMER_FIRE_START = register("hammer_fire_start");
    public static final SoundEvent HAMMER_FIRE_LOOP = register("hammer_fire_loop");
    public static final SoundEvent HAMMER_CUTOFF = register("hammer_cutoff");
    public static final SoundEvent HAMMER_DISTANT_THUNDER = register("hammer_distant_thunder");

    private HammerSounds() {
    }

    public static void initialize() {
        // Static init does registration.
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.of(ExampleMod.MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}

