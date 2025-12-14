package com.example.hud;

public final class HudOverlayState {
    private static boolean enabled;

    private HudOverlayState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }
}

