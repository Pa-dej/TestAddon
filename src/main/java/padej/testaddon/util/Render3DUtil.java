package padej.testaddon.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Перенос 1:1 из {@code winvi.moscow.soupbetter.util.Render3DUtil}.
 * Все методы и сигнатуры идентичны оригиналу.
 */
public class Render3DUtil {

    public static void draw3x3Box(MatrixStack matrices, BlockPos centerPos, int color, float lineWidth) {
        draw3x3BoxWithFill(matrices, centerPos, color, lineWidth, true);
    }

    public static void draw3x3BoxWithFill(MatrixStack matrices, BlockPos centerPos, int color, float lineWidth, boolean fill) {
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8  & 255) / 255.0F;
        float b = (color       & 255) / 255.0F;

        float x1 = centerPos.getX() - 1;
        float y1 = centerPos.getY();
        float z1 = centerPos.getZ() - 1;
        float x2 = centerPos.getX() + 2;
        float y2 = centerPos.getY() + 3;
        float z2 = centerPos.getZ() + 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        if (fill) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float fillAlpha = 90 / 255.0F;
            quadsForBox(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, fillAlpha);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(lineWidth);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        linesForBox(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    public static void draw10x10BoxWithFill(MatrixStack matrices, BlockPos centerPos, int color, float lineWidth, boolean fill) {
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8  & 255) / 255.0F;
        float b = (color       & 255) / 255.0F;

        float x1 = centerPos.getX() - 7.5f;
        float y1 = centerPos.getY();
        float z1 = centerPos.getZ() - 7.5f;
        float x2 = centerPos.getX() + 7.5f;
        float y2 = centerPos.getY() + 3;
        float z2 = centerPos.getZ() + 7.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        if (fill) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float fillAlpha = 90 / 255.0F;
            quadsForBox(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, fillAlpha);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(lineWidth);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        linesForBox(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    public static void drawCircle(MatrixStack matrices, BlockPos centerPos, int radius, int color, float lineWidth) {
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8  & 255) / 255.0F;
        float b = (color       & 255) / 255.0F;

        float centerX = centerPos.getX() + 0.5f;
        float centerY = centerPos.getY() + 0.01f;
        float centerZ = centerPos.getZ() + 0.5f;
        float topY    = centerY + 1.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(lineWidth);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        int segments = 128;

        for (int i = 0; i < segments; i++) {
            double a1 = 2 * Math.PI * i / segments;
            double a2 = 2 * Math.PI * (i + 1) / segments;
            float x1 = centerX + (float) (Math.cos(a1) * radius);
            float z1 = centerZ + (float) (Math.sin(a1) * radius);
            float x2 = centerX + (float) (Math.cos(a2) * radius);
            float z2 = centerZ + (float) (Math.sin(a2) * radius);
            buffer.vertex(matrix, x1, centerY, z1).color(r, g, b, a).normal(0, 1, 0);
            buffer.vertex(matrix, x2, centerY, z2).color(r, g, b, a).normal(0, 1, 0);
        }
        for (int i = 0; i < segments; i++) {
            double a1 = 2 * Math.PI * i / segments;
            double a2 = 2 * Math.PI * (i + 1) / segments;
            float x1 = centerX + (float) (Math.cos(a1) * radius);
            float z1 = centerZ + (float) (Math.sin(a1) * radius);
            float x2 = centerX + (float) (Math.cos(a2) * radius);
            float z2 = centerZ + (float) (Math.sin(a2) * radius);
            buffer.vertex(matrix, x1, topY, z1).color(r, g, b, a).normal(0, 1, 0);
            buffer.vertex(matrix, x2, topY, z2).color(r, g, b, a).normal(0, 1, 0);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    public static void drawBlockBox(MatrixStack matrices, BlockPos pos, int color, float lineWidth) {
        draw3x3Box(matrices, pos, color, lineWidth);
    }

    public static void drawPlastBox(MatrixStack matrices, BlockPos centerPos, int color, float lineWidth, Direction side, float pitch) {
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8  & 255) / 255.0F;
        float b = (color       & 255) / 255.0F;
        float fillAlpha = 90 / 255.0F;

        float x1, y1, z1, x2, y2, z2;
        if (side == Direction.DOWN) {
            x1 = centerPos.getX() - 2; y1 = centerPos.getY() + 1; z1 = centerPos.getZ() - 2;
            x2 = centerPos.getX() + 3; y2 = centerPos.getY() + 3; z2 = centerPos.getZ() + 3;
        } else if (side == Direction.UP) {
            x1 = centerPos.getX() - 2; y1 = centerPos.getY();     z1 = centerPos.getZ() - 2;
            x2 = centerPos.getX() + 3; y2 = centerPos.getY() + 2; z2 = centerPos.getZ() + 3;
        } else if (side == Direction.NORTH || side == Direction.SOUTH) {
            x1 = centerPos.getX() - 2; y1 = centerPos.getY() - 2; z1 = centerPos.getZ();
            x2 = centerPos.getX() + 3; y2 = centerPos.getY() + 3; z2 = centerPos.getZ() + 2;
        } else {
            x1 = centerPos.getX();     y1 = centerPos.getY() - 2; z1 = centerPos.getZ() - 2;
            x2 = centerPos.getX() + 2; y2 = centerPos.getY() + 3; z2 = centerPos.getZ() + 3;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        drawBox(matrix, x1, y1, z1, x2, y2, z2, r, g, b, a, fillAlpha, lineWidth);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void drawBox(Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float fillAlpha, float lineWidth) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        quadsForBox(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, fillAlpha);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(lineWidth);
        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        linesForBox(buffer, matrix, x1, y1, z1, x2, y2, z2, r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void quadsForBox(BufferBuilder buffer, Matrix4f matrix,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float r, float g, float b, float fillAlpha) {
        // bottom
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillAlpha);
        // top
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillAlpha);
        // north
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillAlpha);
        // south
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillAlpha);
        // west
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, fillAlpha);
        // east
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, fillAlpha);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, fillAlpha);
    }

    private static void linesForBox(BufferBuilder buffer, Matrix4f matrix,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float r, float g, float b, float a) {
        // bottom rectangle
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        // top rectangle
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).normal(0, 1, 0);
        // vertical edges
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a).normal(0, 1, 0);
    }

    /**
     * Линия между двумя мировыми точками (с поправкой на позицию камеры).
     * Используется PredictionsModule для отрисовки траекторий проджектайлов.
     */
    public static void drawLine(Vec3d start, Vec3d end, int color, float width, boolean depth) {
        Vec3d cameraPos = MinecraftClient.getInstance().getEntityRenderDispatcher().camera.getPos();
        Vec3d relStart = start.subtract(cameraPos);
        Vec3d relEnd   = end.subtract(cameraPos);

        MatrixStack matrices = new MatrixStack();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8  & 255) / 255.0F;
        float b = (color       & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (!depth) RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(width);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        buffer.vertex(matrix, (float) relStart.x, (float) relStart.y, (float) relStart.z).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, (float) relEnd.x,   (float) relEnd.y,   (float) relEnd.z  ).color(r, g, b, a).normal(0, 1, 0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        if (!depth) RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer, Vec3d start, Vec3d end, int lineColor) {
        vertexLine(matrices, buffer, start, end, lineColor, lineColor);
    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer, Vec3d start, Vec3d end, int startColor, int endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float a1 = (startColor >> 24 & 255) / 255.0F;
        float r1 = (startColor >> 16 & 255) / 255.0F;
        float g1 = (startColor >> 8  & 255) / 255.0F;
        float b1 = (startColor       & 255) / 255.0F;

        float a2 = (endColor >> 24 & 255) / 255.0F;
        float r2 = (endColor >> 16 & 255) / 255.0F;
        float g2 = (endColor >> 8  & 255) / 255.0F;
        float b2 = (endColor       & 255) / 255.0F;

        buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(r1, g1, b1, a1).normal(0, 1, 0);
        buffer.vertex(matrix, (float) end.x,   (float) end.y,   (float) end.z  ).color(r2, g2, b2, a2).normal(0, 1, 0);
    }
}
