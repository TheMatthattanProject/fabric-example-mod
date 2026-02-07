package com.example.hammer.client;

import com.example.ExampleMod;
import com.example.hammer.HammerConfig;
import com.example.hammer.HammerStage;
import com.example.hammer.client.render.HammerRenderUtil;
import com.example.hammer.network.S2CHammerCraterPacket;
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
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static com.example.hammer.HammerStrikeTimeline.*;

@Environment(EnvType.CLIENT)
public final class HammerClientEffects {
    private static final Identifier WHITE_TEXTURE = Identifier.of(ExampleMod.MOD_ID, "textures/misc/hammer_beam.png");
    private static final Identifier HAMMER_POST_EFFECT = Identifier.of(ExampleMod.MOD_ID, "hammer");

    private static final int LASER_COLOR_ARGB = 0xFFFF0000;

    private static final float TARGET_FOG_R = 0x1A / 255.0F;
    private static final float TARGET_FOG_G = 0x0F / 255.0F;
    private static final float TARGET_FOG_B = 0x2E / 255.0F;



    private static final int LASER_TOP_Y = 320;
    private static final int CONE_TIP_START_Y = 300;
    private static final float STAGE_CYLINDER_BASE_RADIUS = 1.0F;
    private static final float STAGE_CYLINDER_RADIUS_STEP = 1.75F;
    private static final float STAGE_CYLINDER_PULSE = 0.03F;
    private static final int CRATER_DEPTH_BLOCKS = 128;
    private static final float STAGE_CYLINDER_ACCELERATION = 1.5F;
    private static final float STAGE_CYLINDER_ACCELERATION_RAMP = 2.0F;
    private static final int STAGE_CYLINDER_PRE_TICKS = 12;
    private static final float STAGE_CYLINDER_EXTENSION_DURATION_SCALE = 0.65F;
    private static final float STAGE_CYLINDER_POST_OFFSET = 0.08F;
    private static final int[] STAGE_CYLINDER_STARTS = {
            STAGE_2_BREACH_START,
            STAGE_4_ERUPTION_START,
            STAGE_5_WAVE_START
    };

    private static final float PLAYER_EFFECT_RADIUS = 100.0F;
    private static final float PRESSURE_WAVE_RADIUS = 80.0F;
    private static final float ASHFALL_RADIUS = 100.0F;
    private static final int ASHFALL_TICKS = 1200;
    private static final int AFTERMATH_GROUND_VFX_TICKS = 20 * 45;
    private static final Vec3d BEAM_CHARGE_DISK_CENTER = new Vec3d(0.0D, 0.04D, 0.0D);
    private static final Vec3d PRESSURE_WAVE_PRIMARY_CENTER = new Vec3d(0.0D, 0.05D, 0.0D);
    private static final Vec3d PRESSURE_WAVE_TRAIL_CENTER = new Vec3d(0.0D, 0.11D, 0.0D);
    private static final Vec3d PRESSURE_WAVE_LEAD_CENTER = new Vec3d(0.0D, 0.16D, 0.0D);
    private static final Vec3d AFTERMATH_CORE_CENTER = new Vec3d(0.0D, 0.03D, 0.0D);
    private static final Vec3d AFTERMATH_SCORCH_CENTER = new Vec3d(0.0D, 0.07D, 0.0D);
    private static final Set<Identifier> HAMMER_POST_EFFECT_TARGETS = Set.of(PostEffectProcessor.MAIN);
    private static final int CYLINDER_SEGMENTS = 48;
    private static final float[] CYLINDER_UNIT_X = new float[CYLINDER_SEGMENTS + 1];
    private static final float[] CYLINDER_UNIT_Z = new float[CYLINDER_SEGMENTS + 1];

    private static final Map<Integer, ClientStrikeState> STRIKES = new HashMap<>();

    private static float shakeStrength;
    private static float fogStrength;
    private static float whiteoutAlpha;
    private static float blackoutAlpha;
    private static float ringRadiusNorm;
    private static float ringStrength;
    private static float glitchStrength;
    private static float beamStrength;
    private static float collapseStrength;
    private static float heatShimmerStrength;

    static {
        for (int i = 0; i <= CYLINDER_SEGMENTS; i++) {
            double angle = (Math.PI * 2.0D) * (i / (double) CYLINDER_SEGMENTS);
            CYLINDER_UNIT_X[i] = (float) Math.cos(angle);
            CYLINDER_UNIT_Z[i] = (float) Math.sin(angle);
        }
    }

    private HammerClientEffects() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(S2CHammerPacket.ID, HammerClientEffects::onStagePacket);
        ClientPlayNetworking.registerGlobalReceiver(S2CHammerCraterPacket.ID, HammerClientEffects::onCraterCompletePacket);

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

    public static float beamStrength() {
        return beamStrength;
    }

    public static float collapseStrength() {
        return collapseStrength;
    }

    public static float heatShimmerStrength() {
        return heatShimmerStrength;
    }

    public static float fogTargetRed() {
        return TARGET_FOG_R;
    }

    public static float fogTargetGreen() {
        return TARGET_FOG_G;
    }

    public static float fogTargetBlue() {
        return TARGET_FOG_B;
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
        applyTargetPos(state, payload.targetPos());
        state.seed = payload.seed();
        state.fxPreset = payload.fxPreset();
        state.stage = payload.stage();
        state.stageStrikeTick = payload.stageStrikeTick();
        state.stageStartWorldTime = payload.stageStartWorldTime();
        if (state.strikeStartWorldTime == Long.MIN_VALUE) {
            state.strikeStartWorldTime = payload.stageStartWorldTime() - payload.stageStrikeTick();
        }
        state.lastSeenWorldTime = client.world.getTime();
    }

    private static void onCraterCompletePacket(S2CHammerCraterPacket payload, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        MinecraftClient client = context.client();
        if (client.world == null) {
            return;
        }

        ClientStrikeState state = STRIKES.computeIfAbsent(payload.strikeEntityId(), ignored -> new ClientStrikeState());
        applyTargetPos(state, payload.targetPos());
        state.craterCompleteWorldTime = payload.completionWorldTime();
        state.lastSeenWorldTime = client.world.getTime();
    }

    private static void applyTargetPos(ClientStrikeState state, BlockPos targetPos) {
        state.targetPos = targetPos;
        state.targetCenter = impactCenter(targetPos);
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
            beamStrength = 0.0F;
            collapseStrength = 0.0F;
            heatShimmerStrength = 0.0F;
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
        float maxBeam = 0.0F;
        float maxCollapse = 0.0F;
        float maxHeatShimmer = 0.0F;

        Iterator<ClientStrikeState> it = STRIKES.values().iterator();
        while (it.hasNext()) {
            ClientStrikeState strike = it.next();
            float strikeTime = strikeTime(worldTime, strike, 0.0F);
            int strikeTick = MathHelper.floor(strikeTime);

            if (strikeTick > STAGE_6_AFTERMATH_END + ASHFALL_TICKS + 40) {
                it.remove();
                continue;
            }

            Vec3d impactPos = strike.targetCenter;
            double dist = listenerPos.distanceTo(impactPos);
            float distFactor = 1.0F - (float) MathHelper.clamp(dist / PLAYER_EFFECT_RADIUS, 0.0D, 1.0D);
            HammerConfig.ClientFxPreset fxPreset = strike.fxPreset;
            float beamPresetScale = HammerConfig.clientFxBeamScale(fxPreset);
            float collapsePresetScale = HammerConfig.clientFxCollapseScale(fxPreset);
            float impactPresetScale = HammerConfig.clientFxImpactScale(fxPreset);
            float shockRingPresetScale = HammerConfig.clientFxShockRingScale(fxPreset);
            float heatPresetScale = HammerConfig.clientFxHeatHazeScale(fxPreset);

            float endTick = beamEndTick(strike);
            float beamCharge = beamChargeStrength(strikeTime, endTick);
            maxBeam = Math.max(maxBeam, beamCharge * distFactor * beamPresetScale);

            if (strikeTime >= STAGE_2_BREACH_START && strikeTime <= (STAGE_3_STROKE_START + 6.0F)) {
                float collapse;
                if (strikeTime < STAGE_3_STROKE_START) {
                    float p = (strikeTime - STAGE_2_BREACH_START) / (float) Math.max(1, STAGE_3_STROKE_START - STAGE_2_BREACH_START);
                    float ramp = smoothStep(MathHelper.clamp(p, 0.0F, 1.0F));
                    collapse = ramp * ramp;
                } else {
                    float decay = 1.0F - ((strikeTime - STAGE_3_STROKE_START) / 6.0F);
                    collapse = MathHelper.clamp(decay, 0.0F, 1.0F);
                }
                maxCollapse = Math.max(maxCollapse, collapse * distFactor * collapsePresetScale);
                maxFog = Math.max(maxFog, (0.35F + (0.65F * collapse)) * distFactor * collapsePresetScale);
            }

            if (strikeTime >= (STAGE_3_STROKE_START - 1.5F) && strikeTime <= (STAGE_3_STROKE_START + 3.5F)) {
                float dt = strikeTime - STAGE_3_STROKE_START;
                float ignitionFlash = (float) Math.exp(-1.3F * dt * dt);
                maxWhiteout = Math.max(maxWhiteout, ignitionFlash * distFactor * impactPresetScale);
            }

            if (strikeTime >= STAGE_3_STROKE_START && strikeTime <= (endTick + BEAM_TAIL_TICKS)) {
                float toward = lookingToward(player, impactPos);
                float heat = beamCharge * distFactor * (0.35F + 0.65F * toward) * heatPresetScale;
                maxHeatShimmer = Math.max(maxHeatShimmer, heat);
            }

            if (strikeTime >= STAGE_6_AFTERMATH_START) {
                float aftermathProgress = (strikeTime - STAGE_6_AFTERMATH_START) / (float) AFTERMATH_GROUND_VFX_TICKS;
                if (aftermathProgress <= 1.0F) {
                    float life = 1.0F - MathHelper.clamp(aftermathProgress, 0.0F, 1.0F);
                    maxHeatShimmer = Math.max(maxHeatShimmer, life * 0.28F * heatPresetScale * distFactor);
                }
            }

            if (worldTime <= strike.aftershockPulseEndWorldTime) {
                float remain = MathHelper.clamp((strike.aftershockPulseEndWorldTime - worldTime + 1L) / 6.0F, 0.0F, 1.0F);
                float pulse = strike.aftershockPulseStrength * remain;
                maxWhiteout = Math.max(maxWhiteout, pulse * distFactor);
                maxShake = Math.max(maxShake, pulse * 0.35F * distFactor);
            }

            if (strikeTime >= 0.0F && strikeTime <= STAGE_1_TARGETING_END) {
                float progress = MathHelper.clamp(strikeTime / (float) Math.max(1, STAGE_1_TARGETING_END), 0.0F, 1.0F);
                maxFog = Math.max(maxFog, progress * distFactor);
                maxShake = Math.max(maxShake, 0.05F * distFactor);
            }

            if (strikeTime >= STAGE_4_ERUPTION_START && strikeTime <= STAGE_4_ERUPTION_END) {
                float t = strikeTime - STAGE_4_ERUPTION_START;
                float alpha = (float) Math.exp(-0.20F * t);
                maxWhiteout = Math.max(maxWhiteout, alpha * distFactor * impactPresetScale);
            }

            if (strikeTime >= STAGE_6_FINAL_CUT_START && strikeTime < (STAGE_6_FINAL_CUT_START + STAGE_6_FINAL_CUT_TICKS)) {
                if (distFactor > 0.001F) {
                    maxBlackout = 1.0F;
                }
            }

            if (strikeTime >= STAGE_5_WAVE_START && strikeTime <= STAGE_5_WAVE_END) {
                float p = (strikeTime - STAGE_5_WAVE_START) / (float) Math.max(1, (STAGE_5_WAVE_END - STAGE_5_WAVE_START));
                float radiusBlocks = MathHelper.clamp(p, 0.0F, 1.0F) * PRESSURE_WAVE_RADIUS;
                float strength = distFactor * shockRingPresetScale;
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
        beamStrength = maxBeam;
        collapseStrength = maxCollapse;
        heatShimmerStrength = maxHeatShimmer;

        tickPostEffect(client, worldTime);
    }

    private static void tickStrikePerTick(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null || client.player == null) {
            return;
        }

        Vec3d impact = strike.targetCenter;
        double dist = client.player.getEyePos().distanceTo(impact);
        if (dist > 512.0D) {
            return;
        }

        if (strikeTick >= STAGE_2_BREACH_START && strikeTick <= STAGE_2_BREACH_END) {
            tickBreachSounds(client, strike, strikeTick);
            tickCloudPush(client, strike, strikeTick);
        }

        if (strikeTick >= STAGE_3_STROKE_START && strikeTick <= STAGE_3_STROKE_END) {
            tickDrillParticles(client, strike, strikeTick);
        }

        if (strikeTick >= STAGE_5_WAVE_START && strikeTick <= STAGE_5_WAVE_END) {
            tickDustWall(client, strike, strikeTick);
        }

        if (strikeTick >= STAGE_4_ERUPTION_END && strikeTick <= (STAGE_6_AFTERMATH_START + 120)) {
            tickAftershockBursts(client, strike, strikeTick);
        }

        if (strikeTick >= STAGE_6_AFTERMATH_START && strikeTick <= (STAGE_6_AFTERMATH_START + AFTERMATH_GROUND_VFX_TICKS)) {
            tickAftermathGroundVfx(client, strike, strikeTick);
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

        Vec3d impact = strike.targetCenter;
        client.world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, volume, pitch);
    }

    private static void tickCloudPush(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null) {
            return;
        }

        double centerX = strike.targetCenter.x;
        double centerZ = strike.targetCenter.z;
        for (int i = 0; i < 120; i++) {
            double theta = client.world.random.nextDouble() * Math.PI * 2.0D;
            double cosTheta = Math.cos(theta);
            double sinTheta = Math.sin(theta);
            double r = client.world.random.nextDouble() * 40.0D;
            double x = centerX + cosTheta * r;
            double z = centerZ + sinTheta * r;
            double y = CONE_TIP_START_Y + client.world.random.nextDouble() * 20.0D;

            double vx = cosTheta * (0.18D + client.world.random.nextDouble() * 0.25D);
            double vz = sinTheta * (0.18D + client.world.random.nextDouble() * 0.25D);
            client.world.addParticleClient(ParticleTypes.CLOUD, x, y, z, vx, 0.02D, vz);
        }
    }

    private static void tickDrillParticles(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null) {
            return;
        }
        double centerX = strike.targetCenter.x;
        double centerY = strike.targetCenter.y + 0.02D;
        double centerZ = strike.targetCenter.z;

        float progress = (strikeTick - STAGE_3_STROKE_START) / (float) Math.max(1, (STAGE_3_STROKE_END - STAGE_3_STROKE_START));
        float intensity = smoothStep(progress);

        int lavaCount = 50 + MathHelper.floor(40 * intensity);
        double lavaSpread = 0.35D + 0.35D * intensity;
        double lavaSpeed = 0.08D + 0.05D * intensity;
        double lavaLift = 0.05D + 0.08D * intensity;

        for (int i = 0; i < lavaCount; i++) {
            double x = centerX + (client.world.random.nextDouble() - 0.5D) * lavaSpread;
            double z = centerZ + (client.world.random.nextDouble() - 0.5D) * lavaSpread;
            double vx = (client.world.random.nextDouble() - 0.5D) * lavaSpeed;
            double vz = (client.world.random.nextDouble() - 0.5D) * lavaSpeed;
            double vy = lavaLift + client.world.random.nextDouble() * (0.08D + 0.08D * intensity);
            client.world.addParticleClient(ParticleTypes.LAVA, x, centerY, z, vx, vy, vz);
        }

        int smokeCount = 100 + MathHelper.floor(140 * intensity);
        double smokeSpread = 0.75D + 0.55D * intensity;
        for (int i = 0; i < smokeCount; i++) {
            double x = centerX + (client.world.random.nextDouble() - 0.5D) * smokeSpread;
            double z = centerZ + (client.world.random.nextDouble() - 0.5D) * smokeSpread;
            double vy = 0.10D + client.world.random.nextDouble() * (0.12D + 0.10D * intensity);
            client.world.addParticleClient(ParticleTypes.LARGE_SMOKE, x, centerY, z, 0.0D, vy, 0.0D);
        }

        int sparkCount = MathHelper.floor(12 * intensity);
        for (int i = 0; i < sparkCount; i++) {
            double x = centerX + (client.world.random.nextDouble() - 0.5D) * 0.45D;
            double z = centerZ + (client.world.random.nextDouble() - 0.5D) * 0.45D;
            double vx = (client.world.random.nextDouble() - 0.5D) * (0.16D + 0.12D * intensity);
            double vz = (client.world.random.nextDouble() - 0.5D) * (0.16D + 0.12D * intensity);
            double vy = 0.18D + client.world.random.nextDouble() * 0.18D;
            client.world.addParticleClient(ParticleTypes.FLAME, x, centerY, z, vx, vy, vz);
        }

        if (intensity > 0.7F && client.world.random.nextFloat() < intensity * 0.18F) {
            client.world.addParticleClient(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFFFF), centerX, centerY + 0.1D, centerZ, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void tickDustWall(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null) {
            return;
        }

        float p = (strikeTick - STAGE_5_WAVE_START) / (float) Math.max(1, (STAGE_5_WAVE_END - STAGE_5_WAVE_START));
        float radius = MathHelper.clamp(p, 0.0F, 1.0F) * PRESSURE_WAVE_RADIUS;
        float dustRadius = Math.max(0.0F, radius - 2.0F);

        Vec3d center = strike.targetCenter;
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
        Vec3d center = strike.targetCenter;
        for (int i = 0; i < 60; i++) {
            double theta = client.world.random.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(client.world.random.nextDouble()) * ASHFALL_RADIUS;
            double x = center.x + Math.cos(theta) * r;
            double z = center.z + Math.sin(theta) * r;
            double y = center.y + 12.0D + client.world.random.nextDouble() * 28.0D;
            client.world.addParticleClient(ParticleTypes.WHITE_ASH, x, y, z, 0.0D, -0.02D, 0.0D);
        }
    }

    private static void tickAftershockBursts(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null) {
            return;
        }

        int burstCount = aftershockBurstCount(strike.fxPreset);
        while (strike.nextAftershockIndex < burstCount) {
            int index = strike.nextAftershockIndex;
            int scheduledTick = aftershockTick(strike, index);
            if (strikeTick < scheduledTick) {
                break;
            }
            if (strikeTick - scheduledTick <= 2) {
                triggerAftershockBurst(client, strike, index, burstCount);
            }
            strike.nextAftershockIndex++;
        }
    }

    private static void triggerAftershockBurst(MinecraftClient client, ClientStrikeState strike, int index, int burstCount) {
        if (client.world == null) {
            return;
        }

        float impactScale = HammerConfig.clientFxImpactScale(strike.fxPreset);
        if (impactScale <= 0.001F) {
            return;
        }

        float indexNorm = index / (float) Math.max(1, burstCount - 1);
        float angleBase = (float) (((strike.seed * 0.013D) + (index * 2.399963229728653D)) % (Math.PI * 2.0D));
        float angle = angleBase + ((hash01(strike.seed ^ (index * 91)) - 0.5F) * 0.9F);
        float radius = 8.0F + (indexNorm * PRESSURE_WAVE_RADIUS * 0.52F) + ((hash01(strike.seed ^ (index * 131)) - 0.5F) * 3.4F);

        double x = strike.targetCenter.x + Math.cos(angle) * radius;
        double z = strike.targetCenter.z + Math.sin(angle) * radius;
        double y = strike.targetCenter.y + 0.22D + (hash01(strike.seed ^ (index * 177)) * 0.36D);

        client.world.addParticleClient(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFF0D8), x, y, z, 0.0D, 0.0D, 0.0D);

        if (HammerConfig.clientFxParticles()) {
            int smokeCount = Math.max(4, MathHelper.floor((8.0F + (8.0F * impactScale)) * (1.0F - (0.25F * indexNorm))));
            int sparkCount = Math.max(3, MathHelper.floor((6.0F + (6.0F * impactScale)) * (1.0F - (0.20F * indexNorm))));
            int ashCount = Math.max(6, MathHelper.floor((10.0F + (10.0F * impactScale)) * (1.0F - (0.18F * indexNorm))));

            for (int i = 0; i < smokeCount; i++) {
                double vx = (client.world.random.nextDouble() - 0.5D) * (0.10D + (0.14D * impactScale));
                double vz = (client.world.random.nextDouble() - 0.5D) * (0.10D + (0.14D * impactScale));
                double vy = 0.04D + client.world.random.nextDouble() * (0.08D + (0.08D * impactScale));
                client.world.addParticleClient(ParticleTypes.LARGE_SMOKE, x, y, z, vx, vy, vz);
            }

            for (int i = 0; i < sparkCount; i++) {
                double vx = (client.world.random.nextDouble() - 0.5D) * (0.18D + (0.12D * impactScale));
                double vz = (client.world.random.nextDouble() - 0.5D) * (0.18D + (0.12D * impactScale));
                double vy = 0.12D + client.world.random.nextDouble() * (0.14D + (0.10D * impactScale));
                client.world.addParticleClient(ParticleTypes.FLAME, x, y, z, vx, vy, vz);
            }

            for (int i = 0; i < ashCount; i++) {
                double vx = (client.world.random.nextDouble() - 0.5D) * 0.08D;
                double vz = (client.world.random.nextDouble() - 0.5D) * 0.08D;
                double vy = 0.01D + client.world.random.nextDouble() * 0.04D;
                client.world.addParticleClient(ParticleTypes.ASH, x, y + 0.08D, z, vx, vy, vz);
            }
        }

        float volume = 0.32F + (0.18F * impactScale);
        float pitch = 0.72F + (0.18F * hash01(strike.seed ^ (index * 211)));
        client.world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.AMBIENT, volume, pitch);

        float pulseStrength = MathHelper.clamp((0.20F + (0.14F * impactScale)) * (1.0F - (0.12F * indexNorm)), 0.0F, 0.70F);
        strike.aftershockPulseStrength = Math.max(strike.aftershockPulseStrength, pulseStrength);
        strike.aftershockPulseEndWorldTime = client.world.getTime() + 5L;
    }

    private static void tickAftermathGroundVfx(MinecraftClient client, ClientStrikeState strike, int strikeTick) {
        if (client.world == null || !HammerConfig.clientFxParticles()) {
            return;
        }

        float impactScale = HammerConfig.clientFxImpactScale(strike.fxPreset);
        if (impactScale <= 0.001F) {
            return;
        }

        float progress = (strikeTick - STAGE_6_AFTERMATH_START) / (float) AFTERMATH_GROUND_VFX_TICKS;
        float life = 1.0F - MathHelper.clamp(progress, 0.0F, 1.0F);
        if (life <= 0.001F) {
            return;
        }

        int emberBudget = switch (strike.fxPreset) {
            case SUBTLE -> 6;
            case CINEMATIC -> 10;
            case APOCALYPTIC -> 16;
        };
        int bursts = Math.max(1, MathHelper.floor(emberBudget * life * MathHelper.clamp(impactScale, 0.65F, 1.8F)));
        float innerRadius = 5.5F + (2.8F * impactScale);
        float scorchRadius = 11.0F + (7.0F * impactScale);

        for (int i = 0; i < bursts; i++) {
            double theta = client.world.random.nextDouble() * Math.PI * 2.0D;
            double zone = client.world.random.nextDouble();
            double r = zone < 0.62D
                    ? Math.sqrt(client.world.random.nextDouble()) * innerRadius
                    : scorchRadius + ((client.world.random.nextDouble() - 0.5D) * (2.2D + (2.0D * impactScale)));
            double x = strike.targetCenter.x + Math.cos(theta) * r;
            double z = strike.targetCenter.z + Math.sin(theta) * r;
            double y = strike.targetCenter.y + 0.04D + (client.world.random.nextDouble() * 0.28D);

            if (zone < 0.35D && client.world.random.nextFloat() < (0.26F * life)) {
                double vy = 0.02D + (client.world.random.nextDouble() * 0.07D);
                client.world.addParticleClient(ParticleTypes.FLAME, x, y, z, 0.0D, vy, 0.0D);
            } else if (zone < 0.78D) {
                double vy = 0.01D + (client.world.random.nextDouble() * 0.05D);
                client.world.addParticleClient(ParticleTypes.ASH, x, y, z, 0.0D, vy, 0.0D);
            } else {
                double vy = 0.02D + (client.world.random.nextDouble() * 0.04D);
                client.world.addParticleClient(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, vy, 0.0D);
            }

            if (life > 0.35F && client.world.random.nextFloat() < (0.03F * impactScale)) {
                client.world.addParticleClient(ParticleTypes.LAVA, x, y, z, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    private static int aftershockBurstCount(HammerConfig.ClientFxPreset preset) {
        return switch (preset) {
            case SUBTLE -> 3;
            case CINEMATIC -> 5;
            case APOCALYPTIC -> 7;
        };
    }

    private static int aftershockTick(ClientStrikeState strike, int index) {
        int base = STAGE_4_ERUPTION_END + 8 + (index * 9);
        int jitter = MathHelper.floor(hash01(strike.seed ^ (index * 59)) * 4.0F);
        return base + jitter;
    }

    private static float hash01(int value) {
        int n = value;
        n ^= (n << 13);
        n ^= (n >>> 17);
        n ^= (n << 5);
        return (n & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static void tickPostEffect(MinecraftClient client, long worldTime) {
        boolean wantsShader = ringStrength > 0.001F || glitchStrength > 0.001F || beamStrength > 0.001F
                || collapseStrength > 0.001F || heatShimmerStrength > 0.001F;
        if (!wantsShader) {
            clearPostEffect(client);
            return;
        }

        if (!HAMMER_POST_EFFECT.equals(client.gameRenderer.getPostProcessorId())) {
            GameRendererAccessor accessor = (GameRendererAccessor) client.gameRenderer;
            accessor.modid$setPostProcessor(HAMMER_POST_EFFECT);
            accessor.modid$setPostProcessorEnabled(true);
        }

        client.getShaderLoader().loadPostEffect(HAMMER_POST_EFFECT, HAMMER_POST_EFFECT_TARGETS);
    }

    private static void clearPostEffect(MinecraftClient client) {
        if (HAMMER_POST_EFFECT.equals(client.gameRenderer.getPostProcessorId())) {
            client.gameRenderer.clearPostProcessor();
        }
    }

    private static float strikeTime(long worldTime, ClientStrikeState state, float tickDelta) {
        if (state.strikeStartWorldTime != Long.MIN_VALUE) {
            return (float) (worldTime - state.strikeStartWorldTime) + tickDelta;
        }
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

    private static float smoothStep(float value) {
        float t = MathHelper.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float beamChargeStrength(float strikeTime, float endTick) {
        if (strikeTime < STAGE_2_BREACH_START) {
            return 0.0F;
        }
        if (strikeTime < STAGE_3_STROKE_START) {
            float t = (strikeTime - STAGE_2_BREACH_START) / (float) Math.max(1, STAGE_3_STROKE_START - STAGE_2_BREACH_START);
            return 0.2F + 0.8F * smoothStep(t);
        }
        if (strikeTime <= endTick) {
            return 1.0F;
        }
        if (strikeTime <= (endTick + BEAM_TAIL_TICKS)) {
            float t = 1.0F - ((strikeTime - endTick) / (float) BEAM_TAIL_TICKS);
            float clamped = MathHelper.clamp(t, 0.0F, 1.0F);
            return clamped * clamped;
        }
        return 0.0F;
    }

    private static float stageBeamLength(float strikeTime, int stageStart, float heightAbove, float depthBelow, float extensionEndTick) {
        float preStart = Math.max(0.0F, stageStart - STAGE_CYLINDER_PRE_TICKS);
        float aboveProgress;
        if (strikeTime <= preStart) {
            aboveProgress = 0.0F;
        } else if (strikeTime < stageStart) {
            float preDuration = Math.max(1.0F, stageStart - preStart);
            float t = MathHelper.clamp((strikeTime - preStart) / preDuration, 0.0F, 1.0F);
            float exp = STAGE_CYLINDER_ACCELERATION + (STAGE_CYLINDER_ACCELERATION_RAMP * t);
            aboveProgress = (float) Math.pow(t, exp);
        } else {
            aboveProgress = 1.0F;
        }

        float belowProgress;
        if (strikeTime < stageStart) {
            belowProgress = 0.0F;
        } else {
            float duration = Math.max(1.0F, extensionEndTick - stageStart);
            float velocityDuration = Math.max(1.0F, duration * STAGE_CYLINDER_EXTENSION_DURATION_SCALE);
            float t = (strikeTime - stageStart) / velocityDuration;
            float tCurve = Math.max(0.0F, t);
            float tRamp = MathHelper.clamp(tCurve, 0.0F, 1.0F);
            float offset = STAGE_CYLINDER_POST_OFFSET;
            float exp = STAGE_CYLINDER_ACCELERATION + (STAGE_CYLINDER_ACCELERATION_RAMP * tRamp);
            float denom = (float) Math.pow(1.0F + offset, exp) - (float) Math.pow(offset, exp);
            float eased = (float) Math.pow(tCurve + offset, exp) - (float) Math.pow(offset, exp);
            belowProgress = denom <= 0.0F ? tCurve : eased / denom;
        }

        return (heightAbove * aboveProgress) + (depthBelow * belowProgress);
    }

    private static float beamEndTick(ClientStrikeState strike) {
        if (strike.craterCompleteWorldTime == Long.MIN_VALUE || strike.strikeStartWorldTime == Long.MIN_VALUE) {
            return Float.POSITIVE_INFINITY;
        }
        return Math.max(0.0F, (float) (strike.craterCompleteWorldTime - strike.strikeStartWorldTime));
    }

    private static float extensionEndTick(ClientStrikeState strike) {
        float endTick = beamEndTick(strike);
        if (!Float.isInfinite(endTick)) {
            return endTick;
        }
        return STAGE_6_AFTERMATH_END;
    }

    private static void renderHud(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        if (!HammerConfig.clientFxHud()) {
            return;
        }

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        float collapse = MathHelper.clamp(collapseStrength * 0.55F, 0.0F, 0.65F);
        if (collapse > 0.001F) {
            int a = (int) (collapse * 255.0F);
            drawContext.fill(0, 0, w, h, (a << 24) | 0x06030C);
        }

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
            Vec3d impactWorld = strike.targetCenter;
            double distSq = impactWorld.squaredDistanceTo(cameraPos);
            if (distSq > (1024.0D * 1024.0D)) {
                continue;
            }

            float strikeTime = strikeTime(worldTime, strike, tickDelta);

            matrices.push();
            matrices.translate(impactWorld.x - cameraPos.x, impactWorld.y - cameraPos.y, impactWorld.z - cameraPos.z);

            float endTick = beamEndTick(strike);
            float extensionEndTick = extensionEndTick(strike);
            float beamCharge = beamChargeStrength(strikeTime, endTick);
            float beamScale = HammerConfig.clientFxBeamScale(strike.fxPreset);
            float shockRingScale = HammerConfig.clientFxShockRingScale(strike.fxPreset);
            if (strikeTime >= 0.0F && strikeTime <= STAGE_1_TARGETING_END) {
                renderLaser(queue, matrices, strike.targetPos, worldTime, tickDelta, beamScale);
            }
            if (strikeTime >= STAGE_2_BREACH_START && strikeTime <= (endTick + BEAM_TAIL_TICKS)) {
                float beamRenderCharge = MathHelper.clamp(beamCharge * beamScale, 0.0F, 1.5F);
                if (beamRenderCharge > 0.001F) {
                    renderHammerBeam(queue, matrices, strike.targetPos, worldTime, tickDelta, strikeTime, beamRenderCharge, endTick, extensionEndTick, strike.seed);
                }
            }
            if (strikeTime >= STAGE_5_WAVE_START && strikeTime <= STAGE_5_WAVE_END) {
                renderPressureWaveOutline(queue, matrices, strikeTime, shockRingScale);
            }
            if (strikeTime >= STAGE_6_AFTERMATH_START && strikeTime <= (STAGE_6_AFTERMATH_START + AFTERMATH_GROUND_VFX_TICKS)) {
                renderAftermathGround(queue, matrices, strikeTime, strike);
            }

            matrices.pop();
        }
    }

    private static void renderLaser(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            BlockPos targetPos,
            long worldTime,
            float tickDelta,
            float beamScale
    ) {
        if (beamScale <= 0.001F) {
            return;
        }

        int top = Math.max(targetPos.getY() + 1, LASER_TOP_Y);
        float height = top - (targetPos.getY() + 0.02F);
        if (height <= 0.5F) {
            return;
        }

        float time = (worldTime + tickDelta) / 20.0F;
        float pulse = 0.85F + 0.15F * MathHelper.sin(time * 2.4F);
        float width = (0.04F + 0.02F * pulse) * MathHelper.clamp(0.85F + 0.25F * beamScale, 0.60F, 1.45F);

        int coreAlpha = MathHelper.clamp(MathHelper.floor((170 + 70 * pulse) * beamScale), 0, 255);
        int glowAlpha = MathHelper.clamp(MathHelper.floor((70 + 50 * pulse) * beamScale), 0, 255);

        int coreColor = (coreAlpha << 24) | (LASER_COLOR_ARGB & 0x00FFFFFF);
        submitCrossBeam(queue, matrices, WHITE_TEXTURE, width, height, coreColor, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        submitCrossBeam(queue, matrices, WHITE_TEXTURE, width * 3.2F, height, argb(glowAlpha, 255, 120, 120), LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    private static void renderHammerBeam(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            BlockPos targetPos,
            long worldTime,
            float tickDelta,
            float strikeTime,
            float beamCharge,
            float endTick,
            float extensionEndTick,
            int seed
    ) {
        int top = Math.max(targetPos.getY() + 1, LASER_TOP_Y);
        float heightAbove = top - (targetPos.getY() + 0.02F);
        if (heightAbove <= 0.5F) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int bottomY = targetPos.getY() - CRATER_DEPTH_BLOCKS;
        if (client.world != null) {
            bottomY = Math.max(client.world.getBottomY(), bottomY);
        }

        float depthBelow = Math.max(0.0F, (targetPos.getY() + 0.02F) - bottomY);

        float time = (worldTime + tickDelta) / 20.0F;
        float strength = MathHelper.clamp(beamCharge, 0.0F, 1.0F);
        float flicker = 0.9F + 0.1F * MathHelper.sin(time * (8.0F + 4.0F * strength) + (seed * 0.017F) + strikeTime * 0.35F);

        float coreRadius = 0.55F;
        int coreAlpha = MathHelper.clamp(MathHelper.floor(220 + 60 * strength * flicker), 0, 255);

        for (int i = 0; i < STAGE_CYLINDER_STARTS.length; i++) {
            float beamLength = stageBeamLength(
                    strikeTime,
                    STAGE_CYLINDER_STARTS[i],
                    heightAbove,
                    depthBelow,
                    extensionEndTick
            );
            if (beamLength <= 0.5F) {
                continue;
            }

            float yOffset = heightAbove - beamLength;
            matrices.push();
            matrices.translate(0.0D, yOffset, 0.0D);

            int radiusIndex = (i == 0) ? 0 : i + 1;
            float pulse = 1.0F + STAGE_CYLINDER_PULSE * MathHelper.sin(time * (2.1F + 0.4F * radiusIndex));
            float radius = (STAGE_CYLINDER_BASE_RADIUS + (radiusIndex * STAGE_CYLINDER_RADIUS_STEP)) * pulse;

            int alpha = MathHelper.clamp(MathHelper.floor((110 + 140 * strength) * (1.0F - (radiusIndex * 0.08F))), 0, 255);
            float scroll = time * (0.25F + 0.12F * radiusIndex) + (seed * 0.007F);

            if (i == 0) {
                float crossWidth = 0.05F;
                submitCrossBeam(queue, matrices, WHITE_TEXTURE, crossWidth, beamLength, argb(coreAlpha, 255, 255, 255), LightmapTextureManager.MAX_LIGHT_COORDINATE);
                submitCylinder(queue, matrices, WHITE_TEXTURE, coreRadius, beamLength, argb(coreAlpha, 255, 255, 255), 0.0F, 0.0F, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            }

            submitCylinder(queue, matrices, WHITE_TEXTURE, radius, beamLength, argb(alpha, 255, 255, 255), scroll, 0.0F, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            submitCylinder(queue, matrices, WHITE_TEXTURE, radius * 1.08F, beamLength, argb(MathHelper.clamp(MathHelper.floor(alpha * 0.55F), 0, 255), 140, 190, 255), 0.0F, 0.0F, LightmapTextureManager.MAX_LIGHT_COORDINATE);

            matrices.pop();
        }

        renderBeamChargeDisk(queue, matrices, strength, time, seed);
    }

    private static void renderBeamChargeDisk(OrderedRenderCommandQueue queue, MatrixStack matrices, float strength, float time, int seed) {
        if (strength <= 0.01F) {
            return;
        }

        float pulse = 0.9F + 0.1F * MathHelper.sin(time * 6.0F + seed * 0.02F);
        float radius = 2.5F + 7.5F * strength;
        float outerRadius = radius * (1.0F + 0.15F * pulse);
        float innerRadius = radius * (0.35F + 0.15F * pulse);

        int outerAlpha = MathHelper.clamp(MathHelper.floor(50 + 140 * strength), 0, 255);
        int innerAlpha = MathHelper.clamp(MathHelper.floor(90 + 140 * strength * pulse), 0, 255);

        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), BEAM_CHARGE_DISK_CENTER, outerRadius, argb(outerAlpha, 255, 170, 170), 2.2F + 1.2F * strength, 72);
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), BEAM_CHARGE_DISK_CENTER, innerRadius, argb(innerAlpha, 255, 235, 235), 1.6F + 0.9F * strength, 36);
    }

    private static void renderPressureWaveOutline(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            float strikeTime,
            float presetScale
    ) {
        if (presetScale <= 0.001F) {
            return;
        }

        float p = (strikeTime - STAGE_5_WAVE_START) / (float) Math.max(1, (STAGE_5_WAVE_END - STAGE_5_WAVE_START));
        float baseRadius = MathHelper.clamp(p, 0.0F, 1.0F) * PRESSURE_WAVE_RADIUS;
        if (baseRadius <= 0.25F) {
            return;
        }

        float pulse = 0.85F + 0.15F * MathHelper.sin((strikeTime - STAGE_5_WAVE_START) * 0.65F);
        float primaryRadius = baseRadius;
        float spacingScale = MathHelper.clamp(0.85F + 0.30F * presetScale, 0.65F, 1.55F);
        float trailRadius = Math.max(0.0F, baseRadius - ((1.35F + 0.65F * pulse) * spacingScale));
        float leadRadius = baseRadius + ((0.95F + 0.55F * pulse) * spacingScale);

        int primaryAlpha = MathHelper.clamp(MathHelper.floor((120 + 80 * pulse) * presetScale), 0, 255);
        int trailAlpha = MathHelper.clamp(MathHelper.floor((95 + 60 * pulse) * presetScale), 0, 255);
        int leadAlpha = MathHelper.clamp(MathHelper.floor((70 + 45 * pulse) * presetScale), 0, 255);
        float widthScale = MathHelper.clamp(0.75F + 0.35F * presetScale, 0.55F, 1.65F);
        int primarySegments = presetScale >= 1.20F ? 128 : (presetScale <= 0.85F ? 96 : 112);
        int trailSegments = presetScale >= 1.20F ? 96 : (presetScale <= 0.85F ? 72 : 84);
        int leadSegments = presetScale >= 1.20F ? 136 : (presetScale <= 0.85F ? 104 : 120);

        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), PRESSURE_WAVE_PRIMARY_CENTER, primaryRadius, argb(primaryAlpha, 255, 244, 232), 3.4F * widthScale, primarySegments);
        if (trailRadius > 0.35F) {
            HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), PRESSURE_WAVE_TRAIL_CENTER, trailRadius, argb(trailAlpha, 220, 200, 170), 2.8F * widthScale, trailSegments);
        }
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), PRESSURE_WAVE_LEAD_CENTER, leadRadius, argb(leadAlpha, 200, 225, 255), 2.5F * widthScale, leadSegments);
    }

    private static void renderAftermathGround(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            float strikeTime,
            ClientStrikeState strike
    ) {
        float impactScale = HammerConfig.clientFxImpactScale(strike.fxPreset);
        if (impactScale <= 0.001F) {
            return;
        }

        float progress = (strikeTime - STAGE_6_AFTERMATH_START) / (float) AFTERMATH_GROUND_VFX_TICKS;
        float life = 1.0F - MathHelper.clamp(progress, 0.0F, 1.0F);
        if (life <= 0.001F) {
            return;
        }

        float pulse = 0.80F + (0.20F * MathHelper.sin(((strikeTime - STAGE_6_AFTERMATH_START) * 0.20F) + (strike.seed * 0.01F)));
        float coreRadius = (4.8F + (3.0F * impactScale)) * (1.0F + (0.06F * pulse));
        float scorchRadius = (10.0F + (7.5F * impactScale)) * (1.0F + (0.03F * pulse));

        int coreAlpha = MathHelper.clamp(MathHelper.floor(110.0F * life * impactScale), 0, 220);
        int scorchAlpha = MathHelper.clamp(MathHelper.floor(85.0F * life * impactScale), 0, 200);
        int heatAlpha = MathHelper.clamp(MathHelper.floor(55.0F * life * impactScale), 0, 170);

        float widthScale = MathHelper.clamp(0.80F + (0.30F * impactScale), 0.6F, 1.8F);
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), AFTERMATH_CORE_CENTER, coreRadius, argb(coreAlpha, 255, 190, 120), 2.8F * widthScale, 72);
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), AFTERMATH_SCORCH_CENTER, scorchRadius, argb(scorchAlpha, 210, 120, 75), 2.4F * widthScale, 92);
        HammerRenderUtil.submitCircle(queue, matrices, RenderLayers.linesTranslucent(), AFTERMATH_SCORCH_CENTER, scorchRadius * (1.08F + (0.02F * pulse)), argb(MathHelper.floor(scorchAlpha * 0.60F), 165, 95, 65), 1.9F * widthScale, 96);

        int spokes = impactScale >= 1.25F ? 10 : (impactScale <= 0.90F ? 6 : 8);
        float tau = (float) (Math.PI * 2.0D);
        for (int i = 0; i < spokes; i++) {
            float angle = ((i / (float) spokes) * tau) + (strike.seed * 0.004F) + ((strikeTime - STAGE_6_AFTERMATH_START) * (0.05F + (i * 0.004F)));
            float inner = coreRadius * (0.82F + ((i & 1) == 0 ? 0.12F : -0.06F));
            float outer = inner + ((1.3F + (0.6F * impactScale)) * life);
            Vec3d from = new Vec3d(Math.cos(angle) * inner, 0.06D, Math.sin(angle) * inner);
            Vec3d to = new Vec3d(Math.cos(angle) * outer, 0.18D + (0.45D * life), Math.sin(angle) * outer);
            HammerRenderUtil.submitLine(queue, matrices, RenderLayers.linesTranslucent(), from, to, argb(heatAlpha, 255, 140, 90), 1.3F * widthScale);
        }
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

        int segments = CYLINDER_SEGMENTS;
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(texture), (entry, vertices) -> {
            for (int i = 0; i < segments; i++) {
                float u0 = (i / (float) segments) + uScroll;
                float u1 = ((i + 1) / (float) segments) + uScroll;
                float x0 = CYLINDER_UNIT_X[i] * radius;
                float z0 = CYLINDER_UNIT_Z[i] * radius;
                float x1 = CYLINDER_UNIT_X[i + 1] * radius;
                float z1 = CYLINDER_UNIT_Z[i + 1] * radius;

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
        Vec3d targetCenter = impactCenter(BlockPos.ORIGIN);
        int seed;
        HammerConfig.ClientFxPreset fxPreset = HammerConfig.clientFxPreset();
        HammerStage stage = HammerStage.TARGETING;
        int stageStrikeTick;
        long stageStartWorldTime;
        long strikeStartWorldTime = Long.MIN_VALUE;
        long lastSeenWorldTime;
        long craterCompleteWorldTime = Long.MIN_VALUE;
        int nextAftershockIndex;
        long aftershockPulseEndWorldTime = Long.MIN_VALUE;
        float aftershockPulseStrength;

        int lastProcessedStrikeTick = Integer.MIN_VALUE;
        int lastBreachSoundTick = Integer.MIN_VALUE;
    }
}
