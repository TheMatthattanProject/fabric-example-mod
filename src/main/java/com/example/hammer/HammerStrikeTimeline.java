package com.example.hammer;

/**
 * Shared (server+client) timing constants for the HAMMER strike sequence.
 *
 * <p>All values are measured in game ticks since strike start (tick 0).
 */
public final class HammerStrikeTimeline {
    private HammerStrikeTimeline() {
    }

    // Stage 1: targeting / acquisition. (Overlaps stage 2.)
    public static final int STAGE_1_TARGETING_START = 0;
    public static final int STAGE_1_TARGETING_END = 42;

    // Stage 2: atmospheric breach / pre-fire phenomena. (Overlaps stages 1 and 3.)
    public static final int STAGE_2_BREACH_START = 18;
    public static final int STAGE_2_BREACH_END = 54;

    // Stage 3: main beam / stroke. (Overlaps stage 2 and stage 4.)
    public static final int STAGE_3_STROKE_START = 40;
    public static final int STAGE_3_STROKE_END = 96;

    // Stage 4: kinetic eruption / core excavation. (Overlaps stage 3 and stage 5.)
    public static final int STAGE_4_ERUPTION_START = 72;
    public static final int STAGE_4_ERUPTION_END = 118;

    // Stage 5: pressure wave. (Overlaps stages 4 and 6.)
    public static final int STAGE_5_WAVE_START = 88;
    public static final int STAGE_5_WAVE_END = 150;

    // Stage 6: aftermath / signal loss / lingering effects.
    public static final int STAGE_6_AFTERMATH_START = 120;
    public static final int STAGE_6_AFTERMATH_END = 240;

    // Client-only timeline beats.
    public static final int STAGE_6_FINAL_CUT_START = 215;
    public static final int STAGE_6_FINAL_CUT_TICKS = 20;
    public static final int BEAM_TAIL_TICKS = 6;
}

