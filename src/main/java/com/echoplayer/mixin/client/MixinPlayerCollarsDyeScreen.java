package com.echoplayer.mixin.client;

import com.echoplayer.client.ClientPossessionData;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the collar screen edit ownership assigned to the possessed EchoPlayer. */
@Pseudo
@Mixin(targets = "org.jlortiz.playercollars.client.CollarDyeScreen", remap = false)
public abstract class MixinPlayerCollarsDyeScreen {
    @Shadow(remap = false)
    @Final
    @Mutable
    private UUID ownUUID;

    @Inject(
        method = "init()V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void echoplayer$useLogicalOwnerInDevelopment(CallbackInfo ci) {
        this.echoplayer$useLogicalOwner();
    }

    @Inject(
        method = "m_7856_()V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void echoplayer$useLogicalOwnerInProduction(CallbackInfo ci) {
        this.echoplayer$useLogicalOwner();
    }

    private void echoplayer$useLogicalOwner() {
        if (ClientPossessionData.possessedUUID != null) {
            this.ownUUID = ClientPossessionData.possessedUUID;
        }
    }
}
