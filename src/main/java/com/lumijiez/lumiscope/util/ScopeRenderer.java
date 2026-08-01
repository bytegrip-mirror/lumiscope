package com.lumijiez.lumiscope.util;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class ScopeRenderer {

    private static final Random RANDOM = new Random();

    // Distance tier colors (ARGB)
    private static final int[] TIER_COLORS = {
            0xFFFFB000, // VERY_CLOSE - amber
            0xFFFFD700, // CLOSE - gold
            0xFFADFF2F, // MODERATE - yellow-green
            0xFF00CED1, // FAR - teal
            0xFF4169E1, // VERY_FAR - royal blue
            0xFF191970, // EXTREMELY_FAR - midnight blue
    };

    private static final int SCOPE_BG = 0xFF0A1A0A;
    private static final int RING_COLOR = 0x5500FF00;
    private static final int OUTER_RING = 0x8800FF00;

    private static long frameCounter = 0;

    public static void renderScope(int cx, int cy, int radius, List<RadarBlip> blips, float partialTicks) {
        frameCounter++;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Reset color to full white+opaque so vertex colors are used as-is
        GlStateManager.color(1f, 1f, 1f, 1f);

        // 1. Dark scope background
        drawFilledCircle(cx, cy, radius, SCOPE_BG);

        // 2. Concentric rings
        int[] ringRadii = {radius / 5, radius * 2 / 5, radius * 3 / 5, radius * 4 / 5};
        for (int r : ringRadii) {
            drawCircleOutline(cx, cy, r, RING_COLOR, 1.5f);
        }

        // 3. Crosshair
        drawLine(cx - radius, cy, cx + radius, cy, 0x2200AA00);
        drawLine(cx, cy - radius, cx, cy + radius, 0x2200AA00);

        // 4. Cardinal tick marks
        float tickLen = radius * 0.08f;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            float ix = cx + (float) Math.cos(angle) * radius;
            float iy = cy + (float) Math.sin(angle) * radius;
            float ox = cx + (float) Math.cos(angle) * (radius - tickLen);
            float oy = cy + (float) Math.sin(angle) * (radius - tickLen);
            drawLine(ox, oy, ix, iy, 0x5500AA00);
        }

        // 5. Rotating sweep line (Tessellator-based, not raw GL)
        float sweepAngle = (float) ((frameCounter * 0.5 + partialTicks * 0.5) % 360);
        double sweepRad = Math.toRadians(sweepAngle);
        float sweepX = cx + (float) Math.cos(sweepRad) * radius;
        float sweepY = cy + (float) Math.sin(sweepRad) * radius;
        double sweepRad2 = Math.toRadians(sweepAngle - 8);
        float sweepX2 = cx + (float) Math.cos(sweepRad2) * radius;
        float sweepY2 = cy + (float) Math.sin(sweepRad2) * radius;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        // Sweep wedge: bright at leading edge, fading behind
        buf.pos(cx, cy, 0).color(0, 255, 0, 40).endVertex();
        buf.pos(sweepX, sweepY, 0).color(0, 255, 0, 40).endVertex();
        buf.pos(sweepX2, sweepY2, 0).color(0, 255, 0, 5).endVertex();
        tess.draw();

        // 6. Player blips — drawn bright and bold
        if (blips != null && !blips.isEmpty() && !RadarNetworkHandler.areResultsStale()) {
            for (RadarBlip blip : blips) {
                renderBlip(cx, cy, radius, blip);
            }
        }

        // 7. Static noise
        renderStatic(cx, cy, radius);

        // 8. Outer ring (prominent)
        drawCircleOutline(cx, cy, radius, OUTER_RING, 2.5f);

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderBlip(int cx, int cy, int radius, RadarBlip blip) {
        // Per-blip jitter
        long blipSeed = Double.doubleToLongBits(blip.direction) + frameCounter;
        RANDOM.setSeed(blipSeed);
        double jitter = (RANDOM.nextDouble() - 0.5) * Math.toRadians(5.0);
        double displayAngle = blip.direction + jitter;

        // Distance tier → radial position (closer = further from center)
        float distRatio;
        switch (blip.distanceTier) {
            case 0: distRatio = 0.92f; break;
            case 1: distRatio = 0.75f; break;
            case 2: distRatio = 0.55f; break;
            case 3: distRatio = 0.35f; break;
            case 4: distRatio = 0.22f; break;
            default: distRatio = 0.10f; break;
        }

        float bx = cx + (float) Math.cos(displayAngle) * radius * distRatio;
        float by = cy + (float) Math.sin(displayAngle) * radius * distRatio;

        int tierColor = TIER_COLORS[blip.distanceTier];
        int r = (tierColor >> 16) & 0xFF;
        int g = (tierColor >> 8) & 0xFF;
        int b = tierColor & 0xFF;

        // Pulse
        double pulse = 0.7 + 0.3 * Math.sin((frameCounter + blipSeed % 100) * 0.1);
        float baseSize = (1.0f - distRatio) * 10f + 4f;
        float size = baseSize * (float) pulse * (1.0f + blip.playerCount * 0.3f);
        if (size < 3f) size = 3f;
        if (size > 20f) size = 20f;

        // Draw a bright glow: outer halo → mid glow → bright core
        // All using the Tessellator with proper per-vertex alpha
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf;

        // Outer glow (large, very transparent)
        buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(bx, by, 0).color(r, g, b, 30).endVertex();
        for (int i = 0; i <= 32; i++) {
            double a = (i / 32.0) * Math.PI * 2;
            float px = bx + (float) Math.cos(a) * size * 3.0f;
            float py = by + (float) Math.sin(a) * size * 3.0f;
            buf.pos(px, py, 0).color(r, g, b, 30).endVertex();
        }
        tess.draw();

        // Mid glow
        buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(bx, by, 0).color(r, g, b, 100).endVertex();
        for (int i = 0; i <= 32; i++) {
            double a = (i / 32.0) * Math.PI * 2;
            float px = bx + (float) Math.cos(a) * size * 1.8f;
            float py = by + (float) Math.sin(a) * size * 1.8f;
            buf.pos(px, py, 0).color(r, g, b, 100).endVertex();
        }
        tess.draw();

        // Bright core
        buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(bx, by, 0).color(255, 255, 255, 220).endVertex();
        for (int i = 0; i <= 32; i++) {
            double a = (i / 32.0) * Math.PI * 2;
            float px = bx + (float) Math.cos(a) * size * 0.6f;
            float py = by + (float) Math.sin(a) * size * 0.6f;
            buf.pos(px, py, 0).color(255, 255, 255, 220).endVertex();
        }
        tess.draw();
    }

    private static void renderStatic(int cx, int cy, int radius) {
        int numDots = 50;
        long staticSeed = frameCounter / 3;
        RANDOM.setSeed(staticSeed);
        GL11.glPointSize(1.5f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < numDots; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double dist = RANDOM.nextDouble() * radius * 0.92;
            float sx = cx + (float) (Math.cos(angle) * dist);
            float sy = cy + (float) (Math.sin(angle) * dist);
            int alpha = RANDOM.nextInt(80) + 15;
            buf.pos(sx, sy, 0).color(0, 200, 0, alpha).endVertex();
        }
        tess.draw();
        GL11.glPointSize(1.0f);
    }

    // ---------- GL Primitives (all Tessellator-based) ----------

    private static void drawFilledCircle(float cx, float cy, float radius, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(cx, cy, 0).color(r, g, b, a).endVertex();
        int segments = 64;
        for (int i = 0; i <= segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            buf.pos(x, y, 0).color(r, g, b, a).endVertex();
        }
        tess.draw();
    }

    private static void drawCircleOutline(float cx, float cy, float radius, int color, float lineWidth) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        GlStateManager.glLineWidth(lineWidth);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        int segments = 72;
        for (int i = 0; i < segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            buf.pos(x, y, 0).color(r, g, b, a).endVertex();
        }
        tess.draw();
        GlStateManager.glLineWidth(1.0f);
    }

    private static void drawLine(float x1, float y1, float x2, float y2, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(x1, y1, 0).color(r, g, b, a).endVertex();
        buf.pos(x2, y2, 0).color(r, g, b, a).endVertex();
        tess.draw();
    }

    public static int getTierColor(byte tier) {
        if (tier >= 0 && tier < TIER_COLORS.length) {
            return TIER_COLORS[tier];
        }
        return 0xFFFFFFFF;
    }
}
