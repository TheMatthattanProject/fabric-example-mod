package com.example.hammer.strike;

import net.minecraft.util.math.MathHelper;

/**
 * Shared (server+client) strike timeline helper.
 *
 * <p>All times are measured in game ticks since {@code startTick}.
 */
public final class StrikeController {
    public enum Phase {
        ACQUIRE,
        ALIGN,
        CHARGE,
        FIRE,
        AFTERMATH,
        DONE
    }

    public record Timing(int acquireTicks, int alignTicks, int chargeTicks, int fireTicks, int aftermathTicks) {
        public Timing {
            acquireTicks = Math.max(0, acquireTicks);
            alignTicks = Math.max(0, alignTicks);
            chargeTicks = Math.max(0, chargeTicks);
            fireTicks = Math.max(0, fireTicks);
            aftermathTicks = Math.max(0, aftermathTicks);
        }

        public int acquireEnd() {
            return acquireTicks;
        }

        public int alignEnd() {
            return acquireTicks + alignTicks;
        }

        public int chargeEnd() {
            return alignEnd() + chargeTicks;
        }

        public int fireEnd() {
            return chargeEnd() + fireTicks;
        }

        public int totalEnd() {
            return fireEnd() + aftermathTicks;
        }

        public int totalTicks() {
            return totalEnd();
        }

        public int phaseStartTick(Phase phase) {
            return switch (phase) {
                case ACQUIRE -> 0;
                case ALIGN -> acquireEnd();
                case CHARGE -> alignEnd();
                case FIRE -> chargeEnd();
                case AFTERMATH -> fireEnd();
                case DONE -> totalEnd();
            };
        }

        public int phaseDuration(Phase phase) {
            return switch (phase) {
                case ACQUIRE -> acquireTicks;
                case ALIGN -> alignTicks;
                case CHARGE -> chargeTicks;
                case FIRE -> fireTicks;
                case AFTERMATH -> aftermathTicks;
                case DONE -> 0;
            };
        }
    }

    private final Timing timing;

    public StrikeController(Timing timing) {
        this.timing = timing;
    }

    public Timing timing() {
        return timing;
    }

    public Phase phaseAt(float strikeTimeTicks) {
        float t = Math.max(0.0F, strikeTimeTicks);
        if (t < timing.acquireEnd()) {
            return Phase.ACQUIRE;
        }
        if (t < timing.alignEnd()) {
            return Phase.ALIGN;
        }
        if (t < timing.chargeEnd()) {
            return Phase.CHARGE;
        }
        if (t < timing.fireEnd()) {
            return Phase.FIRE;
        }
        if (t < timing.totalEnd()) {
            return Phase.AFTERMATH;
        }
        return Phase.DONE;
    }

    public float timeInPhase(float strikeTimeTicks) {
        float t = Math.max(0.0F, strikeTimeTicks);
        Phase phase = phaseAt(t);
        return t - timing.phaseStartTick(phase);
    }

    public float phaseProgress(float strikeTimeTicks) {
        Phase phase = phaseAt(strikeTimeTicks);
        int duration = timing.phaseDuration(phase);
        if (duration <= 0) {
            return 1.0F;
        }
        return MathHelper.clamp(timeInPhase(strikeTimeTicks) / (float) duration, 0.0F, 1.0F);
    }

    public float overallProgress(float strikeTimeTicks) {
        int total = Math.max(1, timing.totalTicks());
        return MathHelper.clamp(Math.max(0.0F, strikeTimeTicks) / (float) total, 0.0F, 1.0F);
    }
}

