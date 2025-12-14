package com.example.item;

import com.example.hud.HudOverlayState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class HudOverlayToggleItem extends Item {
    public HudOverlayToggleItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) {
            boolean enabled = HudOverlayState.toggle();
            user.sendMessage(
                    Text.translatable(enabled ? "message.modid.hud_overlay.enabled" : "message.modid.hud_overlay.disabled"),
                    true
            );
        }

        return world.isClient() ? ActionResult.SUCCESS : ActionResult.SUCCESS_SERVER;
    }
}
