package com.example.mixin.client;

import com.example.hammer.client.HammerClientEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class HammerCameraShakeMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void modid$hammerCameraShake(World world, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        float strength = HammerClientEffects.shakeStrength(tickDelta);
        if (strength <= 0.001F) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        float time = client.world.getTime() + tickDelta;
        float yawOffset = (MathHelper.sin(time * 3.1F) + MathHelper.sin(time * 1.7F + 1.4F) * 0.5F) * strength * 1.4F;
        float pitchOffset = (MathHelper.cos(time * 2.7F + 2.2F) + MathHelper.cos(time * 4.3F) * 0.5F) * strength * 1.1F;
        setRotation(getYaw() + yawOffset, getPitch() + pitchOffset);
    }
}

