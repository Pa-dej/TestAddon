#version 150

uniform vec4 color1;
uniform vec4 color2;
uniform float radius;
uniform float thickness;
uniform float start;
uniform float end;
uniform vec2 size;
uniform vec2 location;

out vec4 fragColor;

#define PI 3.141592653589793
#define RAD 0.0174533

void main() {
    float startAngle = start * RAD;
    // НЕ клемпим end к 2π — это нужно чтобы шейдер сам обрабатывал wrap-around.
    float endAngle = startAngle + end * RAD;

    float smoothThresh = 6.0 * (1.0 / length(size));
    vec2 centerPos = ((gl_FragCoord.xy - location) / size.xy) * 2.0 - 1.0;

    float dist = length(centerPos);
    float bandAlpha = smoothstep(radius, radius + smoothThresh, dist) * smoothstep(radius + thickness, (radius + thickness) - smoothThresh, dist);
    float angle = (atan(centerPos.y, centerPos.x) + PI);

    float angleAlpha;
    if (end >= 360.0) {
        // Полное кольцо — пропускаем угловой тест (иначе шов на 0°/360°).
        angleAlpha = 1.0;
    } else if (endAngle <= PI * 2.0) {
        // Без wrap'а: pixel.angle ∈ [startAngle, endAngle].
        angleAlpha = smoothstep(angle, angle - smoothThresh, startAngle)
                   * smoothstep(angle, angle + smoothThresh, endAngle);
    } else {
        // Wrap: pixel.angle ∈ [startAngle, 2π] ∪ [0, endAngle - 2π].
        // Рендерим как ИЛИ-выражение, чтобы Java мог отправить один draw call.
        float wrappedEnd = endAngle - PI * 2.0;
        float pastStart    = smoothstep(angle, angle - smoothThresh, startAngle);
        float beforeWrapEnd = 1.0 - smoothstep(wrappedEnd, wrappedEnd + smoothThresh, angle);
        angleAlpha = max(pastStart, beforeWrapEnd);
    }

    float angle2 = (angle / PI * 180.);
    angle2 = angle2 - 360. * floor(angle2 / 360.);
    if (angle2 >= 180.) {
        angle2 = (360. - angle2) * 2.;
    } else {
        angle2 = angle2 * 2.;
    }
    fragColor = mix(color1, color2, angle2 / 360.) * bandAlpha * angleAlpha;
}
