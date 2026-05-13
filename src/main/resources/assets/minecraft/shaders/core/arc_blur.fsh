#version 150

uniform vec4 color1;
uniform float radius;
uniform float thickness;
uniform float start;
uniform float end;
uniform vec2 size;
uniform vec2 location;

uniform sampler2D InputSampler;
uniform vec2 InputResolution;
uniform float Quality;

out vec4 fragColor;

#define PI 3.141592653589793
#define RAD 0.0174533
#define TAU 6.28318530718

// 16 направлений × 5 радиусов = 80 семплов — то же что в core/blur.fsh.
vec4 blurSample() {
    vec2 Radius = Quality / InputResolution.xy;
    vec2 uv = gl_FragCoord.xy / InputResolution.xy;
    vec4 blur = texture(InputSampler, uv);

    float step = TAU / 16.0;
    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            blur += texture(InputSampler, uv + vec2(cos(d), sin(d)) * Radius * i);
        }
    }
    blur /= 80.0;
    return blur;
}

void main() {
    float startAngle = start * RAD;
    float endAngle = startAngle + end * RAD;

    float smoothThresh = 6.0 * (1.0 / length(size));
    vec2 centerPos = ((gl_FragCoord.xy - location) / size.xy) * 2.0 - 1.0;

    float dist = length(centerPos);
    float bandAlpha = smoothstep(radius, radius + smoothThresh, dist)
                    * smoothstep(radius + thickness, (radius + thickness) - smoothThresh, dist);
    float angle = (atan(centerPos.y, centerPos.x) + PI);

    float angleAlpha;
    if (end >= 360.0) {
        angleAlpha = 1.0;
    } else if (endAngle <= PI * 2.0) {
        angleAlpha = smoothstep(angle, angle - smoothThresh, startAngle)
                   * smoothstep(angle, angle + smoothThresh, endAngle);
    } else {
        float wrappedEnd = endAngle - PI * 2.0;
        float pastStart    = smoothstep(angle, angle - smoothThresh, startAngle);
        float beforeWrapEnd = 1.0 - smoothstep(wrappedEnd, wrappedEnd + smoothThresh, angle);
        angleAlpha = max(pastStart, beforeWrapEnd);
    }

    float maskAlpha = bandAlpha * angleAlpha;
    if (maskAlpha <= 0.0) discard;

    // Композ: блюр-фон + color1 (полупрозрачная заливка) сверху.
    vec4 bg = blurSample();
    vec3 composed = bg.rgb * (1.0 - color1.a) + color1.rgb * color1.a;
    fragColor = vec4(composed, maskAlpha);
}
