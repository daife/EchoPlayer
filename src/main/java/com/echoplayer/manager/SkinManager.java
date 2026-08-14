package com.echoplayer.manager;

import com.echoplayer.Constants;
import com.echoplayer.data.EchoPlayerSavedData;
import com.echoplayer.entity.EchoServerPlayer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.properties.Property;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class SkinManager {

    public static void updateSkinAsync(MinecraftServer server, EchoServerPlayer echoPlayer, String skinSourceUsername, CommandSourceStack source) {
        CompletableFuture.runAsync(() -> server.getProfileRepository().findProfilesByNames(new String[]{skinSourceUsername}, Agent.MINECRAFT, new ProfileLookupCallback() {

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
                    resendSkinPackets(server, echoPlayer);
                    source.sendSuccess(() -> Component.literal("Successfully updated skin for " + targetProfile.getName() + " to match " + skinSourceUsername), true);
                });
            }

            public void onProfileLookupFailed(GameProfile profile, Exception e) {
                server.execute(() -> source.sendFailure(Component.literal("Could not find player: " + skinSourceUsername)));
            }
        }));
    }

    public static void updateSkinFromUrlAsync(MinecraftServer server, EchoServerPlayer echoPlayer, String rawUrl, CommandSourceStack source) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = normalizeSkinUrl(rawUrl);
                HttpClient client = HttpClient.newHttpClient();
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("url", url);
                requestBody.addProperty("visibility", (Number)0);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mineskin.org/generate/url"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "EchoPlayer/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
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
                        resendSkinPackets(server, echoPlayer);
                        source.sendSuccess(() -> Component.literal("Successfully updated skin from URL."), true);
                    });
                } else {
                    server.execute(() -> {
                        try {
                            JsonObject err = JsonParser.parseString(response.body()).getAsJsonObject();
                            String msg = err.has("error") ? err.get("error").getAsString() : "Unknown error";
                            source.sendFailure(Component.literal("Failed to generate skin: " + msg));
                        } catch (Exception ex) {
                            source.sendFailure(Component.literal("Failed to generate skin. Status code: " + response.statusCode()));
                        }
                    });
                }
            } catch (Exception e) {
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
        resendSkinPackets(server, echoPlayer);
        source.sendSuccess(() -> Component.literal("Successfully cleared skin for " + targetProfile.getName()), true);
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

    private static void resendSkinPackets(MinecraftServer server, EchoServerPlayer echoPlayer) {
        ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(List.of(echoPlayer.getUUID()));
        ClientboundPlayerInfoUpdatePacket updatePacket = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(echoPlayer));
        ClientboundRemoveEntitiesPacket removeEntityPacket = new ClientboundRemoveEntitiesPacket(echoPlayer.getId());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(removePacket);
            player.connection.send(updatePacket);
            if (player == echoPlayer || EchoPlayerManager.getPossessed(player) == echoPlayer || player.level().dimension() != echoPlayer.level().dimension()) {
                continue;
            }
            int trackingChunks = Math.min(echoPlayer.getType().clientTrackingRange(), server.getPlayerList().getViewDistance());
            double trackingRange = (double)trackingChunks * 16.0;
            if (!(echoPlayer.distanceToSqr(player) <= trackingRange * trackingRange)) {
                continue;
            }
            player.connection.send(removeEntityPacket);
            EchoPlayerManager.sendPlayerEntityToViewer(echoPlayer, player);
        }
    }
}