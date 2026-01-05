package com.example.hammer.client;

import com.example.ExampleMod;
import com.example.hammer.HammerStage;
import com.example.hammer.client.render.HammerRenderUtil;
import com.example.hammer.network.S2CHammerPacket;
import com.example.mixin.client.GameRendererAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class HammerClientEffects {
    private static final Identifier WHITE_TEXTURE = Identifier.ofVanilla("textures/misc/white.png");
    private static final Identifier NOISE_TEXTURE = Identifier.ofVanilla("textures/misc/noise.png");
    private static final Identifier HAMMER_POST_EFFECT = Identifier.of(ExampleMod.MOD_ID, "hammer");

    private static final int LASER_COLOR_ARGB = 0xFFFF0000;

    private static final float TARGET_FOG_R = 0x1A / 255.0F;
    private static final float TARGET_FOG_G = 0x0F / 255.0F;
    private static final float TARGET_FOG_B = 0x2E / 255.0F;

    private static final int STAGE_1_TARGETING_END = 30;
    private static final int STAGE_2_BREACH_START = 31;
    private static final int STAGE_2_BREACH_END = 45;
    private static final int STAGE_3_STROKE_START = 46;
    private static final int STAGE_3_STROKE_END = 80;
    private static final int STAGE_4_ERUPTION_START = 81;
    private static final int STAGE_4_ERUPTION_END = 100;
    private static final int STAGE_5_WAVE_START = 85;
    private static final int STAGE_5_WAVE_END = 130;
    private static final int STAGE_6_AFTERMATH_START = 131;
    private static final int STAGE_6_AFTERMATH_END = 220;
    private static final int STAGE_6_FINAL_CUT_START = 200;
    private static final int STAGE_6_FINAL_CUT_TICKS = 20;

    private static final int LASER_TOP_Y = 320;
    private static final int CONE_TIP_START_Y = 300;
    private static final float CONE_DESCENT_PER_TICK = 25.0F;
    private static final float CONE_RADIUS = 10.0F;
    private static final float CONE_HEIGHT = 100.0F;

    private static final float PLAYER_EFFECT_RADIUS = 100.0F;
    private static final float PRESSURE_WAVE_RADIUS = 80.0F;
    private static final float ASHFALL_RADIUS = 100.0F;
    private static final int ASHFALL_TICKS = 1200;

    private static final Map<Integer, ClientStrikeState> STRIKES = new HashMap<>();

    private static float shakeStrength;
    private static float fogStrength;
    private static float whiteoutAlpha;
    private static float blackoutAlpha;
    private static float ringRadiusNorm;
    private static float ringStrength;
    private static float glitchStrength;

    private HammerClientEffects() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(S2CHammerPacket.ID, HammerClientEffects::onStagePacket);

        ClientTickEvents.END_CLIENT_TICK.register(HammerClientEffects::tickClient);
        HudRenderCallback.EVENT.register(HammerClientEffects::renderHud);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(HammerClientEffects::renderWorld);
    }

    public static float shakeStrength(float tickDelta) {
        return shakeStrength;
    }

    public static float fogStrength() {
        return fogStrength;
    }

    public static float ringRadiusNorm() {
        return ringRadiusNorm;
    }

    public static float ringStrength() {
        return ringStrength;
    }

    public static float glitchStrength() {
        return glitchStrength;
    }

    public static Vector4f fogTargetColor() {
        return new Vector4f(TARGET_FOG_R, TARGET_FOG_G, TARGET_FOG_B, 1.0F);
    }

    public static float fogDistanceMultiplier() {
        return 1.0F - 0.5F * MathHelper.clamp(fogStrength, 0.0F, 1.0F);
    }

    private static void onStagePacket(S2CHammerPacket payload, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        MinecraftClient client = context.client();
        if (client.world == null) {
            return;
        }

        int id = payload.strikeEntityId();
        ClientStrikeState state = STRIKES.computeIfAbsent(id, ignored -> new ClientStrikeState());
        state.targetPos = payload.targetPos();
        state.seed = payload.seed();
        state.stage = payload.stage();
        state.stageStrikeTick = payload.stageStrikeTick();
        state.stageStartWorldTime = payload.stageStartWorldTime();
        state.lastSeenWorldTime = client.world.getTime();
    }

    private static void tickClient(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            STRIKES.clear();
            shakeStrength = 0.0F;
            fogStrength = 0.0F;
            whiteoutAlpha = 0.0F;
            blackoutAlpha = 0.0F;
            ringRadiusNorm = 0.0F;
            ringStrength = 0.0F;
            glitchStrength = 0.0F;
            clearPostEffect(client);
            return;
        }

        long worldTime = client.world.getTime();
        PlayerEntity player = client.player;
        Vec3d listenerPos = player.getEyePos();

        float maxShake = 0.0F;
        float maxFog = 0.0F;
        float maxWhiteout = 0.0F;
        float maxBlackout = 0.0F;
        float bestRingStrength = 0.0F;
        float bestRingRadiusNorm = 0.0F;
        float maxGlitch = 0.0F;

        Iterator<ClientStrikeState> it = STRIKES.values().iterator();
        while (it.hasNext()) {
            ClientStrikeState strike = it.next();
            float strikeTime = strikeTime(worldTime, strike, 0.0F);
            int strikeTick = MathHelper.floor(strikeTime);

            if (strikeTick > STAGE_6_AFTERMATH_END + ASHFALL_TICKS + 40) {
                it.remove();
                continue;
            }

            Vec3d impactPos = impactCenter(strike.targetPos);
            double dist = listenerPos.distanceTo(impactPos);
            float distFactor = 1.0F - (float) MathHelper.clamp(dist / PLAYER_EFFECT_RADIUS, 0.0D, 1.0D);

            if (strikeTime >= 0.0F && strikeTime <= STAGE_1_TARGETING_END) {
                float progress = MathHelper.clamp(strikeTime / (float) Math.max(1, STAGE_1_TARGETING_END), 0.0F, 1.0F);
                maxFog = Math.max(maxFog, progress * distFactor);
                maxShake = Math.max(maxShake, 0.05F * distFactor);
            }

            if (strikeTime >= STAGE_4_ERUPTION_START && strikeTime <= STAGE_4_ERUPTION_END) {
                float t = strikeTime - STAGE_4_ERUPTION_START;
                float alpha = (float) Math.exp(-0.20F * t);
                maxWhiteout = Math.max(maxWhiteout, alpha * distFactor);
            }

            if (strikeTime >= STAGE_6_FINAL_CUT_START && strikeTime < (STAGE_6_FINAL_CUT_START + STAGE_6_FINAL_CUT_TICKS)) {
                if (distFactor > 0.001F) {
                    maxBlackout = 1.0F;
                }
            }

            if (strikeTime >= STAGE_5_WAVE_START && strikeTime <= STAGE_5_WAVE_END) {
                float p = (strikeTime - STAGE_5_WAVE_START) / (float) Math.max(1, (STAGE_5_WAVE_END - STAGE_5_WAVE_START));
                float radiusBlocks = MathHelper.clamp(p, 0.0F, 1.0F) * PRESSURE_WAVE_RADIUS;
                float strength = distFactor;
                if (strength > bestRingStrength) {
                    bestRingStrength = strength;
                    bestRingRadiusNorm = radiusBlocks / PRESSURE_WAVE_RADIUS;
                }
            }

            if (strikeTime >= STAGE_6_AFTERMATH_START && strikeTime <= STAGE_6_AFTERMATH_END) {
                float toward = lookingToward(player, impactPos);
                maxGlitch = Math.max(maxGlitch, distFactor * toward);
            }

            if (strikeTick != strike.lastProcessedStrikeTick) {
                strike.lastProcessedStrikeTick = strikeTick;
                tickStrikePerTick(client, strike, strikeTick);
            }
        }

        shakeStrength = maxShake;
        fogStrength = maxFog;
        whiteoutAlpha = maxWhiteout;
        blackoutAlpha = maxBlackout;
        ringStrength = bestRingStrength;
        ringRadiusNorm = bestRingRadiusNorm;
        glitchStrength = maxGlitch;

        tickPostEffect(client, worldTime);
    }

    private static void tickStrikePerTick(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null || client.player == null) {
            return;
        }

        Vec3d impact = impactCenter(strike.targetPos);
        double dist = client.player.getEyePos().distanceTo(impact);
        if (dist > 512.0D) {
            return;
        }

        if (strikeTick >= STAGE_2_BREACH_START && strikeTick <= STAGE_2_BREACH_END) {
            tickBreachSounds(client, strike, strikeTick);
            tickCloudPush(client, strike, strikeTick);
        }

        if (strikeTick >= STAGE_3_STROKE_START && strikeTick <= STAGE_3_STROKE_END) {
            tickDrillParticles(client, strike);
        }

        if (strikeTick >= STAGE_5_WAVE_START && strikeTick <= STAGE_5_WAVE_END) {
            tickDustWall(client, strike, strikeTick);
        }

        if (strikeTick >= STAGE_6_AFTERMATH_START && strikeTick <= (STAGE_6_AFTERMATH_START + ASHFALL_TICKS)) {
            tickAshfall(client, strike);
        }
    }

    private static void tickBreachSounds(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null) {
            return;
        }
        if (strikeTick - strike.lastBreachSoundTick < 3) {
            return;
        }
        strike.lastBreachSoundTick = strikeTick;

        float progress = (strikeTick - STAGE_2_BREACH_START) / (float) Math.max(1, (STAGE_2_BREACH_END - STAGE_2_BREACH_START));
        float volume = 0.35F + 2.15F * MathHelper.clamp(progress, 0.0F, 1.0F);
        float pitch = 1.1F + 0.75F * MathHelper.clamp(progress, 0.0F, 1.0F);

        Vec3d impact = impactCenter(strike.targetPos);
        client.world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, volume, pitch);
    }

    private static void tickCloudPush(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null) {
            return;
        }

        Vec3d center = impactCenter(strike.targetPos).add(0.0D, (CONE_TIP_START_Y - strike.targetPos.getY()), 0.0D);
        for (int i = 0; i < 120; i++) {
            double theta = client.world.random.nextDouble() * Math.PI * 2.0D;
            double r = client.world.random.nextDouble() * 40.0D;
            double x = center.x + Math.cos(theta) * r;
            double z = center.z + Math.sin(theta) * r;
            double y = CONE_TIP_START_Y + client.world.random.nextDouble() * 20.0D;

            double vx = Math.cos(theta) * (0.18D + client.world.random.nextDouble() * 0.25D);
            double vz = Math.sin(theta) * (0.18D + client.world.random.nextDouble() * 0.25D);
            client.world.addParticleClient(ParticleTypes.CLOUD, x, y, z, vx, 0.02D, vz);
        }
    }

    private static void tickDrillParticles(MinecraftClient client, ClientStrikeState strike) {
        if (client.world == null) {
            return;
        }
        Vec3d center = impactCenter(strike.targetPos).add(0.0D, 0.02D, 0.0D);

        for (int i = 0; i < 50; i++) {
            double x = center.x + (client.world.random.nextDouble() - 0.5D) * 0.35D;
            double z = center.z + (client.world.random.nextDouble() - 0.5D) * 0.35D;
            double vx = (client.world.random.nextDouble() - 0.5D) * 0.08D;
            double vz = (client.world.random.nextDouble() - 0.5D) * 0.08D;
            client.world.addParticleClient(ParticleTypes.LAVA, x, center.y, z, vx, 0.05D + client.world.random.nextDouble() * 0.08D, vz);
        }
        for (int i = 0; i < 100; i++) {
            double x = center.x + (client.world.random.nextDouble() - 0.5D) * 0.75D;
            double z = center.z + (client.world.random.nextDouble() - 0.5D) * 0.75D;
            client.world.addParticleClient(ParticleTypes.LARGE_SMOKE, x, center.y, z, 0.0D, 0.10D + client.world.random.nextDouble() * 0.10D, 0.0D);
        }
    }

    private static void tickDustWall(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null) {
            return;
        }

        float p = (strikeTick - STAGE_5_WAVE_START) / (float) Math.max(1, (STAGE_5_WAVE_END - STAGE_5_WAVE_START));
        float radius = MathHelper.clamp(p, 0.0F, 1.0F) * PRESSURE_WAVE_RADIUS;
        float dustRadius = Math.max(0.0F, radius - 2.0F);

        Vec3d center = impactCenter(strike.targetPos);
        for (int i = 0; i < 180; i++) {
            double theta = client.world.random.nextDouble() * Math.PI * 2.0D;
            double r = dustRadius + (client.world.random.nextDouble() - 0.5D) * 0.9D;
            double x = center.x + Math.cos(theta) * r;
            double z = center.z + Math.sin(theta) * r;
            double y = center.y + client.world.random.nextDouble() * 12.0D;
            client.world.addParticleClient(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 0.0D, 0.05D, 0.0D);
        }
    }

    private static void tickAshfall(MinecraftClient client, ClientStrikeState strike) {
        if (client.world == null) {
            return;
        }
        Vec3d center = impactCenter(strike.targetPos);
        for (int i = 0; i < 60; i++) {
            double theta = client.world.random.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(client.world.random.nextDouble()) * ASHFALL_RADIUS;
            double x = center.x + Math.cos(theta) * r;
            double z = center.z + Math.sin(theta) * r;
            double y = center.y + 12.0D + client.world.random.nextDouble() * 28.0D;
            client.world.addParticleClient(ParticleTypes.WHITE_ASH, x, y, z, 0.0D, -0.02D, 0.0D);
        }
    }

    private static void tickPostEffect(MinecraftClient client, long worldTime) {
        boolean wantsShader = ringStrength > 0.001F || glitchStrength > 0.001F;
        if (!wantsShader) {
            clearPostEffect(client);
            return;
        }

        if (!HAMMER_POST_EFFECT.equals(client.gameRenderer.getPostProcessorId())) {
            GameRendererAccessor accessor = (GameRendererAccessor) client.gameRenderer;
            accessor.modid$setPostProcessor(HAMMER_POST_EFFECT);
            accessor.modid$setPostProcessorEnabled(true);
        }

        client.getShaderLoader().loadPostEffect(HAMMER_POST_EFFECT, Set.of(PostEffectProcessor.MAIN));
    }

    private static void clearPostEffect(MinecraftClient client) {
        if (HAMMER_POST_EFFECT.equals(client.gameRenderer.getPostProcessorId())) {
            client.gameRenderer.clearPostProcessor();
        }
    }

    private static float strikeTime(long worldTime, ClientStrikeState state, float tickDelta) {
        return (float) (worldTime - state.stageStartWorldTime) + state.stageStrikeTick + tickDelta;
    }

    private static Vec3d impactCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.02D, pos.getZ() + 0.5D);
    }

    private static float lookingToward(PlayerEntity player, Vec3d impactPos) {
        Vec3d toImpact = impactPos.subtract(player.getEyePos());
        double distSq = toImpact.lengthSquared();
        if (distSq < 1.0E-6D) {
            return 1.0F;
        }
        Vec3d dir = toImpact.normalize();
        Vec3d look = player.getRotationVec(1.0F);
        float dot = (float) look.dotProduct(dir);
        return MathHelper.clamp((dot - 0.55F) / 0.35F, 0.0F, 1.0F);
    }

    private static void renderHud(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        float white = MathHelper.clamp(whiteoutAlpha, 0.0F, 1.0F);
        if (white > 0.001F) {
            int a = (int) (white * 255.0F);
            drawContext.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }

        float black = MathHelper.clamp(blackoutAlpha, 0.0F, 1.0F);
        if (black > 0.001F) {
            int a = (int) (black * 255.0F);
            drawContext.fill(0, 0, w, h, (a << 24));
        }
    }

    private static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
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

        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        long worldTime = client.world.getTime();

        for (ClientStrikeState strike : STRIKES.values()) {
            Vec3d impactWorld = impactCenter(strike.targetPos);
            double distSq = impactWorld.squaredDistanceTo(cameraPos);
            if (distSq > (1024.0D * 1024.0D)) {
                continue;
            }

            float strikeTime = strikeTime(worldTime, strike, tickDelta);
            Vec3d center = impactWorld.subtract(cameraPos);

            matrices.push();
            matrices.translate(center.x, center.y, center.z);

            if (strikeTime >= 0.0F && strikeTime <= STAGE_1_TARGETING_END) {
                renderLaser(queue, matrices, strike.targetPos);
            }
            if (strikeTime >= STAGE_2_BREACH_START && strikeTime <= STAGE_2_BREACH_END) {
                renderMachCone(queue, matrices, strike.targetPos, strikeTime);
            }
            if (strikeTime >= STAGE_3_STROKE_START && strikeTime <= STAGE_3_STROKE_END) {
                renderHammerBeam(queue, matrices, strike.targetPos, worldTime, tickDelta);
            }
            if (strikeTime >= STAGE_5_WAVE_START && strikeTime <= STAGE_5_WAVE_END) {
                renderPressureWaveOutline(queue, matrices, strikeTime);
            }

            matrices.pop();
        }
    }

    private static void renderLaser(OrderedRenderCommandQueue queue, MatrixStack matrices, BlockPos targetPos) {
        int top = Math.max(targetPos.getY() + 1, LASER_TOP_Y);
        float height = top - (targetPos.getY() + 0.02F);
        if (height <= 0.5F) {
            return;
        }

        submitCrossBeam(queue, matrices, WHITE_TEXTURE, 0.05F, height, LASER_COLOR_ARGB, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    private static void renderMachCone(OrderedRenderCommandQueue queue, MatrixStack matrices, BlockPos targetPos, float strikeTime) {
        float localTick = strikeTime - STAGE_2_BREACH_START;
        float tipWorldY = CONE_TIP_START_Y - localTick * CONE_DESCENT_PER_TICK;
        float groundWorldY = targetPos.getY() + 0.02F;
        float tipY = Math.max(0.0F, tipWorldY - groundWorldY);
        float baseY = tipY + CONE_HEIGHT;

        int alpha = 120;
        int color = (alpha << 24) | 0xFFFFFF;

        int segments = 48;
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), new Vec3d(0.0D, baseY, 0.0D), CONE_RADIUS, color, 2.5F, segments);

        Vec3d tip = new Vec3d(0.0D, tipY, 0.0D);
        for (int i = 1; i <= segments; i++) {
            double theta = (Math.PI * 2.0D) * (i / (double) segments);
            double x = Math.cos(theta) * CONE_RADIUS;
            double z = Math.sin(theta) * CONE_RADIUS;
            HammerRenderUtil.submitLine(queue, matrices, RenderLayers.linesTranslucent(), tip, new Vec3d(x, baseY, z), color, 1.6F);
        }
    }

    private static void renderHammerBeam(OrderedRenderCommandQueue queue, MatrixStack matrices, BlockPos targetPos, long worldTime, float tickDelta) {
        int top = Math.max(targetPos.getY() + 1, LASER_TOP_Y);
        float height = top - (targetPos.getY() + 0.02F);
        if (height <= 0.5F) {
            return;
        }

        float time = (worldTime + tickDelta) / 20.0F;
        float pulse = 1.0F + 0.18F * MathHelper.sin(time * 2.3F);

        submitCylinder(queue, matrices, WHITE_TEXTURE, 1.0F, height, argb(255, 255, 255, 255), 0.0F, 0.0F, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        submitCylinder(queue, matrices, NOISE_TEXTURE, 3.0F, height, argb(128, 0, 255, 255), time * 0.25F, time * 0.6F, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        submitCylinder(queue, matrices, WHITE_TEXTURE, 6.0F * pulse, height, argb(51, 110, 120, 255), 0.0F, 0.0F, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    private static void renderPressureWaveOutline(OrderedRenderCommandQueue queue, MatrixStack matrices, float strikeTime) {
        float p = (strikeTime - STAGE_5_WAVE_START) / (float) Math.max(1, (STAGE_5_WAVE_END - STAGE_5_WAVE_START));
        float radius = MathHelper.clamp(p, 0.0F, 1.0F) * PRESSURE_WAVE_RADIUS;
        if (radius <= 0.25F) {
            return;
        }

        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), new Vec3d(0.0D, 0.05D, 0.0D), radius, argb(140, 255, 255, 255), 3.0F, 96);
    }

    private static void submitCrossBeam(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            Identifier texture,
            float halfWidth,
            float height,
            int argb,
            int light
    ) {
        submitBeamQuad(queue, matrices, texture, -halfWidth, 0.0F, 0.0F, halfWidth, height, 0.0F, argb, light);
        submitBeamQuad(queue, matrices, texture, 0.0F, 0.0F, -halfWidth, 0.0F, height, halfWidth, argb, light);
    }

    private static void submitBeamQuad(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            Identifier texture,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int argb,
            int light
    ) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vertices) -> {
            vertex(vertices, entry, x0, y0, z0, r, g, b, a, 0.0F, 0.0F, light);
            vertex(vertices, entry, x1, y0, z1, r, g, b, a, 1.0F, 0.0F, light);
            vertex(vertices, entry, x1, y1, z1, r, g, b, a, 1.0F, 1.0F, light);
            vertex(vertices, entry, x0, y1, z0, r, g, b, a, 0.0F, 1.0F, light);
        });
    }

    private static void submitCylinder(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            Identifier texture,
            float radius,
            float height,
            int argb,
            float uScroll,
            float vScroll,
            int light
    ) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        int segments = 48;
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vertices) -> {
            for (int i = 0; i < segments; i++) {
                float u0 = (i / (float) segments) + uScroll;
                float u1 = ((i + 1) / (float) segments) + uScroll;
                double a0 = (Math.PI * 2.0D) * (i / (double) segments);
                double a1 = (Math.PI * 2.0D) * ((i + 1) / (double) segments);
                float x0 = (float) (Math.cos(a0) * radius);
                float z0 = (float) (Math.sin(a0) * radius);
                float x1 = (float) (Math.cos(a1) * radius);
                float z1 = (float) (Math.sin(a1) * radius);

                float v0 = vScroll;
                float v1 = vScroll + height * 0.05F;

                vertex(vertices, entry, x0, 0.0F, z0, r, g, b, a, u0, v0, light);
                vertex(vertices, entry, x1, 0.0F, z1, r, g, b, a, u1, v0, light);
                vertex(vertices, entry, x1, height, z1, r, g, b, a, u1, v1, light);
                vertex(vertices, entry, x0, height, z0, r, g, b, a, u0, v1, light);
            }
        });
    }

    private static void vertex(
            VertexConsumer vertices,
            MatrixStack.Entry entry,
            float x,
            float y,
            float z,
            int r,
            int g,
            int b,
            int a,
            float u,
            float v,
            int light
    ) {
        vertices.vertex(entry, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0F, 1.0F, 0.0F);
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class ClientStrikeState {
        BlockPos targetPos = BlockPos.ORIGIN;
        int seed;
        HammerStage stage = HammerStage.TARGETING;
        int stageStrikeTick;
        long stageStartWorldTime;
        long lastSeenWorldTime;

        int lastProcessedStrikeTick = Integer.MIN_VALUE;
        int lastBreachSoundTick = Integer.MIN_VALUE;
    }
}
