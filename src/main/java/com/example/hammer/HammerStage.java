package com.example.hammer;

public enum HammerStage {
    TARGETING(0),
    ATMOSPHERIC_BREACH(1),
    HAMMER_STROKE(2),
    KINETIC_ERUPTION(3),
    PRESSURE_WAVE(4),
    AFTERMATH_SIGNAL_LOSS(5);

    private final int networkId;

    HammerStage(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static HammerStage fromNetworkId(int id) {
        HammerStage[] values = values();
        if (id < 0 || id >= values.length) {
            return TARGETING;
        }
        return values[id];
    }
}

