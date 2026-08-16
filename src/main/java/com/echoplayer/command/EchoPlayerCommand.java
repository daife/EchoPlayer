package com.echoplayer.command;

import com.echoplayer.Constants;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import com.echoplayer.manager.SkinManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class EchoPlayerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("echoplayer");

        command.then(Commands.literal("spawn")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(EchoPlayerCommand::spawn)));

        command.then(Commands.literal("control")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    EchoPlayerManager.getManageableEchoPlayerNames(
                        context.getSource().getServer(), EchoPlayerManager.getCommandExecutor(context.getSource())), builder))
                .executes(EchoPlayerCommand::control)));

        command.then(Commands.literal("unpossess")
            .executes(EchoPlayerCommand::unpossess));

        command.then(Commands.literal("remove")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    EchoPlayerManager.getManageableEchoPlayerNames(
                        context.getSource().getServer(), EchoPlayerManager.getCommandExecutor(context.getSource())), builder))
                .executes(EchoPlayerCommand::remove)));

        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config");
        config.then(Commands.literal("allow_other_players_control")
            .executes(EchoPlayerCommand::getAllowOtherPlayersControl)
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .requires(source -> source.hasPermission(2))
                .executes(EchoPlayerCommand::setAllowOtherPlayersControl)));
        command.then(config);

        LiteralArgumentBuilder<CommandSourceStack> skin = Commands.literal("skin");
        skin.then(Commands.literal("set")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    EchoPlayerManager.getManageableEchoPlayerNames(
                        context.getSource().getServer(), EchoPlayerManager.getCommandExecutor(context.getSource())), builder))
                .then(Commands.argument("skin_name", StringArgumentType.word())
                    .executes(EchoPlayerCommand::skinSet))));
        skin.then(Commands.literal("url")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    EchoPlayerManager.getManageableEchoPlayerNames(
                        context.getSource().getServer(), EchoPlayerManager.getCommandExecutor(context.getSource())), builder))
                .then(Commands.argument("url", StringArgumentType.greedyString())
                    .executes(EchoPlayerCommand::skinUrl))));
        skin.then(Commands.literal("clear")
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    EchoPlayerManager.getManageableEchoPlayerNames(
                        context.getSource().getServer(), EchoPlayerManager.getCommandExecutor(context.getSource())), builder))
                .executes(EchoPlayerCommand::skinClear)));
        command.then(skin);

        dispatcher.register(command);
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        if (name.length() > 16) {
            source.sendFailure(Component.literal("Name cannot be longer than 16 characters."));
            return 0;
        }
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        ServerPlayer owner = EchoPlayerManager.getCommandExecutor(source);
        GameProfile profile = EchoPlayerManager.createStableProfile(name);
        String conflict = EchoPlayerManager.getSpawnConflict(server, profile);
        if (conflict != null) {
            source.sendFailure(Component.literal(conflict));
            return 0;
        }
        try {
            EchoServerPlayer echoPlayer = EchoPlayerManager.spawnEchoPlayer(server, level, profile, owner.getUUID());
            if (echoPlayer != null) {
                echoPlayer.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
                echoPlayer.setXRot(source.getRotation().x);
                echoPlayer.setYRot(source.getRotation().y);
            }
            source.sendSuccess(() -> Component.literal("EchoPlayer spawned!"), true);
        } catch (Throwable e) {
            e.printStackTrace();
            Constants.LOG.error("Failed to spawn EchoPlayer", e);
            source.sendFailure(Component.literal("Failed to spawn EchoPlayer: " + e.getMessage()));
        }
        return 1;
    }

    private static int control(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer sender = EchoPlayerManager.getCommandExecutor(source);
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = sender.getServer();
        EchoServerPlayer echoPlayer = resolveSingleEchoPlayer(server, name, source);
        if (echoPlayer == null) {
            return 1;
        }
        String failure = EchoPlayerManager.possess(sender, echoPlayer);
        if (failure != null) {
            source.sendFailure(Component.literal(failure));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("You are now controlling " + name), false);
        return 1;
    }

    private static int unpossess(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer sender = EchoPlayerManager.getCommandExecutor(source);
        try {
            EchoPlayerManager.revertPossession(sender);
            source.sendSuccess(() -> Component.literal("You are no longer controlling the EchoPlayer."), false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = source.getServer();
        List<EchoServerPlayer> targets = EchoPlayerManager.getEchoPlayersByName(server, name);
        if (!targets.isEmpty()) {
            ServerPlayer sender;
            try {
                sender = EchoPlayerManager.getCommandExecutor(source);
            } catch (CommandSyntaxException e) {
                source.sendFailure(Component.literal("This command must be run by a player."));
                return 0;
            }
            for (EchoServerPlayer echoPlayer : targets) {
                if (!EchoPlayerManager.canManageEchoPlayer(sender, echoPlayer)) {
                    source.sendFailure(Component.literal("Only the player who spawned " + name + " may remove it while public access is disabled."));
                    return 0;
                }
            }
            for (EchoServerPlayer echoPlayer : targets) {
                EchoPlayerManager.removeEchoPlayer(echoPlayer);
            }
            source.sendSuccess(() -> Component.literal("Removed " + targets.size() + " EchoPlayer(s) named " + name + "."), true);
        } else {
            source.sendFailure(Component.literal("Player not found or is not an EchoPlayer."));
        }
        return 1;
    }

    private static int getAllowOtherPlayersControl(CommandContext<CommandSourceStack> context) {
        boolean enabled = EchoPlayerManager.isOtherPlayersControlAllowed(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("allow_other_players_control is " + (enabled ? "enabled" : "disabled") + "."), false);
        return enabled ? 1 : 0;
    }

    private static int setAllowOtherPlayersControl(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        EchoPlayerManager.setOtherPlayersControlAllowed(context.getSource().getServer(), enabled);
        context.getSource().sendSuccess(() -> Component.literal("allow_other_players_control set to " + (enabled ? "enabled" : "disabled") + "."), true);
        return 1;
    }

    private static int skinSet(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        String skinName = StringArgumentType.getString(context, "skin_name");
        MinecraftServer server = source.getServer();
        EchoServerPlayer echoPlayer = resolveSingleEchoPlayer(server, name, source);
        if (echoPlayer == null) {
            return 1;
        }
        if (!canManage(source, echoPlayer, "change its skin")) {
            return 0;
        }
        SkinManager.updateSkinAsync(server, echoPlayer, skinName, source);
        return 1;
    }

    private static int skinUrl(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        String url = StringArgumentType.getString(context, "url");
        MinecraftServer server = source.getServer();
        EchoServerPlayer echoPlayer = resolveSingleEchoPlayer(server, name, source);
        if (echoPlayer == null) {
            return 1;
        }
        if (!canManage(source, echoPlayer, "change its skin")) {
            return 0;
        }
        SkinManager.updateSkinFromUrlAsync(server, echoPlayer, url, source);
        return 1;
    }

    private static int skinClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        MinecraftServer server = source.getServer();
        EchoServerPlayer echoPlayer = resolveSingleEchoPlayer(server, name, source);
        if (echoPlayer == null) {
            return 1;
        }
        if (!canManage(source, echoPlayer, "clear its skin")) {
            return 0;
        }
        SkinManager.clearSkin(server, echoPlayer, source);
        return 1;
    }

    private static boolean canManage(CommandSourceStack source, EchoServerPlayer echoPlayer, String action) {
        try {
            ServerPlayer sender = EchoPlayerManager.getCommandExecutor(source);
            if (EchoPlayerManager.canManageEchoPlayer(sender, echoPlayer)) {
                return true;
            }
        } catch (CommandSyntaxException ignored) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return false;
        }
        source.sendFailure(Component.literal("Only the player who spawned " + echoPlayer.getGameProfile().getName()
            + " may " + action + " while public access is disabled."));
        return false;
    }

    private static EchoServerPlayer resolveSingleEchoPlayer(MinecraftServer server, String name, CommandSourceStack source) {
        List<EchoServerPlayer> targets = EchoPlayerManager.getEchoPlayersByName(server, name);
        if (targets.size() == 1) {
            return targets.get(0);
        }
        if (targets.size() > 1) {
            source.sendFailure(Component.literal("Multiple EchoPlayers named " + name + " exist. Remove them first."));
        } else {
            source.sendFailure(Component.literal("Player not found or is not an EchoPlayer."));
        }
        return null;
    }
}
