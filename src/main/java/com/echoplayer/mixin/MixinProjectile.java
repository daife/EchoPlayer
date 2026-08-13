package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Projectile.class})
public class MixinProjectile {
    @Unique
    private Vec3 echoplayer$storedShooterVelocity = null;

    @Inject(method={"canHitEntity"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$ignorePossessedCollision(Entity pTarget, CallbackInfoReturnable<Boolean> cir) {
        Projectile projectile = (Projectile)((Object)this);
        Entity owner = projectile.getOwner();
        if (owner != null && EchoPlayerManager.shouldDisableCollision(owner, pTarget)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"shootFromRotation"}, at={@At(value="HEAD")})
    private void echoplayer$clearVelocityBeforeShoot(Entity shooter, float x, float y, float z, float velocity, float inaccuracy, CallbackInfo ci) {
        ServerPlayer player;
        if (shooter instanceof ServerPlayer && EchoPlayerManager.isPossessing(player = (ServerPlayer)shooter)) {
            this.echoplayer$storedShooterVelocity = shooter.getDeltaMovement();
            shooter.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Inject(method={"shootFromRotation"}, at={@At(value="TAIL")})
    private void echoplayer$restoreVelocityAfterShoot(Entity shooter, float x, float y, float z, float velocity, float inaccuracy, CallbackInfo ci) {
        if (this.echoplayer$storedShooterVelocity != null && shooter != null) {
            shooter.setDeltaMovement(this.echoplayer$storedShooterVelocity);
            this.echoplayer$storedShooterVelocity = null;
        }
    }
}
