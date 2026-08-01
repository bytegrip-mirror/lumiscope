package com.lumijiez.lumiscope.util;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

public class CustomMath {

    /**
     * Calculate the raw direction angle from one player to another.
     * @return Angle in degrees [0, 360), where 0 = North, 90 = East, etc.
     */
    public static double calculateRawAngle(EntityPlayerMP from, EntityPlayerMP to) {
        double deltaX = to.posX - from.posX;
        double deltaZ = to.posZ - from.posZ;

        double angle = MathHelper.atan2(deltaZ, deltaX) * (180.0 / Math.PI) - 90.0;
        if (angle < 0) angle += 360.0;

        return (angle + 180.0) % 360.0;
    }

    /**
     * Apply a large Perlin-noise-based angular error to prevent triangulation.
     * Error ranges from ±25° to ±35° depending on the noise value.
     *
     * @param rawAngleDegrees The true angle in degrees
     * @param seed A unique seed combining world time and target UUID for per-scan uniqueness
     * @return Noisy angle in degrees [0, 360)
     */
    public static double applyLargeAngularError(double rawAngleDegrees, long seed) {
        double noiseVal = PerlinNoise.noise(seed * 0.001 + rawAngleDegrees * 0.01);
        double errorDegrees = noiseVal * 35.0;
        double result = rawAngleDegrees + errorDegrees;
        return normalizeAngle(result);
    }

    /**
     * Normalize an angle to [0, 360).
     */
    public static double normalizeAngle(double angle) {
        return ((angle % 360.0) + 360.0) % 360.0;
    }

    /**
     * Interpolate a color from green (close) to red (far) based on distance.
     */
    public static int interpolateColor(int maxDistance, int minDistance, int currentDistance) {
        int clampedDistance = Math.max(minDistance, Math.min(maxDistance, currentDistance));
        float ratio = (float) (maxDistance - clampedDistance) / (maxDistance - minDistance);

        int r = (int) (ratio * 255);
        int g = (int) ((1 - ratio) * 255);
        int b = 0;

        return new Color(r, g, b).getRGB();
    }
}
