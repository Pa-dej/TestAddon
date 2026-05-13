package padej.testaddon.modules.gameplay;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import org.lwjgl.opengl.GL13;
import padej.soup.api.system.shape.implement.Blur;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Радиальное меню AutoSwap.
 *
 * <p><b>Архитектура отрисовки</b>: N независимых секторов с заливкой через
 * arc-шейдер. Цвет всех секторов — цвет drag-виджетов SoupVisuals (фон
 * SoupVisuals). Между секторами небольшой угловой зазор ({@link #GAP_DEG}).</p>
 *
 * <p><b>Hover-индикация</b>: наведённый сектор рисуется ПОСЛЕДНИМ с
 * расширенными радиусами (наружу на {@code module.getHoverScale()} px, внутрь
 * чуть меньше). Это создаёт эффект «выскакивания» сектора без отдельных
 * outline/highlight элементов.</p>
 *
 * <p><b>Hover-зона</b>: внешней границы нет — курсор может быть сколь угодно
 * далеко за внешним радиусом, всё равно регистрируется правильный сектор по
 * углу. Только внутреннее «мёртвое пятно» (dist &lt; INNER_RADIUS) даёт
 * нейтральное состояние. Это позволяет резкими движениями быстро выбирать
 * нужный сектор.</p>
 *
 * <p><b>Управление</b>: ЛКМ по пустому слоту — bind main-hand; ЛКМ по
 * заполненному — swap; ПКМ — очистить; отпускание menu-key — действие на
 * наведённом секторе; ESC — закрыть.</p>
 */
public class AutoSwapRadialScreen extends Screen {

    private static final ShaderProgramKey ARC_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("minecraft", "core/arc"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );

    private static final ShaderProgramKey ARC_BLUR_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("minecraft", "core/arc_blur"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );

    private static final ShaderProgramKey ARC_OUTLINE_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("minecraft", "core/arc_outline"),
            VertexFormats.POSITION,
            Defines.EMPTY
    );

    /** Blur-helper из SoupVisuals — используем только для setup()/input
     *  (захват framebuffer'а). Сам рендер делает наш arc_blur шейдер. */
    private final Blur blurHelper = new Blur();

    private static final float OUTER_RADIUS = 92f;
    private static final float INNER_RADIUS = 54f;
    /** Максимально возможное расширение наведённого сектора (px).
     *  Используется как margin для квада arc-шейдера. */
    private static final float MAX_HOVER_EXPAND = 16f;
    /** Угловой зазор между секторами (градусы). */
    private static final float GAP_DEG = 1f;

    /** Fallback'и на случай если SoupVisuals.Theme недоступен. */
    private static final int FALLBACK_BG_ARGB = 0xC8CDCDCD;

    private final AutoSwapModule module;
    private final int holdKey;
    private final long openedAt;

    private final List<SwapSlotView> slots = new ArrayList<>();
    private int maxSlots;

    private float centerX, centerY;
    private int hovered = -1;

    private static final int OPEN_MS  = 180;
    private static final int CLOSE_MS = 180;
    private static final int HOVER_MS = 140;

    /** Время начала close-анимации (−1 пока окно открыто). */
    private long closeStartedAt = -1;
    /** Индекс сектора, который надо активировать после завершения close-анимации
     *  (−1 = просто закрыть без действия). */
    private int pendingActivateIdx = -1;
    /** True пока идёт close-анимация. Запрещает действия и hover. */
    private boolean closing = false;

    /** Per-sector hover-интерполяция: время последней смены состояния
     *  и значение прогресса в момент той смены — чтобы анимация продолжалась
     *  плавно даже если hover переключился до завершения предыдущей. */
    private long[] hoverChangedAt;
    private boolean[] hoverActive;
    private float[]   hoverProgressAtChange;

    public AutoSwapRadialScreen(AutoSwapModule module, int holdKey) {
        super(Text.literal("AutoSwap"));
        this.module = module;
        this.holdKey = holdKey;
        this.openedAt = System.currentTimeMillis();
        this.maxSlots = Math.max(2, Math.min(8, module.getMaxSlotsValue()));
        this.hoverChangedAt        = new long[maxSlots];
        this.hoverActive           = new boolean[maxSlots];
        this.hoverProgressAtChange = new float[maxSlots];
        long now = System.currentTimeMillis();
        for (int i = 0; i < maxSlots; i++) hoverChangedAt[i] = now - HOVER_MS;
        rebuildSlots();
    }

    /** Smoothstep — easeInOut t ∈ [0..1]. */
    private static float easeInOut(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    /** Прогресс открытия 0..1 с учётом close-анимации (тогда идёт обратно). */
    private float openProgress() {
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
    private boolean closeFinished() {
        return closing && System.currentTimeMillis() - closeStartedAt >= CLOSE_MS;
    }

    /** Прогресс расширения для сектора i, 0..1. */
    private float hoverProgress(int i) {
        long now = System.currentTimeMillis();
        long elapsed = now - hoverChangedAt[i];
        float t = easeInOut(Math.min(1f, (float) elapsed / HOVER_MS));
        if (hoverActive[i]) {
            // движемся от prev к 1
            return hoverProgressAtChange[i] + (1f - hoverProgressAtChange[i]) * t;
        } else {
            // движемся от prev к 0
            return hoverProgressAtChange[i] * (1f - t);
        }
    }

    /** Обновляет состояние hover'а конкретного сектора, фиксируя текущий
     *  прогресс как стартовое значение для новой интерполяции. */
    private void setSectorHovered(int i, boolean v) {
        if (hoverActive[i] == v) return;
        hoverProgressAtChange[i] = hoverProgress(i);
        hoverChangedAt[i] = System.currentTimeMillis();
        hoverActive[i] = v;
    }

    private void rebuildSlots() {
        slots.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 0; i < maxSlots; i++) {
            String name = module.getSavedItem(i);
            ItemStack icon = findItemByName(mc, name);
            slots.add(new SwapSlotView(icon, name, i));
        }
    }

    private ItemStack findItemByName(MinecraftClient mc, String name) {
        if (name == null || name.isBlank() || mc.player == null) return null;
        var inv = mc.player.getInventory();
        for (int j = 0; j < inv.main.size(); j++) {
            ItemStack s = inv.main.get(j);
            if (!s.isEmpty() && s.getName().getString().equals(name)) return s.copy();
        }
        ItemStack off = inv.offHand.getFirst();
        if (!off.isEmpty() && off.getName().getString().equals(name)) return off.copy();
        return null;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // НЕ вызываем super.render — не нужен ванильный фон Screen.

        // Если close-анимация отыграла — выполняем отложенное действие и выходим.
        if (closeFinished()) {
            int idx = pendingActivateIdx;
            pendingActivateIdx = -1;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(null);
            if (idx >= 0) module.activateSavedSlotSwap(idx);
            return;
        }

        centerX = this.width / 2f;
        centerY = this.height / 2f;
        // Во время close-анимации hover заморожен — выбор уже зафиксирован.
        hovered = closing ? -1 : getHoverIndex(mouseX, mouseY);
        if (slots.isEmpty()) return;

        // Сначала flush — чтобы прежние draw'ы (super от виджетов) попали
        // в framebuffer ДО setup, и блюр их захватил.
        ctx.draw();
        // Снимок framebuffer'а в blurHelper.input — будет использован
        // arc_blur шейдером при заливке секторов.
        blurHelper.setup();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        // Заливка — полупрозрачный rectColor поверх блюра.
        int bgColor      = padej.soup.base.util.color.ColorUtil.getRect(0.7f);
        int outlineColor = outlineClientColor();   // accent client color для линий
        int n = slots.size();
        float sweepDeg = 360f / n;
        float maxExpand = Math.min(module.getHoverScale(), MAX_HOVER_EXPAND);

        // Open-scale 0..1 — управляет общим размером кольца.
        float openScale = openProgress();
        float effInnerR = INNER_RADIUS * openScale;
        float effOuterR = OUTER_RADIUS * openScale;
        float outlineW = module.getOutlineThickness() * openScale;

        // Обновляем hover-состояние каждого сектора по текущему hovered.
        for (int i = 0; i < n; i++) {
            setSectorHovered(i, i == hovered);
        }

        // Рендер заливки + обводки per-sector. Радиальные штрихи рисуются
        // полностью ВНУТРИ сектора через arc-шейдер с узким angular sweep
        // (≈ outlineW / bandOuter rad). Дуги тем же arc-шейдером.
        for (int i = 0; i < n; i++) {
            float hoverFactor = hoverProgress(i);
            float sectorShift = maxExpand * hoverFactor * openScale;
            float startDeg = -90f + sweepDeg * i + GAP_DEG / 2f;
            float endDeg   = -90f + sweepDeg * (i + 1) - GAP_DEG / 2f;
            float bandInner = effInnerR + sectorShift;
            float bandOuter = effOuterR + sectorShift;

            // Заливка с блюром.
            drawBlurredSegmentWrapped(ctx, bandInner, bandOuter, startDeg, endDeg, bgColor);

            // Обводка контура — единым draw call'ом через arc_outline шейдер.
            // SDF к каждому ребру в ПИКСЕЛЯХ → толщина одинакова по всему периметру.
            if (outlineW > 0.1f) {
                drawArcOutlineWrapped(ctx, bandInner, bandOuter,
                        startDeg, endDeg, outlineW, outlineColor);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        // ── Иконки и текст: появляются после openScale > 0.5. Иконка hovered
        //    сектора едет наружу вместе с band'ом, scale остаётся 1.0 (толщина
        //    band'а постоянная при сдвиге).
        if (openScale > 0.5f) {
            for (int i = 0; i < n; i++) {
                float hoverFactor = hoverProgress(i);
                float sectorShift = maxExpand * hoverFactor * openScale;
                // Band смещён наружу — позиция иконки на середине band'а.
                float midR = (effInnerR + effOuterR) / 2f + sectorShift;

                SwapSlotView slot = slots.get(i);
                float startRad = (float) Math.toRadians(-90f + sweepDeg * i);
                float endRad   = (float) Math.toRadians(-90f + sweepDeg * (i + 1));
                float mid = (startRad + endRad) / 2f;
                float ix = centerX + (float) Math.cos(mid) * midR;
                float iy = centerY + (float) Math.sin(mid) * midR;

                // Иконки рисуем в 2× размере через матричный scale вокруг центра иконки.
                if (slot.itemStack != null && !slot.itemStack.isEmpty()) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(ix, iy, 0);
                    ctx.getMatrices().scale(1.5f, 1.5f, 1f);
                    ctx.drawItem(slot.itemStack, -8, -8);
                    ctx.getMatrices().pop();
                } else {
                    String label = String.valueOf(i + 1);
                    int w = this.textRenderer.getWidth(label);
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(ix, iy, 0);
                    ctx.getMatrices().scale(1.5f, 1.5f, 1f);
                    ctx.drawText(this.textRenderer, label, -w / 2, -4, 0xFFBBBBBB, false);
                    ctx.getMatrices().pop();
                }
            }
        }

        // Подсказка появляется только при полностью открытом кольце.
        if (openScale > 0.85f) {
            String hint = getString();
            ctx.drawCenteredTextWithShadow(this.textRenderer, hint,
                    this.width / 2, (int) (centerY + OUTER_RADIUS + 12), 0xFFFFFFFF);
        }
    }

    private @NotNull String getString() {
        String hint;
        if (hovered >= 0) {
            String name = slots.get(hovered).savedItemName;
            if (name != null && !name.isBlank()) {
                hint = "ЛКМ или отпусти клавишу: свап на " + name + ". ПКМ — очистить.";
            } else {
                hint = "Слот " + (hovered + 1) + " пуст — ЛКМ привяжет предмет из руки.";
            }
        } else {
            hint = "Наведи мышь на сектор. ЛКМ — bind/swap, ПКМ — очистить.";
        }
        return hint;
    }

    /**
     * Рисует сегмент кольца через arc-шейдер. Wrap-around обрабатывает сам
     * шейдер (см. arc.fsh — там есть ветка для endAngle > 2π). Поэтому Java
     * передаёт ровно один draw call независимо от того, пересекает ли сектор
     * границу 360°/0°.
     */
    private void drawArcSegmentWrapped(DrawContext ctx,
                                       float bandInner, float bandOuter,
                                       float startDeg, float endDeg, int argb) {
        float startDegrees = normalizeDegrees(startDeg);
        float endDegrees   = normalizeDegrees(endDeg);
        float sweepDegrees = normalizeDegrees(endDegrees - startDegrees);
        if (sweepDegrees < 0.001f) sweepDegrees = 360f;
        float shaderStart = normalizeDegrees(180f - endDegrees);

        drawArcQuad(ctx, centerX, centerY, bandInner, bandOuter,
                shaderStart, sweepDegrees, argb);
    }

    /**
     * Однократный arc-шейдер draw с заранее посчитанными shader-углами.
     * Квад фиксированно размером OUTER_RADIUS*2 (центрирован на cx,cy).
     */
    private void drawArcQuad(DrawContext ctx, float cx, float cy,
                             float bandInner, float bandOuter,
                             float shaderStartDeg, float sweepDeg,
                             int argb) {
        ShaderProgram shader = RenderSystem.setShader(ARC_SHADER_KEY);
        if (shader == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        // Размер квада с запасом — чтобы расширенный hover-сектор и
        // отрицательный сдвиг inner не выходили за границы квада.
        float quadHalfSize = OUTER_RADIUS + MAX_HOVER_EXPAND;
        float quadX = cx - quadHalfSize;
        float quadY = cy - quadHalfSize;
        float quadSize = quadHalfSize * 2f;
        float scale = (float) mc.getWindow().getScaleFactor();

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Vector3f pos = matrix.transformPosition(quadX, quadY, 0, new Vector3f()).mul(scale);
        Vector3f matrixScale = matrix.getScale(new Vector3f()).mul(scale);
        float width  = quadSize * matrixScale.x;
        float height = quadSize * matrixScale.y;

        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;
        float a = ((argb >>> 24) & 0xFF) / 255f;

        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(
                pos.x, mc.getWindow().getHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(bandInner / quadHalfSize);
        shader.getUniformOrDefault("thickness").set((bandOuter - bandInner) / quadHalfSize);
        shader.getUniformOrDefault("start").set(shaderStartDeg);
        shader.getUniformOrDefault("end").set(sweepDeg);
        shader.getUniformOrDefault("color1").set(r, g, b, a);
        shader.getUniformOrDefault("color2").set(r, g, b, a);

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(matrix, quadX,            quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY,            0);
        buffer.vertex(matrix, quadX,            quadY,            0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
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
        MinecraftClient mc = MinecraftClient.getInstance();
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

        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;
        float a = ((argb >>> 24) & 0xFF) / 255f;

        ShaderProgram shader = RenderSystem.setShader(ARC_OUTLINE_SHADER_KEY);
        if (shader == null) return;

        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(
                pos.x, mc.getWindow().getHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(bandInner / quadHalfSize);
        shader.getUniformOrDefault("thickness").set((bandOuter - bandInner) / quadHalfSize);
        shader.getUniformOrDefault("start").set(shaderStartDeg);
        shader.getUniformOrDefault("end").set(sweepDeg);
        shader.getUniformOrDefault("color1").set(r, g, b, a);
        // outlineWidth — нормализованный к quadHalfSize. На выходе обводка
        // получит ровно `outlineWidthPx` пикселей толщины.
        shader.getUniformOrDefault("outlineWidth").set(outlineWidthPx / quadHalfSize);

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(matrix, quadX,            quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY,            0);
        buffer.vertex(matrix, quadX,            quadY,            0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

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
     * Заливка сектора через arc_blur — фон семплируется из захваченного
     * framebuffer'а ({@link #blurHelper}) и блюрится, поверх лежит цвет
     * {@code argb} с собственной альфой (полупрозрачный фон виджетов).
     */
    private void drawArcBlurredFill(DrawContext ctx,
                                    float bandInner, float bandOuter,
                                    float shaderStartDeg, float sweepDeg, int argb) {
        if (blurHelper.input == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
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

        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;
        float a = ((argb >>> 24) & 0xFF) / 255f;

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
        shader.getUniformOrDefault("color1").set(r, g, b, a);
        shader.getUniformOrDefault("InputResolution").set(blurHelper.resolution.x, blurHelper.resolution.y);
        shader.getUniformOrDefault("Quality").set(8f);

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(matrix, quadX,            quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY + quadSize, 0);
        buffer.vertex(matrix, quadX + quadSize, quadY,            0);
        buffer.vertex(matrix, quadX,            quadY,            0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
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
     * Цвет фона сектора — тот же что у перемещаемых HUD-виджетов SoupVisuals
     * ({@code ColorUtil.getRect(float)}). Так радиал выглядит «родным» —
     * цветовая палитра единая со всеми остальными drag-виджетами клиента.
     */
    private int themeBgColor() {
        try {
            int c = padej.soup.base.util.color.ColorUtil.getRect(1f);
            if ((c & 0x00FFFFFF) != 0 || (c >>> 24) != 0) return c;
        } catch (Throwable ignored) {}
        return FALLBACK_BG_ARGB;
    }

    /**
     * Цвет обводки — акцент клиента (clientColor через ColorUtil).
     * Принудительный alpha=0xFF на случай низкой настройки темы.
     */
    private int outlineClientColor() {
        int c = padej.soup.base.util.color.ColorUtil.getClientColor();
        return (c & 0x00FFFFFF) | 0xFF000000;
    }

    private float normalizeDegrees(float d) {
        float r = d % 360f;
        return r < 0 ? r + 360f : r;
    }

    private int getHoverIndex(double mouseX, double mouseY) {
        float dx = (float) (mouseX - centerX);
        float dy = (float) (mouseY - centerY);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // Только внутреннее «мёртвое пятно» вокруг центра — для нейтрального
        // состояния. Внешней границы нет: можно резко мотнуть курсор далеко
        // за края — сектор всё равно зарегистрируется по углу.
        if (dist < INNER_RADIUS) return -1;

        int n = slots.size();
        double sweep = Math.PI * 2 / n;
        double ang = Math.atan2(dy, dx) + Math.PI / 2;
        double twoPi = Math.PI * 2;
        ang = ((ang % twoPi) + twoPi) % twoPi;

        int idx = (int) Math.floor(ang / sweep);
        if (idx < 0 || idx >= n) return -1;
        return idx;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return false;
        if (hovered < 0) return false;
        SwapSlotView slot = slots.get(hovered);
        MinecraftClient mc = MinecraftClient.getInstance();

        if (button == 0) {
            if (slot.savedItemName == null || slot.savedItemName.isBlank()) {
                // Пустой слот: bind предмета из руки — без закрытия меню,
                // пользователь может биндить дальше.
                if (mc.player != null) {
                    ItemStack held = mc.player.getMainHandStack();
                    if (!held.isEmpty()) {
                        String name = held.getName().getString();
                        slot.itemStack = held.copy();
                        slot.savedItemName = name;
                        module.setSavedSlot(hovered, name);
                    }
                }
                return true;
            } else {
                // Заполненный слот: запускаем close-анимацию с активацией.
                startClose(hovered);
                return true;
            }
        }
        if (button == 1) {
            slot.itemStack = null;
            slot.savedItemName = null;
            module.clearSavedSlot(hovered);
            return true;
        }
        return false;
    }

    /** Запускает обратную анимацию. После её завершения render() сделает
     *  setScreen(null) и (если activateIdx >= 0) активирует слот. */
    private void startClose(int activateIdx) {
        if (closing) return;
        closing = true;
        pendingActivateIdx = activateIdx;
        // closeStartedAt становится точкой отсчёта обратной анимации.
        // Стартуем с currentOpenProgress = текущее значение openScale,
        // чтобы не было прыжка если закрытие началось раньше окончания открытия.
        // Реализация openProgress() при closeStartedAt >= 0 уже считает
        // от 1, но если currentOpen < 1, можно компенсировать сдвигом
        // closeStartedAt назад во времени.
        float currentOpen = openProgress();
        closeStartedAt = System.currentTimeMillis()
                - (long) ((1f - currentOpen) * CLOSE_MS);
        // Сектора тоже «сдуются» сами — нужно перевести все в not-hovered.
        for (int i = 0; i < maxSlots; i++) setSectorHovered(i, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // ESC — закрыть с анимацией, без активации.
            startClose(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == holdKey) {
            confirmAndClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (closing) return;
        if (System.currentTimeMillis() - openedAt < 80) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        long window = mc.getWindow().getHandle();
        if (GLFW.glfwGetKey(window, holdKey) == GLFW.GLFW_RELEASE) {
            confirmAndClose();
        }
        for (SwapSlotView slot : slots) {
            if (slot.savedItemName != null) slot.itemStack = findItemByName(mc, slot.savedItemName);
        }
    }

    /** Запускает закрытие. Если под курсором заполненный слот — отложенный
     *  swap, иначе просто закрываем. */
    private void confirmAndClose() {
        int sel = hovered;
        int activateIdx = -1;
        if (sel >= 0 && sel < slots.size()) {
            SwapSlotView slot = slots.get(sel);
            if (slot.savedItemName != null && !slot.savedItemName.isBlank()) {
                activateIdx = sel;
            }
        }
        startClose(activateIdx);
    }

    private static class SwapSlotView {
        ItemStack itemStack;
        String savedItemName;
        final int index;

        SwapSlotView(ItemStack itemStack, String savedItemName, int index) {
            this.itemStack = itemStack;
            this.savedItemName = savedItemName;
            this.index = index;
        }
    }
}
