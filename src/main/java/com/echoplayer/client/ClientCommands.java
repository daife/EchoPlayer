package com.echoplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ClientCommands {
    private ClientCommands() {
    }

    public static boolean execute(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        connection.sendCommand(command);
        return true;
    }
}
