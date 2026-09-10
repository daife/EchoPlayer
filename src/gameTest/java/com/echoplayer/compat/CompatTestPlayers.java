package com.echoplayer.compat;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import com.echoplayer.network.EchoConnection;
import com.echoplayer.network.EchoServerGamePacketListenerImpl;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

final class CompatTestPlayers implements AutoCloseable {
    final ServerPlayer player;
    final EchoServerPlayer first;
    final EchoServerPlayer second;

    CompatTestPlayers(GameTestHelper helper) {
        var level = helper.getLevel();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Test" + suffix));
        var connection = new EchoConnection(PacketFlow.SERVERBOUND);
        player.connection = new EchoServerGamePacketListenerImpl(level.getServer(), connection, player);
        connection.setListener(player.connection);
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        level.addNewPlayer(player);
        first = EchoPlayerManager.spawnEchoPlayer(level.getServer(), level,
            new GameProfile(UUID.randomUUID(), "EchoA" + suffix), player.getUUID());
        second = EchoPlayerManager.spawnEchoPlayer(level.getServer(), level,
            new GameProfile(UUID.randomUUID(), "EchoB" + suffix), player.getUUID());
        first.setPos(player.position());
        second.setPos(player.position());
    }

    @Override
    public void close() {
        EchoPlayerManager.revertPossession(player);
        EchoPlayerManager.removeEchoPlayer(first);
        EchoPlayerManager.removeEchoPlayer(second);
        player.serverLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }
}
