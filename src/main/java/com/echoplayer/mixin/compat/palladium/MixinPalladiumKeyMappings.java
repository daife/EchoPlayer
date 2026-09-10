package com.echoplayer.mixin.compat.palladium;

import com.echoplayer.client.ClientPossessionData;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.threetag.palladium.client.PalladiumKeyMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.threetag.palladium.client.PalladiumKeyMappings", remap = false)
public abstract class MixinPalladiumKeyMappings {
    @Shadow(remap = false) private static LocalPlayer MOVEMENT_KEYS_PLAYER;
    @Unique private UUID echoplayer$character;
    @Unique private LocalPlayer echoplayer$player;

    @Inject(method = "clientTick", at = @At("HEAD"))
    private void echoplayer$changedCharacter(Minecraft minecraft, CallbackInfo ci) {
        UUID character = ClientPossessionData.possessedUUID;
        if (echoplayer$player == minecraft.player && Objects.equals(echoplayer$character, character)) return;
        echoplayer$player = minecraft.player;
        echoplayer$character = character;
        // LocalPlayer is reused by possession, so Palladium's normal identity
        // check would not resend movement keys or discard references to old abilities.
        MOVEMENT_KEYS_PLAYER = null;
        PalladiumKeyMappings.LEFT_CLICKED_ABILITY = null;
        PalladiumKeyMappings.RIGHT_CLICKED_ABILITY = null;
        PalladiumKeyMappings.SPACE_BAR_ABILITY = null;
        PalladiumKeyMappings.DUAL_WIELDING_RIGHT_CLICK = false;
        for (int i = 0; i < PalladiumKeyMappings.ABILITY_KEYS.length; i++) {
            // Require a fresh press for abilities; a held toggle key must not
            // toggle the newly controlled character as a side effect of switching.
            PalladiumKeyMappings.ABILITY_KEYS_PRESSED[i] = PalladiumKeyMappings.ABILITY_KEYS[i].isDown();
        }
    }
}
