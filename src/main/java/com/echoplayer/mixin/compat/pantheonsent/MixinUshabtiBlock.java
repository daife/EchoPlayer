package com.echoplayer.mixin.compat.pantheonsent;

import com.echoplayer.compat.palladium.PantheonIdentity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.threetag.pantheonsent.block.entity.UshabtiBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.threetag.pantheonsent.block.UshabtiBlock", remap = false)
public abstract class MixinUshabtiBlock {
    @Inject(method = {"setPlacedBy", "m_6402_"}, at = @At("RETURN"))
    private void echoplayer$storeOwner(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof UshabtiBlockEntity ushabti
            && placer.getUUID().equals(ushabti.owner)) {
            ushabti.owner = PantheonIdentity.id(placer);
            ushabti.setChanged();
        }
    }

    @Redirect(method = {"playerDestroy", "m_6240_"}, at = @At(value = "INVOKE", target = "Ljava/util/UUID;equals(Ljava/lang/Object;)Z"), require = 0)
    private boolean echoplayer$checkOwner(UUID owner, Object ignored, Level level, Player player, BlockPos pos,
                                          BlockState state, BlockEntity blockEntity, ItemStack tool) {
        return owner.equals(PantheonIdentity.id(player));
    }
}
