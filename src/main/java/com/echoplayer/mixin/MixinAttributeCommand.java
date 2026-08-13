package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AttributeCommand.class})
public class MixinAttributeCommand {
    @Inject(method={"setAttributeBase"}, at={@At(value="RETURN")})
    private static void syncAttributeBase(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, double value, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        EchoPlayerManager.syncLogicalStateAfterExternalMutation(entity);
    }

    @Inject(method={"addModifier"}, at={@At(value="RETURN")})
    private static void syncAddedModifier(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, UUID uuid, String name, double amount, AttributeModifier.Operation operation, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        EchoPlayerManager.syncLogicalStateAfterExternalMutation(entity);
    }

    @Inject(method={"removeModifier"}, at={@At(value="RETURN")})
    private static void syncRemovedModifier(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, UUID uuid, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        EchoPlayerManager.syncLogicalStateAfterExternalMutation(entity);
    }
}

