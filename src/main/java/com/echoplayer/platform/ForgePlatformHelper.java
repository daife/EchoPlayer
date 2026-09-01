package com.echoplayer.platform;

import com.echoplayer.EchoPlayer;
import com.echoplayer.Constants;
import com.echoplayer.platform.services.IPlatformHelper;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.nbt.Tag;
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
    private static Method getCuriosInventory;
    private static Method resolveCurios;
    private static Method writeCuriosTag;
    private static Method readCuriosTag;

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
                getCuriosInventory = apiClass.getMethod("getCuriosInventory", LivingEntity.class);
            }
            catch (ReflectiveOperationException e) {
                Constants.LOG.error("Failed to initialize Curios compatibility", e);
            }
            curiosInit = true;
        }
        if (getCuriosInventory == null) {
            return;
        }
        try {
            Object sourceLazyOptional = getCuriosInventory.invoke(null, source);
            Object targetLazyOptional = getCuriosInventory.invoke(null, target);
            if (resolveCurios == null) {
                resolveCurios = sourceLazyOptional.getClass().getMethod("resolve");
            }
            Optional<?> sourceOpt = (Optional<?>)resolveCurios.invoke(sourceLazyOptional);
            Optional<?> targetOpt = (Optional<?>)resolveCurios.invoke(targetLazyOptional);
            if (sourceOpt.isPresent() && targetOpt.isPresent()) {
                Object sourceHandler = sourceOpt.get();
                Object targetHandler = targetOpt.get();
                if (writeCuriosTag == null) {
                    writeCuriosTag = sourceHandler.getClass().getMethod("writeTag");
                    readCuriosTag = targetHandler.getClass().getMethod("readTag", Tag.class);
                }
                Tag tag = (Tag)writeCuriosTag.invoke(sourceHandler);
                readCuriosTag.invoke(targetHandler, tag.copy());
            }
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            Constants.LOG.error("Failed to synchronize Curios inventory between {} and {}",
                source.getGameProfile().getName(), target.getGameProfile().getName(), exception);
        }
    }
}
