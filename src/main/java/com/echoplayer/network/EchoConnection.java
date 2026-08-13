package com.echoplayer.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.SocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

public class EchoConnection
extends Connection {
    private Channel echoChannel = new EmbeddedChannel();

    public EchoConnection(PacketFlow pReceiving) {
        super(pReceiving);
    }

    @Override
    public Channel channel() {
        return this.echoChannel;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return new SocketAddress(){

            public String toString() {
                return "EchoPlayer_Connection";
            }
        };
    }

    @Override
    public void channelActive(ChannelHandlerContext pContext) throws Exception {
    }

    @Override
    public void channelInactive(ChannelHandlerContext pContext) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext pContext, Throwable pException) {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext pContext, Packet<?> pPacket) {
    }

    @Override
    public void setListener(PacketListener pHandler) {
        super.setListener(pHandler);
    }

    @Override
    public void send(Packet<?> pPacket) {
    }

    @Override
    public void send(Packet<?> pPacket, PacketSendListener pListener) {
    }

    @Override
    public void tick() {
    }

    @Override
    protected void tickSecond() {
    }

    @Override
    public void disconnect(Component pMessage) {
    }

    @Override
    public boolean isMemoryConnection() {
        return true;
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void handleDisconnection() {
    }
}

