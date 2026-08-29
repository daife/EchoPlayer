package com.echoplayer.manager;

import com.echoplayer.entity.EchoServerPlayer;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

class ControllerVisibility {

    static void hideControllerFromViewer(ServerPlayer controller, ServerPlayer viewer) {
        if (controller == viewer || viewer instanceof EchoServerPlayer || !EchoPlayerManager.isPossessing(controller)) {
            return;
        }
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(controller.getId()));
    }

    static void hidePossessingControllersFromViewer(ServerPlayer viewer) {
        if (viewer instanceof EchoServerPlayer) {
            return;
        }
        for (EchoPlayerManager.ControllerState state : EchoPlayerManager.CONTROLLERS.values()) {
            hideControllerFromViewer(state.realPlayer, viewer);
        }
    }

    static void hideControllerFromObservers(ServerPlayer controller) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(controller.getId());
        for (ServerPlayer viewer : controller.server.getPlayerList().getPlayers()) {
            if (viewer == controller) {
                continue;
            }
            viewer.connection.send(destroyPacket);
        }
    }

    static void showControllerToObservers(ServerPlayer controller) {
        // The controller never left the level's entity tracker; updates for
        // observers were only suppressed while possession was active.  Once the
        // controller state is removed, ChunkMap's next normal tracking pass will
        // add it back to nearby viewers.
        //
        // Do not force that pass by removing and re-adding the ServerPlayer.
        // ChunkMap also treats ServerPlayer as a chunk-loading client, so doing
        // that tears down and rebuilds the controller's own chunk subscription
        // and makes unpossess look like the world was reloaded.
    }

    static void sendPlayerEntityToViewer(ServerPlayer controller, ServerPlayer viewer) {
        viewer.connection.send(new ClientboundAddPlayerPacket(controller));
        List<SynchedEntityData.DataValue<?>> entityData = controller.getEntityData().getNonDefaultValues();
        if (entityData != null) {
            viewer.connection.send(new ClientboundSetEntityDataPacket(controller.getId(), entityData));
        }
        viewer.connection.send(new ClientboundRotateHeadPacket(controller, (byte)Mth.floor(controller.getYHeadRot() * 256.0f / 360.0f)));
        viewer.connection.send(new ClientboundSetEntityMotionPacket(controller));
        ArrayList<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<Pair<EquipmentSlot, ItemStack>>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = controller.getItemBySlot(slot);
            if (item.isEmpty()) {
                continue;
            }
            equipment.add(Pair.of(slot, item.copy()));
        }
        if (!equipment.isEmpty()) {
            viewer.connection.send(new ClientboundSetEquipmentPacket(controller.getId(), equipment));
        }
    }

    static void sendEchoEntityToViewer(EchoServerPlayer echoPlayer, ServerPlayer viewer) {
        if (echoPlayer.level().dimension() != viewer.level().dimension()) {
            return;
        }
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(actions, List.of(echoPlayer)));
        sendPlayerEntityToViewer(echoPlayer, viewer);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = echoPlayer.getItemBySlot(slot);
            viewer.connection.send(new ClientboundSetEquipmentPacket(echoPlayer.getId(), List.of(Pair.of(slot, item))));
        }
    }

    private ControllerVisibility() {
    }
}
