package com.example.hammer.client;

import com.example.hammer.HammerConfig;
import com.example.item.ModItems;
import com.example.hammer.client.render.HammerRenderUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class HammerTargetingClient {
    private static boolean designating;
    private static int designateTicks;
    @Nullable
    private static Vec3d markerCenter;

    private HammerTargetingClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(HammerTargetingClient::clientTick);
        HudRenderCallback.EVENT.register(HammerTargetingClient::renderHud);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(HammerTargetingClient::renderWorld);
    }

    public static boolean isDesignating() {
        return designating;
    }

    public static float getChargeProgress() {
        return MathHelper.clamp(designateTicks / (float) Math.max(1, HammerConfig.minChargeTicks()), 0.0F, 1.0F);
    }

    private static void clientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            designating = false;
            designateTicks = 0;
            markerCenter = null;
            return;
        }

        PlayerEntity player = client.player;
        ItemStack active = player.getActiveItem();
        if (!player.isUsingItem() || active.isEmpty() || !active.isOf(ModItems.HAMMER_DESIGNATOR)) {
            designating = false;
            designateTicks = 0;
            markerCenter = null;
            return;
        }

        designating = true;
        designateTicks++;

        HitResult hit = player.raycast(HammerConfig.rangeBlocks(), 1.0F, false);
        Vec3d hitPos = hit.getPos();
        int x = MathHelper.floor(hitPos.x);
        int z = MathHelper.floor(hitPos.z);

        int topY = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int surfaceY = Math.max(client.world.getBottomY(), topY - 1);
        markerCenter = new Vec3d(x + 0.5D, surfaceY + 0.05D, z + 0.5D);
    }

    private static void renderHud(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!designating || markerCenter == null || client.player == null) {
            return;
        }

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        float alpha = 0.08F + 0.22F * getChargeProgress();
        int a = (int) (alpha * 255.0F);
        drawContext.fill(0, 0, w, h, (a << 24));

        int cx = w / 2;
        int cy = h / 2;
        int color = 0xFFFF3344;

        drawContext.fill(cx - 1, cy - 10, cx + 1, cy - 2, color);
        drawContext.fill(cx - 1, cy + 2, cx + 1, cy + 10, color);
        drawContext.fill(cx - 10, cy - 1, cx - 2, cy + 1, color);
        drawContext.fill(cx + 2, cy - 1, cx + 10, cy + 1, color);
    }

    private static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!designating || markerCenter == null || client.player == null || client.world == null) {
            return;
        }

        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            return;
        }
        OrderedRenderCommandQueue queue = context.commandQueue();
        WorldRenderState worldState = context.worldState();
        CameraRenderState cameraState = worldState.cameraRenderState;

        Vec3d cameraPos = cameraState.pos;
        Vec3d center = markerCenter.subtract(cameraPos);

        float t = getChargeProgress();
        int radius = HammerConfig.maxRadius();
        int color = argb(120, 255, 80, 80);
        int beamColor = argb(140, 210, 235, 255);

        // Keep the presentation orbital: never draw a "beam from the player" during targeting.
        HammerRenderUtil.submitLine(queue, matrices, RenderLayers.linesTranslucent(), center.add(0.0D, 220.0D, 0.0D), center, beamColor, 1.5F);
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), center, radius + 0.25F, color, 2.0F, 72);
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), center, (radius * 0.35F) + (t * 0.45F), argb(180, 255, 255, 255), 1.5F, 36);
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
