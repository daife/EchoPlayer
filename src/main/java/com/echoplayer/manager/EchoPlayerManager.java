package com.echoplayer.manager;

import com.echoplayer.Constants;
import com.echoplayer.data.EchoPlayerSavedData;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.mixin.AttributeInstanceAccessor;
import com.echoplayer.mixin.AttributeMapAccessor;
import com.echoplayer.mixin.CommandSourceStackAccessor;
import com.echoplayer.mixin.LivingEntityInvoker;
import com.echoplayer.mixin.MobEffectInstanceAccessor;
import com.echoplayer.mixin.PlayerAccessor;
import com.echoplayer.mixin.ServerGamePacketListenerImplAccessor;
import com.echoplayer.network.EchoConnection;
import com.echoplayer.network.EchoServerGamePacketListenerImpl;
import com.echoplayer.network.NetworkPackets;
import com.echoplayer.platform.Services;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
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
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

public class EchoPlayerManager {
    private static final UUID SPRINTING_SPEED_MODIFIER_ID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final Set<UUID> MOVEMENT_SPEED_STATE_MODIFIER_IDS = Set.of(SPRINTING_SPEED_MODIFIER_ID, UUID.fromString("87f46a96-686f-4796-b035-22e16ee9e038"), UUID.fromString("1eaf83ff-7207-4596-b37a-d7a07b3ec4ce"));
    private static final Map<UUID, ControllerState> CONTROLLERS = new ConcurrentHashMap<UUID, ControllerState>();
    private static final Map<UUID, PossessionSession> SESSIONS = new ConcurrentHashMap<UUID, PossessionSession>();
    private static final Map<UUID, PendingEchoReshow> PENDING_ECHO_RESHOWS = new ConcurrentHashMap<UUID, PendingEchoReshow>();

    public static ServerPlayer getPossessor(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        ControllerState controller = EchoPlayerManager.chooseAuthoritativeController(session);
        return controller != null ? controller.realPlayer : null;
    }

    public static EchoServerPlayer getPossessed(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        return state != null ? state.echoPlayer : null;
    }

    public static ServerPlayer getController(Player echoPlayer) {
        ControllerState state;
        if (echoPlayer == null) {
            return null;
        }
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null && session.authoritativeControllerId != null && (state = session.controllers.get(session.authoritativeControllerId)) != null) {
            return state.realPlayer;
        }
        return null;
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
        ServerPlayer avatar = EchoPlayerManager.getIdentityAvatar(authenticatedPlayerId);
        return avatar != null && avatar.level() == level ? avatar : null;
    }

    public static Entity getLogicalDamageEntity(Entity entity) {
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)entity;
            return EchoPlayerManager.getLogicalPlayer(player);
        }
        return entity;
    }

    public static ServerPlayer getAuthenticatedPlayer(ServerPlayer logicalPlayer) {
        EchoServerPlayer echoPlayer;
        ServerPlayer possessor;
        if (logicalPlayer instanceof EchoServerPlayer && (possessor = EchoPlayerManager.getPossessor(echoPlayer = (EchoServerPlayer)logicalPlayer)) != null) {
            return possessor;
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
            projected.add(EchoPlayerManager.projectSelectorPlayer(player));
        }
        return projected;
    }

    public static List<ServerPlayer> projectSleepStatusPlayers(List<ServerPlayer> players) {
        if (CONTROLLERS.isEmpty()) {
            return players;
        }
        ArrayList<ServerPlayer> projected = new ArrayList<ServerPlayer>(players.size());
        for (ServerPlayer player : players) {
            if (!CONTROLLERS.containsKey(player.getUUID())) {
                projected.add(player);
            }
        }
        return projected;
    }

    public static List<ServerPlayer> getProjectedLevelPlayers(ServerLevel level, Predicate<? super ServerPlayer> predicate, int limit) {
        ArrayList<ServerPlayer> projected = new ArrayList<ServerPlayer>(Math.min(limit, level.getServer().getPlayerCount()));
        for (ServerPlayer player : EchoPlayerManager.projectSelectorPlayers(level.getServer().getPlayerList().getPlayers())) {
            if (player.serverLevel() != level || !predicate.test(player)) continue;
            projected.add(player);
            if (projected.size() < limit) continue;
            break;
        }
        return projected;
    }

    public static List<? extends Entity> filterLogicalSelectorEntities(List<? extends Entity> entities) {
        ArrayList<Entity> filtered = new ArrayList<Entity>(entities.size());
        for (Entity entity : entities) {
            ServerPlayer player;
            if (entity instanceof ServerPlayer && EchoPlayerManager.isPossessing(player = (ServerPlayer)entity)) continue;
            filtered.add(entity);
        }
        return filtered;
    }

    public static int getInheritedPermissionLevel(EchoServerPlayer echoPlayer) {
        ServerPlayer authenticatedPlayer = EchoPlayerManager.getAuthenticatedPlayer(echoPlayer);
        if (authenticatedPlayer == echoPlayer) {
            return -1;
        }
        for (int level = 4; level >= 0; --level) {
            if (!authenticatedPlayer.hasPermissions(level)) continue;
            return level;
        }
        return 0;
    }

    public static List<Entity> filterControlledBoatPlacementEntities(Entity source, List<Entity> entities) {
        ServerPlayer controller;
        block9: {
            block8: {
                if (!(source instanceof ServerPlayer)) break block8;
                controller = (ServerPlayer)source;
                if (!entities.isEmpty()) break block9;
            }
            return entities;
        }
        ControllerState state = CONTROLLERS.get(controller.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return entities;
        }
        List<Entity> filtered = null;
        for (int i = 0; i < entities.size(); ++i) {
            Entity entity = entities.get(i);
            if (EchoPlayerManager.isInternalBoatPlacementEntity(state, entity)) {
                if (filtered != null) continue;
                filtered = new ArrayList<Entity>(entities.size() - 1);
                for (int j = 0; j < i; ++j) {
                    filtered.add(entities.get(j));
                }
                continue;
            }
            if (filtered == null) continue;
            filtered.add(entity);
        }
        return filtered != null ? filtered : entities;
    }

    private static boolean isInternalBoatPlacementEntity(ControllerState state, Entity entity) {
        if (entity == state.echoPlayer || entity == state.shellPlayer) {
            return true;
        }
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)entity;
            ControllerState otherState = CONTROLLERS.get(player.getUUID());
            return otherState != null && otherState.session == state.session;
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
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
            }
            finally {
                session.tickingCanonicalEffects = false;
            }
        }
        EchoPlayerManager.synchronizeEffects(state.echoPlayer, realPlayer);
        EchoPlayerManager.synchronizeAttributes(state.echoPlayer, realPlayer, false);
        realPlayer.setAbsorptionAmount(state.echoPlayer.getAbsorptionAmount());
        EchoPlayerManager.hideControllerBody(realPlayer);
        if (EchoPlayerManager.isAuthoritativeController(state)) {
            realPlayer.absMoveTo(state.echoPlayer.getX(), state.echoPlayer.getY(), state.echoPlayer.getZ(), realPlayer.getYRot(), realPlayer.getXRot());
        } else {
            realPlayer.absMoveTo(state.echoPlayer.getX(), state.echoPlayer.getY(), state.echoPlayer.getZ(), state.echoPlayer.getYRot(), state.echoPlayer.getXRot());
        }
    }

    public static boolean shouldCancelPossessedEchoEffectTick(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        return session != null && !session.tickingCanonicalEffects;
    }

    public static void syncLogicalStateAfterExternalMutation(Entity entity) {
        ServerPlayer player;
        ControllerState state;
        if (entity instanceof EchoServerPlayer) {
            EchoServerPlayer echoPlayer = (EchoServerPlayer)entity;
            if (echoPlayer.linkedRealPlayer == null) {
                PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
                if (session != null) {
                    EchoPlayerManager.syncEchoStateToControllers(session);
                }
                return;
            }
        }
        if (entity instanceof ServerPlayer && (state = CONTROLLERS.get((player = (ServerPlayer)entity).getUUID())) != null && !state.echoPlayer.isRemoved() && !state.echoPlayer.isDeadOrDying()) {
            EchoPlayerManager.copyRealStateToEcho(state, true);
            EchoPlayerManager.syncCanonicalStateToControllers(state.session);
        }
    }

    public static void syncLogicalStateAfterExternalMutation(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            EchoPlayerManager.syncLogicalStateAfterExternalMutation(entity);
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
        EchoPlayerManager.updateEchoEquipment(echoPlayer);
        EchoPlayerManager.syncCanonicalStateToControllers(session);
    }

    public static ServerPlayer getCommandExecutor(ServerPlayer player) {
        EchoServerPlayer echoPlayer;
        ServerPlayer possessor;
        if (player instanceof EchoServerPlayer && (possessor = EchoPlayerManager.getPossessor(echoPlayer = (EchoServerPlayer)player)) != null) {
            return possessor;
        }
        return player;
    }

    public static ServerPlayer getCommandExecutor(CommandSourceStack source) throws CommandSyntaxException {
        CommandSource commandSource = ((CommandSourceStackAccessor)((Object)source)).echoplayer$getSource();
        if (commandSource instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)commandSource;
            return EchoPlayerManager.getCommandExecutor(player);
        }
        return EchoPlayerManager.getCommandExecutor(source.getPlayerOrException());
    }

    public static CommandSourceStack createPossessedCommandSource(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying()) {
            return realPlayer.createCommandSourceStack();
        }
        EchoPlayerManager.claimController(state);
        EchoServerPlayer echoPlayer = state.echoPlayer;
        return realPlayer.createCommandSourceStack().withEntity(echoPlayer).withLevel(echoPlayer.serverLevel()).withPosition(echoPlayer.position()).withRotation(echoPlayer.getRotationVector());
    }

    public static void syncPossessedAfterCommand(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying() || realPlayer.isDeadOrDying()) {
            return;
        }
        EchoPlayerManager.syncEchoStateToControllers(state.session);
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static boolean isPossessed(Entity entity) {
        if (!(entity instanceof EchoServerPlayer)) return false;
        EchoServerPlayer echoPlayer = (EchoServerPlayer)entity;
        if (echoPlayer.linkedRealPlayer != null) return false;
        if (!SESSIONS.containsKey(echoPlayer.getUUID())) return false;
        return true;
    }

    public static boolean isPossessing(ServerPlayer player) {
        return CONTROLLERS.containsKey(player.getUUID());
    }

    public static boolean shouldDisableCollision(Entity e1, Entity e2) {
        if (e1 instanceof ServerPlayer) {
            ServerPlayer p1 = (ServerPlayer)e1;
            if (e2 instanceof EchoServerPlayer) {
                EchoServerPlayer f2 = (EchoServerPlayer)e2;
                return EchoPlayerManager.getPossessed(p1) == f2;
            }
        }
        if (e2 instanceof ServerPlayer) {
            ServerPlayer p2 = (ServerPlayer)e2;
            if (e1 instanceof EchoServerPlayer) {
                EchoServerPlayer f1 = (EchoServerPlayer)e1;
                return EchoPlayerManager.getPossessed(p2) == f1;
            }
        }
        return false;
    }

    public static void markControllerInput(ServerPlayer realPlayer, long sequence, int inputMask) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || realPlayer.hasDisconnected() || realPlayer.isDeadOrDying() || inputMask == 0 || sequence <= state.lastClientSequence) {
            return;
        }
        state.lastClientSequence = sequence;
        EchoPlayerManager.claimController(state);
    }

    public static boolean shouldRunPassivePhysics(EchoServerPlayer echoPlayer) {
        if (echoPlayer == null || echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return false;
        }
        if (echoPlayer.linkedRealPlayer != null) {
            return true;
        }
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        return session == null || session.controllers.isEmpty();
    }

    public static boolean isMultiControlEnabled(MinecraftServer server) {
        return EchoPlayerSavedData.get(server).isAllowMultipleControllers();
    }

    public static void setMultiControlEnabled(MinecraftServer server, boolean enabled) {
        EchoPlayerSavedData.get(server).setAllowMultipleControllers(enabled);
    }

    public static List<EchoServerPlayer> getEchoPlayersByName(MinecraftServer server, String name) {
        ArrayList<EchoServerPlayer> echoPlayers = new ArrayList<EchoServerPlayer>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EchoServerPlayer)) continue;
            EchoServerPlayer echoPlayer = (EchoServerPlayer)player;
            if (echoPlayer.linkedRealPlayer != null || !player.getGameProfile().getName().equalsIgnoreCase(name)) continue;
            echoPlayers.add(echoPlayer);
        }
        return echoPlayers;
    }

    public static List<String> getEchoPlayerNames(MinecraftServer server) {
        ArrayList<String> names = new ArrayList<String>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EchoServerPlayer)) continue;
            EchoServerPlayer echoPlayer = (EchoServerPlayer)player;
            if (echoPlayer.linkedRealPlayer != null) continue;
            names.add(player.getGameProfile().getName());
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

    public static EchoServerPlayer spawnEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, boolean persistent) {
        String conflict = EchoPlayerManager.getSpawnConflict(server, profile);
        if (conflict != null) {
            throw new IllegalArgumentException(conflict);
        }
        return EchoPlayerManager.createEchoPlayer(server, level, profile, persistent);
    }

    private static EchoServerPlayer createEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, boolean persistent) {
        EchoServerPlayer echoPlayer = new EchoServerPlayer(server, level, profile);
        EchoConnection connection = new EchoConnection(PacketFlow.SERVERBOUND);
        server.getPlayerList().placeNewPlayer(connection, echoPlayer);
        EchoServerGamePacketListenerImpl listener = new EchoServerGamePacketListenerImpl(server, connection, echoPlayer);
        echoPlayer.connection = listener;
        connection.setListener(listener);
        if (persistent) {
            EchoPlayerSavedData.get(server).addEchoPlayer(profile);
        }
        return echoPlayer;
    }

    public static EchoServerPlayer spawnEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        return EchoPlayerManager.spawnEchoPlayer(server, level, profile, true);
    }

    public static EchoServerPlayer respawnPersistentEchoPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        return EchoPlayerManager.spawnEchoPlayer(server, level, profile, false);
    }

    public static void updateSkinAsync(final MinecraftServer server, final EchoServerPlayer echoPlayer, final String skinSourceUsername, final CommandSourceStack source) {
        CompletableFuture.runAsync(() -> server.getProfileRepository().findProfilesByNames(new String[]{skinSourceUsername}, Agent.MINECRAFT, new ProfileLookupCallback(){

            public void onProfileLookupSucceeded(GameProfile profile) {
                GameProfile filledProfile = server.getSessionService().fillProfileProperties(profile, true);
                server.execute(() -> {
                    if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
                        return;
                    }
                    GameProfile targetProfile = echoPlayer.getGameProfile();
                    targetProfile.getProperties().removeAll("textures");
                    for (Property property : filledProfile.getProperties().get("textures")) {
                        targetProfile.getProperties().put("textures", property);
                    }
                    EchoPlayerSavedData.get(server).setDirty();
                    EchoPlayerManager.resendSkinPackets(server, echoPlayer);
                    source.sendSuccess(() -> Component.literal("Successfully updated skin for " + targetProfile.getName() + " to match " + skinSourceUsername), true);
                });
            }

            public void onProfileLookupFailed(GameProfile profile, Exception e) {
                server.execute(() -> source.sendFailure(Component.literal("Could not find player: " + skinSourceUsername)));
            }
        }));
    }

    private static String normalizeSkinUrl(String url) {
        Matcher nameMcMatcher = Pattern.compile("namemc\\.com/skin/([a-zA-Z0-9]+)").matcher(url);
        if (nameMcMatcher.find()) {
            return "https://s.namemc.com/i/" + nameMcMatcher.group(1) + ".png";
        }
        Matcher novaSkinMatcher = Pattern.compile("novask\\.in/([0-9]+)").matcher(url);
        if (novaSkinMatcher.find() && !url.endsWith(".png")) {
            return "http://novask.in/" + novaSkinMatcher.group(1) + ".png";
        }
        Matcher imgurMatcher = Pattern.compile("imgur\\.com/([a-zA-Z0-9]+)$").matcher(url);
        if (imgurMatcher.find()) {
            return "https://i.imgur.com/" + imgurMatcher.group(1) + ".png";
        }
        return url;
    }

    public static void updateSkinFromUrlAsync(MinecraftServer server, EchoServerPlayer echoPlayer, String rawUrl, CommandSourceStack source) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = EchoPlayerManager.normalizeSkinUrl(rawUrl);
                HttpClient client = HttpClient.newHttpClient();
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("url", url);
                requestBody.addProperty("visibility", (Number)0);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.mineskin.org/generate/url")).header("Content-Type", "application/json").header("User-Agent", "EchoPlayer/1.0").POST(HttpRequest.BodyPublishers.ofString(requestBody.toString())).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString((String)response.body()).getAsJsonObject();
                    JsonObject data = json.getAsJsonObject("data");
                    JsonObject texture = data.getAsJsonObject("texture");
                    String value = texture.get("value").getAsString();
                    String signature = texture.get("signature").getAsString();
                    server.execute(() -> {
                        if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
                            return;
                        }
                        GameProfile targetProfile = echoPlayer.getGameProfile();
                        targetProfile.getProperties().removeAll("textures");
                        targetProfile.getProperties().put("textures", new Property("textures", value, signature));
                        EchoPlayerSavedData.get(server).setDirty();
                        EchoPlayerManager.resendSkinPackets(server, echoPlayer);
                        source.sendSuccess(() -> Component.literal("Successfully updated skin from URL."), true);
                    });
                } else {
                    server.execute(() -> {
                        try {
                            JsonObject err = JsonParser.parseString((String)((String)response.body())).getAsJsonObject();
                            String msg = err.has("error") ? err.get("error").getAsString() : "Unknown error";
                            source.sendFailure(Component.literal("Failed to generate skin: " + msg));
                        }
                        catch (Exception ex) {
                            source.sendFailure(Component.literal("Failed to generate skin. Status code: " + response.statusCode()));
                        }
                    });
                }
            }
            catch (Exception e) {
                server.execute(() -> source.sendFailure(Component.literal("Exception while generating skin: " + e.getMessage())));
            }
        });
    }

    public static void clearSkin(MinecraftServer server, EchoServerPlayer echoPlayer, CommandSourceStack source) {
        if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return;
        }
        GameProfile targetProfile = echoPlayer.getGameProfile();
        targetProfile.getProperties().removeAll("textures");
        EchoPlayerSavedData.get(server).setDirty();
        EchoPlayerManager.resendSkinPackets(server, echoPlayer);
        source.sendSuccess(() -> Component.literal("Successfully cleared skin for " + targetProfile.getName()), true);
    }

    private static void resendSkinPackets(MinecraftServer server, EchoServerPlayer echoPlayer) {
        ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(List.of(echoPlayer.getUUID()));
        ClientboundPlayerInfoUpdatePacket updatePacket = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(echoPlayer));
        ClientboundRemoveEntitiesPacket removeEntityPacket = new ClientboundRemoveEntitiesPacket(echoPlayer.getId());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(removePacket);
            player.connection.send(updatePacket);
            if (player == echoPlayer || EchoPlayerManager.getPossessed(player) == echoPlayer || player.level().dimension() != echoPlayer.level().dimension()) continue;
            int trackingChunks = Math.min(echoPlayer.getType().clientTrackingRange(), server.getPlayerList().getViewDistance());
            double trackingRange = (double)trackingChunks * 16.0;
            if (!(echoPlayer.distanceToSqr(player) <= trackingRange * trackingRange)) continue;
            player.connection.send(removeEntityPacket);
            EchoPlayerManager.sendPlayerEntityToViewer(echoPlayer, player);
        }
    }

    public static String possess(ServerPlayer realPlayer, EchoServerPlayer echoPlayer) {
        Entity realVehicle;
        EchoPlayerManager.restorePendingEchoState(echoPlayer);
        if (CONTROLLERS.containsKey(realPlayer.getUUID())) {
            return "You are already controlling an EchoPlayer.";
        }
        if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying() || echoPlayer.linkedRealPlayer != null) {
            return "EchoPlayer " + echoPlayer.getGameProfile().getName() + " is not available.";
        }
        boolean allowMultipleControllers = EchoPlayerSavedData.get(realPlayer.server).isAllowMultipleControllers();
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null && !session.controllers.isEmpty() && !allowMultipleControllers) {
            return "EchoPlayer " + echoPlayer.getGameProfile().getName() + " is already being controlled.";
        }
        if (session == null) {
            session = new PossessionSession(echoPlayer);
            SESSIONS.put(echoPlayer.getUUID(), session);
        }
        EchoPlayerManager.createCrashBackup(realPlayer);
        EchoServerPlayer shell = EchoPlayerManager.createOriginalBodyShell(realPlayer);
        ControllerState state = new ControllerState(realPlayer, echoPlayer, shell, session);
        ControllerState previousState = CONTROLLERS.putIfAbsent(realPlayer.getUUID(), state);
        if (previousState != null) {
            EchoPlayerManager.removeShellEntity(shell, realPlayer.server);
            return "You are already controlling an EchoPlayer.";
        }
        ControllerState previousController = session.controllers.putIfAbsent(realPlayer.getUUID(), state);
        if (previousController != null) {
            CONTROLLERS.remove(realPlayer.getUUID(), state);
            EchoPlayerManager.removeShellEntity(shell, realPlayer.server);
            return "You are already controlling this EchoPlayer.";
        }
        if (session.authoritativeControllerId == null) {
            EchoPlayerManager.claimController(state);
        }
        EchoPlayerManager.updateLogicalSleepStatus(state);
        if ((realVehicle = realPlayer.getVehicle()) != null) {
            realPlayer.stopRiding();
        }
        Entity echoVehicle = echoPlayer.getVehicle();
        EchoPlayerManager.teleportRealPlayerToEcho(state, true);
        EchoPlayerManager.copyEchoStateToRealController(state);
        EchoPlayerManager.syncControlledEchoToController(state);
        EchoPlayerManager.copyRealStateToEcho(state, true);
        EchoPlayerManager.hideControllerBody(realPlayer);
        EchoPlayerManager.sendPossessPacket(state);
        EchoPlayerManager.hideEchoFromReal(state);
        EchoPlayerManager.hideControllerFromObservers(realPlayer);
        if (realVehicle != null) {
            shell.startRiding(realVehicle, true);
        }
        if (echoVehicle != null) {
            int[] passengerIds = new int[echoVehicle.getPassengers().size()];
            for (int i = 0; i < echoVehicle.getPassengers().size(); ++i) {
                Entity p = echoVehicle.getPassengers().get(i);
                passengerIds[i] = p == echoPlayer ? realPlayer.getId() : p.getId();
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(echoVehicle.getId());
            buf.writeVarIntArray(passengerIds);
            realPlayer.connection.send(new ClientboundSetPassengersPacket(buf));
        }
        return null;
    }

    public static boolean handlePossessedDamage(ServerPlayer realPlayer, DamageSource source, float amount) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.echoPlayer.isRemoved() || state.echoPlayer.isDeadOrDying() || !EchoPlayerManager.isAuthoritativeController(state)) {
            return false;
        }
        boolean damaged = state.echoPlayer.hurt(source, amount);
        if (damaged && !state.echoPlayer.isDeadOrDying() && !state.echoPlayer.isRemoved()) {
            EchoPlayerManager.copyRealStateToEcho(state, true);
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
            EchoPlayerManager.updateEchoEquipment(state.echoPlayer);
            EchoPlayerManager.syncCanonicalStateToControllers(state.session);
        }
        return added;
    }

    public static void prepareEchoForIncomingDamage(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session == null) {
            return;
        }
        ControllerState authoritative = EchoPlayerManager.chooseAuthoritativeController(session);
        if (authoritative != null && !authoritative.realPlayer.isDeadOrDying() && !authoritative.realPlayer.hasDisconnected()) {
            EchoPlayerManager.syncControlledEchoToController(authoritative);
            EchoPlayerManager.copyRealStateToEcho(authoritative, false);
        }
    }

    public static void afterEchoHurt(EchoServerPlayer echoPlayer, DamageSource source) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null) {
            for (ControllerState state : session.controllers.values()) {
                ServerPlayer realPlayer;
                if (EchoPlayerManager.isAuthoritativeController(state)) {
                    EchoPlayerManager.copyRealStateToEcho(state, true);
                } else {
                    EchoPlayerManager.syncFollowingControllerFromEcho(state);
                }
                if ((realPlayer = state.realPlayer).isDeadOrDying() || realPlayer.hasDisconnected()) continue;
                realPlayer.connection.send(new ClientboundEntityEventPacket(realPlayer, (byte)2));
                realPlayer.connection.send(new ClientboundDamageEventPacket(realPlayer, source));
            }
        }
    }

    public static void afterEchoKnockback(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null) {
            for (ControllerState state : session.controllers.values()) {
                ServerPlayer realPlayer;
                if (!EchoPlayerManager.isAuthoritativeController(state) || (realPlayer = state.realPlayer).isDeadOrDying() || realPlayer.hasDisconnected()) continue;
                realPlayer.setDeltaMovement(echoPlayer.getDeltaMovement());
                realPlayer.connection.send(new ClientboundSetEntityMotionPacket(realPlayer));
            }
        }
    }

    public static void revertPossession(ServerPlayer realPlayer) {
        Entity realVehicle;
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null) {
            return;
        }
        Entity echoVehicle = realPlayer.getVehicle();
        if (echoVehicle != null) {
            realPlayer.stopRiding();
        }
        if ((realVehicle = state.shellPlayer.getVehicle()) != null) {
            state.shellPlayer.stopRiding();
        }
        EchoPlayerManager.commitControllerContainer(state);
        if (EchoPlayerManager.isAuthoritativeController(state)) {
            EchoPlayerManager.syncControlledEchoToController(state);
            EchoPlayerManager.copyRealStateToEcho(state, !state.echoPlayer.isDeadOrDying());
        }
        EchoPlayerManager.removeControllerState(state);
        EchoPlayerManager.removeShell(state);
        if (!realPlayer.isDeadOrDying()) {
            EchoPlayerManager.restoreRealPlayerFromShell(state, true);
            EchoPlayerManager.sendUnpossessPacket(realPlayer);
            EchoPlayerManager.reshowEchoToReal(state);
            EchoPlayerManager.removeCrashBackup(realPlayer);
        } else {
            EchoPlayerManager.restoreRealPlayerForRespawn(state);
            EchoPlayerManager.sendUnpossessPacket(realPlayer);
            EchoPlayerManager.removeCrashBackup(realPlayer);
        }
        EchoPlayerManager.showControllerToObservers(realPlayer);
        if (echoVehicle != null && !state.echoPlayer.isDeadOrDying() && !state.echoPlayer.isRemoved()) {
            state.echoPlayer.startRiding(echoVehicle, true);
        }
        if (realVehicle != null && !realPlayer.isDeadOrDying()) {
            realPlayer.startRiding(realVehicle, true);
        }
    }

    public static void revertAllPossessions(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session == null) {
            return;
        }
        for (ControllerState state : List.copyOf(session.controllers.values())) {
            EchoPlayerManager.revertPossession(state.realPlayer);
        }
    }

    public static void ejectControllersOnDeath(EchoServerPlayer echoPlayer) {
        PossessionSession session = SESSIONS.get(echoPlayer.getUUID());
        if (session != null) {
            for (ControllerState state : List.copyOf(session.controllers.values())) {
                EchoPlayerManager.revertPossession(state.realPlayer);
            }
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
        if (session == null) {
            return;
        }
        for (ControllerState state : List.copyOf(session.controllers.values())) {
            EchoPlayerManager.commitControllerContainer(state);
        }
    }

    public static void finalizeOriginalBodyDeath(EchoServerPlayer shellPlayer, DamageSource source, float amount) {
        ControllerState state = EchoPlayerManager.findControllerByShell(shellPlayer);
        if (state == null) {
            shellPlayer.discard();
            return;
        }
        boolean wasAuthoritative = EchoPlayerManager.isAuthoritativeController(state);
        PendingEchoReshow pendingEchoReshow = new PendingEchoReshow(state.echoPlayer);
        EchoPlayerManager.commitControllerContainer(state);
        if (wasAuthoritative) {
            EchoPlayerManager.syncControlledEchoToController(state);
            EchoPlayerManager.copyRealStateToEcho(state, false);
        }
        EchoPlayerManager.removeControllerState(state);
        EchoPlayerManager.restoreRealPlayerFromShell(state, false);
        EchoPlayerManager.teleportRealPlayerToShell(state);
        EchoPlayerManager.removeShell(state);
        EchoPlayerManager.showControllerToObservers(state.realPlayer);
        EchoPlayerManager.sendUnpossessPacket(state.realPlayer);
        PENDING_ECHO_RESHOWS.put(state.realPlayer.getUUID(), pendingEchoReshow);
        float recordedAmount = amount > 0.0f ? amount : state.realPlayer.getMaxHealth();
        state.realPlayer.getCombatTracker().recordDamage(source, recordedAmount);
        state.realPlayer.setHealth(0.0f);
        state.realPlayer.die(source);
        EchoPlayerManager.restoreEchoStateAndWorld(state.echoPlayer, pendingEchoReshow);
        EchoPlayerManager.removeCrashBackup(state.realPlayer);
    }

    public static void removeEchoPlayer(EchoServerPlayer echoPlayer) {
        if (echoPlayer.linkedRealPlayer != null) {
            echoPlayer.discard();
            return;
        }
        PossessionSession session = SESSIONS.remove(echoPlayer.getUUID());
        if (session != null) {
            for (ControllerState state : List.copyOf(session.controllers.values())) {
                EchoPlayerManager.commitControllerContainer(state);
                EchoPlayerManager.removeControllerState(state);
                EchoPlayerManager.removeShell(state);
                EchoPlayerManager.restoreRealPlayerFromShell(state, true);
                EchoPlayerManager.showControllerToObservers(state.realPlayer);
                EchoPlayerManager.sendUnpossessPacket(state.realPlayer);
                EchoPlayerManager.removeCrashBackup(state.realPlayer);
            }
        }
        EchoPlayerManager.removeEchoPlayerEntityAndData(echoPlayer);
    }

    public static void completePendingEchoReshow(ServerPlayer player) {
        EchoServerPlayer echoPlayer;
        PendingEchoReshow pending;
        block5: {
            block4: {
                pending = PENDING_ECHO_RESHOWS.remove(player.getUUID());
                if (pending == null) {
                    return;
                }
                ServerPlayer registeredPlayer = player.server.getPlayerList().getPlayer(pending.echoPlayerId);
                if (!(registeredPlayer instanceof EchoServerPlayer)) break block4;
                echoPlayer = (EchoServerPlayer)registeredPlayer;
                if (echoPlayer.linkedRealPlayer == null) break block5;
            }
            return;
        }
        EchoPlayerManager.restoreEchoStateAndWorld(echoPlayer, pending);
        EchoPlayerManager.sendEchoEntityToViewer(echoPlayer, player);
    }

    private static void restorePendingEchoState(EchoServerPlayer echoPlayer) {
        for (PendingEchoReshow pending : PENDING_ECHO_RESHOWS.values()) {
            if (!pending.echoPlayerId.equals(echoPlayer.getUUID())) continue;
            EchoPlayerManager.restoreEchoStateAndWorld(echoPlayer, pending);
            return;
        }
    }

    private static void restoreEchoStateAndWorld(EchoServerPlayer echoPlayer, PendingEchoReshow pending) {
        boolean wasRemoved = echoPlayer.isRemoved();
        echoPlayer.restoreAfterControllerBodyDeath(pending.health);
        EchoPlayerManager.setGameModeIfNeeded(echoPlayer, pending.gameMode);
        if (wasRemoved) {
            echoPlayer.serverLevel().addRespawnedPlayer(echoPlayer);
        }
    }

    public static void tick() {
        for (PossessionSession session : List.copyOf(SESSIONS.values())) {
            EchoPlayerManager.tickSession(session);
        }
    }

    private static void tickSession(PossessionSession session) {
        EchoServerPlayer echoPlayer = session.echoPlayer;
        if (echoPlayer.isDeadOrDying()) {
            EchoPlayerManager.ejectControllersOnDeath(echoPlayer);
            return;
        }
        if (echoPlayer.isRemoved()) {
            SESSIONS.remove(echoPlayer.getUUID(), session);
            EchoPlayerManager.endSessionControllers(session, false);
            return;
        }
        for (ControllerState state : List.copyOf(session.controllers.values())) {
            ServerPlayer realPlayer = state.realPlayer;
            if (realPlayer.hasDisconnected()) {
                EchoPlayerManager.revertPossession(realPlayer);
                continue;
            }
            if (state.shellPlayer.isDeadOrDying()) {
                EchoPlayerManager.finalizeOriginalBodyDeath(state.shellPlayer, state.shellPlayer.damageSources().genericKill(), state.shellPlayer.getMaxHealth());
                continue;
            }
            if (!state.shellPlayer.isRemoved() && !realPlayer.isDeadOrDying()) continue;
            EchoPlayerManager.revertPossession(realPlayer);
        }
        if (session.controllers.isEmpty()) {
            SESSIONS.remove(echoPlayer.getUUID(), session);
            return;
        }
        ControllerState authoritative = EchoPlayerManager.chooseAuthoritativeController(session);
        if (authoritative == null) {
            SESSIONS.remove(echoPlayer.getUUID(), session);
            return;
        }
        if (authoritative.lastSyncDimension != null) {
            boolean realMoved;
            boolean echoMoved = echoPlayer.level().dimension() != authoritative.lastSyncDimension || echoPlayer.position().distanceToSqr(authoritative.lastSyncX, authoritative.lastSyncY, authoritative.lastSyncZ) > 1.0E-6 || Math.abs(Mth.wrapDegrees(echoPlayer.getYRot() - authoritative.lastSyncYRot)) > 0.01f || Math.abs(Mth.wrapDegrees(echoPlayer.getXRot() - authoritative.lastSyncXRot)) > 0.01f;
            boolean bl = realMoved = authoritative.realPlayer.level().dimension() != authoritative.lastSyncDimension || authoritative.realPlayer.position().distanceToSqr(authoritative.lastSyncX, authoritative.lastSyncY, authoritative.lastSyncZ) > 1.0E-6 || Math.abs(Mth.wrapDegrees(authoritative.realPlayer.getYRot() - authoritative.lastSyncYRot)) > 0.01f || Math.abs(Mth.wrapDegrees(authoritative.realPlayer.getXRot() - authoritative.lastSyncXRot)) > 0.01f;
            if (echoMoved && !realMoved) {
                EchoPlayerManager.teleportRealPlayerToEcho(authoritative, true);
            }
        }
        EchoPlayerManager.syncControlledEchoToController(authoritative);
        authoritative.lastSyncDimension = authoritative.realPlayer.level().dimension();
        authoritative.lastSyncX = authoritative.realPlayer.getX();
        authoritative.lastSyncY = authoritative.realPlayer.getY();
        authoritative.lastSyncZ = authoritative.realPlayer.getZ();
        authoritative.lastSyncYRot = authoritative.realPlayer.getYRot();
        authoritative.lastSyncXRot = authoritative.realPlayer.getXRot();
        for (ControllerState state : List.copyOf(session.controllers.values())) {
            ServerPlayer realPlayer = state.realPlayer;
            if (state == authoritative) {
                EchoPlayerManager.copyRealStateToEcho(state, true);
            } else {
                EchoPlayerManager.syncFollowingControllerFromEcho(state);
            }
            EchoPlayerManager.hideControllerBody(realPlayer);
        }
    }

    private static void endSessionControllers(PossessionSession session, boolean reshowEcho) {
        for (ControllerState state : List.copyOf(session.controllers.values())) {
            Entity realVehicle;
            Entity echoVehicle = state.realPlayer.getVehicle();
            if (echoVehicle != null) {
                state.realPlayer.stopRiding();
            }
            if ((realVehicle = state.shellPlayer.getVehicle()) != null) {
                state.shellPlayer.stopRiding();
            }
            EchoPlayerManager.commitControllerContainer(state);
            EchoPlayerManager.removeControllerState(state);
            EchoPlayerManager.removeShell(state);
            if (!state.realPlayer.isDeadOrDying()) {
                EchoPlayerManager.restoreRealPlayerFromShell(state, true);
                EchoPlayerManager.copyEchoSharedStateToRealController(state);
                EchoPlayerManager.showControllerToObservers(state.realPlayer);
                EchoPlayerManager.sendUnpossessPacket(state.realPlayer);
                if (reshowEcho) {
                    EchoPlayerManager.reshowEchoToReal(state);
                }
                if (realVehicle != null) {
                    state.realPlayer.startRiding(realVehicle, true);
                }
            } else {
                EchoPlayerManager.restoreRealPlayerForRespawn(state);
                EchoPlayerManager.sendUnpossessPacket(state.realPlayer);
            }
            EchoPlayerManager.removeCrashBackup(state.realPlayer);
        }
    }

    private static ControllerState findControllerByShell(EchoServerPlayer shellPlayer) {
        for (PossessionSession session : List.copyOf(SESSIONS.values())) {
            for (ControllerState state : List.copyOf(session.controllers.values())) {
                if (state.shellPlayer != shellPlayer) continue;
                return state;
            }
        }
        return null;
    }

    private static void removeControllerState(ControllerState state) {
        CONTROLLERS.remove(state.realPlayer.getUUID(), state);
        state.session.controllers.remove(state.realPlayer.getUUID(), state);
        if (state.session.controllers.isEmpty()) {
            SESSIONS.remove(state.echoPlayer.getUUID(), state.session);
            state.session.authoritativeControllerId = null;
        } else if (state.realPlayer.getUUID().equals(state.session.authoritativeControllerId)) {
            state.session.authoritativeControllerId = null;
            EchoPlayerManager.chooseAuthoritativeController(state.session);
        }
    }

    private static boolean isAuthoritativeController(ControllerState state) {
        ControllerState authoritative = EchoPlayerManager.chooseAuthoritativeController(state.session);
        return authoritative == state;
    }

    private static void claimController(ControllerState state) {
        PossessionSession session = state.session;
        state.lastActionRevision = ++session.revision;
        session.authoritativeControllerId = state.realPlayer.getUUID();
    }

    private static ControllerState chooseAuthoritativeController(PossessionSession session) {
        ControllerState authoritative;
        if (session == null || session.controllers.isEmpty()) {
            return null;
        }
        if (session.authoritativeControllerId != null && (authoritative = session.controllers.get(session.authoritativeControllerId)) != null && !authoritative.realPlayer.hasDisconnected() && !authoritative.realPlayer.isDeadOrDying()) {
            return authoritative;
        }
        ControllerState best = null;
        for (ControllerState state : session.controllers.values()) {
            if (state.realPlayer.hasDisconnected() || state.realPlayer.isDeadOrDying()) continue;
            if (best == null) {
                best = state;
                continue;
            }
            int revisionCompare = Long.compare(state.lastActionRevision, best.lastActionRevision);
            if (revisionCompare <= 0 && (revisionCompare != 0 || state.realPlayer.getUUID().compareTo(best.realPlayer.getUUID()) >= 0)) continue;
            best = state;
        }
        if (best != null) {
            session.authoritativeControllerId = best.realPlayer.getUUID();
        }
        return best;
    }

    private static EchoServerPlayer createOriginalBodyShell(ServerPlayer realPlayer) {
        GameProfile shellProfile = new GameProfile(UUID.randomUUID(), realPlayer.getGameProfile().getName());
        EchoServerPlayer shell = new EchoServerPlayer(realPlayer.server, realPlayer.serverLevel(), shellProfile);
        shell.linkedRealPlayer = realPlayer;
        EchoConnection shellConn = new EchoConnection(PacketFlow.SERVERBOUND);
        EchoServerGamePacketListenerImpl shellListener = new EchoServerGamePacketListenerImpl(realPlayer.server, shellConn, shell);
        shell.connection = shellListener;
        shellConn.setListener(shellListener);
        shell.setGameMode(realPlayer.gameMode.getGameModeForPlayer());
        shell.setPos(realPlayer.getX(), realPlayer.getY(), realPlayer.getZ());
        shell.setYRot(realPlayer.getYRot());
        shell.setXRot(realPlayer.getXRot());
        shell.yHeadRot = realPlayer.yHeadRot;
        shell.yBodyRot = realPlayer.yBodyRot;
        EchoPlayerManager.copyRealStateToShell(realPlayer, shell);
        EchoPlayerManager.transferSleepingState(realPlayer, shell);
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        ClientboundPlayerInfoUpdatePacket addPacket = new ClientboundPlayerInfoUpdatePacket(actions, List.of(shell));
        for (ServerPlayer player : realPlayer.server.getPlayerList().getPlayers()) {
            player.connection.send(addPacket);
        }
        realPlayer.serverLevel().addFreshEntity(shell);
        return shell;
    }

    private static void copyRealStateToShell(ServerPlayer realPlayer, EchoServerPlayer shellPlayer) {
        EchoPlayerManager.copyInventoryContents(realPlayer, shellPlayer);
        EchoPlayerManager.setGameModeIfNeeded(shellPlayer, realPlayer.gameMode.getGameModeForPlayer());
        EchoPlayerManager.synchronizeEffects(realPlayer, shellPlayer);
        EchoPlayerManager.synchronizeAttributes(realPlayer, shellPlayer, true);
        EchoPlayerManager.copySprintingState(realPlayer, shellPlayer);
        shellPlayer.setHealth(realPlayer.getHealth());
        shellPlayer.setAbsorptionAmount(realPlayer.getAbsorptionAmount());
        shellPlayer.getFoodData().setFoodLevel(realPlayer.getFoodData().getFoodLevel());
        shellPlayer.getFoodData().setSaturation(realPlayer.getFoodData().getSaturationLevel());
        shellPlayer.experienceLevel = realPlayer.experienceLevel;
        shellPlayer.experienceProgress = realPlayer.experienceProgress;
        shellPlayer.totalExperience = realPlayer.totalExperience;
        EchoPlayerManager.copyAbilities(realPlayer, shellPlayer);
    }

    private static void copyRealStateToEcho(ControllerState state, boolean copyHealth) {
        boolean echoAbsChanged;
        boolean echoExhChanged;
        boolean echoSatChanged;
        boolean echoFoodChanged;
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        GameType realGameMode = realPlayer.gameMode.getGameModeForPlayer();
        GameType echoGameMode = echoPlayer.gameMode.getGameModeForPlayer();
        if (realGameMode != state.lastSyncGameMode) {
            EchoPlayerManager.setGameModeIfNeeded(echoPlayer, realGameMode);
            state.lastSyncGameMode = realGameMode;
        } else if (echoGameMode != state.lastSyncGameMode) {
            EchoPlayerManager.setGameModeIfNeeded(realPlayer, echoGameMode);
            state.lastSyncGameMode = echoGameMode;
        }
        boolean inventoryChanged = false;
        Inventory realInv = realPlayer.getInventory();
        Inventory echoInv = echoPlayer.getInventory();
        for (int i = 0; i < state.lastInventoryState.length; ++i) {
            boolean echoChanged;
            ItemStack realStack = realInv.getItem(i);
            ItemStack echoStack = echoInv.getItem(i);
            ItemStack lastStack = state.lastInventoryState[i];
            boolean realChanged = !ItemStack.matches(realStack, lastStack);
            boolean bl = echoChanged = !ItemStack.matches(echoStack, lastStack);
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
            if (!realChanged) continue;
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
        EchoPlayerManager.synchronizeEffects(realPlayer, echoPlayer);
        EchoPlayerManager.synchronizeAttributes(realPlayer, echoPlayer, false);
        EchoPlayerManager.copySprintingState(realPlayer, echoPlayer);
        boolean realFoodChanged = realPlayer.getFoodData().getFoodLevel() != state.lastFoodLevel;
        boolean bl = echoFoodChanged = echoPlayer.getFoodData().getFoodLevel() != state.lastFoodLevel;
        if (realFoodChanged) {
            echoPlayer.getFoodData().setFoodLevel(realPlayer.getFoodData().getFoodLevel());
            state.lastFoodLevel = realPlayer.getFoodData().getFoodLevel();
        } else if (echoFoodChanged) {
            realPlayer.getFoodData().setFoodLevel(echoPlayer.getFoodData().getFoodLevel());
            state.lastFoodLevel = echoPlayer.getFoodData().getFoodLevel();
        }
        boolean realSatChanged = Float.compare(realPlayer.getFoodData().getSaturationLevel(), state.lastSaturation) != 0;
        boolean bl2 = echoSatChanged = Float.compare(echoPlayer.getFoodData().getSaturationLevel(), state.lastSaturation) != 0;
        if (realSatChanged) {
            echoPlayer.getFoodData().setSaturation(realPlayer.getFoodData().getSaturationLevel());
            state.lastSaturation = realPlayer.getFoodData().getSaturationLevel();
        } else if (echoSatChanged) {
            realPlayer.getFoodData().setSaturation(echoPlayer.getFoodData().getSaturationLevel());
            state.lastSaturation = realPlayer.getFoodData().getSaturationLevel();
        }
        boolean realExhChanged = Float.compare(realPlayer.getFoodData().getExhaustionLevel(), state.lastExhaustion) != 0;
        boolean bl3 = echoExhChanged = Float.compare(echoPlayer.getFoodData().getExhaustionLevel(), state.lastExhaustion) != 0;
        if (realExhChanged) {
            echoPlayer.getFoodData().setExhaustion(realPlayer.getFoodData().getExhaustionLevel());
            state.lastExhaustion = realPlayer.getFoodData().getExhaustionLevel();
        } else if (echoExhChanged) {
            realPlayer.getFoodData().setExhaustion(echoPlayer.getFoodData().getExhaustionLevel());
            state.lastExhaustion = echoPlayer.getFoodData().getExhaustionLevel();
        }
        if (echoPlayer.experienceLevel != realPlayer.experienceLevel) {
            echoPlayer.experienceLevel = realPlayer.experienceLevel;
        }
        if (Float.compare(echoPlayer.experienceProgress, realPlayer.experienceProgress) != 0) {
            echoPlayer.experienceProgress = realPlayer.experienceProgress;
        }
        if (echoPlayer.totalExperience != realPlayer.totalExperience) {
            echoPlayer.totalExperience = realPlayer.totalExperience;
        }
        if (copyHealth) {
            boolean echoHealthChanged;
            boolean realHealthChanged = Float.compare(realPlayer.getHealth(), state.lastHealth) != 0;
            boolean bl4 = echoHealthChanged = Float.compare(echoPlayer.getHealth(), state.lastHealth) != 0;
            if (echoHealthChanged) {
                realPlayer.setHealth(echoPlayer.getHealth());
                state.lastHealth = echoPlayer.getHealth();
            } else if (realHealthChanged) {
                echoPlayer.setHealth(realPlayer.getHealth());
                state.lastHealth = realPlayer.getHealth();
            }
        }
        boolean realAbsChanged = Float.compare(realPlayer.getAbsorptionAmount(), state.lastAbsorption) != 0;
        boolean bl5 = echoAbsChanged = Float.compare(echoPlayer.getAbsorptionAmount(), state.lastAbsorption) != 0;
        if (echoAbsChanged) {
            realPlayer.setAbsorptionAmount(echoPlayer.getAbsorptionAmount());
            state.lastAbsorption = echoPlayer.getAbsorptionAmount();
        } else if (realAbsChanged) {
            echoPlayer.setAbsorptionAmount(realPlayer.getAbsorptionAmount());
            state.lastAbsorption = realPlayer.getAbsorptionAmount();
        }
        EchoPlayerManager.copyAbilitiesIfDifferent(realPlayer, echoPlayer);
        EchoPlayerManager.synchronizeUsingItem(realPlayer, echoPlayer);
        if (inventoryChanged) {
            EchoPlayerManager.updateEchoEquipment(echoPlayer);
        }
    }

    private static void copyEchoStateToRealController(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        EchoPlayerManager.copyEchoSharedStateToRealController(state, true);
        if (EchoPlayerManager.needsTeleportToEcho(state)) {
            EchoPlayerManager.teleportRealPlayerToEcho(state, false);
        }
        realPlayer.setDeltaMovement(echoPlayer.getDeltaMovement());
    }

    private static void syncFollowingControllerFromEcho(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        boolean snap = realPlayer.level().dimension() != echoPlayer.level().dimension() || realPlayer.position().distanceToSqr(echoPlayer.position()) > 16.0;
        EchoPlayerManager.copyEchoSharedStateToRealController(state);
        if (EchoPlayerManager.syncControllerSleepState(state)) {
            EchoPlayerManager.sendControlSyncPacket(state, false, snap);
            return;
        }
        if (realPlayer.level().dimension() != echoPlayer.level().dimension()) {
            EchoPlayerManager.teleportRealPlayerToEcho(state, false);
        } else {
            realPlayer.moveTo(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), echoPlayer.getYRot(), echoPlayer.getXRot());
            EchoPlayerManager.syncConnectionPosition(realPlayer, echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ());
        }
        realPlayer.yHeadRot = echoPlayer.yHeadRot;
        realPlayer.yBodyRot = echoPlayer.yBodyRot;
        realPlayer.setPose(echoPlayer.getPose());
        realPlayer.setShiftKeyDown(echoPlayer.isShiftKeyDown());
        EchoPlayerManager.copySprintingState(echoPlayer, realPlayer);
        realPlayer.setDeltaMovement(echoPlayer.getDeltaMovement());
        EchoPlayerManager.sendControlSyncPacket(state, false, snap);
    }

    private static void copyEchoSharedStateToRealController(ControllerState state) {
        EchoPlayerManager.copyEchoSharedStateToRealController(state, false);
    }

    private static void copyEchoSharedStateToRealController(ControllerState state, boolean synchronizeAllAttributes) {
        boolean experienceChanged;
        boolean healthChanged;
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        int previousSelected = realPlayer.getInventory().selected;
        boolean inventoryChanged = EchoPlayerManager.synchronizeInventoryContents(echoPlayer, realPlayer);
        Services.PLATFORM.syncModdedInventories(echoPlayer, realPlayer);
        EchoPlayerManager.setGameModeIfNeeded(realPlayer, echoPlayer.gameMode.getGameModeForPlayer());
        EchoPlayerManager.synchronizeEffects(echoPlayer, realPlayer);
        EchoPlayerManager.synchronizeAttributes(echoPlayer, realPlayer, synchronizeAllAttributes);
        EchoPlayerManager.copySprintingState(echoPlayer, realPlayer);
        boolean bl = healthChanged = Float.compare(realPlayer.getHealth(), echoPlayer.getHealth()) != 0 || Float.compare(realPlayer.getAbsorptionAmount(), echoPlayer.getAbsorptionAmount()) != 0 || realPlayer.getFoodData().getFoodLevel() != echoPlayer.getFoodData().getFoodLevel() || Float.compare(realPlayer.getFoodData().getSaturationLevel(), echoPlayer.getFoodData().getSaturationLevel()) != 0;
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
        boolean bl2 = experienceChanged = realPlayer.experienceLevel != echoPlayer.experienceLevel || Float.compare(realPlayer.experienceProgress, echoPlayer.experienceProgress) != 0 || realPlayer.totalExperience != echoPlayer.totalExperience;
        if (experienceChanged) {
            realPlayer.experienceLevel = echoPlayer.experienceLevel;
            realPlayer.experienceProgress = echoPlayer.experienceProgress;
            realPlayer.totalExperience = echoPlayer.totalExperience;
        }
        boolean abilitiesChanged = EchoPlayerManager.copyAbilitiesIfDifferent(echoPlayer, realPlayer);
        EchoPlayerManager.synchronizeUsingItem(echoPlayer, realPlayer);
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
        EchoPlayerManager.hideControllerBody(realPlayer);
    }

    private static void syncConnectionPosition(ServerPlayer realPlayer, double x, double y, double z) {
        ServerGamePacketListenerImplAccessor accessor = (ServerGamePacketListenerImplAccessor)((Object)realPlayer.connection);
        accessor.echoplayer$setFirstGoodX(x);
        accessor.echoplayer$setFirstGoodY(y);
        accessor.echoplayer$setFirstGoodZ(z);
        accessor.echoplayer$setLastGoodX(x);
        accessor.echoplayer$setLastGoodY(y);
        accessor.echoplayer$setLastGoodZ(z);
    }

    private static void syncControlledEchoToController(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return;
        }
        if (EchoPlayerManager.syncControllerSleepState(state)) {
            return;
        }
        if (echoPlayer.isPassenger()) {
            realPlayer.moveTo(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), realPlayer.getYRot(), realPlayer.getXRot());
            echoPlayer.setYRot(realPlayer.getYRot());
            echoPlayer.setXRot(realPlayer.getXRot());
            echoPlayer.yHeadRot = realPlayer.yHeadRot;
            echoPlayer.yBodyRot = realPlayer.yBodyRot;
            echoPlayer.setShiftKeyDown(realPlayer.isShiftKeyDown());
            EchoPlayerManager.copySprintingState(realPlayer, echoPlayer);
            echoPlayer.setOnGround(echoPlayer.onGround());
            echoPlayer.fallDistance = echoPlayer.fallDistance;
        } else {
            if (echoPlayer.level().dimension() != realPlayer.level().dimension()) {
                echoPlayer.teleportTo(realPlayer.serverLevel(), realPlayer.getX(), realPlayer.getY(), realPlayer.getZ(), realPlayer.getYRot(), realPlayer.getXRot());
            } else {
                echoPlayer.moveTo(realPlayer.getX(), realPlayer.getY(), realPlayer.getZ(), realPlayer.getYRot(), realPlayer.getXRot());
            }
            echoPlayer.yHeadRot = realPlayer.yHeadRot;
            echoPlayer.yBodyRot = realPlayer.yBodyRot;
            echoPlayer.setPose(realPlayer.getPose());
            echoPlayer.setShiftKeyDown(realPlayer.isShiftKeyDown());
            EchoPlayerManager.copySprintingState(realPlayer, echoPlayer);
            echoPlayer.setOnGround(realPlayer.onGround());
            echoPlayer.fallDistance = realPlayer.fallDistance;
            echoPlayer.setDeltaMovement(realPlayer.getDeltaMovement());
        }
    }

    public static void createCrashBackup(ServerPlayer player) {
        try {
            CompoundTag backupTag = new CompoundTag();
            player.saveWithoutId(backupTag);
            Path path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + "_afp_backup.dat");
            NbtIo.writeCompressed(backupTag, path.toFile());
        }
        catch (Exception e) {
            Constants.LOG.error("Failed to create crash backup for " + player.getName().getString(), (Throwable)e);
        }
    }

    public static void removeCrashBackup(ServerPlayer player) {
        try {
            Path path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + "_afp_backup.dat");
            Files.deleteIfExists(path);
        }
        catch (Exception e) {
            Constants.LOG.error("Failed to delete crash backup for " + player.getName().getString(), (Throwable)e);
        }
    }

    public static boolean isAuthoritativeControllerForPossession(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null || state.session == null) {
            return true;
        }
        if (state.session.controllers.size() <= 1) {
            return true;
        }
        ControllerState authoritative = EchoPlayerManager.chooseAuthoritativeController(state.session);
        return authoritative == state;
    }

    public static void restoreCrashBackup(ServerPlayer realPlayer) {
        try {
            CompoundTag backup;
            Path path = realPlayer.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(realPlayer.getUUID() + "_afp_backup.dat");
            if (Files.exists(path, new LinkOption[0]) && (backup = NbtIo.readCompressed(path.toFile())) != null) {
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
                EchoPlayerManager.syncRealPlayerPackets(realPlayer);
                realPlayer.containerMenu.broadcastChanges();
            }
        }
        catch (Exception e) {
            Constants.LOG.error("Failed to restore crash backup for " + realPlayer.getName().getString(), (Throwable)e);
        }
    }

    private static void restoreRealPlayerFromShell(ControllerState state, boolean teleport) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer shellPlayer = state.shellPlayer;
        EchoPlayerManager.clearMirroredSleep(state);
        EchoPlayerManager.copyInventoryContents(shellPlayer, realPlayer);
        EchoPlayerManager.setGameModeIfNeeded(realPlayer, shellPlayer.gameMode.getGameModeForPlayer());
        EchoPlayerManager.synchronizeEffects(shellPlayer, realPlayer);
        EchoPlayerManager.synchronizeAttributes(shellPlayer, realPlayer, true);
        realPlayer.setHealth(Math.max(0.0f, shellPlayer.getHealth()));
        realPlayer.setAbsorptionAmount(shellPlayer.getAbsorptionAmount());
        realPlayer.getFoodData().setFoodLevel(shellPlayer.getFoodData().getFoodLevel());
        realPlayer.getFoodData().setSaturation(shellPlayer.getFoodData().getSaturationLevel());
        realPlayer.experienceLevel = shellPlayer.experienceLevel;
        realPlayer.experienceProgress = shellPlayer.experienceProgress;
        realPlayer.totalExperience = shellPlayer.totalExperience;
        EchoPlayerManager.copyAbilities(shellPlayer, realPlayer);
        realPlayer.setInvisible(shellPlayer.isInvisible());
        realPlayer.setSilent(shellPlayer.isSilent());
        EchoPlayerManager.syncRealPlayerPackets(realPlayer);
        realPlayer.containerMenu.broadcastChanges();
        if (teleport) {
            EchoPlayerManager.teleportRealPlayerToShell(state);
        }
        if (shellPlayer.isSleeping()) {
            EchoPlayerManager.transferSleepingState(shellPlayer, realPlayer);
            realPlayer.serverLevel().updateSleepingPlayerList();
        }
    }

    public static EchoServerPlayer getSleepTarget(ServerPlayer realPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        return state != null && !state.echoPlayer.isRemoved() && !state.echoPlayer.isDeadOrDying() ? state.echoPlayer : null;
    }

    public static void mirrorPossessedSleep(ServerPlayer realPlayer, EchoServerPlayer echoPlayer) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state != null && state.echoPlayer == echoPlayer) {
            EchoPlayerManager.syncControllerSleepState(state);
        }
    }

    public static boolean stopPossessedSleep(ServerPlayer realPlayer, boolean wakeImmediately, boolean updateLevel) {
        ControllerState state = CONTROLLERS.get(realPlayer.getUUID());
        if (state == null) {
            return false;
        }
        if (!EchoPlayerManager.isAuthoritativeController(state)) {
            EchoPlayerManager.syncControllerSleepState(state);
            return true;
        }
        if (state.echoPlayer.isSleeping()) {
            state.echoPlayer.stopSleepInBed(wakeImmediately, updateLevel);
        }
        EchoPlayerManager.clearMirroredSleep(state);
        return true;
    }

    private static boolean syncControllerSleepState(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        Optional<BlockPos> sleepingPos = echoPlayer.getSleepingPos();
        if (sleepingPos.isEmpty()) {
            EchoPlayerManager.clearMirroredSleep(state);
            return false;
        }
        BlockPos bedPos = sleepingPos.get();
        if (!realPlayer.getSleepingPos().filter(bedPos::equals).isPresent()) {
            realPlayer.setPose(Pose.SLEEPING);
            realPlayer.setSleepingPos(bedPos);
        }
        realPlayer.absMoveTo(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), echoPlayer.getYRot(), echoPlayer.getXRot());
        realPlayer.setDeltaMovement(echoPlayer.getDeltaMovement());
        EchoPlayerManager.syncConnectionPosition(realPlayer, echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ());
        return true;
    }

    private static void clearMirroredSleep(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        if (!realPlayer.isSleeping()) {
            return;
        }
        realPlayer.clearSleepingPos();
        realPlayer.setPose(Pose.STANDING);
        EchoServerPlayer echoPlayer = state.echoPlayer;
        if (!echoPlayer.isRemoved()) {
            EchoPlayerManager.teleportRealPlayerToEcho(state, true);
        }
    }

    private static void transferSleepingState(ServerPlayer from, ServerPlayer to) {
        Optional<BlockPos> sleepingPos = from.getSleepingPos();
        if (sleepingPos.isEmpty()) {
            return;
        }
        BlockPos bedPos = sleepingPos.get();
        int sleepTimer = ((PlayerAccessor)((Object)from)).echoplayer$getSleepCounter();
        from.clearSleepingPos();
        from.setPose(Pose.STANDING);
        to.absMoveTo(from.getX(), from.getY(), from.getZ(), from.getYRot(), from.getXRot());
        to.setPose(Pose.SLEEPING);
        to.setSleepingPos(bedPos);
        ((PlayerAccessor)((Object)to)).echoplayer$setSleepCounter(sleepTimer);
        to.setDeltaMovement(from.getDeltaMovement());
        var bedState = to.level().getBlockState(bedPos);
        if (bedState.isBed(to.level(), bedPos, to)) {
            bedState.setBedOccupied(to.level(), bedPos, to, true);
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

    private static void applyGhostBlockFix(ServerPlayer realPlayer, ServerLevel level, BlockPos posUnder) {
        realPlayer.connection.send(new ClientboundBlockUpdatePacket(posUnder, Blocks.BARRIER.defaultBlockState()));
        realPlayer.server.tell(new TickTask(realPlayer.server.getTickCount() + 40, () -> {
            if (!realPlayer.hasDisconnected() && realPlayer.level() == level) {
                realPlayer.connection.send(new ClientboundBlockUpdatePacket(level, posUnder));
            }
        }));
    }

    private static void teleportRealPlayerToShell(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer shellPlayer = state.shellPlayer;
        ServerLevel shellLevel = shellPlayer.serverLevel();
        boolean requiresTerrainDownload = realPlayer.level().dimension() != shellLevel.dimension() || realPlayer.distanceToSqr(shellPlayer) > 4096.0;
        realPlayer.teleportTo(shellLevel, shellPlayer.getX(), shellPlayer.getY(), shellPlayer.getZ(), shellPlayer.getYRot(), shellPlayer.getXRot());
        if (requiresTerrainDownload) {
            EchoPlayerManager.applyGhostBlockFix(realPlayer, shellLevel, shellPlayer.blockPosition().below());
        }
    }

    private static void teleportRealPlayerToEcho(ControllerState state, boolean forcePacket) {
        float xRot;
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        ServerLevel echoLevel = echoPlayer.serverLevel();
        boolean requiresTerrainDownload = realPlayer.level().dimension() != echoLevel.dimension() || realPlayer.distanceToSqr(echoPlayer) > 4096.0;
        float yRot = echoPlayer.isPassenger() ? realPlayer.getYRot() : echoPlayer.getYRot();
        float f = xRot = echoPlayer.isPassenger() ? realPlayer.getXRot() : echoPlayer.getXRot();
        if (realPlayer.level().dimension() != echoLevel.dimension()) {
            realPlayer.teleportTo(echoLevel, echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
        } else if (forcePacket) {
            realPlayer.connection.teleport(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
            realPlayer.absMoveTo(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
        } else {
            realPlayer.absMoveTo(echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ(), yRot, xRot);
            EchoPlayerManager.syncConnectionPosition(realPlayer, echoPlayer.getX(), echoPlayer.getY(), echoPlayer.getZ());
        }
        if (requiresTerrainDownload) {
            EchoPlayerManager.applyGhostBlockFix(realPlayer, echoLevel, echoPlayer.blockPosition().below());
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
        EchoPlayerManager.synchronizeEffects(state.shellPlayer, realPlayer);
        EchoPlayerManager.synchronizeAttributes(state.shellPlayer, realPlayer, true);
        realPlayer.setAbsorptionAmount(state.shellPlayer.getAbsorptionAmount());
        realPlayer.setInvisible(state.shellPlayer.isInvisible());
        realPlayer.setSilent(state.shellPlayer.isSilent());
        realPlayer.containerMenu.broadcastChanges();
    }

    private static void syncEchoStateToControllers(PossessionSession session) {
        EchoPlayerManager.updateEchoEquipment(session.echoPlayer);
        for (ControllerState state : List.copyOf(session.controllers.values())) {
            EchoPlayerManager.copyEchoStateToRealController(state);
        }
    }

    private static void syncCanonicalStateToControllers(PossessionSession session) {
        for (ControllerState state : List.copyOf(session.controllers.values())) {
            EchoPlayerManager.copyEchoSharedStateToRealController(state);
        }
    }

    private static void syncRealPlayerPackets(ServerPlayer realPlayer) {
        realPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, realPlayer.gameMode.getGameModeForPlayer().getId()));
        realPlayer.connection.send(new ClientboundSetHealthPacket(realPlayer.getHealth(), realPlayer.getFoodData().getFoodLevel(), realPlayer.getFoodData().getSaturationLevel()));
        realPlayer.connection.send(new ClientboundSetExperiencePacket(realPlayer.experienceProgress, realPlayer.totalExperience, realPlayer.experienceLevel));
        realPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(realPlayer.getAbilities()));
    }

    private static void hideControllerBody(ServerPlayer realPlayer) {
        if (!realPlayer.isSilent()) {
            realPlayer.setSilent(true);
        }
    }

    private static void copyInventoryContents(ServerPlayer source, ServerPlayer target) {
        EchoPlayerManager.synchronizeInventoryContents(source, target);
    }

    private static boolean synchronizeInventoryContents(ServerPlayer source, ServerPlayer target) {
        Inventory sourceInventory = source.getInventory();
        Inventory targetInventory = target.getInventory();
        boolean changed = false;
        int size = Math.min(sourceInventory.getContainerSize(), targetInventory.getContainerSize());
        for (int slot = 0; slot < size; ++slot) {
            ItemStack sourceItem = sourceInventory.getItem(slot);
            if (ItemStack.matches(sourceItem, targetInventory.getItem(slot))) continue;
            targetInventory.setItem(slot, sourceItem.copy());
            changed = true;
        }
        if (targetInventory.selected != sourceInventory.selected) {
            targetInventory.selected = sourceInventory.selected;
            changed = true;
        }
        if (changed) {
            targetInventory.setChanged();
        }
        return changed;
    }

    private static void synchronizeEffects(ServerPlayer source, ServerPlayer target) {
        Map<MobEffect, MobEffectInstance> sourceEffects = source.getActiveEffectsMap();
        Map<MobEffect, MobEffectInstance> targetEffects = target.getActiveEffectsMap();
        ArrayList<MobEffect> effectsToRemove = null;
        for (MobEffect mobEffect : targetEffects.keySet()) {
            if (sourceEffects.containsKey(mobEffect)) continue;
            if (effectsToRemove == null) {
                effectsToRemove = new ArrayList<MobEffect>();
            }
            effectsToRemove.add(mobEffect);
        }
        if (effectsToRemove != null) {
            for (MobEffect mobEffect : effectsToRemove) {
                target.removeEffect(mobEffect);
            }
        }
        for (Map.Entry entry : sourceEffects.entrySet()) {
            MobEffect effect = (MobEffect)entry.getKey();
            MobEffectInstance sourceEffect = (MobEffectInstance)entry.getValue();
            MobEffectInstance targetEffect = targetEffects.get(effect);
            if (targetEffect == null) {
                target.addEffect(new MobEffectInstance(sourceEffect));
                continue;
            }
            if (!EchoPlayerManager.hasSameEffectConfiguration(sourceEffect, targetEffect)) {
                target.removeEffect(effect);
                target.addEffect(new MobEffectInstance(sourceEffect));
                continue;
            }
            if (sourceEffect.getDuration() == targetEffect.getDuration()) continue;
            ((MobEffectInstanceAccessor)((Object)targetEffect)).echoplayer$setDuration(sourceEffect.getDuration());
        }
    }

    private static boolean hasSameEffectConfiguration(MobEffectInstance first, MobEffectInstance second) {
        return first.getAmplifier() == second.getAmplifier() && first.isAmbient() == second.isAmbient() && first.isVisible() == second.isVisible() && first.showIcon() == second.showIcon();
    }

    private static void synchronizeAttributes(ServerPlayer source, ServerPlayer target, boolean synchronizeAll) {
        LinkedHashMap<Attribute, AttributeInstance> changedAttributes = null;
        if (synchronizeAll) {
            for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
                if (!source.getAttributes().hasAttribute(attribute) || !target.getAttributes().hasAttribute(attribute)) continue;
                AttributeState sourceState = EchoPlayerManager.captureAttributeState(source, attribute);
                AttributeState targetState = EchoPlayerManager.captureAttributeState(target, attribute);
                if (sourceState == null || targetState == null || EchoPlayerManager.hasSameAttributeState(sourceState, targetState)) continue;
                AttributeInstance targetAttribute = EchoPlayerManager.replaceAttribute(target, attribute, sourceState);
                if (changedAttributes == null) {
                    changedAttributes = new LinkedHashMap<Attribute, AttributeInstance>();
                }
                changedAttributes.put(attribute, targetAttribute);
            }
        } else {
            ArrayList<Attribute> dirtyAttributes = new ArrayList<Attribute>();
            for (AttributeInstance sourceAttribute : List.copyOf(source.getAttributes().getDirtyAttributes())) {
                if (sourceAttribute == null || sourceAttribute.getAttribute() == null || !target.getAttributes().hasAttribute(sourceAttribute.getAttribute())) continue;
                dirtyAttributes.add(sourceAttribute.getAttribute());
            }
            for (Attribute attribute : dirtyAttributes) {
                AttributeState sourceState = EchoPlayerManager.captureAttributeState(source, attribute);
                AttributeState targetState = EchoPlayerManager.captureAttributeState(target, attribute);
                if (sourceState == null || targetState == null || EchoPlayerManager.hasSameAttributeState(sourceState, targetState)) continue;
                AttributeInstance targetAttribute = EchoPlayerManager.replaceAttribute(target, attribute, sourceState);
                if (changedAttributes == null) {
                    changedAttributes = new LinkedHashMap();
                }
                changedAttributes.put(attribute, targetAttribute);
            }
        }
        if (changedAttributes != null && !(target instanceof EchoServerPlayer)) {
            target.connection.send(new ClientboundUpdateAttributesPacket(target.getId(), List.copyOf(changedAttributes.values())));
        }
    }

    private static boolean hasSameAttributeState(AttributeState firstState, AttributeState secondState) {
        if (Double.compare(firstState.baseValue(), secondState.baseValue()) != 0) {
            return false;
        }
        if (firstState.modifiers().size() != secondState.modifiers().size()) {
            return false;
        }
        if (!firstState.permanentModifierIds().equals(secondState.permanentModifierIds())) {
            return false;
        }
        for (AttributeModifier firstModifier : firstState.modifiers()) {
            AttributeModifier secondModifier = secondState.modifiersById().get(firstModifier.getId());
            if (secondModifier != null && Double.compare(firstModifier.getAmount(), secondModifier.getAmount()) == 0 && firstModifier.getOperation() == secondModifier.getOperation()) continue;
            return false;
        }
        return true;
    }

    private static AttributeInstance replaceAttribute(ServerPlayer target, Attribute attribute, AttributeState sourceState) {
        return EchoPlayerManager.rebuildAttributeInstance(target, attribute, sourceState);
    }

    private static AttributeState captureAttributeState(ServerPlayer owner, Attribute attribute) {
        AttributeInstance attributeInstance = owner.getAttribute(attribute);
        if (attributeInstance == null) {
            return null;
        }
        double baseValue = attributeInstance.getBaseValue();
        LinkedHashMap<UUID, AttributeModifier> modifiersById = new LinkedHashMap<UUID, AttributeModifier>();
        HashSet<UUID> permanentModifierIds = new HashSet<UUID>();
        try {
            for (AttributeModifier modifier : attributeInstance.getModifiers()) {
                if (!EchoPlayerManager.isCopiedAttributeModifier(attribute, modifier)) continue;
                modifiersById.putIfAbsent(modifier.getId(), modifier);
            }
        }
        catch (RuntimeException exception) {
            Constants.LOG.error("Rebuilding corrupted attribute state for {} on {}", new Object[]{BuiltInRegistries.ATTRIBUTE.getKey(attribute), owner.getGameProfile().getName(), exception});
            EchoPlayerManager.rebuildAttributeInstance(owner, attribute, new AttributeState(baseValue, List.of(), Map.of(), Set.of()));
            return new AttributeState(baseValue, List.of(), Map.of(), Set.of());
        }
        try {
            for (AttributeModifier modifier : ((AttributeInstanceAccessor)((Object)attributeInstance)).echoplayer$getPermanentModifiers()) {
                if (!EchoPlayerManager.isCopiedAttributeModifier(attribute, modifier) || !modifiersById.containsKey(modifier.getId())) continue;
                permanentModifierIds.add(modifier.getId());
            }
        }
        catch (RuntimeException exception) {
            Constants.LOG.error("Could not read permanent modifiers for {} on {}", new Object[]{BuiltInRegistries.ATTRIBUTE.getKey(attribute), owner.getGameProfile().getName(), exception});
        }
        return new AttributeState(baseValue, List.copyOf(modifiersById.values()), Map.copyOf(modifiersById), Set.copyOf(permanentModifierIds));
    }

    private static AttributeInstance rebuildAttributeInstance(ServerPlayer owner, Attribute attribute, AttributeState sourceState) {
        AttributeMap attributeMap = owner.getAttributes();
        AttributeMapAccessor mapAccessor = (AttributeMapAccessor)((Object)attributeMap);
        AttributeInstance previous = attributeMap.getInstance(attribute);
        AttributeInstance replacement = new AttributeInstance(attribute, mapAccessor::echoplayer$onAttributeModified);
        mapAccessor.echoplayer$getAttributes().put(attribute, replacement);
        if (previous != null) {
            mapAccessor.echoplayer$getDirtyAttributes().remove(previous);
        }
        replacement.setBaseValue(sourceState.baseValue());
        for (AttributeModifier modifier : sourceState.modifiers()) {
            AttributeModifier clone = new AttributeModifier(modifier.getId(), modifier.getName(), modifier.getAmount(), modifier.getOperation());
            if (sourceState.permanentModifierIds().contains(modifier.getId())) {
                replacement.addPermanentModifier(clone);
                continue;
            }
            replacement.addTransientModifier(clone);
        }
        ((AttributeInstanceAccessor)((Object)replacement)).echoplayer$setDirty();
        return replacement;
    }

    private static boolean isCopiedAttributeModifier(Attribute attribute, AttributeModifier modifier) {
        return modifier != null && modifier.getId() != null && (attribute != Attributes.MOVEMENT_SPEED || !MOVEMENT_SPEED_STATE_MODIFIER_IDS.contains(modifier.getId()));
    }

    private static boolean repairSprintingAttribute(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return false;
        }
        try {
            attribute.getModifier(SPRINTING_SPEED_MODIFIER_ID);
            attribute.getModifiers();
            return false;
        }
        catch (RuntimeException exception) {
            Constants.LOG.error("Rebuilding corrupted movement speed attribute for {}", (Object)player.getGameProfile().getName(), (Object)exception);
            EchoPlayerManager.rebuildAttributeInstance(player, Attributes.MOVEMENT_SPEED, new AttributeState(attribute.getBaseValue(), List.of(), Map.of(), Set.of()));
            return true;
        }
    }

    private static void copySprintingState(ServerPlayer source, ServerPlayer target) {
        AttributeInstance attribute = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        boolean rebuilt = EchoPlayerManager.repairSprintingAttribute(target);
        boolean sprinting = source.isSprinting();
        boolean modifierPresent = false;
        AttributeInstance currentAttribute = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (currentAttribute != null) {
            try {
                modifierPresent = currentAttribute.getModifier(SPRINTING_SPEED_MODIFIER_ID) != null;
            }
            catch (RuntimeException exception) {
                Constants.LOG.error("Rebuilding corrupted movement speed attribute for {}", (Object)target.getGameProfile().getName(), (Object)exception);
                EchoPlayerManager.rebuildAttributeInstance(target, Attributes.MOVEMENT_SPEED, new AttributeState(currentAttribute.getBaseValue(), List.of(), Map.of(), Set.of()));
                rebuilt = true;
            }
        }
        if (rebuilt || target.isSprinting() != sprinting || modifierPresent != sprinting) {
            target.setSprinting(sprinting);
        }
    }

    private static void copyAbilities(ServerPlayer source, ServerPlayer target) {
        CompoundTag abilities = new CompoundTag();
        source.getAbilities().addSaveData(abilities);
        target.getAbilities().loadSaveData(abilities);
    }

    private static boolean copyAbilitiesIfDifferent(ServerPlayer source, ServerPlayer target) {
        boolean changed;
        Abilities sourceAbilities = source.getAbilities();
        Abilities targetAbilities = target.getAbilities();
        boolean bl = changed = sourceAbilities.invulnerable != targetAbilities.invulnerable || sourceAbilities.flying != targetAbilities.flying || sourceAbilities.mayfly != targetAbilities.mayfly || sourceAbilities.instabuild != targetAbilities.instabuild || sourceAbilities.mayBuild != targetAbilities.mayBuild || Float.compare(sourceAbilities.getFlyingSpeed(), targetAbilities.getFlyingSpeed()) != 0 || Float.compare(sourceAbilities.getWalkingSpeed(), targetAbilities.getWalkingSpeed()) != 0;
        if (changed) {
            EchoPlayerManager.copyAbilities(source, target);
        }
        return changed;
    }

    private static void synchronizeUsingItem(ServerPlayer source, ServerPlayer target) {
        if (source.isUsingItem()) {
            if (!target.isUsingItem() || target.getUsedItemHand() != source.getUsedItemHand()) {
                target.stopUsingItem();
                target.startUsingItem(source.getUsedItemHand());
            }
        } else if (target.isUsingItem()) {
            target.stopUsingItem();
        }
    }

    private static void updateEchoEquipment(EchoServerPlayer echoPlayer) {
        ((LivingEntityInvoker)((Object)echoPlayer)).echoplayer$detectEquipmentUpdates();
    }

    private static void setGameModeIfNeeded(ServerPlayer player, GameType gameType) {
        if (player.gameMode.getGameModeForPlayer() != gameType) {
            player.setGameMode(gameType);
        }
    }

    private static void removeShell(ControllerState state) {
        EchoPlayerManager.removeShellEntity(state.shellPlayer, state.realPlayer.server);
    }

    private static void commitControllerContainer(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        if (realPlayer.isRemoved() || echoPlayer.isRemoved()) {
            return;
        }
        EchoPlayerManager.synchronizeInventoryContents(echoPlayer, realPlayer);
        realPlayer.closeContainer();
        if (EchoPlayerManager.synchronizeInventoryContents(realPlayer, echoPlayer)) {
            EchoPlayerManager.updateEchoEquipment(echoPlayer);
        }
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

    private static void sendPossessPacket(ControllerState state) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(state.echoPlayer.getUUID());
        buf.writeInt(state.shellPlayer.getId());
        Services.PLATFORM.sendToClient(state.realPlayer, NetworkPackets.POSSESS_PACKET, buf);
    }

    private static void sendUnpossessPacket(ServerPlayer realPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Services.PLATFORM.sendToClient(realPlayer, NetworkPackets.UNPOSSESS_PACKET, buf);
    }

    private static void sendControlSyncPacket(ControllerState state, boolean authoritative, boolean snap) {
        EchoServerPlayer echoPlayer = state.echoPlayer;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarLong(state.session.revision);
        buf.writeVarLong(state.lastClientSequence);
        buf.writeBoolean(authoritative);
        buf.writeBoolean(snap);
        buf.writeDouble(echoPlayer.getX());
        buf.writeDouble(echoPlayer.getY());
        buf.writeDouble(echoPlayer.getZ());
        buf.writeFloat(echoPlayer.getYRot());
        buf.writeFloat(echoPlayer.getXRot());
        buf.writeDouble(echoPlayer.getDeltaMovement().x);
        buf.writeDouble(echoPlayer.getDeltaMovement().y);
        buf.writeDouble(echoPlayer.getDeltaMovement().z);
        Services.PLATFORM.sendToClient(state.realPlayer, NetworkPackets.CONTROL_SYNC_PACKET, buf);
    }

    private static void hideEchoFromReal(ControllerState state) {
        state.realPlayer.connection.send(new ClientboundRemoveEntitiesPacket(state.echoPlayer.getId()));
    }

    public static void hideControllerFromViewer(ServerPlayer controller, ServerPlayer viewer) {
        if (controller == viewer || viewer instanceof EchoServerPlayer || !EchoPlayerManager.isPossessing(controller)) {
            return;
        }
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(controller.getId()));
    }

    public static void hidePossessingControllersFromViewer(ServerPlayer viewer) {
        if (viewer instanceof EchoServerPlayer) {
            return;
        }
        for (ControllerState state : CONTROLLERS.values()) {
            EchoPlayerManager.hideControllerFromViewer(state.realPlayer, viewer);
        }
    }

    public static void hideControllerFromObservers(ServerPlayer controller) {
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(controller.getId());
        for (ServerPlayer viewer : controller.server.getPlayerList().getPlayers()) {
            if (viewer == controller) continue;
            viewer.connection.send(destroyPacket);
        }
    }

    public static void showControllerToObservers(ServerPlayer controller) {
        ServerLevel level = controller.serverLevel();
        level.getChunkSource().removeEntity(controller);
        level.getChunkSource().addEntity(controller);
    }

    private static void sendPlayerEntityToViewer(ServerPlayer controller, ServerPlayer viewer) {
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
            if (item.isEmpty()) continue;
            equipment.add(Pair.of(slot, item.copy()));
        }
        if (!equipment.isEmpty()) {
            viewer.connection.send(new ClientboundSetEquipmentPacket(controller.getId(), equipment));
        }
    }

    private static void reshowEchoToReal(ControllerState state) {
        ServerPlayer realPlayer = state.realPlayer;
        EchoServerPlayer echoPlayer = state.echoPlayer;
        if (echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return;
        }
        EchoPlayerManager.sendEchoEntityToViewer(echoPlayer, realPlayer);
    }

    private static void sendEchoEntityToViewer(EchoServerPlayer echoPlayer, ServerPlayer viewer) {
        if (echoPlayer.level().dimension() != viewer.level().dimension()) {
            return;
        }
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(actions, List.of(echoPlayer)));
        EchoPlayerManager.sendPlayerEntityToViewer(echoPlayer, viewer);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = echoPlayer.getItemBySlot(slot);
            viewer.connection.send(new ClientboundSetEquipmentPacket(echoPlayer.getId(), List.of(Pair.of(slot, item))));
        }
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
        EchoPlayerManager.deletePlayerDataFiles(server, echoPlayer.getUUID());
    }

    private static void deletePlayerDataFiles(MinecraftServer server, UUID uuid) {
        EchoPlayerManager.deletePlayerDataFile(EchoPlayerManager.getPlayerDataPath(server, uuid, ".dat"));
        EchoPlayerManager.deletePlayerDataFile(EchoPlayerManager.getPlayerDataPath(server, uuid, ".dat_old"));
    }

    private static Path getPlayerDataPath(MinecraftServer server, UUID uuid, String suffix) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + suffix);
    }

    private static void deletePlayerDataFile(Path path) {
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException exception) {
            Constants.LOG.warn("Failed to delete EchoPlayer data file {}", (Object)path, (Object)exception);
        }
    }

    private static final class PossessionSession {
        private final EchoServerPlayer echoPlayer;
        private final Map<UUID, ControllerState> controllers = new ConcurrentHashMap<UUID, ControllerState>();
        private UUID authoritativeControllerId;
        private long revision;
        private long lastDamageGameTime = Long.MIN_VALUE;
        private String lastDamageType = "";
        private int lastDamageDirectEntityId = Integer.MIN_VALUE;
        private int lastDamageCausingEntityId = Integer.MIN_VALUE;
        private float lastDamageAmount;
        private long lastEffectTickGameTime = Long.MIN_VALUE;
        private boolean tickingCanonicalEffects;

        private PossessionSession(EchoServerPlayer echoPlayer) {
            this.echoPlayer = echoPlayer;
        }
    }

    private static final class ControllerState {
        private final ServerPlayer realPlayer;
        private final EchoServerPlayer echoPlayer;
        private final EchoServerPlayer shellPlayer;
        private final PossessionSession session;
        private final ResourceKey<Level> originalDimension;
        private final double origX;
        private final double origY;
        private final double origZ;
        private final float origYRot;
        private final float origXRot;
        private final ListTag originalInventory;
        private final GameType originalGameMode;
        private final float originalHealth;
        private final int originalFoodLevel;
        private final float originalSaturation;
        private final int originalXpLevel;
        private final float originalXpProgress;
        private final int originalXpTotal;
        private final CompoundTag originalAbilities;
        private long lastActionRevision;
        private long lastClientSequence;
        private ResourceKey<Level> lastSyncDimension;
        private double lastSyncX;
        private double lastSyncY;
        private double lastSyncZ;
        private float lastSyncYRot;
        private float lastSyncXRot;
        public ItemStack[] lastInventoryState;
        public float lastHealth;
        public int lastFoodLevel;
        public float lastSaturation;
        public float lastExhaustion;
        public float lastAbsorption;
        private GameType lastSyncGameMode;

        private ControllerState(ServerPlayer realPlayer, EchoServerPlayer echoPlayer, EchoServerPlayer shellPlayer, PossessionSession session) {
            this.realPlayer = realPlayer;
            this.echoPlayer = echoPlayer;
            this.shellPlayer = shellPlayer;
            this.session = session;
            this.originalDimension = realPlayer.level().dimension();
            this.origX = realPlayer.getX();
            this.origY = realPlayer.getY();
            this.origZ = realPlayer.getZ();
            this.origYRot = realPlayer.getYRot();
            this.origXRot = realPlayer.getXRot();
            this.originalInventory = new ListTag();
            realPlayer.getInventory().save(this.originalInventory);
            this.originalGameMode = realPlayer.gameMode.getGameModeForPlayer();
            this.originalHealth = realPlayer.getHealth();
            this.originalFoodLevel = realPlayer.getFoodData().getFoodLevel();
            this.originalSaturation = realPlayer.getFoodData().getSaturationLevel();
            this.originalXpLevel = realPlayer.experienceLevel;
            this.originalXpProgress = realPlayer.experienceProgress;
            this.originalXpTotal = realPlayer.totalExperience;
            this.originalAbilities = new CompoundTag();
            realPlayer.getAbilities().addSaveData(this.originalAbilities);
            int size = echoPlayer.getInventory().getContainerSize();
            this.lastInventoryState = new ItemStack[size];
            for (int i = 0; i < size; ++i) {
                ItemStack echoStack = echoPlayer.getInventory().getItem(i);
                this.lastInventoryState[i] = echoStack.copy();
                realPlayer.getInventory().setItem(i, echoStack.copy());
            }
            this.lastHealth = echoPlayer.getHealth();
            this.lastFoodLevel = echoPlayer.getFoodData().getFoodLevel();
            this.lastSaturation = shellPlayer.getFoodData().getSaturationLevel();
            this.lastExhaustion = shellPlayer.getFoodData().getExhaustionLevel();
            this.lastAbsorption = shellPlayer.getAbsorptionAmount();
            realPlayer.setHealth(this.lastHealth);
            realPlayer.getFoodData().setFoodLevel(this.lastFoodLevel);
            realPlayer.getFoodData().setSaturation(this.lastSaturation);
            realPlayer.getFoodData().setExhaustion(this.lastExhaustion);
            this.lastSyncGameMode = this.originalGameMode;
            this.lastSyncDimension = realPlayer.level().dimension();
            this.lastSyncX = realPlayer.getX();
            this.lastSyncY = realPlayer.getY();
            this.lastSyncZ = realPlayer.getZ();
            this.lastSyncYRot = realPlayer.getYRot();
            this.lastSyncXRot = realPlayer.getXRot();
        }
    }

    private static final class PendingEchoReshow {
        private final UUID echoPlayerId;
        private final float health;
        private final GameType gameMode;

        private PendingEchoReshow(EchoServerPlayer echoPlayer) {
            this.echoPlayerId = echoPlayer.getUUID();
            this.health = echoPlayer.getHealth();
            this.gameMode = echoPlayer.gameMode.getGameModeForPlayer();
        }
    }

    private record AttributeState(double baseValue, List<AttributeModifier> modifiers, Map<UUID, AttributeModifier> modifiersById, Set<UUID> permanentModifierIds) {
    }
}
