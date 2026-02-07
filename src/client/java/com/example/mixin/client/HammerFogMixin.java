package com.example.mixin.client;

import com.example.hammer.client.HammerClientEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public class HammerFogMixin {
    @ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
    private Vector4f modid$hammerFogColor(Vector4f original, Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness) {
        float strength = HammerClientEffects.fogStrength();
        if (strength <= 0.001F) {
            return original;
        }

        float t = Math.min(1.0F, strength);

        return new Vector4f(
                MathHelper.lerp(t, original.x, HammerClientEffects.fogTargetRed()),
                MathHelper.lerp(t, original.y, HammerClientEffects.fogTargetGreen()),
                MathHelper.lerp(t, original.z, HammerClientEffects.fogTargetBlue()),
                original.w
        );
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static float modid$hammerFogEnvironmentalStart(float value) {
        return scaleFogDistance(value);
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private static float modid$hammerFogEnvironmentalEnd(float value) {
        return scaleFogDistance(value);
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private static float modid$hammerFogRenderDistanceStart(float value) {
        return scaleFogDistance(value);
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3
    )
    private static float modid$hammerFogRenderDistanceEnd(float value) {
        return scaleFogDistance(value);
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 4
    )
    private static float modid$hammerFogSkyEnd(float value) {
        return scaleFogDistance(value);
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 5
    )
    private static float modid$hammerFogCloudEnd(float value) {
        return scaleFogDistance(value);
    }

    private static float scaleFogDistance(float value) {
        float strength = HammerClientEffects.fogStrength();
        if (strength <= 0.001F) {
            return value;
        }
        return value * HammerClientEffects.fogDistanceMultiplier();
    }
}

