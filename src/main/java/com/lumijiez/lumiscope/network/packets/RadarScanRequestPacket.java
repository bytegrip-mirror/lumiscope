package com.lumijiez.lumiscope.network.packets;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class RadarScanRequestPacket implements IMessage {

    public RadarScanRequestPacket() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<RadarScanRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(RadarScanRequestPacket message, MessageContext ctx) {
            return RadarNetworkHandler.handleScanRequest(ctx.getServerHandler().player);
        }
    }
}
