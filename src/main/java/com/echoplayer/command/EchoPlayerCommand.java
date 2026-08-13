package com.echoplayer.command;

import com.echoplayer.Constants;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class EchoPlayerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("echoplayer").requires(source -> source.hasPermission(2))).then(Commands.literal("spawn").then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
            CommandSourceStack source = (CommandSourceStack)context.getSource();
            String name = StringArgumentType.getString((CommandContext)context, (String)"name");
            if (name.length() > 16) {
                source.sendFailure(Component.literal("Name cannot be longer than 16 characters."));
                return 0;
            }
            MinecraftServer server = source.getServer();
            ServerLevel level = source.getLevel();
            UUID offlineUuid = UUIDUtil.createOfflinePlayerUUID(name);
            GameProfile profile = new GameProfile(offlineUuid, name);
            String conflict = EchoPlayerManager.getSpawnConflict(server, profile);
            if (conflict != null) {
                source.sendFailure(Component.literal(conflict));
                return 0;
            }
            try {
                EchoServerPlayer echoPlayer = EchoPlayerManager.spawnEchoPlayer(server, level, profile);
                if (echoPlayer != null) {
                    echoPlayer.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
                    echoPlayer.setXRot(source.getRotation().x);
                    echoPlayer.setYRot(source.getRotation().y);
                }
                source.sendSuccess(() -> Component.literal("EchoPlayer spawned!"), true);
            }
            catch (Throwable e) {
                e.printStackTrace();
                Constants.LOG.error("Failed to spawn EchoPlayer", e);
                source.sendFailure(Component.literal("Failed to spawn EchoPlayer: " + e.getMessage()));
            }
            return 1;
        })))).then(Commands.literal("control").then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(EchoPlayerManager.getEchoPlayerNames(((CommandSourceStack)context.getSource()).getServer()), builder)).executes(context -> {
            CommandSourceStack source = (CommandSourceStack)context.getSource();
            ServerPlayer sender = EchoPlayerManager.getCommandExecutor(source);
            String name = StringArgumentType.getString((CommandContext)context, (String)"name");
            MinecraftServer server = sender.getServer();
            List<EchoServerPlayer> targets = EchoPlayerManager.getEchoPlayersByName(server, name);
            if (targets.size() == 1) {
                EchoServerPlayer echoPlayer = targets.get(0);
                String failure = EchoPlayerManager.possess(sender, echoPlayer);
                if (failure != null) {
                    source.sendFailure(Component.literal(failure));
                    return 0;
                }
                source.sendSuccess(() -> Component.literal("You are now controlling " + name), false);
            } else if (targets.size() > 1) {
                source.sendFailure(Component.literal("Multiple EchoPlayers named " + name + " exist. Remove them first."));
            } else {
                source.sendFailure(Component.literal("Player not found or is not an EchoPlayer."));
            }
            return 1;
        })))).then(Commands.literal("unpossess").executes(context -> {
            CommandSourceStack source = (CommandSourceStack)context.getSource();
            ServerPlayer sender = EchoPlayerManager.getCommandExecutor(source);
            try {
                EchoPlayerManager.revertPossession(sender);
                source.sendSuccess(() -> Component.literal("You are no longer controlling the EchoPlayer."), false);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return 1;
        }))).then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(EchoPlayerManager.getEchoPlayerNames(((CommandSourceStack)context.getSource()).getServer()), builder)).executes(context -> {
            CommandSourceStack source = (CommandSourceStack)context.getSource();
            String name = StringArgumentType.getString((CommandContext)context, (String)"name");
            MinecraftServer server = source.getServer();
            List<EchoServerPlayer> targets = EchoPlayerManager.getEchoPlayersByName(server, name);
            if (!targets.isEmpty()) {
                for (EchoServerPlayer echoPlayer : targets) {
                    EchoPlayerManager.removeEchoPlayer(echoPlayer);
                }
                source.sendSuccess(() -> Component.literal("Removed " + targets.size() + " EchoPlayer(s) named " + name + "."), true);
            } else {
                source.sendFailure(Component.literal("Player not found or is not an EchoPlayer."));
            }
            return 1;
        })))).then(Commands.literal("config").then(((LiteralArgumentBuilder)Commands.literal("multi_control").executes(context -> {
            boolean enabled = EchoPlayerManager.isMultiControlEnabled(((CommandSourceStack)context.getSource()).getServer());
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("multi_control is " + (enabled ? "enabled" : "disabled") + "."), false);
            return enabled ? 1 : 0;
        })).then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
            boolean enabled = BoolArgumentType.getBool((CommandContext)context, (String)"enabled");
            EchoPlayerManager.setMultiControlEnabled(((CommandSourceStack)context.getSource()).getServer(), enabled);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("multi_control set to " + (enabled ? "enabled" : "disabled") + "."), true);
            return 1;
        }))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("skin").then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(EchoPlayerManager.getEchoPlayerNames(((CommandSourceStack)context.getSource()).getServer()), builder)).then(Commands.argument("skin_name", StringArgumentType.word()).executes(context -> {
            CommandSourceStack source = (CommandSourceStack)context.getSource();
            String name = StringArgumentType.getString((CommandContext)context, (String)"name");
            String skinName = StringArgumentType.getString((CommandContext)context, (String)"skin_name");
            MinecraftServer server = source.getServer();
            List<EchoServerPlayer> targets = EchoPlayerManager.getEchoPlayersByName(server, name);
            if (targets.size() == 1) {
                EchoServerPlayer echoPlayer = targets.get(0);
                EchoPlayerManager.updateSkinAsync(server, echoPlayer, skinName, source);
            } else if (targets.size() > 1) {
                source.sendFailure(Component.literal("Multiple EchoPlayers named " + name + " exist. Remove them first."));
            } else {
                source.sendFailure(Component.literal("Player not found or is not an EchoPlayer."));
            }
            return 1;
        }))))).then(Commands.literal("url").then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(EchoPlayerManager.getEchoPlayerNames(((CommandSourceStack)context.getSource()).getServer()), builder)).then(Commands.argument("url", StringArgumentType.greedyString()).executes(context -> {
            CommandSourceStack source = (CommandSourceStack)context.getSource();
            String name = StringArgumentType.getString((CommandContext)context, (String)"name");
            String url = StringArgumentType.getString((CommandContext)context, (String)"url");
            MinecraftServer server = source.getServer();
            List<EchoServerPlayer> targets = EchoPlayerManager.getEchoPlayersByName(server, name);
            if (targets.size() == 1) {
                EchoServerPlayer echoPlayer = targets.get(0);
                EchoPlayerManager.updateSkinFromUrlAsync(server, echoPlayer, url, source);
            } else if (targets.size() > 1) {
                source.sendFailure(Component.literal("Multiple EchoPlayers named " + name + " exist. Remove them first."));
            } else {
                source.sendFailure(Component.literal("Player not found or is not an EchoPlayer."));
            }
            return 1;
        }))))).then(Commands.literal("clear").then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(EchoPlayerManager.getEchoPlayerNames(((CommandSourceStack)context.getSource()).getServer()), builder)).executes(context -> {
            CommandSourceStack source = (CommandSourceStack)context.getSource();
            String name = StringArgumentType.getString((CommandContext)context, (String)"name");
            MinecraftServer server = source.getServer();
            List<EchoServerPlayer> targets = EchoPlayerManager.getEchoPlayersByName(server, name);
            if (targets.size() == 1) {
                EchoServerPlayer echoPlayer = targets.get(0);
                EchoPlayerManager.clearSkin(server, echoPlayer, source);
            } else if (targets.size() > 1) {
                source.sendFailure(Component.literal("Multiple EchoPlayers named " + name + " exist. Remove them first."));
            } else {
                source.sendFailure(Component.literal("Player not found or is not an EchoPlayer."));
            }
            return 1;
        })))));
    }
}
