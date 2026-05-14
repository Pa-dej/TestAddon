package padej.testaddon.render2d;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL13;
import padej.soup.api.system.shape.implement.Blur;

import java.util.List;

/**
 * Рендерер радиального меню. Содержит:
 * <ul>
 *     <li>Шейдер-программы (arc / arc_blur / arc_outline) и геометрические
 *     константы (внешний/внутренний радиус, hover-расширение, gap).</li>
 *     <li>Анимационное состояние (open/close прогресс, per-sector hover).</li>
 *     <li>Helper Blur из SoupVisuals для захвата framebuffer'а под заливку.</li>
 *     <li>Метод {@link #render} с полной отрисовкой кадра.</li>
 * </ul>
 *
 * <p>Экран ({@link padej.testaddon.modules.gameplay.AutoSwapRadialScreen})
 * хранит только доменное состояние (slots, hovered, holdKey, module) и
 * делегирует рисование сюда. Это позволяет переиспользовать рендерер для
 * других радиальных меню в будущем.</p>
 */
public class RenderRadialMenu extends Render2D {

    // ── Шейдеры ───────────────────────────────────────────────────────────────

    private static final ShaderProgramKey ARC_BLUR_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("core/arc_blur"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );

    private static final ShaderProgramKey ARC_OUTLINE_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("core/arc_outline"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );

    // ── Геометрия ─────────────────────────────────────────────────────────────

    public static final float OUTER_RADIUS = 92f;
    public static final float INNER_RADIUS = 54f;
    /** Максимально возможное расширение наведённого сектора (px).
     *  Используется как margin для квада arc-шейдера. */
    public static final float MAX_HOVER_EXPAND = 16f;
    /** Угловой зазор между секторами (градусы). */
    public static final float GAP_DEG = 1f;

    // ── Таймминги анимаций (мс) ───────────────────────────────────────────────

    public static final int OPEN_MS  = 180;
    public static final int CLOSE_MS = 180;
    public static final int HOVER_MS = 140;

    // ── Состояние ─────────────────────────────────────────────────────────────

    /** Blur-helper из SoupVisuals — используем для setup()/input
     *  (захват framebuffer'а). Сам рендер делает наш arc_blur шейдер. */
    private final Blur blurHelper = new Blur();

    private final int sectorCount;
    private final long openedAt;
    private long closeStartedAt = -1;
    private boolean closing = false;

    /** Per-sector hover-интерполяция: время последней смены состояния
     *  и значение прогресса в момент той смены — чтобы анимация продолжалась
     *  плавно даже если hover переключился до завершения предыдущей. */
    private final long[]    hoverChangedAt;
    private final boolean[] hoverActive;
    private final float[]   hoverProgressAtChange;

    /** Центр меню — обновляется на каждом render() из размеров экрана. */
    private float centerX, centerY;

    public RenderRadialMenu(int sectorCount) {
        this.sectorCount = sectorCount;
        this.openedAt = System.currentTimeMillis();
        this.hoverChangedAt        = new long[sectorCount];
        this.hoverActive           = new boolean[sectorCount];
        this.hoverProgressAtChange = new float[sectorCount];
        long now = System.currentTimeMillis();
        for (int i = 0; i < sectorCount; i++) hoverChangedAt[i] = now - HOVER_MS;
    }

    // ── Animation API ─────────────────────────────────────────────────────────

    /** Прогресс открытия 0..1 с учётом close-анимации (тогда идёт обратно). */
    public float openProgress() {
        long now = System.currentTimeMillis();
        if (closeStartedAt >= 0) {
            long elapsed = now - closeStartedAt;
            if (elapsed >= CLOSE_MS) return 0f;
            return 1f - easeInOut((float) elapsed / CLOSE_MS);
        }
        long elapsed = now - openedAt;
        return easeInOut((float) elapsed / OPEN_MS);
    }

    /** True если close-анимация полностью отыграла. */
    public boolean closeFinished() {
        return closing && System.currentTimeMillis() - closeStartedAt >= CLOSE_MS;
    }

    public boolean isClosing() { return closing; }

    /** Запускает обратную анимацию. {@code currentOpen} — текущий
     *  {@link #openProgress()}, нужен чтобы close не «прыгнул» если меню
     *  закрылось до окончания открытия. */
    public void startClose() {
        if (closing) return;
        closing = true;
        float currentOpen = openProgress();
        closeStartedAt = System.currentTimeMillis()
                - (long) ((1f - currentOpen) * CLOSE_MS);
        // Сектора тоже «сдуются» — переводим все в not-hovered.
        for (int i = 0; i < sectorCount; i++) setSectorHovered(i, false);
    }

    /** Прогресс расширения для сектора i, 0..1. */
    public float hoverProgress(int i) {
        long now = System.currentTimeMillis();
        long elapsed = now - hoverChangedAt[i];
        float t = easeInOut(Math.min(1f, (float) elapsed / HOVER_MS));
        if (hoverActive[i]) {
            return hoverProgressAtChange[i] + (1f - hoverProgressAtChange[i]) * t;
        } else {
            return hoverProgressAtChange[i] * (1f - t);
        }
    }

    /** Обновляет состояние hover'а конкретного сектора, фиксируя текущий
     *  прогресс как стартовое значение для новой интерполяции. */
    public void setSectorHovered(int i, boolean v) {
        if (hoverActive[i] == v) return;
        hoverProgressAtChange[i] = hoverProgress(i);
        hoverChangedAt[i] = System.currentTimeMillis();
        hoverActive[i] = v;
    }

    // ── Hit-test ──────────────────────────────────────────────────────────────

    /**
     * По координатам мыши + размерам экрана возвращает индекс сектора под
     * курсором, или -1 для внутреннего «мёртвого пятна».
     * Внешней границы нет — резкие движения за пределы кольца всё равно
     * регистрируются по углу.
     */
    public int getHoverIndex(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        float cx = screenWidth  / 2f;
        float cy = screenHeight / 2f;
        float dx = (float) (mouseX - cx);
        float dy = (float) (mouseY - cy);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER_RADIUS) return -1;

        double sweep = Math.PI * 2 / sectorCount;
        double ang = Math.atan2(dy, dx) + Math.PI / 2;
        double twoPi = Math.PI * 2;
        ang = ((ang % twoPi) + twoPi) % twoPi;
        int idx = (int) Math.floor(ang / sweep);
        if (idx < 0 || idx >= sectorCount) return -1;
        return idx;
    }

    // ── Main render ───────────────────────────────────────────────────────────

    /**
     * Полная отрисовка кадра радиала.
     *
     * @param ctx          контекст рендера от Screen.render
     * @param screenWidth  ширина экрана (px)
     * @param screenHeight высота экрана (px)
     * @param slots        список слотов (item-stack + сохранённое имя)
     * @param hoveredIdx   индекс наведённого сектора или -1
     * @param hoverScalePx px-расширение hovered-сектора наружу
     * @param outlineThicknessPx толщина обводки сектора (0 → не рисуем)
     * @param bgArgb       цвет заливки секторов (полупрозрачный поверх блюра)
     * @param outlineArgb  цвет обводки
     * @param textRenderer вершинный рендерер для подсказки/цифр
     * @param hintText     текст подсказки под кольцом (показывается при openScale > 0.85)
     */
    public void render(DrawContext ctx,
                       int screenWidth, int screenHeight,
                       List<? extends SlotView> slots,
                       int hoveredIdx,
                       float hoverScalePx,
                       float outlineThicknessPx,
                       int bgArgb,
                       int outlineArgb,
                       TextRenderer textRenderer,
                       String hintText) {
        centerX = screenWidth  / 2f;
        centerY = screenHeight / 2f;
        if (slots.isEmpty()) return;

        // Flush ранее накопленных draw'ов в framebuffer ДО setup, чтобы блюр
        // их захватил.
        ctx.draw();
        blurHelper.setup();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        int n = slots.size();
        float sweepDeg = 360f / n;
        float maxExpand = Math.min(hoverScalePx, MAX_HOVER_EXPAND);

        float openScale = openProgress();
        float effInnerR = INNER_RADIUS * openScale;
        float effOuterR = OUTER_RADIUS * openScale;
        float outlineW  = outlineThicknessPx * openScale;

        // Обновляем hover-состояние каждого сектора.
        for (int i = 0; i < n; i++) {
            setSectorHovered(i, !closing && i == hoveredIdx);
        }

        // Рендер заливки + обводки per-sector.
        for (int i = 0; i < n; i++) {
            float hoverFactor = hoverProgress(i);
            float sectorShift = maxExpand * hoverFactor * openScale;
            float startDeg = -90f + sweepDeg * i + GAP_DEG / 2f;
            float endDeg   = -90f + sweepDeg * (i + 1) - GAP_DEG / 2f;
            float bandInner = effInnerR + sectorShift;
            float bandOuter = effOuterR + sectorShift;

            drawBlurredSegmentWrapped(ctx, bandInner, bandOuter, startDeg, endDeg, bgArgb);

            if (outlineW > 0.1f) {
                drawArcOutlineWrapped(ctx, bandInner, bandOuter,
                        startDeg, endDeg, outlineW, outlineArgb);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        // ── Иконки и текст: появляются после openScale > 0.5. ─────────────────
        if (openScale > 0.5f) {
            for (int i = 0; i < n; i++) {
                float hoverFactor = hoverProgress(i);
                float sectorShift = maxExpand * hoverFactor * openScale;
                float midR = (effInnerR + effOuterR) / 2f + sectorShift;

                SlotView slot = slots.get(i);
                float startRad = (float) Math.toRadians(-90f + sweepDeg * i);
                float endRad   = (float) Math.toRadians(-90f + sweepDeg * (i + 1));
                float mid = (startRad + endRad) / 2f;
                float ix = centerX + (float) Math.cos(mid) * midR;
                float iy = centerY + (float) Math.sin(mid) * midR;

                ItemStack stack = slot.itemStack();
                boolean available = slot.isAvailable();
                boolean dragging = slot.isDragging();
                ctx.getMatrices().push();
                ctx.getMatrices().translate(ix, iy, 0);
                ctx.getMatrices().scale(1.5f, 1.5f, 1f);
                if (dragging) {
                    // Иконку рисует Screen поверх курсора — здесь пропускаем,
                    // чтобы не было дубликата.
                } else if (stack != null && !stack.isEmpty()) {
                    if (!available) {
                        // Предмет привязан, но физически не в инвентаре —
                        // рисуем тот же стек (тот же item + NBT), но
                        // приглушённо: shaderColor (0.35, 0.35, 0.35, 0.6)
                        // ≈ серая полупрозрачная заливка, читается как
                        // «недоступно» без отдельного шейдера.
                        RenderSystem.setShaderColor(0.35f, 0.35f, 0.35f, 0.6f);
                        ctx.drawItem(stack, -8, -8);
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    } else {
                        ctx.drawItem(stack, -8, -8);
                    }
                } else {
                    String label = String.valueOf(i + 1);
                    int w = textRenderer.getWidth(label);
                    ctx.drawText(textRenderer, label, -w / 2, -4, 0xFFBBBBBB, false);
                }
                ctx.getMatrices().pop();
            }
        }

        // Подсказка появляется только при полностью открытом кольце.
        if (openScale > 0.85f && hintText != null && !hintText.isBlank()) {
            ctx.drawCenteredTextWithShadow(textRenderer, hintText,
                    screenWidth / 2, (int) (centerY + OUTER_RADIUS + 12), 0xFFFFFFFF);
        }
    }

    // ── Shader-quads (нижний уровень) ────────────────────────────────────────

    /** Обёртка над {@link #drawArcOutline} с обработкой wrap-around (>360°). */
    private void drawArcOutlineWrapped(DrawContext ctx,
                                       float bandInner, float bandOuter,
                                       float startDeg, float endDeg,
                                       float outlineWidthPx, int argb) {
        float startDegrees = normalizeDegrees(startDeg);
        float endDegrees   = normalizeDegrees(endDeg);
        float sweepDegrees = normalizeDegrees(endDegrees - startDegrees);
        if (sweepDegrees < 0.001f) sweepDegrees = 360f;
        float shaderStart = normalizeDegrees(180f - endDegrees);
        drawArcOutline(ctx, bandInner, bandOuter, shaderStart, sweepDegrees, outlineWidthPx, argb);
    }

    /**
     * Полный outline сектора одним draw call'ом через arc_outline шейдер.
     * Внутри шейдера: SDF к каждому из 4 рёбер сектора в ПИКСЕЛЬНЫХ единицах
     * (не угловых), берётся min и применяется smoothstep AA на расстоянии
     * outlineWidth/2 от ребра. Поэтому толщина обводки идеально одинакова
     * по всему периметру — и на дугах, и на радиальных штрихах.
     */
    private void drawArcOutline(DrawContext ctx,
                                float bandInner, float bandOuter,
                                float shaderStartDeg, float sweepDeg,
                                float outlineWidthPx, int argb) {
        float quadHalfSize = OUTER_RADIUS + MAX_HOVER_EXPAND;
        float quadX = centerX - quadHalfSize;
        float quadY = centerY - quadHalfSize;
        float quadSize = quadHalfSize * 2f;
        float scale = (float) mc.getWindow().getScaleFactor();

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Vector3f pos = matrix.transformPosition(quadX, quadY, 0, new Vector3f()).mul(scale);
        Vector3f matrixScale = matrix.getScale(new Vector3f()).mul(scale);
        float width  = quadSize * matrixScale.x;
        float height = quadSize * matrixScale.y;

        ShaderProgram shader = RenderSystem.setShader(ARC_OUTLINE_SHADER_KEY);
        if (shader == null) return;

        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(
                pos.x, mc.getWindow().getHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(bandInner / quadHalfSize);
        shader.getUniformOrDefault("thickness").set((bandOuter - bandInner) / quadHalfSize);
        shader.getUniformOrDefault("start").set(shaderStartDeg);
        shader.getUniformOrDefault("end").set(sweepDeg);
        shader.getUniformOrDefault("color1").set(r(argb), g(argb), b(argb), a(argb));
        // outlineWidth — нормализованный к quadHalfSize. На выходе обводка
        // получит ровно `outlineWidthPx` пикселей толщины.
        shader.getUniformOrDefault("outlineWidth").set(outlineWidthPx / quadHalfSize);

        emitFullQuad(matrix, quadX, quadY, quadSize);
    }

    /** Обёртка над {@link #drawArcBlurredFill} с обработкой wrap-around. */
    private void drawBlurredSegmentWrapped(DrawContext ctx,
                                           float bandInner, float bandOuter,
                                           float startDeg, float endDeg, int argb) {
        float startDegrees = normalizeDegrees(startDeg);
        float endDegrees   = normalizeDegrees(endDeg);
        float sweepDegrees = normalizeDegrees(endDegrees - startDegrees);
        if (sweepDegrees < 0.001f) sweepDegrees = 360f;
        float shaderStart = normalizeDegrees(180f - endDegrees);
        drawArcBlurredFill(ctx, bandInner, bandOuter, shaderStart, sweepDegrees, argb);
    }

    /**
     * Заливка сектора через arc_blur — фон семплируется из захваченного
     * framebuffer'а ({@link #blurHelper}) и блюрится, поверх лежит цвет
     * {@code argb} с собственной альфой (полупрозрачный фон виджетов).
     */
    private void drawArcBlurredFill(DrawContext ctx,
                                    float bandInner, float bandOuter,
                                    float shaderStartDeg, float sweepDeg, int argb) {
        if (blurHelper.input == null) return;

        float quadHalfSize = OUTER_RADIUS + MAX_HOVER_EXPAND;
        float quadX = centerX - quadHalfSize;
        float quadY = centerY - quadHalfSize;
        float quadSize = quadHalfSize * 2f;
        float scale = (float) mc.getWindow().getScaleFactor();

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Vector3f pos = matrix.transformPosition(quadX, quadY, 0, new Vector3f()).mul(scale);
        Vector3f matrixScale = matrix.getScale(new Vector3f()).mul(scale);
        float width  = quadSize * matrixScale.x;
        float height = quadSize * matrixScale.y;

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(blurHelper.input.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(ARC_BLUR_SHADER_KEY);
        if (shader == null) return;

        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(
                pos.x, mc.getWindow().getHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(bandInner / quadHalfSize);
        shader.getUniformOrDefault("thickness").set((bandOuter - bandInner) / quadHalfSize);
        shader.getUniformOrDefault("start").set(shaderStartDeg);
        shader.getUniformOrDefault("end").set(sweepDeg);
        shader.getUniformOrDefault("color1").set(r(argb), g(argb), b(argb), a(argb));
        shader.getUniformOrDefault("InputResolution").set(blurHelper.resolution.x, blurHelper.resolution.y);
        shader.getUniformOrDefault("Quality").set(8f);

        emitFullQuad(matrix, quadX, quadY, quadSize);
    }

    /** Один общий vertex-блок для arc_outline / arc_blur квадов. */
    private static void emitFullQuad(Matrix4f matrix, float quadX, float quadY, float quadSize) {
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(matrix, quadX,            quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY,            0);
        buffer.vertex(matrix, quadX,            quadY,            0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    // ── Slot view interface ──────────────────────────────────────────────────

    /** Минимальный интерфейс слота, нужный рендереру: предмет + сохранённое имя.
     *  Реализуется доменным классом из {@code AutoSwapRadialScreen}. */
    public interface SlotView {
        ItemStack itemStack();
        String savedItemName();
        /** {@code true} если предмет физически есть в инвентаре игрока сейчас.
         *  {@code false} → стек взят из NBT-кэша как fallback, рисуется
         *  приглушённо (греет «недоступно»). */
        boolean isAvailable();
        /** {@code true} если игрок перетаскивает иконку этого слота — рендер
         *  пропустит её отрисовку в секторе (экран сам нарисует поверх курсора). */
        default boolean isDragging() { return false; }
    }
}
