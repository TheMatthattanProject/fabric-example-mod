#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform HammerConfig {
    float Time;
    float RingRadius;
    float RingStrength;
    float GlitchStrength;
    float BeamStrength;
    float CollapseStrength;
    float HeatShimmerStrength;
};

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453123);
}

float ringMask(vec2 uv, float r, float w) {
    float d = length(uv - vec2(0.5));
    float a = smoothstep(r - w, r, d);
    float b = smoothstep(r, r + w, d);
    return clamp(a - b, 0.0, 1.0);
}

void main() {
    vec2 screenUv = texCoord;
    vec2 uv = screenUv;

    float ringStrength = clamp(RingStrength, 0.0, 1.0);
    float beam = clamp(BeamStrength, 0.0, 1.0);
    float collapse = clamp(CollapseStrength, 0.0, 1.0);
    float heat = clamp(HeatShimmerStrength, 0.0, 1.0);

    float ringPrimary = 0.0;
    float ringTrail = 0.0;
    float ringLead = 0.0;
    float ringComposite = 0.0;

    vec2 d = screenUv - vec2(0.5);
    float len = max(length(d), 1.0e-5);
    vec2 dir = d / len;

    // Stage 5: stacked refraction ring with a trailing dust shell and leading edge.
    if (ringStrength > 0.001) {
        float r = RingRadius * 0.70;
        ringPrimary = ringMask(screenUv, r, 0.014);
        ringTrail = ringMask(screenUv, max(0.0, r - 0.022), 0.020);
        ringLead = ringMask(screenUv, r + 0.016, 0.012);
        ringComposite = clamp((ringPrimary * 1.00) + (ringTrail * 0.65) + (ringLead * 0.50), 0.0, 1.0);

        float n = hash(vec2(floor(screenUv.y * OutSize.y), Time * 9.7));
        float s = ringStrength * ringComposite * (0.008 + 0.010 * n);
        uv += dir * s;
    }

    // Beam heat distortion and wave-edge shimmer.
    if (heat > 0.001) {
        float n = hash(screenUv * OutSize + vec2(Time * 43.0, Time * 17.0));
        float centerFalloff = 1.0 - smoothstep(0.06, 0.72, len);
        float ripple = sin((screenUv.y * OutSize.y) * 0.050 + Time * 18.0 + n * 6.2831853);
        float shimmer = (ripple + (n - 0.5) * 1.2) * 0.0028 * heat;
        float ringEdgeBoost = ringStrength * ringComposite * 0.0020;
        uv += vec2(shimmer * (0.45 + centerFalloff), -shimmer * 0.32) + (dir * ringEdgeBoost);
    }

    vec4 base = texture(InSampler, uv);

    // Pre-ignition atmospheric collapse.
    if (collapse > 0.001) {
        float vignette = 1.0 - smoothstep(0.20, 1.00, len);
        float horizon = clamp((screenUv.y - 0.10) * 1.35, 0.0, 1.0);
        float darken = collapse * (0.24 + 0.56 * vignette) * horizon;
        vec3 tint = vec3(0.03, 0.01, 0.06);
        base.rgb = (base.rgb * (1.0 - darken)) + (tint * collapse * 0.14);
    }

    if (ringStrength > 0.001) {
        float ringLight = ringStrength * ((ringPrimary * 0.20) + (ringTrail * 0.09) + (ringLead * 0.06));
        base.rgb += vec3(ringLight * 1.10, ringLight * 0.95, ringLight * 0.85);
    }

    // Stage 6: found-footage glitch.
    if (GlitchStrength > 0.001) {
        float scanline = floor(screenUv.y * OutSize.y);
        float jitter = (hash(vec2(scanline, Time * 13.1)) - 0.5) * 0.018 * GlitchStrength;
        vec4 shifted = texture(InSampler, uv + vec2(jitter, 0.0));

        float gray = dot(shifted.rgb, vec3(0.299, 0.587, 0.114));
        vec3 desat = mix(shifted.rgb, vec3(gray), GlitchStrength);

        float scan = 0.86 + 0.14 * sin((screenUv.y * OutSize.y) * 3.14159 + Time * 24.0);
        desat *= mix(1.0, scan, GlitchStrength);

        float staticN = (hash(screenUv * OutSize + vec2(Time * 71.0, Time * 19.0)) - 0.5) * 0.16 * GlitchStrength;
        desat += staticN;

        base = vec4(desat, 1.0);
    }

    if (beam > 0.001) {
        float glow = 0.08 + 0.18 * beam;
        float shimmer = sin((screenUv.y * OutSize.y) * 0.012 + Time * 8.0) * 0.02 * beam;
        base.rgb = min(base.rgb + vec3(glow + shimmer, glow * 0.8, glow * 0.8), vec3(1.0));
    }

    if (heat > 0.001) {
        float thermalN = (hash(screenUv * OutSize + vec2(Time * 47.0, Time * 23.0)) - 0.5) * 0.06 * heat;
        base.rgb += vec3(thermalN * 0.8, thermalN * 0.55, thermalN * 0.45);
    }

    fragColor = vec4(clamp(base.rgb, 0.0, 1.0), 1.0);
}

