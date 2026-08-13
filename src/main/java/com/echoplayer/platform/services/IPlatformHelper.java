package com.echoplayer.platform.services;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface IPlatformHelper {
    public String getPlatformName();

    public boolean isModLoaded(String var1);

    public boolean isDevelopmentEnvironment();

    public void sendToClient(ServerPlayer var1, ResourceLocation var2, FriendlyByteBuf var3);

    public void sendToServer(ResourceLocation var1, FriendlyByteBuf var2);

    public void syncModdedInventories(ServerPlayer var1, ServerPlayer var2);

    default public String getEnvironmentName() {
        return this.isDevelopmentEnvironment() ? "development" : "production";
    }
}

