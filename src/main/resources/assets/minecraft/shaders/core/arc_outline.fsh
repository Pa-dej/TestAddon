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
    // Точно та же угловая система, что в arc.fsh: angle = atan2(y,x) + π.
    // Это гарантирует, что обводка ляжет ровно на края заливки и работает
    // для любого start/sweep без зависимости от того, в каком квадранте сектор.
    float startAngle = start * RAD;
    float endAngle = startAngle + end * RAD;
    float smoothThresh = 6.0 * (1.0 / length(size));
    vec2 centerPos = ((gl_FragCoord.xy - location) / size.xy) * 2.0 - 1.0;
    float dist = length(centerPos);
    float angle = atan(centerPos.y, centerPos.x) + PI;

    float innerR = radius;
    float outerR = radius + thickness;

    // Маска формы сектора (band × angle), идентичная arc.fsh — включая
    // обработку wrap-around (sweep, чьё endAngle > 2π).
    float bandAlpha = smoothstep(radius, radius + smoothThresh, dist)
                    * smoothstep(radius + thickness, (radius + thickness) - smoothThresh, dist);

    float angleAlpha;
    if (end >= 360.0) {
        angleAlpha = 1.0;
    } else if (endAngle <= PI * 2.0) {
        angleAlpha = smoothstep(angle, angle - smoothThresh, startAngle)
                   * smoothstep(angle, angle + smoothThresh, endAngle);
    } else {
        // AA на обоих рёбрах ВНУТРЬ сектора — симметрично с не-wrap веткой.
        // Иначе end-сторона маски "вытекает" наружу на smoothThresh и
        // визуально утолщает обводку end-радиали в wrap-случае
        // (предпоследний сектор при n=5,6,7).
        float wrappedEnd = endAngle - PI * 2.0;
        float pastStart    = smoothstep(angle, angle - smoothThresh, startAngle);
        float beforeWrapEnd = 1.0 - smoothstep(wrappedEnd - smoothThresh, wrappedEnd, angle);
        angleAlpha = max(pastStart, beforeWrapEnd);
    }
    float maskAlpha = bandAlpha * angleAlpha;

    // Дистанции до рёбер в нормализованных (≈пиксельных) единицах:
    // — до дуг: |dist - radius|
    // — до радиали под углом θ: |dist · sin(angle - θ)|
    //   (это перпендикулярное screen-space расстояние от пикселя до луча
    //   радиали; одинаковое на любом радиусе → толщина обводки равная).
    float distInnerArc = abs(dist - innerR);
    float distOuterArc = abs(dist - outerR);
    float distBandEdge = min(distInnerArc, distOuterArc);

    float distRadialEdge;
    if (end >= 360.0) {
        distRadialEdge = 1.0;
    } else {
        float distRadialStart = abs(dist * sin(angle - startAngle));
        float distRadialEnd   = abs(dist * sin(angle - endAngle));
        // Радиаль — это ЛУЧ из центра, а не вся линия. На «противоположной»
        // стороне (angle ≈ startAngle ± π) sin = 0, но это уже не та радиаль.
        // cos(angle - θ) > 0 означает «по ту же сторону от центра, что и луч».
        // Если cos < 0 — пиксель напротив, дистанцию до этого луча не учитываем.
        if (cos(angle - startAngle) < 0.0) distRadialStart = 1.0;
        if (cos(angle - endAngle)   < 0.0) distRadialEnd   = 1.0;
        distRadialEdge = min(distRadialStart, distRadialEnd);
    }

    float distAnyEdge = min(distBandEdge, distRadialEdge);

    float halfW = outlineWidth * 0.5;
    float outlineAlpha = 1.0 - smoothstep(halfW - smoothThresh, halfW + smoothThresh, distAnyEdge);
    outlineAlpha *= maskAlpha;

    if (outlineAlpha <= 0.0) discard;

    fragColor = vec4(color1.rgb, color1.a * outlineAlpha);
}
