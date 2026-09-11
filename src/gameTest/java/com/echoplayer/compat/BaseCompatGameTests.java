package com.echoplayer.gametest;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("echoplayer")
@PrefixGameTestTemplate(false)
public final class BaseCompatGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void possessionRoundTrip(GameTestHelper helper) {
        try (var players = new CompatTestPlayers(helper)) {
            helper.assertTrue(EchoPlayerManager.possess(players.player, players.first) == null, "First possession failed");
            helper.assertTrue(EchoPlayerManager.getPossessed(players.player) == players.first, "Wrong first character");
            helper.assertTrue(EchoPlayerManager.possess(players.player, players.second) == null, "Character switch failed");
            helper.assertTrue(EchoPlayerManager.getPossessed(players.player) == players.second, "Wrong second character");
            EchoPlayerManager.revertPossession(players.player);
            helper.assertTrue(EchoPlayerManager.getPossessed(players.player) == null, "Possession remained active");
        }
        helper.succeed();
    }
}
