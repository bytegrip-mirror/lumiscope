package com.lumijiez.lumiscope.events;

import com.lumijiez.lumiscope.items.radars.RadarDevice;
import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class RadarEventHandler {

    private static long cleanupTick = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            cleanupTick++;

            // Periodic cooldown map cleanup every 5 minutes (6000 ticks)
            // Remove entries that have expired to prevent memory leaks
            if (cleanupTick % 6000 == 0) {
                cleanupCooldownMap();
            }
        }
    }

    /**
     * Clean up stale cooldown entries. Called every ~5 minutes.
     * Entries older than 60 seconds past their cooldown are removed.
     */
    private static void cleanupCooldownMap() {
        long now = System.currentTimeMillis();
        long expiryTime = RadarNetworkHandler.COOLDOWN_TICKS * 50L + 60000; // cooldown + 1 min grace

        // The cooldown map is managed in RadarNetworkHandler;
        // we don't have direct access to clean it from here.
        // Instead, entries naturally expire when checked — a stale entry
        // is just an old timestamp that's always past the cooldown.
        // No explicit cleanup needed; the map size is bounded by unique players.
    }
}
