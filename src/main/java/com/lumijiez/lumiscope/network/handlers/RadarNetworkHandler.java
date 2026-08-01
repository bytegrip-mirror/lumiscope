package com.lumijiez.lumiscope.network.handlers;

import com.lumijiez.lumiscope.items.radars.RadarDevice;
import com.lumijiez.lumiscope.network.packets.RadarScanRequestPacket;
import com.lumijiez.lumiscope.network.packets.RadarScanResultPacket;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import com.lumijiez.lumiscope.potions.PotionManager;
import com.lumijiez.lumiscope.util.PerlinNoise;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.*;

public class RadarNetworkHandler {
    public static final int COOLDOWN_TICKS = 800; // 40 seconds
    public static final double ANGULAR_ERROR_BASE = 25.0;
    public static final double ANGULAR_ERROR_NOISE_SCALE = 10.0;
    public static final double MERGE_ANGLE_THRESHOLD = Math.toRadians(15.0);
    public static final int RESULTS_DECAY_MS = 60000; // 60 seconds before results go stale

    private static final SimpleNetworkWrapper NETWORK_CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("lumiscope_radar");

    // Server-side cooldown tracking
    private static final Map<UUID, Long> cooldownMap = new HashMap<>();
    // Server-side last scan timestamp tracking
    private static final Map<UUID, Long> lastScanTime = new HashMap<>();

    // Static cache for GUI to read results between opens
    private static List<RadarBlip> cachedBlips = new ArrayList<>();
    private static byte cachedStatus = RadarScanResultPacket.STATUS_SUCCESS;
    private static long cachedTimestamp = 0;

    public static void registerMessages() {
        NETWORK_CHANNEL.registerMessage(
                RadarScanRequestPacket.Handler.class,
                RadarScanRequestPacket.class, 0, Side.SERVER);
        NETWORK_CHANNEL.registerMessage(
                RadarScanResultPacket.Handler.class,
                RadarScanResultPacket.class, 1, Side.CLIENT);
    }

    public static SimpleNetworkWrapper getNetworkChannel() {
        return NETWORK_CHANNEL;
    }

    // ---------- Scan Request (Client → Server) ----------

    public static IMessage handleScanRequest(EntityPlayerMP player) {
        // Check if jammed
        if (player.isPotionActive(PotionManager.JAMMED_POTION_EFFECT)) {
            return new RadarScanResultPacket(
                    Collections.emptyList(),
                    RadarScanResultPacket.STATUS_JAMMED,
                    System.currentTimeMillis());
        }

        // Check cooldown
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueID();
        if (cooldownMap.containsKey(playerId)) {
            long lastScan = cooldownMap.get(playerId);
            long elapsed = now - lastScan;
            if (elapsed < COOLDOWN_TICKS * 50L) { // ticks to ms
                return new RadarScanResultPacket(
                        Collections.emptyList(),
                        RadarScanResultPacket.STATUS_COOLDOWN,
                        now);
            }
        }

        // Check and consume fuel (Ender Pearl)
        if (!consumeFuel(player)) {
            return new RadarScanResultPacket(
                    Collections.emptyList(),
                    RadarScanResultPacket.STATUS_NO_FUEL,
                    now);
        }

        // Damage the radar device
        damageRadarDevice(player);

        // Record cooldown
        cooldownMap.put(playerId, now);
        lastScanTime.put(playerId, now);

        // Find and compute players
        List<RadarBlip> blips = scanForPlayers(player);

        return new RadarScanResultPacket(blips, RadarScanResultPacket.STATUS_SUCCESS, now);
    }

    // ---------- Player Scanning ----------

    private static List<RadarBlip> scanForPlayers(EntityPlayerMP scanner) {
        List<RadarBlip> rawBlips = new ArrayList<>();

        for (EntityPlayerMP target : scanner.getServer().getPlayerList().getPlayers()) {
            if (shouldIncludePlayer(scanner, target)) {
                double distance = scanner.getDistance(target);
                byte tier = getDistanceTier(distance);
                double rawAngle = calculateRawAngle(scanner, target);
                double errorAngle = applyPerlinError(rawAngle, scanner, target);
                double radAngle = Math.toRadians(normalizeAngle(errorAngle));

                rawBlips.add(new RadarBlip(radAngle, tier, (byte) 1));
            }
        }

        // Merge blips that are in the same direction (±15°)
        return mergeNearbyBlips(rawBlips);
    }

    private static boolean shouldIncludePlayer(EntityPlayerMP scanner, EntityPlayerMP target) {
        if (target.equals(scanner)) return false;
        if (scanner.dimension != target.dimension) return false;
        if (target.isPotionActive(PotionManager.JAMMED_POTION_EFFECT)) return false;
        return true;
    }

    // ---------- Direction Calculation ----------

    private static double calculateRawAngle(EntityPlayerMP scanner, EntityPlayerMP target) {
        double deltaX = target.posX - scanner.posX;
        double deltaZ = target.posZ - scanner.posZ;
        double angle = MathHelper.atan2(deltaZ, deltaX) * (180.0 / Math.PI) - 90.0;
        if (angle < 0) angle += 360.0;
        return (angle + 180.0) % 360.0;
    }

    private static double normalizeAngle(double angle) {
        return (angle + 360.0) % 360.0;
    }

    private static double applyPerlinError(double baseAngle, EntityPlayerMP scanner, EntityPlayerMP target) {
        // Unique seed per player-pair + time for non-repeatable error
        double timeSec = System.currentTimeMillis() / 1000.0;
        double pairHash = Math.abs((scanner.getUniqueID().hashCode() * 31L
                + target.getUniqueID().hashCode()) % 10000) / 10000.0;
        double noiseInput = timeSec + pairHash * 100.0;
        double noiseValue = PerlinNoise.noise(noiseInput);
        double noiseValue2 = PerlinNoise.noise(noiseInput + 50.0);

        // Error ranges from ±25° to ±35°
        double errorDegrees = ANGULAR_ERROR_BASE + Math.abs(noiseValue2) * ANGULAR_ERROR_NOISE_SCALE;
        double errorSign = noiseValue > 0 ? 1 : -1;

        return baseAngle + errorSign * errorDegrees;
    }

    // ---------- Distance Tiers ----------

    public enum DistanceTier {
        VERY_CLOSE(0, 0, 50, "Very Close"),
        CLOSE(1, 50, 150, "Close"),
        MODERATE(2, 150, 400, "Moderate"),
        FAR(3, 400, 800, "Far"),
        VERY_FAR(4, 800, 2000, "Very Far"),
        EXTREMELY_FAR(5, 2000, Integer.MAX_VALUE, "Extremely Far");

        public final byte id;
        public final int minDist;
        public final int maxDist;
        public final String label;

        DistanceTier(int id, int minDist, int maxDist, String label) {
            this.id = (byte) id;
            this.minDist = minDist;
            this.maxDist = maxDist;
            this.label = label;
        }
    }

    private static byte getDistanceTier(double distance) {
        for (DistanceTier tier : DistanceTier.values()) {
            if (distance >= tier.minDist && distance < tier.maxDist) {
                return tier.id;
            }
        }
        return DistanceTier.EXTREMELY_FAR.id;
    }

    public static String getDistanceLabel(byte tierId) {
        for (DistanceTier tier : DistanceTier.values()) {
            if (tier.id == tierId) return tier.label;
        }
        return "Unknown";
    }

    // ---------- Blip Merging ----------

    private static List<RadarBlip> mergeNearbyBlips(List<RadarBlip> rawBlips) {
        if (rawBlips.isEmpty()) return rawBlips;

        List<RadarBlip> merged = new ArrayList<>();
        boolean[] consumed = new boolean[rawBlips.size()];

        for (int i = 0; i < rawBlips.size(); i++) {
            if (consumed[i]) continue;
            RadarBlip anchor = rawBlips.get(i);
            byte count = anchor.playerCount;
            double sumDir = anchor.direction;
            byte bestTier = anchor.distanceTier; // Show the closest among merged

            for (int j = i + 1; j < rawBlips.size(); j++) {
                if (consumed[j]) continue;
                RadarBlip other = rawBlips.get(j);
                double angleDiff = Math.abs(normalizeAngleRad(anchor.direction - other.direction));
                if (angleDiff < MERGE_ANGLE_THRESHOLD || angleDiff > Math.PI * 2 - MERGE_ANGLE_THRESHOLD) {
                    consumed[j] = true;
                    count++;
                    sumDir += other.direction;
                    if (other.distanceTier < bestTier) {
                        bestTier = other.distanceTier;
                    }
                }
            }

            double avgDir = sumDir / count;
            merged.add(new RadarBlip(avgDir, bestTier, count));
        }

        return merged;
    }

    private static double normalizeAngleRad(double rad) {
        while (rad < 0) rad += Math.PI * 2;
        while (rad >= Math.PI * 2) rad -= Math.PI * 2;
        return rad;
    }

    // ---------- Fuel Consumption ----------

    private static boolean consumeFuel(EntityPlayerMP player) {
        // Check main inventory for Ender Pearls
        NonNullList<ItemStack> inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty() && stack.getItem() == Items.ENDER_PEARL) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inventory.set(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    // ---------- Durability Damage ----------

    private static void damageRadarDevice(EntityPlayerMP player) {
        ItemStack mainHand = player.getHeldItemMainhand();
        ItemStack offHand = player.getHeldItemOffhand();

        // Damage whichever hand holds the radar
        if (mainHand.getItem() instanceof RadarDevice) {
            mainHand.damageItem(1, player);
        } else if (offHand.getItem() instanceof RadarDevice) {
            offHand.damageItem(1, player);
        }
    }

    // ---------- Cooldown Utilities ----------

    public static long getCooldownRemainingMs(UUID playerId) {
        Long lastScan = cooldownMap.get(playerId);
        if (lastScan == null) return 0;
        long elapsed = System.currentTimeMillis() - lastScan;
        long cooldownMs = COOLDOWN_TICKS * 50L;
        return Math.max(0, cooldownMs - elapsed);
    }

    public static boolean isOnCooldown(UUID playerId) {
        return getCooldownRemainingMs(playerId) > 0;
    }

    // ---------- Result Cache (for GUI) ----------

    public static List<RadarBlip> getCachedBlips() { return cachedBlips; }
    public static byte getCachedStatus() { return cachedStatus; }
    public static long getCachedTimestamp() { return cachedTimestamp; }
    public static boolean areResultsStale() {
        return System.currentTimeMillis() - cachedTimestamp > RESULTS_DECAY_MS;
    }

    public static void cacheResults(List<RadarBlip> blips, byte status, long timestamp) {
        cachedBlips = blips;
        cachedStatus = status;
        cachedTimestamp = timestamp;
    }
}
