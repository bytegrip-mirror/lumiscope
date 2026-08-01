package com.lumijiez.lumiscope.network.records;

public class RadarBlip {
    public double direction;
    public byte distanceTier;
    public byte playerCount;

    public RadarBlip() {
        this.direction = 0;
        this.distanceTier = 0;
        this.playerCount = 0;
    }

    public RadarBlip(double direction, byte distanceTier, byte playerCount) {
        this.direction = direction;
        this.distanceTier = distanceTier;
        this.playerCount = playerCount;
    }
}
