package com.example.mixin.client;

import com.example.hammer.client.HammerClientEffects;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.Handle;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(PostEffectPass.class)
public abstract class HammerPostEffectPassMixin {
    private static final ByteBuffer HAMMER_UNIFORMS = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());

    @Shadow
    @Final
    private Map<String, GpuBuffer> uniformBuffers;

    @Inject(method = "render", at = @At("HEAD"))
    private void modid$hammerUpdateUniforms(
            FrameGraphBuilder builder,
            Map<Identifier, Handle<Framebuffer>> targets,
            GpuBufferSlice samplerInfoSlice,
            CallbackInfo ci
    ) {
        GpuBuffer uniformBuffer = uniformBuffers.get("HammerConfig");
        if (uniformBuffer == null) {
            return;
        }

        if ((uniformBuffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
            long size = uniformBuffer.size();
            int usage = uniformBuffer.usage() | GpuBuffer.USAGE_COPY_DST;
            GpuBuffer replacement = RenderSystem.getDevice().createBuffer(() -> "modid:hammer_config", usage, size);
            uniformBuffers.put("HammerConfig", replacement);
            uniformBuffer.close();
            uniformBuffer = replacement;
        }

        float ringStrength = HammerClientEffects.ringStrength();
        float glitchStrength = HammerClientEffects.glitchStrength();
        float ringRadius = HammerClientEffects.ringRadiusNorm();
        float beamStrength = HammerClientEffects.beamStrength();
        float collapseStrength = HammerClientEffects.collapseStrength();
        float heatShimmerStrength = HammerClientEffects.heatShimmerStrength();

        MinecraftClient client = MinecraftClient.getInstance();
        float timeSeconds = 0.0F;
        if (client.world != null) {
            float tickDelta = client.getRenderTickCounter().getTickProgress(false);
            timeSeconds = (client.world.getTime() + tickDelta) / 20.0F;
        }

        HAMMER_UNIFORMS.clear();
        Std140Builder.intoBuffer(HAMMER_UNIFORMS)
                .putFloat(timeSeconds)
                .putFloat(ringRadius)
                .putFloat(ringStrength)
                .putFloat(glitchStrength)
                .putFloat(beamStrength)
                .putFloat(collapseStrength)
                .putFloat(heatShimmerStrength);
        HAMMER_UNIFORMS.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(0L, HAMMER_UNIFORMS.remaining()), HAMMER_UNIFORMS);
    }
}
