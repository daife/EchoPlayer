package com.echoplayer.platform;

import com.echoplayer.EchoPlayer;
import com.echoplayer.Constants;
import com.echoplayer.platform.services.IPlatformHelper;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.PacketDistributor;

public class ForgePlatformHelper
implements IPlatformHelper {
    private static boolean curiosInit = false;
    private static Object curiosHelperInstance;
    private static Method getCuriosHandler;
    private static Method serializeNBT;
    private static Method deserializeNBT;
    private static Method syncCurios;

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public void sendToClient(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        if (EchoPlayer.CHANNEL != null) {
            EchoPlayer.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EchoPlayer.CustomPayload(id, buf));
        }
    }

    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        if (EchoPlayer.CHANNEL != null) {
            EchoPlayer.CHANNEL.sendToServer(new EchoPlayer.ServerboundPayload(id, buf));
        }
    }

    @Override
    public void syncModdedInventories(ServerPlayer source, ServerPlayer target) {
        if (!this.isModLoaded("curios")) {
            return;
        }
        if (!curiosInit) {
            try {
                Class<?> apiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                Method getCuriosHelper = apiClass.getMethod("getCuriosHelper", new Class[0]);
                curiosHelperInstance = getCuriosHelper.invoke(null, new Object[0]);
                getCuriosHandler = curiosHelperInstance.getClass().getMethod("getCuriosHandler", LivingEntity.class);
                syncCurios = curiosHelperInstance.getClass().getMethod("syncCurios", ServerPlayer.class);
            }
            catch (Exception e) {
                Constants.LOG.error("Falha ao inicializar cache de reflexao Curios", (Throwable)e);
            }
            curiosInit = true;
        }
        if (curiosHelperInstance == null || getCuriosHandler == null) {
            return;
        }
        try {
            Optional sourceOpt = (Optional)getCuriosHandler.invoke(curiosHelperInstance, source);
            Optional targetOpt = (Optional)getCuriosHandler.invoke(curiosHelperInstance, target);
            if (sourceOpt.isPresent() && targetOpt.isPresent()) {
                Object sourceHandler = sourceOpt.get();
                Object targetHandler = targetOpt.get();
                if (serializeNBT == null) {
                    serializeNBT = sourceHandler.getClass().getMethod("serializeNBT", new Class[0]);
                    deserializeNBT = targetHandler.getClass().getMethod("deserializeNBT", CompoundTag.class);
                }
                CompoundTag tag = (CompoundTag)serializeNBT.invoke(sourceHandler, new Object[0]);
                deserializeNBT.invoke(targetHandler, tag);
                syncCurios.invoke(curiosHelperInstance, target);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

