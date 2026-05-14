package padej.testaddon.render2d;

import net.minecraft.client.MinecraftClient;

/**
 * Базовый класс 2D-рендеров SoupBetter. Сюда выносятся общие хелперы и
 * утилиты, которые могут пригодиться разным экранам — easing-функции,
 * нормализация углов, конверсия ARGB в float-каналы и т.д.
 *
 * <p>Сам по себе не рисует ничего: подклассы (см. {@link RenderRadialMenu})
 * добавляют конкретную геометрию + шейдеры + анимационное состояние.</p>
 */
public abstract class Render2D {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    // ── Easing ────────────────────────────────────────────────────────────────

    /** Smoothstep — easeInOut на t ∈ [0..1]. Используется во всех анимациях. */
    public static float easeInOut(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    // ── Angle math ────────────────────────────────────────────────────────────

    /** Нормализация угла в полуоткрытый интервал [0, 360). */
    public static float normalizeDegrees(float d) {
        float r = d % 360f;
        return r < 0 ? r + 360f : r;
    }

    // ── ARGB → float channels ─────────────────────────────────────────────────

    public static float r(int argb) { return ((argb >> 16) & 0xFF) / 255f; }
    public static float g(int argb) { return ((argb >>  8) & 0xFF) / 255f; }
    public static float b(int argb) { return ( argb        & 0xFF) / 255f; }
    public static float a(int argb) { return ((argb >>> 24) & 0xFF) / 255f; }
}
