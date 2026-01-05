package com.example.hammer.client;

import com.example.hammer.HammerEntities;
import com.example.hammer.client.render.HammerStrikeEntityRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public final class HammerClient {
    private HammerClient() {
    }

    public static void initializeClient() {
        EntityRendererRegistry.register(HammerEntities.HAMMER_STRIKE, HammerStrikeEntityRenderer::new);

        HammerClientEffects.initialize();
        HammerTargetingClient.initialize();
    }
}
