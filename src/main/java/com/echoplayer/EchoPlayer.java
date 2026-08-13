package com.echoplayer;

import com.echoplayer.CommonClass;
import com.echoplayer.Constants;
import com.echoplayer.client.ClientPacketHandler;
import com.echoplayer.client.ClientPossessionData;
import com.echoplayer.client.Keybinds;
import com.echoplayer.command.EchoPlayerCommand;
import com.echoplayer.data.EchoPlayerSavedData;
import com.echoplayer.network.NetworkPackets;
import com.echoplayer.network.ServerPacketHandler;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(value="echoplayer")
public class EchoPlayer {
    public static SimpleChannel CHANNEL;

    public EchoPlayer() {
        CommonClass.init();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> EchoPlayerCommand.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setupNetwork);
        DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> {
            FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent event) -> event.register(Keybinds.UNPOSSESS_KEY));
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.START) {
                    ClientPossessionData.clientTick(Minecraft.getInstance());
                } else {
                    Keybinds.clientTick(Minecraft.getInstance());
                    ClientPossessionData.afterClientTick(Minecraft.getInstance());
                }
            });
        });
    }

    private void setupNetwork(FMLCommonSetupEvent event) {
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("echoplayer", "main"), () -> "1", "1"::equals, "1"::equals);
        CHANNEL.registerMessage(0, CustomPayload.class, (msg, buf) -> {
            buf.writeResourceLocation(msg.id);
            buf.writeBytes(msg.buf);
        }, buf -> {
            ResourceLocation id = buf.readResourceLocation();
            return new CustomPayload(id, new FriendlyByteBuf(buf.readBytes(buf.readableBytes())));
        }, (msg, ctx) -> {
            ((NetworkEvent.Context)ctx.get()).enqueueWork(() -> EchoPlayer.dispatchClientPayload((Supplier)ctx, msg));
            ((NetworkEvent.Context)ctx.get()).setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, ServerboundPayload.class, (msg, buf) -> {
            buf.writeResourceLocation(msg.id);
            buf.writeBytes(msg.buf);
        }, buf -> {
            ResourceLocation id = buf.readResourceLocation();
            return new ServerboundPayload(id, new FriendlyByteBuf(buf.readBytes(buf.readableBytes())));
        }, (msg, ctx) -> {
            ((NetworkEvent.Context)ctx.get()).enqueueWork(() -> EchoPlayer.handleServerPayload((Supplier)ctx, msg));
            ((NetworkEvent.Context)ctx.get()).setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static void handleClientPayload(CustomPayload msg) {
        DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> {
            if (msg.id.equals(NetworkPackets.POSSESS_PACKET)) {
                ClientPacketHandler.handlePossessPacket(msg.buf);
            } else if (msg.id.equals(NetworkPackets.UNPOSSESS_PACKET)) {
                ClientPacketHandler.handleUnpossessPacket(msg.buf);
            } else if (msg.id.equals(NetworkPackets.CONTROL_SYNC_PACKET)) {
                ClientPacketHandler.handleControlSyncPacket(msg.buf);
            }
        });
    }

    private void onServerStarted(ServerStartedEvent event) {
        EchoPlayerSavedData.respawnAll(event.getServer());
    }

    private static void handleServerPayload(Supplier<NetworkEvent.Context> ctx, ServerboundPayload msg) {
        ServerPlayer sender = ((NetworkEvent.Context)ctx.get()).getSender();
        if (sender != null && msg.id.equals(NetworkPackets.UNPOSSESS_PACKET)) {
            ServerPacketHandler.handleUnpossessPacket(sender, msg.buf);
        } else if (sender != null && msg.id.equals(NetworkPackets.CONTROL_INPUT_PACKET)) {
            ServerPacketHandler.handleControlInputPacket(sender, msg.buf);
        }
    }

    private static void dispatchClientPayload(Supplier<NetworkEvent.Context> ctx, CustomPayload msg) {
        if (((NetworkEvent.Context)ctx.get()).getDirection().getReceptionSide().isClient()) {
            EchoPlayer.handleClientPayload(msg);
        }
    }

    public static class CustomPayload {
        public final ResourceLocation id;
        public final FriendlyByteBuf buf;

        public CustomPayload(ResourceLocation id, FriendlyByteBuf buf) {
            this.id = id;
            this.buf = buf;
        }
    }

    public static class ServerboundPayload {
        public final ResourceLocation id;
        public final FriendlyByteBuf buf;

        public ServerboundPayload(ResourceLocation id, FriendlyByteBuf buf) {
            this.id = id;
            this.buf = buf;
        }
    }
}
