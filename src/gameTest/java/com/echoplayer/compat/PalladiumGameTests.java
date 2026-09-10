package com.echoplayer.compat;

import com.echoplayer.compat.palladium.PantheonIdentity;
import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.threetag.palladium.entity.PalladiumPlayerExtension;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.PowerManager;
import net.threetag.palladium.power.SuperpowerUtil;
import net.threetag.palladium.util.property.PalladiumProperties;
import net.threetag.pantheonsent.entity.Khonshu;
import net.threetag.pantheonsent.util.PantheonSentProperties;

@GameTestHolder("echoplayer")
@PrefixGameTestTemplate(false)
public final class PalladiumGameTests {
    private static final ResourceLocation POWER = new ResourceLocation("echoplayer", "compat_test");

    private static IPowerHolder grant(ServerPlayer player) {
        SuperpowerUtil.addSuperpower(player, POWER);
        var handler = PowerManager.getPowerHandler(player).orElseThrow();
        handler.tick();
        return handler.getPowerHolders().get(POWER);
    }

    @GameTest(template = "forge:empty", timeoutTicks = 100)
    public static void liveStateAndSaveIsolation(GameTestHelper helper) {
        try (var players = new CompatTestPlayers(helper)) {
            var body = grant(players.player);
            var first = grant(players.first);
            var second = grant(players.second);
            helper.assertTrue(body != null && first != null && second != null, "Fixture power was not loaded");
            body.getAbilities().get("timer").cooldown = 19;
            first.getAbilities().get("timer").cooldown = 73;
            second.getAbilities().get("timer").cooldown = 37;
            first.getAbilities().get("timer").activationTimer = 41;
            first.getAbilities().get("toggle").keyPressed = true;
            first.getAbilities().get("held").keyPressed = true;
            first.getEnergyBars().get("energy").setMax(100);
            first.getEnergyBars().get("energy").set(64);
            PantheonSentProperties.KHONSHU_RECRUITING_TIMER.set(players.player, 8);
            PantheonSentProperties.KHONSHU_RECRUITING_TIMER.set(players.first, 88);
            players.player.getPersistentData().putString("addon_character", "body");
            players.first.getPersistentData().putString("addon_character", "first");
            helper.assertTrue(EchoPlayerManager.possess(players.player, players.first) == null, "Possession failed");
            var handler = PowerManager.getPowerHandler(players.player).orElseThrow();
            helper.assertTrue(handler == PowerManager.getPowerHandler(players.first).orElseThrow(), "Duplicate power authority");
            helper.assertTrue(handler.getPowerHolders().get(POWER) == first, "Live power holder was recreated");
            helper.assertTrue(first.getEntity() == players.player, "Power owner was not rebound");
            helper.assertTrue(players.player.getPersistentData() == players.first.getPersistentData(), "Script data authority not shared");
            helper.assertTrue(first.getAbilities().get("timer").cooldown == 73, "Cooldown lost on entry");
            helper.assertTrue(first.getAbilities().get("timer").activationTimer == 41, "Activation timer lost");
            helper.assertTrue(first.getAbilities().get("toggle").keyPressed, "Toggle was cleared by switching");
            helper.assertTrue(!first.getAbilities().get("held").keyPressed, "Held input stuck on released character");
            helper.assertTrue(first.getEnergyBars().get("energy").get() == 64, "Energy lost");
            helper.assertTrue(PantheonSentProperties.KHONSHU_RECRUITING_TIMER.get(players.player) == 88, "Addon property not transferred");
            var shell = EchoPlayerManager.getIdentityAvatar(players.player.getUUID());
            helper.assertTrue(PowerManager.getPowerHandler(shell).orElseThrow().getPowerHolders().get(POWER) == body, "Body power lost");
            helper.assertTrue(body.getEntity() == shell, "Original body power still bound to controller");
            helper.assertTrue(((PalladiumPlayerExtension) players.player).palladium$getFlightHandler()
                == ((PalladiumPlayerExtension) players.first).palladium$getFlightHandler(), "Flight authority not shared");
            var savedBody = players.player.saveWithoutId(new CompoundTag()).getCompound("Palladium");
            var savedEcho = players.first.saveWithoutId(new CompoundTag()).getCompound("Palladium");
            helper.assertTrue(players.player.saveWithoutId(new CompoundTag()).getCompound("ForgeData").getString("addon_character").equals("body"), "Script data saved to wrong character");
            helper.assertTrue(savedBody.getCompound("Properties").getInt("khonshu_recruiting_timer") == 8, "Body save contains Echo data");
            helper.assertTrue(savedEcho.getCompound("Properties").getInt("khonshu_recruiting_timer") == 88, "Echo save contains body data");
            helper.assertTrue(EchoPlayerManager.possess(players.player, players.second) == null, "Switch failed");
            helper.assertTrue(first.getEntity() == players.first, "Released Echo retained controller owner");
            helper.assertTrue(first.getAbilities().get("timer").cooldown == 73, "Cooldown lost on release");
            helper.assertTrue(PowerManager.getPowerHandler(players.player).orElseThrow().getPowerHolders().get(POWER) == second, "Wrong power on switch");
            EchoPlayerManager.revertPossession(players.player);
            helper.assertTrue(PowerManager.getPowerHandler(players.player).orElseThrow().getPowerHolders().get(POWER) == body, "Original power not restored");
            helper.assertTrue(body.getEntity() == players.player && body.getAbilities().get("timer").cooldown == 19, "Original runtime lost");
        }
        helper.succeed();
    }

    @GameTest(template = "forge:empty", timeoutTicks = 100)
    public static void khonshuFollowsCharacter(GameTestHelper helper) {
        try (var players = new CompatTestPlayers(helper)) {
            var originalKhonshu = new Khonshu(helper.getLevel(), players.player);
            helper.assertTrue(EchoPlayerManager.possess(players.player, players.first) == null, "Possession failed");
            var khonshu = new Khonshu(helper.getLevel(), players.player);
            helper.assertTrue(khonshu.avatarId.equals(players.first.getUUID()), "Khonshu recorded authentication UUID");
            helper.assertTrue(khonshu.getAvatar() == players.first, "Khonshu bound to wrong entity");
            helper.assertTrue(originalKhonshu.getAvatar() == EchoPlayerManager.getIdentityAvatar(players.player.getUUID()), "Original Khonshu followed controller");
            helper.assertTrue(!khonshu.isInvisibleTo(players.player), "Possessor cannot see own Khonshu");
            EchoPlayerManager.revertPossession(players.player);
            helper.assertTrue(khonshu.getAvatar() == players.first && khonshu.isInvisibleTo(players.player), "Khonshu identity leaked after release");
            helper.assertTrue(originalKhonshu.getAvatar() == players.player, "Original Khonshu cached removed shell");
        }
        helper.succeed();
    }
}
