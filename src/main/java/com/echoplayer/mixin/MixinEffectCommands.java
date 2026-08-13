package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.EffectCommands;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={EffectCommands.class})
public class MixinEffectCommands {
    @Inject(method={"giveEffect"}, at={@At(value="RETURN")})
    private static void syncGivenEffects(CommandSourceStack source, Collection<? extends Entity> targets, Holder<MobEffect> effect, Integer seconds, int amplifier, boolean showParticles, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        EchoPlayerManager.syncLogicalStateAfterExternalMutation(targets);
    }

    @Inject(method={"clearEffects"}, at={@At(value="RETURN")})
    private static void syncClearedEffects(CommandSourceStack source, Collection<? extends Entity> targets, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        EchoPlayerManager.syncLogicalStateAfterExternalMutation(targets);
    }

    @Inject(method={"clearEffect"}, at={@At(value="RETURN")})
    private static void syncClearedEffect(CommandSourceStack source, Collection<? extends Entity> targets, Holder<MobEffect> effect, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        EchoPlayerManager.syncLogicalStateAfterExternalMutation(targets);
    }
}

