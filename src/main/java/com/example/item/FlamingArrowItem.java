package com.example.item;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;

public class FlamingArrowItem extends ArrowItem {
    public FlamingArrowItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter, ItemStack shotFrom) {
        // Create the arrow entity using the super method
        PersistentProjectileEntity arrow = super.createArrow(world, stack, shooter, shotFrom);
        // Set it on fire. The flame enchant typically sets the arrow on fire for some ticks.
        // Flame enchant sets the entity on fire for 100 ticks (5 seconds), so let's do that:
        arrow.setOnFireFor(100);
        return arrow;
    }
}
