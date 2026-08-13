package com.echoplayer.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={ServerGamePacketListenerImpl.class})
public interface ServerGamePacketListenerImplAccessor {
    @Accessor(value="connection")
    public Connection getConnection();

    @Accessor(value="firstGoodX")
    public void echoplayer$setFirstGoodX(double var1);

    @Accessor(value="firstGoodY")
    public void echoplayer$setFirstGoodY(double var1);

    @Accessor(value="firstGoodZ")
    public void echoplayer$setFirstGoodZ(double var1);

    @Accessor(value="lastGoodX")
    public void echoplayer$setLastGoodX(double var1);

    @Accessor(value="lastGoodY")
    public void echoplayer$setLastGoodY(double var1);

    @Accessor(value="lastGoodZ")
    public void echoplayer$setLastGoodZ(double var1);
}

