package com.lumijiez.lumiscope.util;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class ScopeRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();
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

    private static final int SCOPE_BG = 0xFF0A1A0A;     // very dark green-black
    private static final int RING_COLOR = 0x3300FF00;    // subtle green ring
    private static final int SWEEP_COLOR = 0x5500FF00;   // sweep line
    private static final int STATIC_COLOR = 0x4400CC00;  // static dots

    private static long frameCounter = 0;

    /**
     * Render the full scope display.
     * @param cx center X on screen
     * @param cy center Y on screen
     * @param radius radius of the scope circle
     * @param blips list of detected player blips (null or empty = no data)
     * @param partialTicks minecraft partial ticks
     */
    public static void renderScope(int cx, int cy, int radius, List<RadarBlip> blips, float partialTicks) {
        frameCounter++;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 1. Dark scope background circle
        drawFilledCircle(cx, cy, radius, SCOPE_BG);

        // 2. Concentric rings
        int[] ringRadii = {radius / 5, radius * 2 / 5, radius * 3 / 5, radius * 4 / 5, radius};
        for (int r : ringRadii) {
            drawCircleOutline(cx, cy, r, RING_COLOR, 1.5f);
        }

        // 3. Crosshair lines (subtle)
        int crossAlpha = 0x2200AA00;
        drawLine(cx - radius, cy, cx + radius, cy, crossAlpha);
        drawLine(cx, cy - radius, cx, cy + radius, crossAlpha);

        // 4. Compass labels (N, E, S, W) — done via text later in GUI
        // But we draw small tick marks at cardinal directions
        float tickLen = radius * 0.08f;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            float ix = cx + (float) Math.cos(angle) * radius;
            float iy = cy + (float) Math.sin(angle) * radius;
            float ox = cx + (float) Math.cos(angle) * (radius - tickLen);
            float oy = cy + (float) Math.sin(angle) * (radius - tickLen);
            drawLine(ox, oy, ix, iy, 0x5500AA00);
        }

        // 5. Rotating sweep line
        float sweepAngle = (float) ((frameCounter * 0.5 + partialTicks * 0.5) % 360);
        double sweepRad = Math.toRadians(sweepAngle);
        float sweepX = cx + (float) Math.cos(sweepRad) * radius;
        float sweepY = cy + (float) Math.sin(sweepRad) * radius;

        // Draw sweeper as a fading gradient line
        GlStateManager.glBegin(GL11.GL_TRIANGLE_FAN);
        GlStateManager.glVertex3f(cx, cy, 0);
        setGlColor(SWEEP_COLOR & 0x00FFFFFF, 0.15f);
        GlStateManager.glVertex3f(sweepX, sweepY, 0);
        // Slightly spread the sweep for a wedge effect
        double sweepRad2 = Math.toRadians(sweepAngle - 8);
        float sweepX2 = cx + (float) Math.cos(sweepRad2) * radius;
        float sweepY2 = cy + (float) Math.sin(sweepRad2) * radius;
        setGlColor(0x00FF00, 0.02f);
        GlStateManager.glVertex3f(sweepX2, sweepY2, 0);
        GlStateManager.glEnd();

        // 6. Player blips
        if (blips != null && !blips.isEmpty() && !RadarNetworkHandler.areResultsStale()) {
            for (RadarBlip blip : blips) {
                renderBlip(cx, cy, radius, blip, partialTicks);
            }
        }

        // 7. Static noise overlay
        renderStatic(cx, cy, radius, partialTicks);

        // 8. Outer ring (more prominent)
        drawCircleOutline(cx, cy, radius, 0x5500FF00, 2.5f);

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * Render a single player blip — a pulsing, wavering blob of light.
     */
    private static void renderBlip(int cx, int cy, int radius, RadarBlip blip, float partialTicks) {
        // Per-blip angular jitter (based on blip hash + time)
        long blipSeed = Double.doubleToLongBits(blip.direction) + frameCounter;
        RANDOM.setSeed(blipSeed);
        double jitter = (RANDOM.nextDouble() - 0.5) * Math.toRadians(5.0); // ±2.5°
        double displayAngle = blip.direction + jitter;

        // Distance from center: farther players are closer to center
        float distRatio;
        switch (blip.distanceTier) {
            case 0: distRatio = 0.95f; break; // very close — near edge
            case 1: distRatio = 0.80f; break;
            case 2: distRatio = 0.60f; break;
            case 3: distRatio = 0.40f; break;
            case 4: distRatio = 0.25f; break;
            default: distRatio = 0.12f; break; // extremely far — near center
        }

        float bx = cx + (float) Math.cos(displayAngle) * radius * distRatio;
        float by = cy + (float) Math.sin(displayAngle) * radius * distRatio;

        // Pulse effect
        double pulse = 0.7 + 0.3 * Math.sin((frameCounter + blipSeed % 100) * 0.1);

        // Blob size: inverse to distance (closer = bigger), affected by player count
        float baseSize = (1.0f - distRatio) * 10f + 3f;
        float size = baseSize * (float) pulse * (1.0f + blip.playerCount * 0.3f);

        int baseColor = TIER_COLORS[blip.distanceTier];

        // Draw main glow blob (several overlapping circles with decreasing alpha)
        float[] alphas = {0.7f, 0.4f, 0.2f, 0.1f};
        float[] sizes = {1.0f, 1.6f, 2.3f, 3.2f};
        for (int i = 0; i < alphas.length; i++) {
            setGlColor(baseColor & 0x00FFFFFF, alphas[i]);
            drawFilledCircle(bx, by, size * sizes[i], baseColor & 0x00FFFFFF);
        }

        // Draw tiny bright core
        setGlColor(0xFFFFFF, 0.9f);
        drawFilledCircle(bx, by, size * 0.35f, 0xFFFFFF);

        // If multiple players merged, draw a small "+N" indicator
        if (blip.playerCount > 1) {
            // We'll handle this with text in the GUI layer
        }
    }

    /**
     * Static noise — pseudo-random dots flickering across the scope area.
     */
    private static void renderStatic(int cx, int cy, int radius, float partialTicks) {
        int numDots = 60;
        long staticSeed = frameCounter / 3; // Update every ~3 frames

        RANDOM.setSeed(staticSeed);
        GlStateManager.glPointSize(1.5f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < numDots; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double dist = RANDOM.nextDouble() * radius * 0.95;
            float sx = cx + (float) (Math.cos(angle) * dist);
            float sy = cy + (float) (Math.sin(angle) * dist);

            int alpha = RANDOM.nextInt(100) + 20;
            int color = (alpha << 24) | (0x00CC00);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            buffer.pos(sx, sy, 0).color(r, g, b, alpha).endVertex();
        }

        tessellator.draw();
        GlStateManager.glPointSize(1.0f);
    }

    // ---------- GL Primitives ----------

    private static void drawFilledCircle(float cx, float cy, float radius, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(cx, cy, 0).color(r, g, b, 255).endVertex();

        int segments = 64;
        for (int i = 0; i <= segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            buffer.pos(x, y, 0).color(r, g, b, 255).endVertex();
        }
        tessellator.draw();
    }

    private static void drawCircleOutline(float cx, float cy, float radius, int color, float lineWidth) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        GlStateManager.glLineWidth(lineWidth);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);

        int segments = 72;
        for (int i = 0; i < segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            buffer.pos(x, y, 0).color(r, g, b, 255).endVertex();
        }
        tessellator.draw();
        GlStateManager.glLineWidth(1.0f);
    }

    private static void drawLine(float x1, float y1, float x2, float y2, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(x1, y1, 0).color(r, g, b, 255).endVertex();
        buffer.pos(x2, y2, 0).color(r, g, b, 255).endVertex();
        tessellator.draw();
    }

    private static void setGlColor(int rgb, float alpha) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        GlStateManager.color(r, g, b, alpha);
    }

    public static int getTierColor(byte tier) {
        if (tier >= 0 && tier < TIER_COLORS.length) {
            return TIER_COLORS[tier];
        }
        return 0xFFFFFFFF;
    }
}
