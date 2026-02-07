package com.example.hammer;

import com.example.ExampleMod;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * JVM property-driven config for the HAMMER orbital strike.
 *
 * <p>Example: {@code -Dmodid.hammer.blockDamage=false -Dmodid.hammer.maxRadius=8}
 */
public final class HammerConfig {
    private static final String PREFIX = ExampleMod.MOD_ID + ".hammer.";

    private static final int COOLDOWN_TICKS = HammerStrikeTimeline.STAGE_6_AFTERMATH_END + 1;
    private static final int MIN_CHARGE_TICKS = readInt("minChargeTicks", 10, 0, 20 * 10);
    private static final int RANGE_BLOCKS = readInt("rangeBlocks", 2048, 8, 2048);

    private static final boolean BLOCK_DAMAGE = readBoolean("blockDamage", true);
    private static final BlockDamageMode BLOCK_DAMAGE_MODE = readBlockDamageMode("blockDamageMode", BlockDamageMode.CRUST);
    private static final boolean FIRE_SPREAD = readBoolean("fireSpread", true);
    private static final int MAX_RADIUS = readInt("maxRadius", 12, 1, 64);
    private static final int MAX_DURATION_TICKS = readInt("maxDuration", 140, 20 * 3, 20 * 20);

    private static final int ACQUIRE_TICKS = readInt("acquireTicks", 20, 0, 20 * 10);
    private static final int ALIGN_TICKS = readInt("alignTicks", 90, 0, 20 * 20);
    private static final int WARNING_TICKS = readInt("warningTicks", 14, 0, 20 * 10);
    private static final int AFTER_TICKS = readInt("afterTicks", 240, 0, 20 * 40);

    private static final float DAMAGE_PER_TICK_CENTER = readFloat("damagePerTickCenter", 0.9F, 0.0F, 100.0F);
    private static final float DAMAGE_PER_TICK_EDGE = readFloat("damagePerTickEdge", 0.25F, 0.0F, 100.0F);
    private static final float KNOCKBACK_STRENGTH = readFloat("knockbackStrength", 0.65F, 0.0F, 10.0F);

    private static final boolean DISINTEGRATE_BLOCKS = readBoolean("disintegrateBlocks", false);
    private static final int DISINTEGRATE_RADIUS = readInt("disintegrateRadius", 3, 0, 16);
    private static final int BLOCKS_PER_TICK = readInt("blocksPerTick", 80, 1, 10_000);
    private static final int DISINTEGRATE_BLOCKS_PER_TICK = readInt("disintegrateBlocksPerTick", 18, 0, 10_000);

    @Nullable
    private static final Identifier AMMO_ITEM = readIdentifier("ammoItem");
    private static final int AMMO_COST = readInt("ammoCost", 0, 0, 64);

    private static final boolean CLIENT_FX_ENABLED = readBoolean("clientFx", true);
    private static final ClientFxPreset CLIENT_FX_PRESET = readClientFxPreset("clientFxPreset", ClientFxPreset.CINEMATIC);
    private static final int CLIENT_FX_NEAR_DISTANCE = readInt("clientFxNearDistance", 96, 16, 1024);
    private static final int CLIENT_FX_FAR_DISTANCE = readInt("clientFxFarDistance", 256, 32, 2048);

    private static final boolean CLIENT_FX_BEAM = readBoolean("clientFxBeam", true);
    private static final boolean CLIENT_FX_PLATFORM = readBoolean("clientFxPlatform", true);
    private static final boolean CLIENT_FX_SPIRAL = readBoolean("clientFxSpiral", true);
    private static final boolean CLIENT_FX_IMPACT_BLOOM = readBoolean("clientFxImpactBloom", true);
    private static final boolean CLIENT_FX_SHOCK_RING = readBoolean("clientFxShockRing", true);
    private static final boolean CLIENT_FX_HEAT_HAZE = readBoolean("clientFxHeatHaze", true);

    private static final boolean CLIENT_FX_HUD = readBoolean("clientFxHud", true);
    private static final boolean CLIENT_FX_PARTICLES = readBoolean("clientFxParticles", true);
    private static final int CLIENT_FX_PARTICLES_NEAR_BUDGET = readInt("clientFxParticlesNearBudget", 260, 0, 5_000);
    private static final int CLIENT_FX_PARTICLES_MID_BUDGET = readInt("clientFxParticlesMidBudget", 120, 0, 5_000);
    private static final int CLIENT_FX_PARTICLES_FAR_BUDGET = readInt("clientFxParticlesFarBudget", 35, 0, 5_000);

    private HammerConfig() {
    }

    public static int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    public static int minChargeTicks() {
        return MIN_CHARGE_TICKS;
    }

    public static int rangeBlocks() {
        return RANGE_BLOCKS;
    }

    public static boolean blockDamage() {
        return BLOCK_DAMAGE;
    }

    public static BlockDamageMode blockDamageMode() {
        if (!BLOCK_DAMAGE) {
            return BlockDamageMode.NONE;
        }
        return BLOCK_DAMAGE_MODE;
    }

    public static boolean fireSpread() {
        return FIRE_SPREAD;
    }

    public static int maxRadius() {
        return MAX_RADIUS;
    }

    public static int maxDurationTicks() {
        return MAX_DURATION_TICKS;
    }

    public static int acquireTicks() {
        return ACQUIRE_TICKS;
    }

    public static int alignTicks() {
        return ALIGN_TICKS;
    }

    public static int warningTicks() {
        return WARNING_TICKS;
    }

    public static int afterTicks() {
        return AFTER_TICKS;
    }

    public static float damagePerTickCenter() {
        return DAMAGE_PER_TICK_CENTER;
    }

    public static float damagePerTickEdge() {
        return DAMAGE_PER_TICK_EDGE;
    }

    public static float knockbackStrength() {
        return KNOCKBACK_STRENGTH;
    }

    public static boolean disintegrateBlocks() {
        return DISINTEGRATE_BLOCKS;
    }

    public static int disintegrateRadius() {
        return DISINTEGRATE_RADIUS;
    }

    public static int blocksPerTick() {
        return BLOCKS_PER_TICK;
    }

    public static int disintegrateBlocksPerTick() {
        return DISINTEGRATE_BLOCKS_PER_TICK;
    }

    @Nullable
    public static Identifier ammoItem() {
        return AMMO_ITEM;
    }

    public static int ammoCost() {
        return AMMO_COST;
    }

    public static int totalSequenceTicks() {
        return ACQUIRE_TICKS + ALIGN_TICKS + WARNING_TICKS + MAX_DURATION_TICKS + AFTER_TICKS;
    }

    public static boolean clientFxEnabled() {
        return CLIENT_FX_ENABLED;
    }

    public static int clientFxNearDistance() {
        return CLIENT_FX_NEAR_DISTANCE;
    }

    public static int clientFxFarDistance() {
        return CLIENT_FX_FAR_DISTANCE;
    }

    public static ClientFxPreset clientFxPreset() {
        return CLIENT_FX_PRESET;
    }

    public static float clientFxBeamScale() {
        return clientFxBeamScale(CLIENT_FX_PRESET);
    }

    public static float clientFxBeamScale(ClientFxPreset preset) {
        return (CLIENT_FX_ENABLED && CLIENT_FX_BEAM) ? preset.beamScale : 0.0F;
    }

    public static float clientFxCollapseScale() {
        return clientFxCollapseScale(CLIENT_FX_PRESET);
    }

    public static float clientFxCollapseScale(ClientFxPreset preset) {
        return (CLIENT_FX_ENABLED && CLIENT_FX_IMPACT_BLOOM) ? preset.collapseScale : 0.0F;
    }

    public static float clientFxImpactScale() {
        return clientFxImpactScale(CLIENT_FX_PRESET);
    }

    public static float clientFxImpactScale(ClientFxPreset preset) {
        return (CLIENT_FX_ENABLED && CLIENT_FX_IMPACT_BLOOM) ? preset.impactScale : 0.0F;
    }

    public static float clientFxShockRingScale() {
        return clientFxShockRingScale(CLIENT_FX_PRESET);
    }

    public static float clientFxShockRingScale(ClientFxPreset preset) {
        return (CLIENT_FX_ENABLED && CLIENT_FX_SHOCK_RING) ? preset.shockRingScale : 0.0F;
    }

    public static float clientFxHeatHazeScale() {
        return clientFxHeatHazeScale(CLIENT_FX_PRESET);
    }

    public static float clientFxHeatHazeScale(ClientFxPreset preset) {
        return (CLIENT_FX_ENABLED && CLIENT_FX_HEAT_HAZE) ? preset.heatHazeScale : 0.0F;
    }

    public static float clientFxHudScale() {
        return clientFxHudScale(CLIENT_FX_PRESET);
    }

    public static float clientFxHudScale(ClientFxPreset preset) {
        return (CLIENT_FX_ENABLED && CLIENT_FX_HUD) ? preset.hudScale : 0.0F;
    }

    public static boolean clientFxBeam() {
        return CLIENT_FX_ENABLED && CLIENT_FX_BEAM;
    }

    public static boolean clientFxPlatform() {
        return CLIENT_FX_ENABLED && CLIENT_FX_PLATFORM;
    }

    public static boolean clientFxSpiral() {
        return CLIENT_FX_ENABLED && CLIENT_FX_SPIRAL;
    }

    public static boolean clientFxImpactBloom() {
        return CLIENT_FX_ENABLED && CLIENT_FX_IMPACT_BLOOM;
    }

    public static boolean clientFxShockRing() {
        return CLIENT_FX_ENABLED && CLIENT_FX_SHOCK_RING;
    }

    public static boolean clientFxHeatHaze() {
        return CLIENT_FX_ENABLED && CLIENT_FX_HEAT_HAZE;
    }

    public static boolean clientFxHud() {
        return CLIENT_FX_ENABLED && CLIENT_FX_HUD;
    }

    public static boolean clientFxParticles() {
        return CLIENT_FX_ENABLED && CLIENT_FX_PARTICLES;
    }

    public static int clientFxParticlesNearBudget() {
        return CLIENT_FX_PARTICLES_NEAR_BUDGET;
    }

    public static int clientFxParticlesMidBudget() {
        return CLIENT_FX_PARTICLES_MID_BUDGET;
    }

    public static int clientFxParticlesFarBudget() {
        return CLIENT_FX_PARTICLES_FAR_BUDGET;
    }

    private static boolean readBoolean(String suffix, boolean defaultValue) {
        String raw = System.getProperty(PREFIX + suffix);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private static int readInt(String suffix, int defaultValue, int min, int max) {
        String raw = System.getProperty(PREFIX + suffix);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return MathHelper.clamp(Integer.parseInt(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static float readFloat(String suffix, float defaultValue, float min, float max) {
        String raw = System.getProperty(PREFIX + suffix);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return MathHelper.clamp(Float.parseFloat(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Nullable
    private static Identifier readIdentifier(String suffix) {
        String raw = System.getProperty(PREFIX + suffix);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Identifier.tryParse(raw.trim());
    }

    private static BlockDamageMode readBlockDamageMode(String suffix, BlockDamageMode defaultValue) {
        String raw = System.getProperty(PREFIX + suffix);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "none", "off", "false" -> BlockDamageMode.NONE;
            case "scorch", "surface" -> BlockDamageMode.SCORCH;
            case "crust", "default" -> BlockDamageMode.CRUST;
            default -> defaultValue;
        };
    }

    private static ClientFxPreset readClientFxPreset(String suffix, ClientFxPreset defaultValue) {
        String raw = System.getProperty(PREFIX + suffix);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return parseClientFxPreset(raw, defaultValue);
    }

    public static ClientFxPreset parseClientFxPreset(String raw, ClientFxPreset defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "subtle", "low", "lite" -> ClientFxPreset.SUBTLE;
            case "cinematic", "default", "normal" -> ClientFxPreset.CINEMATIC;
            case "apocalyptic", "extreme", "high", "max" -> ClientFxPreset.APOCALYPTIC;
            default -> defaultValue;
        };
    }

    public enum ClientFxPreset {
        SUBTLE(0, "subtle", 0.78F, 0.82F, 0.80F, 0.72F, 0.78F, 0.84F),
        CINEMATIC(1, "cinematic", 1.00F, 1.00F, 1.00F, 1.00F, 1.00F, 1.00F),
        APOCALYPTIC(2, "apocalyptic", 1.28F, 1.40F, 1.35F, 1.42F, 1.40F, 1.22F);

        private final int networkId;
        private final String id;
        private final float beamScale;
        private final float collapseScale;
        private final float impactScale;
        private final float shockRingScale;
        private final float heatHazeScale;
        private final float hudScale;

        ClientFxPreset(
                int networkId,
                String id,
                float beamScale,
                float collapseScale,
                float impactScale,
                float shockRingScale,
                float heatHazeScale,
                float hudScale
        ) {
            this.networkId = networkId;
            this.id = id;
            this.beamScale = beamScale;
            this.collapseScale = collapseScale;
            this.impactScale = impactScale;
            this.shockRingScale = shockRingScale;
            this.heatHazeScale = heatHazeScale;
            this.hudScale = hudScale;
        }

        public int networkId() {
            return networkId;
        }

        public String id() {
            return id;
        }

        public static ClientFxPreset fromNetworkId(int id) {
            for (ClientFxPreset value : values()) {
                if (value.networkId == id) {
                    return value;
                }
            }
            return CINEMATIC;
        }
    }

    public enum BlockDamageMode {
        NONE,
        SCORCH,
        CRUST
    }
}
