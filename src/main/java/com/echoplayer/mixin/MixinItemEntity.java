package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ItemEntity.class})
public class MixinItemEntity {
    @Inject(method={"playerTouch"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$ignoreControllerObserver(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer && EchoPlayerManager.isPossessing((ServerPlayer)player)) {
            ci.cancel();
        }
    }

    @Redirect(method={"playerTouch"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean redirectPossessedInventory(Inventory inventory, ItemStack stack, Player player) {
        ServerPlayer serverPlayer;
        if (player instanceof ServerPlayer && EchoPlayerManager.isPossessing(serverPlayer = (ServerPlayer)player)) {
            return EchoPlayerManager.addPickedItemToPossessedInventory(serverPlayer, (ItemEntity)((Object)this), stack);
        }
        return inventory.add(stack);
    }
}
