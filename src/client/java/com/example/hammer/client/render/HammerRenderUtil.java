package com.example.hammer.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class HammerRenderUtil {
    private HammerRenderUtil() {
    }

    public static void submitLine(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer layer, Vec3d from, Vec3d to, int argb, float lineWidth) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        queue.submitCustom(matrices, layer, (entry, vertices) -> {
            vertexLine(vertices, entry, from.x, from.y, from.z, r, g, b, a, lineWidth);
            vertexLine(vertices, entry, to.x, to.y, to.z, r, g, b, a, lineWidth);
        });
    }

    public static void submitCircle(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer layer, Vec3d center, float radius, int argb, float lineWidth, int segments) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        int safeSegments = MathHelper.clamp(segments, 8, 256);
        queue.submitCustom(matrices, layer, (entry, vertices) -> {
            double centerX = center.x;
            double centerY = center.y;
            double centerZ = center.z;
            double prevX = centerX + radius;
            double prevZ = centerZ;
            for (int i = 1; i <= safeSegments; i++) {
                double theta = (Math.PI * 2.0D) * (i / (double) safeSegments);
                double x = centerX + Math.cos(theta) * radius;
                double z = centerZ + Math.sin(theta) * radius;

                vertexLine(vertices, entry, prevX, centerY, prevZ, r, g, b, a, lineWidth);
                vertexLine(vertices, entry, x, centerY, z, r, g, b, a, lineWidth);

                prevX = x;
                prevZ = z;
            }
        });
    }

    private static void vertexLine(VertexConsumer vertices, MatrixStack.Entry entry, double x, double y, double z, int r, int g, int b, int a, float lineWidth) {
        vertices.vertex(entry, (float) x, (float) y, (float) z)
                .color(r, g, b, a)
                .normal(entry, 0.0F, 1.0F, 0.0F);
        vertices.lineWidth(lineWidth);
    }
}
