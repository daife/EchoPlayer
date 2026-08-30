package com.echoplayer.client.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;

/**
 * Invalidates 3D Skin Layers' per-player mesh after EchoPlayer remaps the
 * texture represented by a client player entity.  Reflection keeps the
 * integration optional and avoids loading any skinlayers3d classes when that
 * mod is absent.
 */
public final class SkinLayers3dCompat {
    private static final String PLAYER_SETTINGS = "dev.tr7zw.skinlayers.accessor.PlayerSettings";
    private static boolean resolved;
    private static Class<?> settingsClass;
    private static Method setCurrentSkin;
    private static Method clearMeshes;

    private SkinLayers3dCompat() {
    }

    public static boolean invalidate(Entity player) {
        if (player == null) {
            return false;
        }
        resolve(player.getClass().getClassLoader());
        if (settingsClass == null || !settingsClass.isInstance(player)) {
            return true;
        }
        try {
            clearMeshes.invoke(player);
            setCurrentSkin.invoke(player, new Object[]{null});
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            // A different 3D Skin Layers version may expose a different API.
            // Its normal texture-change detection remains the safe fallback.
        }
        return true;
    }

    private static void resolve(ClassLoader classLoader) {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            settingsClass = Class.forName(PLAYER_SETTINGS, false, classLoader);
            setCurrentSkin = settingsClass.getMethod("setCurrentSkin", net.minecraft.resources.ResourceLocation.class);
            clearMeshes = settingsClass.getMethod("clearMeshes");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            settingsClass = null;
            setCurrentSkin = null;
            clearMeshes = null;
        }
    }
}
