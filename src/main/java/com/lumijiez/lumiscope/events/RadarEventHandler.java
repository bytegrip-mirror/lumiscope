package com.lumijiez.lumiscope.events;

import net.minecraftforge.fml.common.Mod;

/**
 * Radar events are now handled on-demand via RadarNetworkHandler.
 * No more passive tick spam — scans happen only when the player
 * actively uses the Lumiscope GUI and clicks "Scan".
 */
@Mod.EventBusSubscriber
public class RadarEventHandler {
    // Intentionally empty — all radar logic is in RadarNetworkHandler.
    // The old implementation sent packets to every player 20 times/sec.
    // Now: 0 ticks/sec server overhead for radar scanning.
}
