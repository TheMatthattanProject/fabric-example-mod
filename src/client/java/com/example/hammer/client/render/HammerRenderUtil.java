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
            vertexLine(vertices, entry, from, r, g, b, a, lineWidth);
            vertexLine(vertices, entry, to, r, g, b, a, lineWidth);
        });
    }

    public static void submitCircle(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer layer, Vec3d center, float radius, int argb, float lineWidth, int segments) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        int safeSegments = MathHelper.clamp(segments, 8, 256);
        queue.submitCustom(matrices, layer, (entry, vertices) -> {
            double prevX = center.x + Math.cos(0.0D) * radius;
            double prevZ = center.z + Math.sin(0.0D) * radius;
            for (int i = 1; i <= safeSegments; i++) {
                double theta = (Math.PI * 2.0D) * (i / (double) safeSegments);
                double x = center.x + Math.cos(theta) * radius;
                double z = center.z + Math.sin(theta) * radius;

                vertexLine(vertices, entry, new Vec3d(prevX, center.y, prevZ), r, g, b, a, lineWidth);
                vertexLine(vertices, entry, new Vec3d(x, center.y, z), r, g, b, a, lineWidth);

                prevX = x;
                prevZ = z;
            }
        });
    }

    private static void vertexLine(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d pos, int r, int g, int b, int a, float lineWidth) {
        vertices.vertex(entry, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, a)
                .normal(entry, 0.0F, 1.0F, 0.0F);
        vertices.lineWidth(lineWidth);
    }
}
