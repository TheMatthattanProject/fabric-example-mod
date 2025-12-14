package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import com.example.hud.HudOverlayState;

public class ExampleModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			if (!HudOverlayState.isEnabled()) {
				return;
			}

			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.options.hudHidden) {
				return;
			}

			int screenWidth = client.getWindow().getScaledWidth();
			int screenHeight = client.getWindow().getScaledHeight();
			drawContext.fill(0, 0, screenWidth, screenHeight, 0xAA000000);

			drawContext.drawTextWithShadow(
					client.textRenderer,
					Text.translatable("hud.modid.overlay"),
					8,
					8,
					0xFFFFFFFF
			);
		});
	}
}
