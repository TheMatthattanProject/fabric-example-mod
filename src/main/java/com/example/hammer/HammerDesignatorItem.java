package com.example.hammer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class HammerDesignatorItem extends Item {
    public HammerDesignatorItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(stack)) {
            if (!world.isClient()) {
                user.sendMessage(Text.translatable("message.modid.hammer.cooldown"), true);
            }
            return ActionResult.FAIL;
        }

        user.setCurrentHand(hand);
        return ActionResult.CONSUME;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient()) {
            return false;
        }
        if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld serverWorld)) {
            return false;
        }

        if (player.getItemCooldownManager().isCoolingDown(stack)) {
            return false;
        }

        int usedTicks = getMaxUseTime(stack, user) - remainingUseTicks;
        if (usedTicks < HammerConfig.minChargeTicks()) {
            player.sendMessage(Text.translatable("message.modid.hammer.too_fast"), true);
            return false;
        }

        if (!player.getAbilities().creativeMode && !consumeAmmoIfConfigured(player)) {
            return false;
        }

        HitResult hit = player.raycast(HammerConfig.rangeBlocks(), 1.0F, false);
        Vec3d hitPos = hit.getPos();
        int x = MathHelper.floor(hitPos.x);
        int z = MathHelper.floor(hitPos.z);

        int topY = serverWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int surfaceY = Math.max(serverWorld.getBottomY(), topY - 1);
        BlockPos surface = new BlockPos(x, surfaceY, z);

        HammerStrikeEntity.spawn(serverWorld, surface, player);
        player.getItemCooldownManager().set(stack, HammerConfig.cooldownTicks());
        return false;
    }

    private boolean consumeAmmoIfConfigured(ServerPlayerEntity player) {
        var ammoId = HammerConfig.ammoItem();
        int cost = HammerConfig.ammoCost();
        if (ammoId == null || cost <= 0) {
            return true;
        }

        Item ammoItem = Registries.ITEM.get(ammoId);
        if (ammoItem == null) {
            return true;
        }

        int available = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(ammoItem)) {
                available += stack.getCount();
                if (available >= cost) {
                    break;
                }
            }
        }

        if (available < cost) {
            player.sendMessage(Text.translatable("message.modid.hammer.no_ammo", ammoItem.getName(), cost), true);
            return false;
        }

        int remaining = cost;
        for (int slot = 0; slot < player.getInventory().size() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(ammoItem)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }

        return true;
    }
}
