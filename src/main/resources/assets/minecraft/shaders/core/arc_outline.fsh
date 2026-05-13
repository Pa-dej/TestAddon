#version 150

uniform vec4 color1;
uniform float radius;
uniform float thickness;
uniform float start;
uniform float end;
uniform vec2 size;
uniform vec2 location;
uniform float outlineWidth;

out vec4 fragColor;

#define PI 3.141592653589793
#define RAD 0.0174533

void main() {
    // Сдвиг -π совпадает с системой координат arc.fsh: там
    // angle = atan2(y,x) + π. Без этого outline рисуется на 180°
    // от собственного сектора заливки.
    float startAngle = (start - 180.0) * RAD;
    float endAngle = startAngle + end * RAD;
    float smoothThresh = 6.0 * (1.0 / length(size));
    vec2 centerPos = ((gl_FragCoord.xy - location) / size.xy) * 2.0 - 1.0;
    float dist = length(centerPos);

    float innerR = radius;
    float outerR = radius + thickness;

    // маска формы сектора (band × angle), как в arc.fsh
    float bandAlpha = smoothstep(0.0, smoothThresh, dist - innerR)
                    * smoothstep(0.0, smoothThresh, outerR - dist);

    float angleAlpha;
    float dotPerpStart = 0.0;
    float dotPerpEnd = 0.0;
    if (end >= 360.0) {
        angleAlpha = 1.0;
    } else {
        dotPerpStart = centerPos.x * (-sin(startAngle)) + centerPos.y * cos(startAngle);
        dotPerpEnd   = centerPos.x * (-sin(endAngle))   + centerPos.y * cos(endAngle);
        angleAlpha = smoothstep(0.0, smoothThresh, dotPerpStart)
                   * smoothstep(0.0, smoothThresh, -dotPerpEnd);
    }

    float maskAlpha = bandAlpha * angleAlpha;

    // дистанции до каждого ребра в пиксельных единицах
    float distInnerArc = abs(dist - innerR);
    float distOuterArc = abs(dist - outerR);
    float distBandEdge = min(distInnerArc, distOuterArc);

    float distRadialEdge;
    if (end >= 360.0) {
        distRadialEdge = 1.0;
    } else {
        float distRadialStart = abs(dotPerpStart);
        float distRadialEnd   = abs(dotPerpEnd);
        distRadialEdge = min(distRadialStart, distRadialEnd);
    }

    float distAnyEdge = min(distBandEdge, distRadialEdge);

    float halfW = outlineWidth * 0.5;
    float outlineAlpha = 1.0 - smoothstep(halfW - smoothThresh, halfW + smoothThresh, distAnyEdge);
    outlineAlpha *= maskAlpha;

    if (outlineAlpha <= 0.0) discard;

    fragColor = vec4(color1.rgb, color1.a * outlineAlpha);
}
