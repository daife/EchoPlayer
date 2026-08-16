package com.echoplayer.manager;

import com.echoplayer.Constants;
import com.echoplayer.data.EchoPlayerSavedData;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.mixin.CommandSourceStackAccessor;
import com.echoplayer.mixin.FoodDataAccessor;
import com.echoplayer.mixin.LivingEntityInvoker;
import com.echoplayer.network.EchoConnection;
import com.echoplayer.network.EchoServerGamePacketListenerImpl;
import com.echoplayer.network.NetworkPackets;
import com.echoplayer.platform.Services;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.storage.LevelResource;

public class EchoPlayerManager {
    static final Map<UUID, ControllerState> CONTROLLERS = new java.util.concurrent.ConcurrentHashMap<UUID, ControllerState>();
    static final Map<UUID, PossessionSession> SESSIONS = new java.util.concurrent.ConcurrentHashMap<UUID, PossessionSession>();
    private static final Map<UUID, ViewRotation> CLIENT_VIEWS = new java.util.concurrent.ConcurrentHashMap<UUID, ViewRotation>();
    private static final Map<UUID, Map<UUID, ViewRotation>> CLIENT_AVATAR_VIEWS = new java.util.concurrent.ConcurrentHashMap<UUID, Map<UUID, ViewRotation>>();
    private static final Map<UUID, PendingEchoReshow> PENDING_ECHO_RESHOWS = new java.util.concurrent.ConcurrentHashMap<UUID, PendingEchoReshow>();

    static Map<UUID, ControllerState> getControllers() { return CONTROLLERS; }
    static Map<UUID, PossessionSession> getSessions() { return SESSIONS; }
    static Map<UUID, PendingEchoReshow> getPendingReshows() { return PENDING_ECHO_RESHOWS; }

    public static GameProfile createStableProfile(String name) {
        return new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
    }

    public static UUID getLogicalOwnerUUID(Player player) {
        if (player instanceof ServerPlayer) {
            return getLogicalPlayer((ServerPlayer)player).getUUID();
        }
        return player.getUUID();
    }

    /**
     * Treats the authenticated player and the EchoPlayer they currently
     * possess as the same identity for ownership checks that cannot accept a
     * logical Player instance. This is deliberately session-scoped: stored
     * item ownership is never rewritten and unrelated players remain distinct.
     */
    public static boolean arePossessionIdentitiesEquivalent(UUID first, Object second) {
        if (!(second instanceof UUID secondId)) {
            return false;
        }
        if (first.equals(secondId)) {
            return true;
        }

        ControllerState firstController = CONTROLLERS.get(first);
        if (firstController != null && firstController.echoPlayer.getUUID().equals(secondId)) {
            return true;
        }
        ControllerState secondController = CONTROLLERS.get(secondId);
        return secondController != null && secondController.echoPlayer.getUUID().equals(first);
    }

    public static ServerPlayer getPossessor(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        return session != null && session.controller != null ? session.controller.realPlayer : null;
    }

    public static EchoServerPlayer getPossessed(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        return state != null ? state.echoPlayer : null;
    }

    public static ServerPlayer getController(Player echoPlayer) {
        if (echoPlayer == null) {
            return null;
        }
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        return session != null && session.controller != null ? session.controller.realPlayer : null;
    }

    public static OutgoingChatMessage createOutgoingChatMessage(PlayerChatMessage message) {
        if (CONTROLLERS.containsKey(message.sender())) {
            return new OutgoingChatMessage.Disguised(message.decoratedContent());
        }
        return OutgoingChatMessage.create(message);
    }

    public static ServerPlayer getLogicalPlayer(ServerPlayer authenticatedPlayer) {
        ControllerState state = CONTROLLERS.get(authenticatedPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return authenticatedPlayer;
        }
        return state.echoPlayer;
    }

    public static ServerPlayer getIdentityAvatar(UUID authenticatedPlayerId) {
        ControllerState state = CONTROLLERS.get(authenticatedPlayerId);
        if (state == null || state.shellPlayer.isRemoved() || state.shellPlayer.isDeadOrDying()) {
            return null;
        }
        return state.shellPlayer;
    }

    public static ServerPlayer getIdentityAvatar(EntityGetter level, UUID authenticatedPlayerId) {
        ServerPlayer avatar = getIdentityAvatar(authenticatedPlayerId);
        return avatar != null && avatar.level() == level ? avatar : null;
    }

    public static Entity getLogicalDamageEntity(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return getLogicalPlayer(player);
        }
        return entity;
    }

    public static ServerPlayer getAuthenticatedPlayer(ServerPlayer logicalPlayer) {
        if (logicalPlayer instanceof EchoServerPlayer echoPlayer) {
            ServerPlayer possessor = getPossessor(echoPlayer);
            if (possessor != null) {
                return possessor;
            }
        }
        return logicalPlayer;
    }

    public static ServerPlayer projectSelectorPlayer(ServerPlayer player) {
        ControllerState state = CONTROLLERS.get(player.getUUID());
        if (state == null || state.shellPlayer.isRemoved() || state.shellPlayer.isDeadOrDying()) {
            return player;
        }
        return state.shellPlayer;
    }

    public static List<ServerPlayer> projectSelectorPlayers(List<ServerPlayer> players) {
        ArrayList<ServerPlayer> projected = new ArrayList<ServerPlayer>(players.size());
        for (ServerPlayer player : players) {
            projected.add(projectSelectorPlayer(player));
        }
        return projected;
    }

    public static List<ServerPlayer> projectSleepStatusPlayers(ServerLevel level, List<ServerPlayer> players) {
        if (CONTROLLERS.isEmpty()) {
            return players;
        }
        ArrayList<ServerPlayer> projected = new ArrayList<ServerPlayer>(players.size() + CONTROLLERS.size());
        for (ServerPlayer player : players) {
            if (player instanceof EchoServerPlayer && isPossessed(player)) {
                continue;
            }
            projected.add(player);
        }
        for (ControllerState state : CONTROLLERS.values()) {
            EchoServerPlayer shellPlayer = state.shellPlayer;
            if (shellPlayer.isRemoved() || shellPlayer.isDeadOrDying() || shellPlayer.serverLevel() != level) {
                continue;
            }
            projected.add(shellPlayer);
        }
        return projected;
    }

    public static void wakeSleepingShells(ServerLevel level) {
        for (ControllerState state : CONTROLLERS.values()) {
            EchoServerPlayer shellPlayer = state.shellPlayer;
            if (shellPlayer.serverLevel() == level && shellPlayer.isSleeping()) {
                shellPlayer.stopSleepInBed(false, false);
            }
        }
    }

    public static List<ServerPlayer> getProjectedLevelPlayers(ServerLevel level, Predicate<? super ServerPlayer> predicate, int limit) {
        ArrayList<ServerPlayer> projected = new ArrayList<ServerPlayer>(Math.min(limit, level.getServer().getPlayerCount()));
        for (ServerPlayer player : projectSelectorPlayers(level.getServer().getPlayerList().getPlayers())) {
            if (player.serverLevel() != level || !predicate.test(player)) {
                continue;
            }
            projected.add(player);
            if (projected.size() >= limit) {
                break;
            }
        }
        return projected;
    }

    public static List<? extends Entity> filterLogicalSelectorEntities(List<? extends Entity> entities) {
        ArrayList<Entity> filtered = new ArrayList<Entity>(entities.size());
        for (Entity entity : entities) {
            if (entity instanceof ServerPlayer player && isPossessing(player)) {
                continue;
            }
            filtered.add(entity);
        }
        return filtered;
    }

    public static int getInheritedPermissionLevel(EchoServerPlayer echoPlayer) {
        ServerPlayer authenticatedPlayer = getAuthenticatedPlayer(echoPlayer);
        if (authenticatedPlayer == echoPlayer) {
            return -1;
        }
        for (int level = 4; level >= 0; level--) {
            if (authenticatedPlayer.hasPermissions(level)) {
                return level;
            }
        }
        return 0;
    }

    public static List<Entity> filterControlledBoatPlacementEntities(Entity source, List<Entity> entities) {
        if (!(source instanceof ServerPlayer controller) || entities.isEmpty()) {
            return entities;
        }
        ControllerState state = CONTROLLERS.get(controller.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return entities;
        }
        List<Entity> filtered = null;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (isInternalBoatPlacementEntity(state, entity)) {
                if (filtered != null) {
                    continue;
                }
                filtered = new ArrayList<Entity>(entities.size() - 1);
                for (int j = 0; j < i; j++) {
                    filtered.add(entities.get(j));
                }
                continue;
            }
            if (filtered == null) {
                continue;
            }
            filtered.add(entity);
        }
        return filtered != null ? filtered : entities;
    }

    private static boolean isInternalBoatPlacementEntity(ControllerState state, Entity entity) {
        return entity == state.echoPlayer;
    }

    public static void tickPossessedEffects(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return;
        }
        PossessionSession session = state.session;
        long gameTime = state.echoPlayer.level().getGameTime();
        if (session.lastEffectTickGameTime != gameTime) {
            session.lastEffectTickGameTime = gameTime;
            session.tickingCanonicalEffects = true;
            try {
                ((LivingEntityInvoker)((Object)state.echoPlayer)).echoplayer$tickEffects();
            } finally {
                session.tickingCanonicalEffects = false;
            }
        }
        StateSynchronizer.synchronizeEffects(state.echoPlayer, realPlayer);
        synchronizePossessedFireState(state);
        StateSynchronizer.copyCooldownState(state.echoPlayer, state.realPlayer);
        StateSyncHelper.syncIntField(realPlayer, state.echoPlayer,
            ServerPlayer::getAirSupply, EchoServerPlayer::getAirSupply,
            ServerPlayer::setAirSupply, EchoServerPlayer::setAirSupply,
            () -> state.lastAirSupply, value -> state.lastAirSupply = value);
        StateSyncHelper.syncIntField(realPlayer, state.echoPlayer,
            ServerPlayer::getTicksFrozen, EchoServerPlayer::getTicksFrozen,
            ServerPlayer::setTicksFrozen, EchoServerPlayer::setTicksFrozen,
            () -> state.lastTicksFrozen, value -> state.lastTicksFrozen = value);
        StateSyncHelper.syncBooleanField(realPlayer, state.echoPlayer,
            realPlayer::isInvisible, state.echoPlayer::isInvisible,
            ServerPlayer::setInvisible, EchoServerPlayer::setInvisible,
            () -> state.lastInvisible, value -> state.lastInvisible = value);
        StateSyncHelper.syncBooleanField(realPlayer, state.echoPlayer,
            realPlayer::hasGlowingTag, state.echoPlayer::hasGlowingTag,
            ServerPlayer::setGlowingTag, EchoServerPlayer::setGlowingTag,
            () -> state.lastGlowing, value -> state.lastGlowing = value);
        StateSynchronizer.synchronizeAttributes(state.echoPlayer, realPlayer, false);
        realPlayer.setAbsorptionAmount(state.echoPlayer.getAbsorptionAmount());
        StateSynchronizer.hideControllerBody(realPlayer);
        realPlayer.absMoveTo(state.echoPlayer.getX(), state.echoPlayer.getY(), state.echoPlayer.getZ(), realPlayer.getYRot(), realPlayer.getXRot());
    }

    /**
     * Keeps effects applied through ordinary gameplay and mod APIs in sync.
     * Commands have their own hooks, but drinking a potion or a mod calling
     * LivingEntity#addEffect previously had to wait for the next tick.
     */
    public static void syncPossessedEffectMutation(LivingEntity entity) {
        if (entity instanceof EchoServerPlayer echoPlayer) {
            if (echoPlayer.linkedRealPlayer != null) {
                return;
            }
            PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
            if (session == null || session.controller == null || session.synchronizingEffects) {
                return;
            }
            session.synchronizingEffects = true;
            try {
                copyEchoSharedStateToRealController(session.controller);
            } finally {
                session.synchronizingEffects = false;
            }
            return;
        }
        if (!(entity instanceof ServerPlayer realPlayer)) {
            return;
        }
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying() || state.session.synchronizingEffects) {
            return;
        }
        state.session.synchronizingEffects = true;
        try {
            copyRealStateToEcho(state, true);
        } finally {
            state.session.synchronizingEffects = false;
        }
    }

    /**
     * The hidden controller and Echo occupy the same space.  Environmental fire
     * can therefore attempt to damage both in one tick; the Echo is canonical.
     */
    public static boolean shouldIgnorePossessedEnvironmentalFireDamage(ServerPlayer realPlayer, DamageSource source) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        return state != null
            && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
            && source.getEntity() == null
            && source.getDirectEntity() == null
            && state.echoPlayer.getRemainingFireTicks() > 0;
    }

    public static boolean shouldCancelPossessedEchoEffectTick(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        return session != null && !session.tickingCanonicalEffects;
    }

    public static void syncLogicalStateAfterExternalMutation(Entity entity) {
        if (entity instanceof EchoServerPlayer echoPlayer) {
            if (echoPlayer.linkedRealPlayer == null) {
                PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
                if (session != null) {
                    syncEchoStateToController(session);
                }
                return;
            }
        }
        if (entity instanceof ServerPlayer player) {
            ControllerState state = CONTROLLERS.get(player.getUUID());
            if (state != null && !state.echoPlayer.isRemoved() && !state.echoPlayer.isDeadOrDying()) {
                StateSynchronizer.synchronizeFireState(player, state.echoPlayer);
                state.lastFireTicks = state.echoPlayer.getRemainingFireTicks();
                copyRealStateToEcho(state, true);
                syncCanonicalStateToController(state.session);
            }
        }
    }

    public static void syncLogicalStateAfterExternalMutation(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            syncLogicalStateAfterExternalMutation(entity);
        }
    }

    public static void syncPossessedEchoSharedState(EchoServerPlayer echoPlayer) {
        if (echoPlayer == null || echoPlayer.linkedRealPlayer != null || echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return;
        }
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session == null) {
            return;
        }
        StateSynchronizer.updateEchoEquipment(echoPlayer);
        syncCanonicalStateToController(session);
    }

    public static ServerPlayer getCommandExecutor(ServerPlayer player) {
        if (player instanceof EchoServerPlayer echoPlayer) {
            ServerPlayer possessor = getPossessor(echoPlayer);
            if (possessor != null) {
                return possessor;
            }
        }
        return player;
    }

    public static ServerPlayer getCommandExecutor(CommandSourceStack source) throws CommandSyntaxException {
        CommandSource commandSource = ((CommandSourceStackAccessor)((Object)source)).echoplayer$getSource();
        if (commandSource instanceof ServerPlayer player) {
            return getCommandExecutor(player);
        }
        return getCommandExecutor(source.getPlayerOrException());
    }

    public static CommandSourceStack createPossessedCommandSource(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return realPlayer.createCommandSourceStack();
        }
        EchoServerPlayer echoPlayer = state.echoPlayer;
        return realPlayer.createCommandSourceStack().withEntity(echoPlayer).withLevel(echoPlayer.serverLevel()).withPosition(echoPlayer.position()).withRotation(echoPlayer.getRotationVector());
    }

    public static void syncPossessedAfterCommand(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying() || realPlayer.isDeadOrDying()) {
            return;
        }
        syncEchoStateToController(state.session);
    }

    public static boolean isPossessed(Entity entity) {
        if (!(entity instanceof EchoServerPlayer echoPlayer)) {
            return false;
        }
        if (echoPlayer.linkedRealPlayer != null) {
            return false;
        }
        return SESSIONS.containsKey(echoPlayer.getUUID());
    }

    public static boolean isPossessing(ServerPlayer player) {
        return CONTROLLERS.containsKey(player.getUUID());
    }

    public static int[] projectPassengerIdsForViewer(ServerPlayer viewer, int[] passengerIds) {
        int[] projected = null;
        for (int i = 0; i < passengerIds.length; i++) {
            int passengerId = passengerIds[i];
            for (ControllerState state : CONTROLLERS.values()) {
                if (state.realPlayer == viewer || state.realPlayer.getId() != passengerId || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
                    continue;
                }
                if (projected == null) {
                    projected = passengerIds.clone();
                }
                projected[i] = state.echoPlayer.getId();
                break;
            }
        }
        return projected;
    }

    public static boolean shouldDisableCollision(Entity e1, Entity e2) {
        if (e1 instanceof ServerPlayer p1) {
            if (e2 instanceof EchoServerPlayer f2) {
                return getPossessed(p1) == f2;
            }
        }
        if (e2 instanceof ServerPlayer p2) {
            if (e1 instanceof EchoServerPlayer f1) {
                return getPossessed(p2) == f1;
            }
        }
        return false;
    }

    public static boolean shouldRunPassivePhysics(EchoServerPlayer echoPlayer) {
        if (echoPlayer == null || echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return false;
        }
        if (echoPlayer.linkedRealPlayer != null) {
            return true;
        }
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        return session == null || session.controller == null;
    }

    public static boolean isOtherPlayersControlAllowed(MinecraftServer server) {
        return EchoPlayerSavedData.get(server).isAllowOtherPlayersControl();
    }

    public static void setOtherPlayersControlAllowed(MinecraftServer server, boolean enabled) {
        EchoPlayerSavedData.get(server).setAllowOtherPlayersControl(enabled);
    }

    public static boolean canManageEchoPlayer(ServerPlayer player, EchoServerPlayer echoPlayer) {
        if (player == null || echoPlayer == null) {
            return false;
        }
        EchoPlayerSavedData savedData = EchoPlayerSavedData.get(player.server);
        UUID ownerId = savedData.getOwner(echoPlayer.getUUID());
        return player.getUUID().equals(ownerId) || savedData.isAllowOtherPlayersControl();
    }

    public static List<EchoServerPlayer> getEchoPlayersByName(MinecraftServer server, String name) {
        ArrayList<EchoServerPlayer> echoPlayers = new ArrayList<EchoServerPlayer>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EchoServerPlayer echoPlayer)) {
                continue;
            }
            if (echoPlayer.linkedRealPlayer != null || !player.getGameProfile().getName().equalsIgnoreCase(name)) {
                continue;
            }
            echoPlayers.add(echoPlayer);
        }
        return echoPlayers;
    }

    public static List<String> getEchoPlayerNames(MinecraftServer server) {
        ArrayList<String> names = new ArrayList<String>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EchoServerPlayer echoPlayer)) {
                continue;
            }
            if (echoPlayer.linkedRealPlayer != null) {
                continue;
            }
            names.add(player.getGameProfile().getName());
        }
        return names;
    }

    public static List<String> getManageableEchoPlayerNames(MinecraftServer server, ServerPlayer player) {
        ArrayList<String> names = new ArrayList<String>();
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            if (!(onlinePlayer instanceof EchoServerPlayer echoPlayer)) {
                continue;
            }
            if (echoPlayer.linkedRealPlayer != null || !canManageEchoPlayer(player, echoPlayer)) {
                continue;
            }
            names.add(echoPlayer.getGameProfile().getName());
        }
        return names;
    }

    public static String getSpawnConflict(MinecraftServer server, GameProfile profile) {
        String name = profile.getName();
        UUID uuid = profile.getId();
        if (name == null || name.isBlank() || uuid == null) {
            return "Invalid EchoPlayer profile.";
        }
        ServerPlayer playerByName = server.getPlayerList().getPlayerByName(name);
        if (playerByName != null) {
            return "A player named " + name + " is already online.";
        }
        ServerPlayer playerByUuid = server.getPlayerList().getPlayer(uuid);
        if (playerByUuid != null) {
            return "A player with this UUID is already online.";
        }
        return null;
    }

    public static EchoServerPlayer spawnEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, boolean persistent, UUID ownerId) {
        String conflict = getSpawnConflict(server, profile);
        if (conflict != null) {
            throw new IllegalArgumentException(conflict);
        }
        return createEchoPlayer(server, level, profile, persistent, ownerId);
    }

    private static EchoServerPlayer createEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, boolean persistent, UUID ownerId) {
        EchoServerPlayer echoPlayer = new EchoServerPlayer(server, level, profile);
        EchoConnection connection = new EchoConnection(PacketFlow.SERVERBOUND);
        server.getPlayerList().placeNewPlayer(connection, echoPlayer);
        EchoServerGamePacketListenerImpl listener = new EchoServerGamePacketListenerImpl(server, connection, echoPlayer);
        echoPlayer.connection = listener;
        connection.setListener(listener);
        if (persistent) {
            EchoPlayerSavedData.get(server).addEchoPlayer(profile, ownerId);
        }
        return echoPlayer;
    }

    public static EchoServerPlayer spawnEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, UUID ownerId) {
        return spawnEchoPlayer(server, level, profile, true, ownerId);
    }

    public static EchoServerPlayer respawnPersistentEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        return spawnEchoPlayer(server, level, profile, false, null);
    }

    public static String possess(ServerPlayer realPlayer, EchoServerPlayer echoPlayer) {
        restorePendingEchoState(echoPlayer);
        if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying() || echoPlayer.linkedRealPlayer != null) {
            return "EchoPlayer " + echoPlayer.getGameProfile().getName() + " is not available.";
        }
        if (!canManageEchoPlayer(realPlayer, echoPlayer)) {
            return "Only the player who spawned " + echoPlayer.getGameProfile().getName() + " may control it.";
        }
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null && session.controller != null) {
            return "EchoPlayer " + echoPlayer.getGameProfile().getName() + " is already being controlled.";
        }
        ControllerState currentState = CONTROLLERS.get(realPlayer.getUUID());
        if (currentState != null) {
            return switchPossession(currentState, echoPlayer, session);
        }
        if (session == null) {
            session = new PossessionSession(echoPlayer);
            SESSIONS.put(echoPlayer.getUUID(), session);
        }
        createCrashBackup(realPlayer);
        ViewRotation bodyView = captureCurrentClientView(realPlayer);
        ViewRotation echoView = captureClientEntityView(realPlayer, echoPlayer);
        EchoServerPlayer shell = createOriginalBodyShell(realPlayer, bodyView);
        List<EntityViewRotation> passiveViews = capturePassiveAvatarViews(realPlayer, shell, echoPlayer, shell);
        ControllerState state = new ControllerState(realPlayer, echoPlayer, shell, session, null);
        ControllerState previousState = CONTROLLERS.putIfAbsent(realPlayer.getUUID(), state);
        if (previousState != null) {
            removeShellEntity(shell, realPlayer.server);
            return "You are already controlling an EchoPlayer.";
        }
        session.controller = state;
        // Treat orientation as a separate state from riding.  Mounting changes a
        // rider's position, but must never decide which direction the camera faces.
        Entity realVehicle = realPlayer.getVehicle();
        if (realVehicle != null) {
            realPlayer.stopRiding();
        }
        if (realVehicle != null) {
            shell.startRiding(realVehicle, true);
            applyViewRotation(shell, bodyView);
        }

        enterControlledEcho(state, echoView, passiveViews);
        hideControllerFromObservers(realPlayer);
        updateLogicalSleepStatus(state);
        return null;
    }

    private static String switchPossession(ControllerState previousState, EchoServerPlayer echoPlayer, PossessionSession targetSession) {
        ServerPlayer realPlayer = previousState.realPlayer;
        ViewRotation echoView = captureClientEntityView(realPlayer, echoPlayer);
        List<EntityViewRotation> passiveViews = capturePassiveAvatarViews(
            realPlayer, previousState.shellPlayer, echoPlayer, previousState.echoPlayer);
        if (targetSession == null) {
            targetSession = new PossessionSession(echoPlayer);
            SESSIONS.put(echoPlayer.getUUID(), targetSession);
        }

        leaveControlledEcho(previousState);
        ControllerState state = new ControllerState(realPlayer, echoPlayer, previousState.shellPlayer, targetSession, previousState);
        CONTROLLERS.put(realPlayer.getUUID(), state);
        targetSession.controller = state;
        enterControlledEcho(state, echoView, passiveViews);
        reshowEchoToReal(previousState);
        updateLogicalSleepStatus(previousState);
        updateLogicalSleepStatus(state);
        return null;
    }

    private static void leaveControlledEcho(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        Entity controlledVehicle = realPlayer.getVehicle();
        if (controlledVehicle != null) {
            realPlayer.stopRiding();
        }
        commitControllerContainer(state);
        syncControlledEchoToController(state);
        copyRealStateToEcho(state, !state.echoPlayer.isDeadOrDying());
        if (realPlayer.isSleeping() && !state.echoPlayer.isDeadOrDying() && !state.echoPlayer.isRemoved()) {
            StateSynchronizer.transferSleepingState(realPlayer, state.echoPlayer);
        }
        if (controlledVehicle != null && !state.echoPlayer.isDeadOrDying() && !state.echoPlayer.isRemoved()) {
            state.echoPlayer.startRiding(controlledVehicle, true);
        }
        removeControllerState(state);
    }

    private static void enterControlledEcho(ControllerState state, ViewRotation echoView, List<EntityViewRotation> passiveViews) {
        EchoServerPlayer echoPlayer = state.echoPlayer;
        Entity echoVehicle = echoPlayer.getVehicle();
        if (echoVehicle != null) {
            echoPlayer.stopRiding();
        }
        teleportRealPlayerToEcho(state, true);
        copyEchoStateToRealController(state);
        if (echoPlayer.isSleeping()) {
            StateSynchronizer.transferSleepingState(echoPlayer, state.realPlayer);
        }
        if (echoVehicle != null) {
            state.realPlayer.startRiding(echoVehicle, true);
        }
        synchronizeViewRotation(state.realPlayer, echoView);
        syncControlledEchoToController(state);
        copyRealStateToEcho(state, true);
        StateSynchronizer.hideControllerBody(state.realPlayer);
        // Minecraft can offset player-model rotations after the camera changes
        // bodies, including Echo entities that were never touched by the
        // transition. Restore every passive avatar only after the new Echo has
        // become authoritative, and send the exact same snapshots to the client.
        applyPassiveAvatarViews(passiveViews);
        sendPossessPacket(state, passiveViews);
        hideEchoFromReal(state);
    }

    public static boolean handlePossessedDamage(ServerPlayer realPlayer, DamageSource source, float amount) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return false;
        }
        boolean damaged = state.echoPlayer.hurt(source, amount);
        if (damaged && !state.echoPlayer.isDeadOrDying() && !state.echoPlayer.isRemoved()) {
            copyRealStateToEcho(state, true);
            StateSynchronizer.synchronizeFireState(state.echoPlayer, realPlayer);
            state.lastFireTicks = state.echoPlayer.getRemainingFireTicks();
        }
        return damaged;
    }

    public static boolean shouldApplyEchoDamage(EchoServerPlayer echoPlayer, DamageSource source, float amount) {
        if (source.getDirectEntity() == echoPlayer) {
            return false;
        }
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session == null) {
            return true;
        }
        long gameTime = echoPlayer.level().getGameTime();
        int directEntityId = source.getDirectEntity() != null ? source.getDirectEntity().getId() : Integer.MIN_VALUE;
        int causingEntityId = source.getEntity() != null ? source.getEntity().getId() : Integer.MIN_VALUE;
        return session.lastDamageGameTime != gameTime || !session.lastDamageType.equals(source.getMsgId()) || session.lastDamageDirectEntityId != directEntityId || session.lastDamageCausingEntityId != causingEntityId || Float.compare(session.lastDamageAmount, amount) != 0;
    }

    public static void recordEchoDamage(EchoServerPlayer echoPlayer, DamageSource source, float amount) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session == null) {
            return;
        }
        session.lastDamageGameTime = echoPlayer.level().getGameTime();
        session.lastDamageType = source.getMsgId();
        session.lastDamageDirectEntityId = source.getDirectEntity() != null ? source.getDirectEntity().getId() : Integer.MIN_VALUE;
        session.lastDamageCausingEntityId = source.getEntity() != null ? source.getEntity().getId() : Integer.MIN_VALUE;
        session.lastDamageAmount = amount;
    }

    public static boolean addPickedItemToPossessedInventory(ServerPlayer realPlayer, ItemEntity itemEntity, ItemStack stack) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return realPlayer.getInventory().add(stack);
        }
        int count = stack.getCount();
        boolean added = state.echoPlayer.getInventory().add(stack);
        if (added) {
            state.echoPlayer.take(itemEntity, count);
            StateSynchronizer.updateEchoEquipment(state.echoPlayer);
            syncCanonicalStateToController(state.session);
        }
        return added;
    }

    public static void prepareEchoForIncomingDamage(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session == null) {
            return;
        }
        ControllerState controller = session.controller;
        if (controller != null && !controller.realPlayer.isDeadOrDying() && !controller.realPlayer.hasDisconnected()) {
            syncControlledEchoToController(controller);
            copyRealStateToEcho(controller, false);
        }
    }

    public static void afterEchoHurt(EchoServerPlayer echoPlayer, DamageSource source) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null) {
            ControllerState state = session.controller;
            if (state != null) {
                copyRealStateToEcho(state, true);
                ServerPlayer realPlayer = state.realPlayer;
                StateSynchronizer.synchronizeFireState(echoPlayer, realPlayer);
                state.lastFireTicks = echoPlayer.getRemainingFireTicks();
                if (!realPlayer.isDeadOrDying() && !realPlayer.hasDisconnected()) {
                    realPlayer.connection.send(new ClientboundEntityEventPacket(realPlayer, (byte)2));
                    realPlayer.connection.send(new ClientboundDamageEventPacket(realPlayer, source));
                }
            }
        }
    }

    public static void afterEchoKnockback(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null) {
            ControllerState state = session.controller;
            if (state != null) {
                ServerPlayer realPlayer = state.realPlayer;
                if (realPlayer.isDeadOrDying() || realPlayer.hasDisconnected()) {
                    return;
                }
                realPlayer.setDeltaMovement(echoPlayer.getDeltaMovement());
                realPlayer.connection.send(new ClientboundSetEntityMotionPacket(realPlayer));
            }
        }
    }

    public static void revertPossession(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null) {
            return;
        }
        ViewRotation shellView = captureClientEntityView(realPlayer, state.shellPlayer);
        List<EntityViewRotation> passiveViews = capturePassiveAvatarViews(
            realPlayer, state.shellPlayer, state.shellPlayer, state.echoPlayer);
        Entity realVehicle = state.shellPlayer.getVehicle();
        if (realVehicle != null) {
            state.shellPlayer.stopRiding();
        }
        leaveControlledEcho(state);
        if (!realPlayer.isDeadOrDying()) {
            restoreRealPlayerFromShell(state, true);
            synchronizeViewRotation(realPlayer, shellView);
        } else {
            restoreRealPlayerForRespawn(state);
        }
        removeShell(state);
        showControllerToObservers(realPlayer);
        if (realVehicle != null && !realPlayer.isDeadOrDying()) {
            realPlayer.startRiding(realVehicle, true);
            synchronizeViewRotation(realPlayer, shellView);
        }
        applyPassiveAvatarViews(passiveViews);
        sendUnpossessPacket(realPlayer, passiveViews);
        if (!realPlayer.isDeadOrDying()) {
            reshowEchoToReal(state);
        }
        removeCrashBackup(realPlayer);
        updateLogicalSleepStatus(state);
    }

    public static void updateClientViews(ServerPlayer realPlayer, float yRot, float xRot, float yHeadRot, float yBodyRot,
                                         Map<Integer, float[]> entityViews) {
        ViewRotation localView = normalizeViewRotation(yRot, xRot, yHeadRot, yBodyRot);
        if (localView == null) {
            return;
        }
        Map<UUID, ViewRotation> avatarViews = new java.util.HashMap<UUID, ViewRotation>();
        for (Map.Entry<Integer, float[]> entry : entityViews.entrySet()) {
            float[] values = entry.getValue();
            if (values == null || values.length != 4) {
                continue;
            }
            ViewRotation view = normalizeViewRotation(values[0], values[1], values[2], values[3]);
            Entity entity = realPlayer.serverLevel().getEntity(entry.getKey());
            if (view != null && entity instanceof ServerPlayer avatar && avatar != realPlayer) {
                avatarViews.put(avatar.getUUID(), view);
            }
        }
        CLIENT_VIEWS.put(realPlayer.getUUID(), localView);
        CLIENT_AVATAR_VIEWS.put(realPlayer.getUUID(), Map.copyOf(avatarViews));
    }

    public static void forgetClientView(ServerPlayer realPlayer) {
        CLIENT_VIEWS.remove(realPlayer.getUUID());
        CLIENT_AVATAR_VIEWS.remove(realPlayer.getUUID());
    }

    public static void revertAllPossessions(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session == null) {
            return;
        }
        if (session.controller != null) {
            revertPossession(session.controller.realPlayer);
        }
    }

    public static void ejectControllersOnDeath(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null && session.controller != null) {
            revertPossession(session.controller.realPlayer);
        }
    }

    public static void respawnEchoAfterDeath(EchoServerPlayer echoPlayer) {
        if (echoPlayer.linkedRealPlayer != null) {
            return;
        }
        SESSIONS.remove(echoPlayer.getUUID());
        echoPlayer.server.getPlayerList().respawn(echoPlayer, false);
    }

    public static void prepareEchoForDeath(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null && session.controller != null) {
            commitControllerContainer(session.controller);
        }
    }

    public static void finalizeOriginalBodyDeath(EchoServerPlayer shellPlayer, DamageSource source, float amount) {
        ControllerState state = findControllerByShell(shellPlayer);
        if (state == null) {
            shellPlayer.discard();
            return;
        }
        List<EntityViewRotation> passiveViews = capturePassiveAvatarViews(
            state.realPlayer, shellPlayer, shellPlayer, state.echoPlayer);
        PendingEchoReshow pendingEchoReshow = new PendingEchoReshow(state.echoPlayer);
        commitControllerContainer(state);
        syncControlledEchoToController(state);
        copyRealStateToEcho(state, false);
        removeControllerState(state);
        restoreRealPlayerFromShell(state, false);
        teleportRealPlayerToShell(state);
        removeShell(state);
        showControllerToObservers(state.realPlayer);
        applyPassiveAvatarViews(passiveViews);
        sendUnpossessPacket(state.realPlayer, passiveViews);
        PENDING_ECHO_RESHOWS.put(state.realPlayer.getUUID(), pendingEchoReshow);
        float recordedAmount = amount > 0.0f ? amount : state.realPlayer.getMaxHealth();
        state.realPlayer.getCombatTracker().recordDamage(source, recordedAmount);
        state.realPlayer.setHealth(0.0f);
        state.realPlayer.die(source);
        restoreEchoStateAndWorld(state.echoPlayer, pendingEchoReshow);
        removeCrashBackup(state.realPlayer);
    }

    public static void removeEchoPlayer(EchoServerPlayer echoPlayer) {
        if (echoPlayer.linkedRealPlayer != null) {
            echoPlayer.discard();
            return;
        }
        PossessionSession session = SESSIONS.remove(echoPlayer.getUUID());
        if (session != null) {
            ControllerState state = session.controller;
            if (state != null) {
                List<EntityViewRotation> passiveViews = capturePassiveAvatarViews(
                    state.realPlayer, state.shellPlayer, state.shellPlayer, state.echoPlayer);
                commitControllerContainer(state);
                removeControllerState(state);
                removeShell(state);
                restoreRealPlayerFromShell(state, true);
                showControllerToObservers(state.realPlayer);
                applyPassiveAvatarViews(passiveViews);
                sendUnpossessPacket(state.realPlayer, passiveViews);
                removeCrashBackup(state.realPlayer);
            }
        }
        removeEchoPlayerEntityAndData(echoPlayer);
    }

    public static void completePendingEchoReshow(ServerPlayer player) {
        PendingEchoReshow pending = PENDING_ECHO_RESHOWS.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        ServerPlayer registeredPlayer = player.server.getPlayerList().getPlayer(pending.echoPlayerId);
        if (!(registeredPlayer instanceof EchoServerPlayer echoPlayer) || echoPlayer.linkedRealPlayer != null) {
            return;
        }
        restoreEchoStateAndWorld(echoPlayer, pending);
        sendEchoEntityToViewer(echoPlayer, player);
    }

    private static void restorePendingEchoState(EchoServerPlayer echoPlayer) {
        for (PendingEchoReshow pending : PENDING_ECHO_RESHOWS.values()) {
            if (!pending.echoPlayerId.equals(echoPlayer.getUUID())) {
                continue;
            }
            restoreEchoStateAndWorld(echoPlayer, pending);
            return;
        }
    }

    private static void restoreEchoStateAndWorld(EchoServerPlayer echoPlayer, PendingEchoReshow pending) {
        boolean wasRemoved = echoPlayer.isRemoved();
        echoPlayer.restoreAfterControllerBodyDeath(pending.health);
        StateSynchronizer.setGameModeIfNeeded(echoPlayer, pending.gameMode);
        if (wasRemoved) {
            echoPlayer.serverLevel().addRespawnedPlayer(echoPlayer);
        }
    }

    public static void tick() {
        for (PossessionSession session : List.copyOf(SESSIONS.values())) {
            tickSession(session);
        }
    }

    private static void tickSession(PossessionSession session) {
        EchoServerPlayer echoPlayer = session.echoPlayer;
        if (echoPlayer.isDeadOrDying()) {
            ejectControllersOnDeath(echoPlayer);
            return;
        }
        if (echoPlayer.isRemoved()) {
            SESSIONS.remove(echoPlayer.getUUID(), session);
            endSessionControllers(session, false);
            return;
        }
        ControllerState state = session.controller;
        if (state == null) {
            SESSIONS.remove(echoPlayer.getUUID(), session);
            return;
        }
        ServerPlayer realPlayer = state.realPlayer;
        if (realPlayer.hasDisconnected()) {
            revertPossession(realPlayer);
            return;
        }
        if (state.shellPlayer.isDeadOrDying()) {
            finalizeOriginalBodyDeath(state.shellPlayer, state.shellPlayer.damageSources().genericKill(), state.shellPlayer.getMaxHealth());
            return;
        }
        if (state.shellPlayer.isRemoved() || realPlayer.isDeadOrDying()) {
            revertPossession(realPlayer);
            return;
        }
        syncControlledEchoToController(state);
        copyRealStateToEcho(state, true);
        synchronizePossessedFireState(state);
        StateSynchronizer.hideControllerBody(realPlayer);
    }

    private static void endSessionControllers(PossessionSession session, boolean reshowEcho) {
        ControllerState state = session.controller;
        if (state != null) {
            List<EntityViewRotation> passiveViews = capturePassiveAvatarViews(
                state.realPlayer, state.shellPlayer, state.shellPlayer, state.echoPlayer);
            Entity echoVehicle = state.realPlayer.getVehicle();
            if (echoVehicle != null) {
                state.realPlayer.stopRiding();
            }
            Entity realVehicle = state.shellPlayer.getVehicle();
            if (realVehicle != null) {
                state.shellPlayer.stopRiding();
            }
            commitControllerContainer(state);
            removeControllerState(state);
            removeShell(state);
            if (!state.realPlayer.isDeadOrDying()) {
                restoreRealPlayerFromShell(state, true);
                copyEchoSharedStateToRealController(state);
                showControllerToObservers(state.realPlayer);
                applyPassiveAvatarViews(passiveViews);
                sendUnpossessPacket(state.realPlayer, passiveViews);
                if (reshowEcho) {
                    reshowEchoToReal(state);
                }
                if (realVehicle != null) {
                    state.realPlayer.startRiding(realVehicle, true);
                }
            } else {
                restoreRealPlayerForRespawn(state);
                applyPassiveAvatarViews(passiveViews);
                sendUnpossessPacket(state.realPlayer, passiveViews);
            }
            removeCrashBackup(state.realPlayer);
        }
    }

    private static ControllerState findControllerByShell(EchoServerPlayer shellPlayer) {
        for (PossessionSession session : List.copyOf(SESSIONS.values())) {
            ControllerState state = session.controller;
            if (state != null && state.shellPlayer == shellPlayer) {
                return state;
            }
        }
        return null;
    }

    private static void removeControllerState(ControllerState state) {
        CONTROLLERS.remove(state.realPlayer.getUUID(), state);
        if (state.session.controller == state) {
            state.session.controller = null;
            SESSIONS.remove(state.echoPlayer.getUUID(), state.session);
        }
    }

    private static EchoServerPlayer createOriginalBodyShell(ServerPlayer realPlayer, ViewRotation bodyView) {
        GameProfile shellProfile = new GameProfile(UUID.randomUUID(), realPlayer.getGameProfile().getName());
        EchoServerPlayer shell = new EchoServerPlayer(realPlayer.server, realPlayer.serverLevel(), shellProfile);
        shell.linkedRealPlayer = realPlayer;
        EchoConnection shellConn = new EchoConnection(PacketFlow.SERVERBOUND);
        EchoServerGamePacketListenerImpl shellListener = new EchoServerGamePacketListenerImpl(realPlayer.server, shellConn, shell);
        shell.connection = shellListener;
        shellConn.setListener(shellListener);
        shell.setGameMode(realPlayer.gameMode.getGameModeForPlayer());
        copyRidingTransform(realPlayer, shell);
        applyViewRotation(shell, bodyView);
        StateSynchronizer.copyRealStateToShell(realPlayer, shell);
        StateSynchronizer.transferSleepingState(realPlayer, shell);
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        ClientboundPlayerInfoUpdatePacket addPacket = new ClientboundPlayerInfoUpdatePacket(actions, List.of(shell));
        for (ServerPlayer player : realPlayer.server.getPlayerList().getPlayers()) {
            player.connection.send(addPacket);
        }
        realPlayer.serverLevel().addFreshEntity(shell);
        return shell;
    }

    private static void copyRealStateToEcho(ControllerState state, boolean copyHealth) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        GameType realGameMode = realPlayer.gameMode.getGameModeForPlayer();
        GameType echoGameMode = echoPlayer.gameMode.getGameModeForPlayer();
        if (realGameMode != state.lastSyncGameMode) {
            StateSynchronizer.setGameModeIfNeeded(echoPlayer, realGameMode);
            state.lastSyncGameMode = realGameMode;
        } else if (echoGameMode != state.lastSyncGameMode) {
            StateSynchronizer.setGameModeIfNeeded(realPlayer, echoGameMode);
            state.lastSyncGameMode = echoGameMode;
        }
        boolean inventoryChanged = false;
        Inventory realInv = realPlayer.getInventory();
        Inventory echoInv = echoPlayer.getInventory();
        for (int i = 0; i < state.lastInventoryState.length; i++) {
            ItemStack realStack = realInv.getItem(i);
            ItemStack echoStack = echoInv.getItem(i);
            ItemStack lastStack = state.lastInventoryState[i];
            boolean realChanged = !ItemStack.matches(realStack, lastStack);
            boolean echoChanged = !ItemStack.matches(echoStack, lastStack);
            if (echoChanged) {
                if (ItemStack.isSameItemSameTags(realStack, echoStack)) {
                    realStack.setCount(echoStack.getCount());
                } else {
                    realInv.setItem(i, echoStack.copy());
                }
                state.lastInventoryState[i] = echoStack.copy();
                inventoryChanged = true;
                continue;
            }
            if (!realChanged) {
                continue;
            }
            echoInv.setItem(i, realStack.copy());
            state.lastInventoryState[i] = realStack.copy();
        }
        if (echoInv.selected != realInv.selected) {
            echoInv.selected = realInv.selected;
            inventoryChanged = true;
        }
        if (inventoryChanged) {
            realPlayer.containerMenu.broadcastChanges();
        }
        Services.PLATFORM.syncModdedInventories(realPlayer, echoPlayer);
        StateSynchronizer.synchronizeEffects(realPlayer, echoPlayer);
        StateSynchronizer.synchronizeAttributes(realPlayer, echoPlayer, false);
        StateSynchronizer.copySprintingState(realPlayer, echoPlayer);
        syncFoodState(state, realPlayer, echoPlayer);
        StateSynchronizer.syncExperienceState(realPlayer, echoPlayer);
        if (copyHealth) {
            syncHealthState(state, realPlayer, echoPlayer);
        }
        syncAbsorptionState(state, realPlayer, echoPlayer);
        StateSynchronizer.copyAbilitiesIfDifferent(realPlayer, echoPlayer);
        StateSynchronizer.synchronizeUsingItem(realPlayer, echoPlayer);
        if (inventoryChanged) {
            StateSynchronizer.updateEchoEquipment(echoPlayer);
        }
    }

    private static void syncFoodState(ControllerState state, ServerPlayer realPlayer, EchoServerPlayer echoPlayer) {
        StateSyncHelper.syncIntField(realPlayer, echoPlayer,
            p -> p.getFoodData().getFoodLevel(), e -> e.getFoodData().getFoodLevel(),
            (p, v) -> p.getFoodData().setFoodLevel(v), (e, v) -> e.getFoodData().setFoodLevel(v),
            () -> state.lastFoodLevel, v -> state.lastFoodLevel = v);
        StateSyncHelper.syncFloatField(realPlayer, echoPlayer,
            p -> p.getFoodData().getSaturationLevel(), e -> e.getFoodData().getSaturationLevel(),
            (p, v) -> p.getFoodData().setSaturation((float)(double)v), (e, v) -> e.getFoodData().setSaturation((float)(double)v),
            () -> state.lastSaturation, v -> state.lastSaturation = (float)v);
        StateSyncHelper.syncFloatField(realPlayer, echoPlayer,
            p -> p.getFoodData().getExhaustionLevel(), e -> e.getFoodData().getExhaustionLevel(),
            (p, v) -> p.getFoodData().setExhaustion((float)(double)v), (e, v) -> e.getFoodData().setExhaustion((float)(double)v),
            () -> state.lastExhaustion, v -> state.lastExhaustion = (float)v);
        StateSyncHelper.syncIntField(realPlayer, echoPlayer,
            p -> ((FoodDataAccessor)((Object)p.getFoodData())).echoplayer$getTickTimer(),
            e -> ((FoodDataAccessor)((Object)e.getFoodData())).echoplayer$getTickTimer(),
            (p, v) -> ((FoodDataAccessor)((Object)p.getFoodData())).echoplayer$setTickTimer(v),
            (e, v) -> ((FoodDataAccessor)((Object)e.getFoodData())).echoplayer$setTickTimer(v),
            () -> state.lastFoodTickTimer, value -> state.lastFoodTickTimer = value);
        StateSyncHelper.syncIntField(realPlayer, echoPlayer,
            p -> ((FoodDataAccessor)((Object)p.getFoodData())).echoplayer$getLastFoodLevel(),
            e -> ((FoodDataAccessor)((Object)e.getFoodData())).echoplayer$getLastFoodLevel(),
            (p, v) -> ((FoodDataAccessor)((Object)p.getFoodData())).echoplayer$setLastFoodLevel(v),
            (e, v) -> ((FoodDataAccessor)((Object)e.getFoodData())).echoplayer$setLastFoodLevel(v),
            () -> state.lastFoodDataLevel, value -> state.lastFoodDataLevel = value);
    }

    private static void syncHealthState(ControllerState state, ServerPlayer realPlayer, EchoServerPlayer echoPlayer) {
        StateSyncHelper.syncFloatField(realPlayer, echoPlayer,
            p -> p.getHealth(), e -> e.getHealth(),
            (p, v) -> p.setHealth((float)(double)v), (e, v) -> e.setHealth((float)(double)v),
            () -> state.lastHealth, v -> state.lastHealth = (float)v);
    }

    private static void syncAbsorptionState(ControllerState state, ServerPlayer realPlayer, EchoServerPlayer echoPlayer) {
        StateSyncHelper.syncFloatField(realPlayer, echoPlayer,
            p -> p.getAbsorptionAmount(), e -> e.getAbsorptionAmount(),
            (p, v) -> p.setAbsorptionAmount((float)(double)v), (e, v) -> e.setAbsorptionAmount((float)(double)v),
            () -> state.lastAbsorption, v -> state.lastAbsorption = (float)v);
    }

    private static void copyEchoStateToRealController(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        copyEchoSharedStateToRealController(state, true);
        if (needsTeleportToEcho(state)) {
            teleportRealPlayerToEcho(state, false);
        }
        realPlayer.setDeltaMovement(echoPlayer.getDeltaMovement());
    }

    private static void copyEchoSharedStateToRealController(ControllerState state) {
        copyEchoSharedStateToRealController(state, false);
    }

    private static void copyEchoSharedStateToRealController(ControllerState state, boolean synchronizeAllAttributes) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        int previousSelected = realPlayer.getInventory().selected;
        boolean inventoryChanged = StateSynchronizer.synchronizeInventoryContents(echoPlayer, realPlayer);
        Services.PLATFORM.syncModdedInventories(echoPlayer, realPlayer);
        StateSynchronizer.setGameModeIfNeeded(realPlayer, echoPlayer.gameMode.getGameModeForPlayer());
        StateSynchronizer.synchronizeEffects(echoPlayer, realPlayer);
        StateSynchronizer.synchronizeAttributes(echoPlayer, realPlayer, synchronizeAllAttributes);
        StateSynchronizer.copySprintingState(echoPlayer, realPlayer);
        StateSynchronizer.synchronizeFireState(echoPlayer, realPlayer);
        state.lastFireTicks = echoPlayer.getRemainingFireTicks();
        realPlayer.setAirSupply(echoPlayer.getAirSupply());
        state.lastAirSupply = echoPlayer.getAirSupply();
        realPlayer.setTicksFrozen(echoPlayer.getTicksFrozen());
        state.lastTicksFrozen = echoPlayer.getTicksFrozen();
        realPlayer.setInvisible(echoPlayer.isInvisible());
        state.lastInvisible = echoPlayer.isInvisible();
        realPlayer.setGlowingTag(echoPlayer.hasGlowingTag());
        state.lastGlowing = echoPlayer.hasGlowingTag();
        StateSynchronizer.copyFoodState(echoPlayer, realPlayer);
        StateSynchronizer.copyCooldownState(echoPlayer, realPlayer);
        state.lastFoodLevel = echoPlayer.getFoodData().getFoodLevel();
        state.lastSaturation = echoPlayer.getFoodData().getSaturationLevel();
        state.lastExhaustion = echoPlayer.getFoodData().getExhaustionLevel();
        FoodDataAccessor echoFood = (FoodDataAccessor)((Object)echoPlayer.getFoodData());
        state.lastFoodTickTimer = echoFood.echoplayer$getTickTimer();
        state.lastFoodDataLevel = echoFood.echoplayer$getLastFoodLevel();
        boolean healthChanged = Float.compare(realPlayer.getHealth(), echoPlayer.getHealth()) != 0
            || Float.compare(realPlayer.getAbsorptionAmount(), echoPlayer.getAbsorptionAmount()) != 0
            || realPlayer.getFoodData().getFoodLevel() != echoPlayer.getFoodData().getFoodLevel()
            || Float.compare(realPlayer.getFoodData().getSaturationLevel(), echoPlayer.getFoodData().getSaturationLevel()) != 0;
        if (Float.compare(realPlayer.getHealth(), echoPlayer.getHealth()) != 0) {
            realPlayer.setHealth(echoPlayer.getHealth());
        }
        if (Float.compare(realPlayer.getAbsorptionAmount(), echoPlayer.getAbsorptionAmount()) != 0) {
            realPlayer.setAbsorptionAmount(echoPlayer.getAbsorptionAmount());
            state.lastAbsorption = echoPlayer.getAbsorptionAmount();
        }
        if (realPlayer.getFoodData().getFoodLevel() != echoPlayer.getFoodData().getFoodLevel()) {
            realPlayer.getFoodData().setFoodLevel(echoPlayer.getFoodData().getFoodLevel());
        }
        if (Float.compare(realPlayer.getFoodData().getSaturationLevel(), echoPlayer.getFoodData().getSaturationLevel()) != 0) {
            realPlayer.getFoodData().setSaturation(echoPlayer.getFoodData().getSaturationLevel());
        }
        boolean experienceChanged = realPlayer.experienceLevel != echoPlayer.experienceLevel
            || Float.compare(realPlayer.experienceProgress, echoPlayer.experienceProgress) != 0
            || realPlayer.totalExperience != echoPlayer.totalExperience;
        if (experienceChanged) {
            realPlayer.experienceLevel = echoPlayer.experienceLevel;
            realPlayer.experienceProgress = echoPlayer.experienceProgress;
            realPlayer.totalExperience = echoPlayer.totalExperience;
        }
        boolean abilitiesChanged = StateSynchronizer.copyAbilitiesIfDifferent(echoPlayer, realPlayer);
        StateSynchronizer.synchronizeUsingItem(echoPlayer, realPlayer);
        if (healthChanged) {
            realPlayer.connection.send(new ClientboundSetHealthPacket(realPlayer.getHealth(), realPlayer.getFoodData().getFoodLevel(), realPlayer.getFoodData().getSaturationLevel()));
        }
        if (experienceChanged) {
            realPlayer.connection.send(new ClientboundSetExperiencePacket(realPlayer.experienceProgress, realPlayer.totalExperience, realPlayer.experienceLevel));
        }
        if (abilitiesChanged) {
            realPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(realPlayer.getAbilities()));
        }
        if (previousSelected != realPlayer.getInventory().selected) {
            realPlayer.connection.send(new ClientboundSetCarriedItemPacket(realPlayer.getInventory().selected));
        }
        if (inventoryChanged) {
            realPlayer.containerMenu.broadcastChanges();
        }
        StateSynchronizer.hideControllerBody(realPlayer);
    }

    private static void syncControlledEchoToController(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return;
        }
        if (echoPlayer.level().dimension() != realPlayer.level().dimension()) {
            echoPlayer.teleportTo(realPlayer.serverLevel(), realPlayer.getX(), realPlayer.getY(), realPlayer.getZ(), realPlayer.getYRot(), realPlayer.getXRot());
        } else {
            echoPlayer.moveTo(realPlayer.getX(), realPlayer.getY(), realPlayer.getZ(), realPlayer.getYRot(), realPlayer.getXRot());
        }
        echoPlayer.yHeadRot = realPlayer.yHeadRot;
        echoPlayer.yBodyRot = realPlayer.yBodyRot;
        echoPlayer.setPose(realPlayer.getPose());
        echoPlayer.setShiftKeyDown(realPlayer.isShiftKeyDown());
        StateSynchronizer.copySprintingState(realPlayer, echoPlayer);
        echoPlayer.setOnGround(realPlayer.onGround());
        echoPlayer.fallDistance = realPlayer.fallDistance;
        echoPlayer.setDeltaMovement(realPlayer.getDeltaMovement());
    }

    private static void copyRidingTransform(ServerPlayer source, ServerPlayer target) {
        target.moveTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
        target.yHeadRot = source.yHeadRot;
        target.yBodyRot = source.yBodyRot;
        target.setPose(source.getPose());
        target.setShiftKeyDown(source.isShiftKeyDown());
        StateSynchronizer.copySprintingState(source, target);
        target.setOnGround(source.onGround());
        target.fallDistance = source.fallDistance;
        target.setDeltaMovement(source.getDeltaMovement());
    }

    private static ViewRotation captureViewRotation(ServerPlayer player) {
        return new ViewRotation(player.getYRot(), player.getXRot(), player.yHeadRot, player.yBodyRot);
    }

    private static ViewRotation captureCurrentClientView(ServerPlayer player) {
        ViewRotation clientView = CLIENT_VIEWS.get(player.getUUID());
        return clientView != null ? clientView : captureViewRotation(player);
    }

    private static ViewRotation captureClientEntityView(ServerPlayer controller, ServerPlayer avatar) {
        Map<UUID, ViewRotation> avatarViews = CLIENT_AVATAR_VIEWS.get(controller.getUUID());
        ViewRotation clientView = avatarViews != null ? avatarViews.get(avatar.getUUID()) : null;
        return clientView != null ? clientView : captureViewRotation(avatar);
    }

    private static ViewRotation captureClientAvatarView(ServerPlayer controller, ServerPlayer avatar, ServerPlayer localAvatar) {
        return avatar == localAvatar
            ? captureCurrentClientView(controller)
            : captureClientEntityView(controller, avatar);
    }

    private static List<EntityViewRotation> capturePassiveAvatarViews(ServerPlayer controller, EchoServerPlayer shellPlayer,
                                                                       ServerPlayer targetAvatar, ServerPlayer localAvatar) {
        ArrayList<EntityViewRotation> views = new ArrayList<EntityViewRotation>();
        if (shellPlayer != targetAvatar) {
            views.add(new EntityViewRotation(shellPlayer, captureClientAvatarView(controller, shellPlayer, localAvatar)));
        }
        for (ServerPlayer player : shellPlayer.server.getPlayerList().getPlayers()) {
            if (!(player instanceof EchoServerPlayer echoPlayer)
                || echoPlayer == targetAvatar
                || echoPlayer.linkedRealPlayer != null
                || echoPlayer.isRemoved()
                || echoPlayer.isDeadOrDying()) {
                continue;
            }
            views.add(new EntityViewRotation(echoPlayer, captureClientAvatarView(controller, echoPlayer, localAvatar)));
        }
        return views;
    }

    private static ViewRotation normalizeViewRotation(float yRot, float xRot, float yHeadRot, float yBodyRot) {
        if (!Float.isFinite(yRot) || !Float.isFinite(xRot) || !Float.isFinite(yHeadRot) || !Float.isFinite(yBodyRot)) {
            return null;
        }
        return new ViewRotation(
            Mth.wrapDegrees(yRot),
            Mth.clamp(xRot, -90.0f, 90.0f),
            Mth.wrapDegrees(yHeadRot),
            Mth.wrapDegrees(yBodyRot));
    }

    private static void applyPassiveAvatarViews(List<EntityViewRotation> passiveViews) {
        for (EntityViewRotation entityView : passiveViews) {
            if (!entityView.player.isRemoved() && !entityView.player.isDeadOrDying()) {
                applyViewRotation(entityView.player, entityView.view);
            }
        }
    }

    private static void applyViewRotation(ServerPlayer player, ViewRotation view) {
        player.setYRot(view.yRot);
        player.setXRot(view.xRot);
        player.yHeadRot = view.yHeadRot;
        player.yBodyRot = view.yBodyRot;
        player.yRotO = view.yRot;
        player.xRotO = view.xRot;
        player.yHeadRotO = view.yHeadRot;
        player.yBodyRotO = view.yBodyRot;
    }

    /**
     * Applies an authoritative view after a teleport or mount change and sends it
     * to the owning client.  Entity tracking packets do not reliably update the
     * local player's camera, especially when the player is a passenger.
     */
    private static void synchronizeViewRotation(ServerPlayer player, ViewRotation view) {
        applyViewRotation(player, view);
        if (!player.hasDisconnected() && !player.isDeadOrDying()) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), view.yRot, view.xRot);
        }
    }

    public static void createCrashBackup(ServerPlayer player) {
        try {
            CompoundTag backupTag = new CompoundTag();
            player.saveWithoutId(backupTag);
            Path path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + "_afp_backup.dat");
            NbtIo.writeCompressed(backupTag, path.toFile());
        } catch (Exception e) {
            Constants.LOG.error("Failed to create crash backup for " + player.getName().getString(), (Throwable)e);
        }
    }

    public static void removeCrashBackup(ServerPlayer player) {
        try {
            Path path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + "_afp_backup.dat");
            Files.deleteIfExists(path);
        } catch (Exception e) {
            Constants.LOG.error("Failed to delete crash backup for " + player.getName().getString(), (Throwable)e);
        }
    }

    public static void restoreCrashBackup(ServerPlayer realPlayer) {
        try {
            Path path = realPlayer.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(realPlayer.getUUID() + "_afp_backup.dat");
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                CompoundTag backup = NbtIo.readCompressed(path.toFile());
                if (backup != null) {
                    if (backup.contains("Inventory")) {
                        realPlayer.getInventory().load(backup.getList("Inventory", 10));
                    }
                    if (backup.contains("SelectedItemSlot")) {
                        realPlayer.getInventory().selected = backup.getInt("SelectedItemSlot");
                    }
                    if (backup.contains("Health", 99)) {
                        realPlayer.setHealth(backup.getFloat("Health"));
                    }
                    if (backup.contains("AbsorptionAmount", 99)) {
                        realPlayer.setAbsorptionAmount(backup.getFloat("AbsorptionAmount"));
                    }
                    if (backup.contains("foodLevel", 99)) {
                        realPlayer.getFoodData().setFoodLevel(backup.getInt("foodLevel"));
                    }
                    if (backup.contains("foodSaturationLevel", 99)) {
                        realPlayer.getFoodData().setSaturation(backup.getFloat("foodSaturationLevel"));
                    }
                    if (backup.contains("foodExhaustionLevel", 99)) {
                        realPlayer.getFoodData().setExhaustion(backup.getFloat("foodExhaustionLevel"));
                    }
                    if (backup.contains("foodTickTimer", 99)) {
                        ((FoodDataAccessor)((Object)realPlayer.getFoodData())).echoplayer$setTickTimer(backup.getInt("foodTickTimer"));
                    }
                    if (backup.contains("XpLevel", 99)) {
                        realPlayer.experienceLevel = backup.getInt("XpLevel");
                    }
                    if (backup.contains("XpP", 99)) {
                        realPlayer.experienceProgress = backup.getFloat("XpP");
                    }
                    if (backup.contains("XpTotal", 99)) {
                        realPlayer.totalExperience = backup.getInt("XpTotal");
                    }
                    if (backup.contains("abilities", 10)) {
                        realPlayer.getAbilities().loadSaveData(backup.getCompound("abilities"));
                        realPlayer.onUpdateAbilities();
                    }
                    Files.deleteIfExists(path);
                    StateSynchronizer.syncRealPlayerPackets(realPlayer);
                    realPlayer.containerMenu.broadcastChanges();
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to restore crash backup for " + realPlayer.getName().getString(), (Throwable)e);
        }
    }

    private static void restoreRealPlayerFromShell(ControllerState state, boolean teleport) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer shellPlayer = state.shellPlayer;
        StateSynchronizer.copyInventoryContents(shellPlayer, realPlayer);
        StateSynchronizer.setGameModeIfNeeded(realPlayer, shellPlayer.gameMode.getGameModeForPlayer());
        StateSynchronizer.synchronizeEffects(shellPlayer, realPlayer);
        StateSynchronizer.synchronizeAttributes(shellPlayer, realPlayer, true);
        realPlayer.setAirSupply(shellPlayer.getAirSupply());
        realPlayer.setTicksFrozen(shellPlayer.getTicksFrozen());
        StateSynchronizer.copyFoodState(shellPlayer, realPlayer);
        StateSynchronizer.copyCooldownStateAndPackets(shellPlayer, realPlayer);
        realPlayer.setHealth(Math.max(0.0f, shellPlayer.getHealth()));
        realPlayer.setAbsorptionAmount(shellPlayer.getAbsorptionAmount());
        realPlayer.experienceLevel = shellPlayer.experienceLevel;
        realPlayer.experienceProgress = shellPlayer.experienceProgress;
        realPlayer.totalExperience = shellPlayer.totalExperience;
        StateSynchronizer.copyAbilities(shellPlayer, realPlayer);
        realPlayer.setInvisible(shellPlayer.isInvisible());
        realPlayer.setSilent(shellPlayer.isSilent());
        realPlayer.setGlowingTag(shellPlayer.hasGlowingTag());
        StateSynchronizer.syncRealPlayerPackets(realPlayer);
        realPlayer.containerMenu.broadcastChanges();
        if (teleport) {
            teleportRealPlayerToShell(state);
            copyRidingTransform(shellPlayer, realPlayer);
        }
        StateSynchronizer.synchronizeFireState(shellPlayer, realPlayer);
        if (shellPlayer.isSleeping()) {
            StateSynchronizer.transferSleepingState(shellPlayer, realPlayer);
            realPlayer.serverLevel().updateSleepingPlayerList();
        }
    }

    private static void updateLogicalSleepStatus(ControllerState state) {
        ServerLevel shellLevel = state.shellPlayer.serverLevel();
        ServerLevel echoLevel = state.echoPlayer.serverLevel();
        shellLevel.updateSleepingPlayerList();
        if (echoLevel != shellLevel) {
            echoLevel.updateSleepingPlayerList();
        }
    }

    private static void teleportRealPlayerToShell(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer shellPlayer = state.shellPlayer;
        ServerLevel shellLevel = shellPlayer.serverLevel();
        boolean requiresTerrainDownload = realPlayer.level().dimension() != shellLevel.dimension() || realPlayer.distanceToSqr(shellPlayer) > 4096.0;
        realPlayer.teleportTo(shellLevel, shellPlayer.getX(), shellPlayer.getY(), shellPlayer.getZ(), shellPlayer.getYRot(), shellPlayer.getXRot());
        if (requiresTerrainDownload) {
            StateSynchronizer.applyGhostBlockFix(realPlayer, shellLevel, shellPlayer.blockPosition().below());
        }
    }

    private static void teleportRealPlayerToEcho(ControllerState state, boolean forcePacket) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        ServerLevel echoLevel = echoPlayer.serverLevel();
        boolean requiresTerrainDownload = realPlayer.level().dimension() != echoLevel.dimension() || realPlayer.distanceToSqr(echoPlayer) > 4096.0;
        float yRot = echoPlayer.getYRot();
        float xRot = echoPlayer.getXRot();
        if (realPlayer.level().dimension() != echoLevel.dimension()) {
            realPlayer.teleportTo(echoLevel, echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
        } else if (forcePacket) {
            realPlayer.connection.teleport(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
            realPlayer.absMoveTo(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
        } else {
            realPlayer.absMoveTo(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
            StateSynchronizer.syncConnectionPosition(realPlayer, echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ());
        }
        if (requiresTerrainDownload) {
            StateSynchronizer.applyGhostBlockFix(realPlayer, echoLevel, echoPlayer.blockPosition().below());
        }
    }

    private static boolean needsTeleportToEcho(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        if (echoPlayer.isPassenger()) {
            return realPlayer.level().dimension() != echoPlayer.level().dimension() || realPlayer.position().distanceToSqr(echoPlayer.position()) > 1.0E-6;
        }
        return realPlayer.level().dimension() != echoPlayer.level().dimension() || realPlayer.position().distanceToSqr(echoPlayer.position()) > 1.0E-6 || Math.abs(Mth.wrapDegrees(realPlayer.getYRot() - echoPlayer.getYRot())) > 0.01f || Math.abs(Mth.wrapDegrees(realPlayer.getXRot() - echoPlayer.getXRot())) > 0.01f;
    }

    private static void restoreRealPlayerForRespawn(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        realPlayer.setGameMode(state.originalGameMode);
        realPlayer.getInventory().clearContent();
        realPlayer.getInventory().load(state.originalInventory);
        StateSynchronizer.synchronizeEffects(state.shellPlayer, realPlayer);
        StateSynchronizer.synchronizeAttributes(state.shellPlayer, realPlayer, true);
        realPlayer.setAirSupply(state.shellPlayer.getAirSupply());
        realPlayer.setTicksFrozen(state.shellPlayer.getTicksFrozen());
        StateSynchronizer.copyFoodState(state.shellPlayer, realPlayer);
        StateSynchronizer.copyCooldownStateAndPackets(state.shellPlayer, realPlayer);
        realPlayer.setAbsorptionAmount(state.shellPlayer.getAbsorptionAmount());
        realPlayer.setInvisible(state.shellPlayer.isInvisible());
        realPlayer.setSilent(state.shellPlayer.isSilent());
        realPlayer.setGlowingTag(state.shellPlayer.hasGlowingTag());
        realPlayer.containerMenu.broadcastChanges();
    }

    private static void syncEchoStateToController(PossessionSession session) {
        StateSynchronizer.updateEchoEquipment(session.echoPlayer);
        if (session.controller != null) {
            copyEchoStateToRealController(session.controller);
        }
    }

    private static void syncCanonicalStateToController(PossessionSession session) {
        if (session.controller != null) {
            copyEchoSharedStateToRealController(session.controller);
        }
    }

    private static void synchronizePossessedFireState(ControllerState state) {
        int realFireTicks = state.realPlayer.getRemainingFireTicks();
        int echoFireTicks = state.echoPlayer.getRemainingFireTicks();
        int previousFireTicks = state.lastFireTicks;
        int synchronizedFireTicks;
        if (realFireTicks == echoFireTicks) {
            synchronizedFireTicks = echoFireTicks;
        } else if (realFireTicks > previousFireTicks && echoFireTicks <= previousFireTicks) {
            synchronizedFireTicks = realFireTicks;
        } else if (echoFireTicks > previousFireTicks && realFireTicks <= previousFireTicks) {
            synchronizedFireTicks = echoFireTicks;
        } else if (realFireTicks == 0 && echoFireTicks > 0) {
            synchronizedFireTicks = realFireTicks;
        } else if (echoFireTicks == 0 && realFireTicks > 0) {
            synchronizedFireTicks = echoFireTicks;
        } else {
            synchronizedFireTicks = echoFireTicks;
        }
        StateSynchronizer.setFireState(state.realPlayer, synchronizedFireTicks);
        StateSynchronizer.setFireState(state.echoPlayer, synchronizedFireTicks);
        state.lastFireTicks = synchronizedFireTicks;
    }

    private static void hideEchoFromReal(ControllerState state) {
        ControllerVisibility.hideEchoFromReal(state);
    }

    public static void hideControllerFromViewer(ServerPlayer controller, ServerPlayer viewer) {
        ControllerVisibility.hideControllerFromViewer(controller, viewer);
    }

    public static void hidePossessingControllersFromViewer(ServerPlayer viewer) {
        ControllerVisibility.hidePossessingControllersFromViewer(viewer);
    }

    public static void hideControllerFromObservers(ServerPlayer controller) {
        ControllerVisibility.hideControllerFromObservers(controller);
    }

    public static void showControllerToObservers(ServerPlayer controller) {
        ControllerVisibility.showControllerToObservers(controller);
    }

    public static void sendPlayerEntityToViewer(ServerPlayer controller, ServerPlayer viewer) {
        ControllerVisibility.sendPlayerEntityToViewer(controller, viewer);
    }

    private static void reshowEchoToReal(ControllerState state) {
        ControllerVisibility.reshowEchoToReal(state);
    }

    private static void sendEchoEntityToViewer(EchoServerPlayer echoPlayer, ServerPlayer viewer) {
        ControllerVisibility.sendEchoEntityToViewer(echoPlayer, viewer);
    }

    private static void removeEchoPlayerEntityAndData(EchoServerPlayer echoPlayer) {
        MinecraftServer server = echoPlayer.server;
        PENDING_ECHO_RESHOWS.entrySet().removeIf(entry -> ((PendingEchoReshow)entry.getValue()).echoPlayerId.equals(echoPlayer.getUUID()));
        if (echoPlayer.isPassenger()) {
            echoPlayer.stopRiding();
        }
        echoPlayer.ejectPassengers();
        ServerPlayer registeredPlayer = server.getPlayerList().getPlayer(echoPlayer.getUUID());
        if (registeredPlayer == echoPlayer) {
            server.getPlayerList().remove(echoPlayer);
        } else if (!echoPlayer.isRemoved()) {
            echoPlayer.serverLevel().removePlayerImmediately(echoPlayer, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        EchoPlayerSavedData.get(server).removeEchoPlayer(echoPlayer.getUUID());
        deletePlayerDataFiles(server, echoPlayer.getUUID());
    }

    private static void deletePlayerDataFiles(MinecraftServer server, UUID uuid) {
        deletePlayerDataFile(getPlayerDataPath(server, uuid, ".dat"));
        deletePlayerDataFile(getPlayerDataPath(server, uuid, ".dat_old"));
    }

    private static Path getPlayerDataPath(MinecraftServer server, UUID uuid, String suffix) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + suffix);
    }

    private static void deletePlayerDataFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            Constants.LOG.warn("Failed to delete EchoPlayer data file {}", (Object)path, (Object)exception);
        }
    }

    private static void sendPossessPacket(ControllerState state, List<EntityViewRotation> passiveViews) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(state.echoPlayer.getUUID());
        buf.writeInt(state.shellPlayer.getId());
        writeViewRotation(buf, captureViewRotation(state.realPlayer));
        writeEntityViewRotations(buf, passiveViews);
        Services.PLATFORM.sendToClient(state.realPlayer, NetworkPackets.POSSESS_PACKET, buf);
    }

    private static void sendUnpossessPacket(ServerPlayer realPlayer, List<EntityViewRotation> passiveViews) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writeViewRotation(buf, captureViewRotation(realPlayer));
        writeEntityViewRotations(buf, passiveViews);
        Services.PLATFORM.sendToClient(realPlayer, NetworkPackets.UNPOSSESS_PACKET, buf);
    }

    private static void writeEntityViewRotations(FriendlyByteBuf buf, List<EntityViewRotation> passiveViews) {
        buf.writeVarInt(passiveViews.size());
        for (EntityViewRotation entityView : passiveViews) {
            buf.writeInt(entityView.player.getId());
            writeViewRotation(buf, entityView.view);
        }
    }

    private static void writeViewRotation(FriendlyByteBuf buf, ViewRotation view) {
        buf.writeFloat(view.yRot);
        buf.writeFloat(view.xRot);
        buf.writeFloat(view.yHeadRot);
        buf.writeFloat(view.yBodyRot);
    }

    private static void removeShell(ControllerState state) {
        removeShellEntity(state.shellPlayer, state.realPlayer.server);
    }

    private static void removeShellEntity(EchoServerPlayer shellPlayer, MinecraftServer server) {
        shellPlayer.discard();
        ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(List.of(shellPlayer.getUUID()));
        ClientboundRemoveEntitiesPacket entityRemovePacket = new ClientboundRemoveEntitiesPacket(shellPlayer.getId());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(removePacket);
            player.connection.send(entityRemovePacket);
        }
    }

    private static void commitControllerContainer(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        if (realPlayer.isRemoved() || echoPlayer.isRemoved()) {
            return;
        }
        StateSynchronizer.synchronizeInventoryContents(echoPlayer, realPlayer);
        realPlayer.closeContainer();
        if (StateSynchronizer.synchronizeInventoryContents(realPlayer, echoPlayer)) {
            StateSynchronizer.updateEchoEquipment(echoPlayer);
        }
    }

    static final class PossessionSession {
        final EchoServerPlayer echoPlayer;
        ControllerState controller;
        long lastDamageGameTime = Long.MIN_VALUE;
        String lastDamageType = "";
        int lastDamageDirectEntityId = Integer.MIN_VALUE;
        int lastDamageCausingEntityId = Integer.MIN_VALUE;
        float lastDamageAmount;
        long lastEffectTickGameTime = Long.MIN_VALUE;
        boolean tickingCanonicalEffects;
        boolean synchronizingEffects;

        PossessionSession(EchoServerPlayer echoPlayer) {
            this.echoPlayer = echoPlayer;
        }
    }

    static final class ControllerState {
        final ServerPlayer realPlayer;
        final EchoServerPlayer echoPlayer;
        final EchoServerPlayer shellPlayer;
        final PossessionSession session;
        final ListTag originalInventory;
        final GameType originalGameMode;
        public ItemStack[] lastInventoryState;
        public float lastHealth;
        public int lastFoodLevel;
        public float lastSaturation;
        public float lastExhaustion;
        int lastFoodTickTimer;
        int lastFoodDataLevel;
        public float lastAbsorption;
        int lastFireTicks;
        int lastAirSupply;
        int lastTicksFrozen;
        boolean lastInvisible;
        boolean lastGlowing;
        GameType lastSyncGameMode;

        ControllerState(ServerPlayer realPlayer, EchoServerPlayer echoPlayer, EchoServerPlayer shellPlayer, PossessionSession session, ControllerState previousState) {
            this.realPlayer = realPlayer;
            this.echoPlayer = echoPlayer;
            this.shellPlayer = shellPlayer;
            this.session = session;
            if (previousState != null) {
                this.originalInventory = previousState.originalInventory;
                this.originalGameMode = previousState.originalGameMode;
            } else {
                this.originalInventory = new ListTag();
                realPlayer.getInventory().save(this.originalInventory);
                this.originalGameMode = realPlayer.gameMode.getGameModeForPlayer();
            }
            int size = echoPlayer.getInventory().getContainerSize();
            this.lastInventoryState = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                ItemStack echoStack = echoPlayer.getInventory().getItem(i);
                this.lastInventoryState[i] = echoStack.copy();
                realPlayer.getInventory().setItem(i, echoStack.copy());
            }
            this.lastHealth = echoPlayer.getHealth();
            this.lastFoodLevel = echoPlayer.getFoodData().getFoodLevel();
            this.lastSaturation = shellPlayer.getFoodData().getSaturationLevel();
            this.lastExhaustion = shellPlayer.getFoodData().getExhaustionLevel();
            FoodDataAccessor echoFood = (FoodDataAccessor)((Object)echoPlayer.getFoodData());
            this.lastFoodTickTimer = echoFood.echoplayer$getTickTimer();
            this.lastFoodDataLevel = echoFood.echoplayer$getLastFoodLevel();
            this.lastAbsorption = shellPlayer.getAbsorptionAmount();
            this.lastFireTicks = echoPlayer.getRemainingFireTicks();
            this.lastAirSupply = echoPlayer.getAirSupply();
            this.lastTicksFrozen = echoPlayer.getTicksFrozen();
            this.lastInvisible = echoPlayer.isInvisible();
            this.lastGlowing = echoPlayer.hasGlowingTag();
            realPlayer.setHealth(this.lastHealth);
            realPlayer.getFoodData().setFoodLevel(this.lastFoodLevel);
            realPlayer.getFoodData().setSaturation(this.lastSaturation);
            realPlayer.getFoodData().setExhaustion(this.lastExhaustion);
            this.lastSyncGameMode = this.originalGameMode;
        }
    }

    private static final class ViewRotation {
        final float yRot;
        final float xRot;
        final float yHeadRot;
        final float yBodyRot;

        ViewRotation(float yRot, float xRot, float yHeadRot, float yBodyRot) {
            this.yRot = yRot;
            this.xRot = xRot;
            this.yHeadRot = yHeadRot;
            this.yBodyRot = yBodyRot;
        }
    }

    private static final class EntityViewRotation {
        final EchoServerPlayer player;
        final ViewRotation view;

        EntityViewRotation(EchoServerPlayer player, ViewRotation view) {
            this.player = player;
            this.view = view;
        }
    }

    static final class PendingEchoReshow {
        final UUID echoPlayerId;
        final float health;
        final GameType gameMode;

        PendingEchoReshow(EchoServerPlayer echoPlayer) {
            this.echoPlayerId = echoPlayer.getUUID();
            this.health = echoPlayer.getHealth();
            this.gameMode = echoPlayer.gameMode.getGameModeForPlayer();
        }
    }
}
