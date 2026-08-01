package com.lumijiez.lumiscope.network.packets;

import com.lumijiez.lumiscope.gui.RadarGuiScreen;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class RadarScanResultPacket implements IMessage {

    private List<RadarBlip> blips;
    private byte status;
    private long scanTimestamp;

    // Status codes
    public static final byte STATUS_SUCCESS = 0;
    public static final byte STATUS_NO_FUEL = 1;
    public static final byte STATUS_JAMMED = 2;
    public static final byte STATUS_COOLDOWN = 3;

    public RadarScanResultPacket() {
        this.blips = new ArrayList<>();
        this.status = STATUS_SUCCESS;
        this.scanTimestamp = 0;
    }

    public RadarScanResultPacket(List<RadarBlip> blips, byte status, long scanTimestamp) {
        this.blips = blips;
        this.status = status;
        this.scanTimestamp = scanTimestamp;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(status);
        buf.writeLong(scanTimestamp);
        buf.writeInt(blips.size());
        for (RadarBlip blip : blips) {
            buf.writeDouble(blip.direction);
            buf.writeByte(blip.distanceTier);
            buf.writeByte(blip.playerCount);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        status = buf.readByte();
        scanTimestamp = buf.readLong();
        int size = buf.readInt();
        blips = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double direction = buf.readDouble();
            byte distanceTier = buf.readByte();
            byte playerCount = buf.readByte();
            blips.add(new RadarBlip(direction, distanceTier, playerCount));
        }
    }

    public static class Handler implements IMessageHandler<RadarScanResultPacket, IMessage> {
        @Override
        public IMessage onMessage(RadarScanResultPacket message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                // Store results where the GUI can read them
                RadarGuiScreen.onScanResult(message.blips, message.status, message.scanTimestamp);
            });
            return null;
        }
    }
}
