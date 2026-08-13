package com.echoplayer.data;

import com.echoplayer.Constants;
import com.echoplayer.manager.EchoPlayerManager;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class EchoPlayerSavedData
extends SavedData {
    private static final String FILE_NAME = "echo_player";
    private final List<GameProfile> activeEchoPlayers = new ArrayList<GameProfile>();
    private final Map<UUID, UUID> echoPlayerOwners = new HashMap<UUID, UUID>();
    private boolean allowOtherPlayersControl;

    public static EchoPlayerSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new EchoPlayerSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(EchoPlayerSavedData::load, EchoPlayerSavedData::new, FILE_NAME);
    }

    private static boolean isValidProfile(GameProfile profile) {
        return profile != null && profile.getId() != null && profile.getName() != null && !profile.getName().isEmpty();
    }

    private static GameProfile readGameProfileCompat(CompoundTag profileTag) {
        GameProfile profile = NbtUtils.readGameProfile(profileTag);
        if (profile != null && profile.getId() != null && profile.getName() != null && !profile.getName().isEmpty()) {
            return profile;
        }
        UUID uuid = null;
        if (profile != null && profile.getId() != null) {
            uuid = profile.getId();
        } else if (profileTag.hasUUID("Id")) {
            uuid = profileTag.getUUID("Id");
        } else if (profileTag.hasUUID("UUID")) {
            uuid = profileTag.getUUID("UUID");
        }
        String name = null;
        if (profile != null && profile.getName() != null && !profile.getName().isEmpty()) {
            name = profile.getName();
        } else if (profileTag.contains("Name")) {
            name = profileTag.getString("Name");
        }
        if (uuid == null || name == null || name.isEmpty()) {
            return null;
        }
        GameProfile compatProfile = new GameProfile(uuid, name);
        if (profile != null) {
            compatProfile.getProperties().putAll((Multimap)profile.getProperties());
        }
        return compatProfile;
    }

    public void addEchoPlayer(GameProfile profile, UUID ownerId) {
        boolean changed = this.activeEchoPlayers.removeIf(p -> !EchoPlayerSavedData.isValidProfile(p));
        if (!EchoPlayerSavedData.isValidProfile(profile)) {
            if (changed) {
                this.setDirty();
            }
            return;
        }
        UUID uuid = profile.getId();
        if (this.activeEchoPlayers.stream().noneMatch(p -> uuid.equals(p.getId()))) {
            this.activeEchoPlayers.add(profile);
            changed = true;
        }
        if (changed) {
            this.setDirty();
        }
        if (ownerId != null && !ownerId.equals(this.echoPlayerOwners.put(uuid, ownerId))) {
            this.setDirty();
        }
    }

    public void removeEchoPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (this.activeEchoPlayers.removeIf(p -> !EchoPlayerSavedData.isValidProfile(p) || uuid.equals(p.getId())) | this.echoPlayerOwners.remove(uuid) != null) {
            this.setDirty();
        }
    }

    public List<GameProfile> getActiveEchoPlayers() {
        return this.activeEchoPlayers;
    }

    public UUID getOwner(UUID echoPlayerId) {
        return this.echoPlayerOwners.get(echoPlayerId);
    }

    public boolean isAllowOtherPlayersControl() {
        return this.allowOtherPlayersControl;
    }

    public void setAllowOtherPlayersControl(boolean allowOtherPlayersControl) {
        if (this.allowOtherPlayersControl != allowOtherPlayersControl) {
            this.allowOtherPlayersControl = allowOtherPlayersControl;
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        ListTag list = new ListTag();
        for (GameProfile profile : this.activeEchoPlayers) {
            CompoundTag profileTag = new CompoundTag();
            NbtUtils.writeGameProfile(profileTag, profile);
            UUID ownerId = this.echoPlayerOwners.get(profile.getId());
            if (ownerId != null) {
                profileTag.putUUID("Owner", ownerId);
            }
            list.add(profileTag);
        }
        compoundTag.put("EchoPlayers", list);
        compoundTag.putBoolean("AllowOtherPlayersControl", this.allowOtherPlayersControl);
        return compoundTag;
    }

    public static EchoPlayerSavedData load(CompoundTag compoundTag) {
        EchoPlayerSavedData data = new EchoPlayerSavedData();
        data.allowOtherPlayersControl = compoundTag.getBoolean("AllowOtherPlayersControl");
        if (compoundTag.contains("EchoPlayers")) {
            ListTag list = compoundTag.getList("EchoPlayers", 10);
            for (int i = 0; i < list.size(); ++i) {
                CompoundTag profileTag = list.getCompound(i);
                GameProfile profile = EchoPlayerSavedData.readGameProfileCompat(profileTag);
                if (EchoPlayerSavedData.isValidProfile(profile)) {
                    data.activeEchoPlayers.add(profile);
                    if (profileTag.hasUUID("Owner")) {
                        data.echoPlayerOwners.put(profile.getId(), profileTag.getUUID("Owner"));
                    }
                    continue;
                }
                data.setDirty();
            }
        }
        return data;
    }

    public static void respawnAll(MinecraftServer server) {
        EchoPlayerSavedData data = EchoPlayerSavedData.get(server);
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        for (GameProfile profile : data.getActiveEchoPlayers()) {
            if (server.getPlayerList().getPlayer(profile.getId()) != null) continue;
            try {
                EchoPlayerManager.respawnPersistentEchoPlayer(server, overworld, profile);
            }
            catch (Exception e) {
                Constants.LOG.error("Failed to respawn persistent EchoPlayer: " + profile.getName(), (Throwable)e);
            }
        }
    }
}
