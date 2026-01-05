package com.example;

import com.example.explosion.ExplosionCarver;
import com.example.hammer.HammerCommands;
import com.example.hammer.HammerEntities;
import com.example.hammer.HammerNetworking;
import com.example.hammer.HammerSounds;
import com.example.block.ModBlocks;
import com.example.effect.ModStatusEffects;
import com.example.item.ModItems;
import com.example.potion.ModPotions;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);




	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		ModStatusEffects.initialize();
		ModPotions.initialize();
		ExplosionCarver.init();
		ModBlocks.initialize();
		HammerEntities.initialize();
		HammerNetworking.initialize();
		HammerSounds.initialize();
		HammerCommands.initialize();
		ModItems.initialize();
		LOGGER.info("INITIALISED");
	}
}
