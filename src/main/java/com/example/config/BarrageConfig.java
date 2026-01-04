package com.example.config;

import com.example.ExampleMod;
import net.minecraft.util.math.MathHelper;

public final class BarrageConfig {
    private static final String PREFIX = ExampleMod.MOD_ID + ".barrage.";

    private static final int MAX_LEVEL = readInt("maxLevel", 3, 1, 255);
    private static final int MAX_TOTAL_ARROWS_CAP = readInt("maxTotalArrowsCap", 1024, 1, 1024);

    private static final float HORIZONTAL_SPREAD_DEGREES = readFloat("horizontalSpreadDegrees", 30.0F, 0.0F, 180.0F);
    private static final float VERTICAL_SPREAD_DEGREES = readFloat("verticalSpreadDegrees", 18.0F, 0.0F, 180.0F);
    private static final float SPEED_VARIANCE = readFloat("speedVariance", 0.3F, 0.0F, 5.0F);

    private static final float SPAWN_FORWARD_OFFSET = readFloat("spawnForwardOffset", 0.25F, 0.0F, 5.0F);

    private BarrageConfig() {
    }

    public static int maxLevel() {
        return MAX_LEVEL;
    }

    public static int maxTotalArrowsCap() {
        return MAX_TOTAL_ARROWS_CAP;
    }

    public static float horizontalSpreadDegrees() {
        return HORIZONTAL_SPREAD_DEGREES;
    }

    public static float verticalSpreadDegrees() {
        return VERTICAL_SPREAD_DEGREES;
    }

    public static float speedVariance() {
        return SPEED_VARIANCE;
    }

    public static float spawnForwardOffset() {
        return SPAWN_FORWARD_OFFSET;
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
}
