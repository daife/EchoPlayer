package com.echoplayer.entity;

import com.echoplayer.manager.EchoPlayerManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public class EchoServerPlayer
extends ServerPlayer {
    private static final byte ALL_SKIN_LAYERS = 0x7F;

    public ServerPlayer linkedRealPlayer = null;
    private boolean deathFinalized;
    private long lastPassiveTick = Long.MIN_VALUE;
    private float lastDamageAmount;
    private Vec3 storedKnockbackMovement;
    private long storedKnockbackTick = Long.MIN_VALUE;
    private double echoplayer$lastFallCheckX;
    private double echoplayer$lastFallCheckY;
    private double echoplayer$lastFallCheckZ;
    private boolean echoplayer$hadFallCheckPosition;

    public EchoServerPlayer(MinecraftServer pServer, ServerLevel pLevel, GameProfile pGameProfile) {
        super(pServer, pLevel, pGameProfile);
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_SKIN_LAYERS);
        this.setNoGravity(false);
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        if (this.linkedRealPlayer != null) {
            this.lastDamageAmount = pAmount;
            return super.hurt(pSource, pAmount);
        }
        if (!EchoPlayerManager.shouldApplyEchoDamage(this, pSource, pAmount)) {
            return false;
        }
        EchoPlayerManager.prepareEchoForIncomingDamage(this);
        boolean damaged = super.hurt(pSource, pAmount);
        if (damaged && !this.isDeadOrDying() && !this.isRemoved()) {
            EchoPlayerManager.recordEchoDamage(this, pSource, pAmount);
            EchoPlayerManager.afterEchoHurt(this, pSource);
        } else if (damaged) {
            EchoPlayerManager.recordEchoDamage(this, pSource, pAmount);
        }
        return damaged;
    }

    @Override
    public void knockback(double pStrength, double pX, double pZ) {
        super.knockback(pStrength, pX, pZ);
        EchoPlayerManager.afterEchoKnockback(this);
        if (EchoPlayerManager.shouldRunPassivePhysics(this)) {
            this.storedKnockbackMovement = this.getDeltaMovement();
            this.storedKnockbackTick = this.level().getGameTime();
        }
    }

    public void restoreStoredKnockback() {
        if (this.storedKnockbackMovement != null && this.storedKnockbackTick == this.level().getGameTime()) {
            this.setDeltaMovement(this.storedKnockbackMovement);
            this.hurtMarked = true;
            this.hasImpulse = true;
        }
        this.storedKnockbackMovement = null;
        this.storedKnockbackTick = Long.MIN_VALUE;
    }

    @Override
    public void travel(Vec3 pTravelVector) {
        if (!EchoPlayerManager.shouldRunPassivePhysics(this)) {
            return;
        }
        super.travel(pTravelVector);
    }

    public void restoreAfterControllerBodyDeath(float health) {
        if (this.isRemoved()) {
            this.unsetRemoved();
        }
        this.deathFinalized = false;
        this.dead = false;
        this.deathTime = 0;
        this.setHealth(Math.max(health, 1.0f));
        this.setPose(Pose.STANDING);
    }

    @Override
    public void die(DamageSource pSource) {
        if (this.deathFinalized) {
            return;
        }
        this.deathFinalized = true;
        if (this.linkedRealPlayer != null) {
            EchoPlayerManager.finalizeOriginalBodyDeath(this, pSource, this.lastDamageAmount);
            return;
        }
        EchoPlayerManager.prepareEchoForDeath(this);
        super.die(pSource);
        EchoPlayerManager.ejectControllersOnDeath(this);
    }

    private void echoplayer$tickPassiveFallDamage() {
        if (this.level().isClientSide || !EchoPlayerManager.shouldRunPassivePhysics(this)) {
            this.echoplayer$hadFallCheckPosition = false;
            return;
        }
        if (!this.echoplayer$hadFallCheckPosition) {
            this.echoplayer$lastFallCheckX = this.getX();
            this.echoplayer$lastFallCheckY = this.getY();
            this.echoplayer$lastFallCheckZ = this.getZ();
            this.echoplayer$hadFallCheckPosition = true;
            return;
        }
        double dx = this.getX() - this.echoplayer$lastFallCheckX;
        double dy = this.getY() - this.echoplayer$lastFallCheckY;
        double dz = this.getZ() - this.echoplayer$lastFallCheckZ;
        this.doCheckFallDamage(dx, dy, dz, this.onGround());
        this.echoplayer$lastFallCheckX = this.getX();
        this.echoplayer$lastFallCheckY = this.getY();
        this.echoplayer$lastFallCheckZ = this.getZ();
    }

    @Override
    public void tick() {
        long gameTime;
        super.tick();
        if (!this.level().isClientSide && (this.isDeadOrDying() || this.isSleeping() || EchoPlayerManager.shouldRunPassivePhysics(this)) && this.lastPassiveTick != (gameTime = this.level().getGameTime())) {
            this.lastPassiveTick = gameTime;
            this.doTick();
        }
        this.echoplayer$tickPassiveFallDamage();
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (!this.level().isClientSide && this.isRemoved()) {
            EchoPlayerManager.respawnEchoAfterDeath(this);
        }
    }

    @Override
    public void playSound(SoundEvent pSound, float pVolume, float pPitch) {
        if (!this.isSilent()) {
            ServerPlayer excludePlayer = EchoPlayerManager.getPossessor(this);
            if (excludePlayer == null) {
                excludePlayer = this;
            }
            this.level().playSound(excludePlayer, this.getX(), this.getY(), this.getZ(), pSound, this.getSoundSource(), pVolume, pPitch);
        }
    }

    @Override
    public void completeUsingItem() {
        if (EchoPlayerManager.isPossessed(this)) {
            this.stopUsingItem();
            return;
        }
        super.completeUsingItem();
    }
}
