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
    vec2 uv = texCoord;

    // Stage 5: refraction ring (screen-space approximation).
    float ring = 0.0;
    if (RingStrength > 0.001) {
        float r = RingRadius * 0.70;
        ring = ringMask(uv, r, 0.015);

        vec2 d = uv - vec2(0.5);
        float len = max(length(d), 1.0e-5);
        vec2 dir = d / len;
        float n = hash(vec2(floor(uv.y * OutSize.y), Time * 9.7));
        float s = RingStrength * ring * (0.010 + 0.010 * n);
        uv += dir * s;
    }

    vec4 base = texture(InSampler, uv);

    // Stage 6: found-footage glitch.
    if (GlitchStrength > 0.001) {
        float scanline = floor(uv.y * OutSize.y);
        float jitter = (hash(vec2(scanline, Time * 13.1)) - 0.5) * 0.018 * GlitchStrength;
        vec4 shifted = texture(InSampler, uv + vec2(jitter, 0.0));

        float gray = dot(shifted.rgb, vec3(0.299, 0.587, 0.114));
        vec3 desat = mix(shifted.rgb, vec3(gray), GlitchStrength);

        float scan = 0.86 + 0.14 * sin((uv.y * OutSize.y) * 3.14159 + Time * 24.0);
        desat *= mix(1.0, scan, GlitchStrength);

        float staticN = (hash(uv * OutSize + vec2(Time * 71.0, Time * 19.0)) - 0.5) * 0.16 * GlitchStrength;
        desat += staticN;

        base = vec4(desat, 1.0);
    }

    fragColor = vec4(base.rgb, 1.0);
}

