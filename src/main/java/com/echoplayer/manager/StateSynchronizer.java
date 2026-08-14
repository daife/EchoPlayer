package com.echoplayer.manager;

import com.echoplayer.Constants;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.mixin.AttributeInstanceAccessor;
import com.echoplayer.mixin.AttributeMapAccessor;
import com.echoplayer.mixin.LivingEntityInvoker;
import com.echoplayer.mixin.MobEffectInstanceAccessor;
import com.echoplayer.mixin.PlayerAccessor;
import com.echoplayer.mixin.ServerGamePacketListenerImplAccessor;
import com.echoplayer.mixin.PlayerAccessor;
import com.echoplayer.mixin.ServerGamePacketListenerImplAccessor;
import com.echoplayer.platform.Services;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class StateSynchronizer {

    private static final UUID SPRINTING_SPEED_MODIFIER_ID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final Set<UUID> MOVEMENT_SPEED_STATE_MODIFIER_IDS = Set.of(
        SPRINTING_SPEED_MODIFIER_ID,
        UUID.fromString("87f46a96-686f-4796-b035-22e16ee9e038"),
        UUID.fromString("1eaf83ff-7207-4596-b37a-d7a07b3ec4ce")
    );

    static void copyRealStateToShell(ServerPlayer realPlayer, EchoServerPlayer shellPlayer) {
        synchronizeInventoryContents(realPlayer, shellPlayer);
        setGameModeIfNeeded(shellPlayer, realPlayer.gameMode.getGameModeForPlayer());
        synchronizeEffects(realPlayer, shellPlayer);
        synchronizeFireState(realPlayer, shellPlayer);
        synchronizeAttributes(realPlayer, shellPlayer, true);
        copySprintingState(realPlayer, shellPlayer);
        shellPlayer.setHealth(realPlayer.getHealth());
        shellPlayer.setAbsorptionAmount(realPlayer.getAbsorptionAmount());
        shellPlayer.getFoodData().setFoodLevel(realPlayer.getFoodData().getFoodLevel());
        shellPlayer.getFoodData().setSaturation(realPlayer.getFoodData().getSaturationLevel());
        shellPlayer.experienceLevel = realPlayer.experienceLevel;
        shellPlayer.experienceProgress = realPlayer.experienceProgress;
        shellPlayer.totalExperience = realPlayer.totalExperience;
        copyAbilities(realPlayer, shellPlayer);
    }

    static boolean synchronizeInventoryContents(ServerPlayer source, ServerPlayer target) {
        Inventory sourceInventory = source.getInventory();
        Inventory targetInventory = target.getInventory();
        boolean changed = false;
        int size = Math.min(sourceInventory.getContainerSize(), targetInventory.getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack sourceItem = sourceInventory.getItem(slot);
            if (ItemStack.matches(sourceItem, targetInventory.getItem(slot))) {
                continue;
            }
            targetInventory.setItem(slot, sourceItem.copy());
            changed = true;
        }
        if (targetInventory.selected != sourceInventory.selected) {
            targetInventory.selected = sourceInventory.selected;
            changed = true;
        }
        if (changed) {
            targetInventory.setChanged();
        }
        return changed;
    }

    static void copyInventoryContents(ServerPlayer source, ServerPlayer target) {
        synchronizeInventoryContents(source, target);
    }

    static void synchronizeFireState(ServerPlayer source, ServerPlayer target) {
        setFireState(target, source.getRemainingFireTicks());
    }

    static void setFireState(ServerPlayer target, int fireTicks) {
        target.setRemainingFireTicks(fireTicks);
        target.setSharedFlagOnFire(fireTicks > 0);
    }

    static void synchronizeEffects(ServerPlayer source, ServerPlayer target) {
        Map<MobEffect, MobEffectInstance> sourceEffects = source.getActiveEffectsMap();
        Map<MobEffect, MobEffectInstance> targetEffects = target.getActiveEffectsMap();
        ArrayList<MobEffect> effectsToRemove = null;
        for (MobEffect mobEffect : targetEffects.keySet()) {
            if (sourceEffects.containsKey(mobEffect)) {
                continue;
            }
            if (effectsToRemove == null) {
                effectsToRemove = new ArrayList<MobEffect>();
            }
            effectsToRemove.add(mobEffect);
        }
        if (effectsToRemove != null) {
            for (MobEffect mobEffect : effectsToRemove) {
                target.removeEffect(mobEffect);
            }
        }
        for (Map.Entry<MobEffect, MobEffectInstance> entry : sourceEffects.entrySet()) {
            MobEffect effect = entry.getKey();
            MobEffectInstance sourceEffect = entry.getValue();
            MobEffectInstance targetEffect = targetEffects.get(effect);
            if (targetEffect == null) {
                target.addEffect(new MobEffectInstance(sourceEffect));
                continue;
            }
            if (!hasSameEffectConfiguration(sourceEffect, targetEffect)) {
                target.removeEffect(effect);
                target.addEffect(new MobEffectInstance(sourceEffect));
                continue;
            }
            if (sourceEffect.getDuration() == targetEffect.getDuration()) {
                continue;
            }
            ((MobEffectInstanceAccessor)((Object)targetEffect)).echoplayer$setDuration(sourceEffect.getDuration());
        }
    }

    static boolean hasSameEffectConfiguration(MobEffectInstance first, MobEffectInstance second) {
        return first.getAmplifier() == second.getAmplifier()
            && first.isAmbient() == second.isAmbient()
            && first.isVisible() == second.isVisible()
            && first.showIcon() == second.showIcon();
    }

    static void synchronizeAttributes(ServerPlayer source, ServerPlayer target, boolean synchronizeAll) {
        LinkedHashMap<Attribute, AttributeInstance> changedAttributes = null;
        if (synchronizeAll) {
            for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
                if (!source.getAttributes().hasAttribute(attribute) || !target.getAttributes().hasAttribute(attribute)) {
                    continue;
                }
                AttributeState sourceState = captureAttributeState(source, attribute);
                AttributeState targetState = captureAttributeState(target, attribute);
                if (sourceState == null || targetState == null || hasSameAttributeState(sourceState, targetState)) {
                    continue;
                }
                AttributeInstance targetAttribute = replaceAttribute(target, attribute, sourceState);
                if (changedAttributes == null) {
                    changedAttributes = new LinkedHashMap<Attribute, AttributeInstance>();
                }
                changedAttributes.put(attribute, targetAttribute);
            }
        } else {
            ArrayList<Attribute> dirtyAttributes = new ArrayList<Attribute>();
            for (AttributeInstance sourceAttribute : List.copyOf(source.getAttributes().getDirtyAttributes())) {
                if (sourceAttribute == null || sourceAttribute.getAttribute() == null || !target.getAttributes().hasAttribute(sourceAttribute.getAttribute())) {
                    continue;
                }
                dirtyAttributes.add(sourceAttribute.getAttribute());
            }
            for (Attribute attribute : dirtyAttributes) {
                AttributeState sourceState = captureAttributeState(source, attribute);
                AttributeState targetState = captureAttributeState(target, attribute);
                if (sourceState == null || targetState == null || hasSameAttributeState(sourceState, targetState)) {
                    continue;
                }
                AttributeInstance targetAttribute = replaceAttribute(target, attribute, sourceState);
                if (changedAttributes == null) {
                    changedAttributes = new LinkedHashMap();
                }
                changedAttributes.put(attribute, targetAttribute);
            }
        }
        if (changedAttributes != null && !(target instanceof EchoServerPlayer)) {
            target.connection.send(new ClientboundUpdateAttributesPacket(target.getId(), List.copyOf(changedAttributes.values())));
        }
    }

    static boolean hasSameAttributeState(AttributeState firstState, AttributeState secondState) {
        if (Double.compare(firstState.baseValue(), secondState.baseValue()) != 0) {
            return false;
        }
        if (firstState.modifiers().size() != secondState.modifiers().size()) {
            return false;
        }
        if (!firstState.permanentModifierIds().equals(secondState.permanentModifierIds())) {
            return false;
        }
        for (AttributeModifier firstModifier : firstState.modifiers()) {
            AttributeModifier secondModifier = secondState.modifiersById().get(firstModifier.getId());
            if (secondModifier != null && Double.compare(firstModifier.getAmount(), secondModifier.getAmount()) == 0 && firstModifier.getOperation() == secondModifier.getOperation()) {
                continue;
            }
            return false;
        }
        return true;
    }

    static AttributeInstance replaceAttribute(ServerPlayer target, Attribute attribute, AttributeState sourceState) {
        return rebuildAttributeInstance(target, attribute, sourceState);
    }

    static AttributeState captureAttributeState(ServerPlayer owner, Attribute attribute) {
        AttributeInstance attributeInstance = owner.getAttribute(attribute);
        if (attributeInstance == null) {
            return null;
        }
        double baseValue = attributeInstance.getBaseValue();
        LinkedHashMap<UUID, AttributeModifier> modifiersById = new LinkedHashMap<UUID, AttributeModifier>();
        HashSet<UUID> permanentModifierIds = new HashSet<UUID>();
        try {
            for (AttributeModifier modifier : attributeInstance.getModifiers()) {
                if (!isCopiedAttributeModifier(attribute, modifier)) {
                    continue;
                }
                modifiersById.putIfAbsent(modifier.getId(), modifier);
            }
        } catch (RuntimeException exception) {
            Constants.LOG.error("Rebuilding corrupted attribute state for {} on {}", new Object[]{BuiltInRegistries.ATTRIBUTE.getKey(attribute), owner.getGameProfile().getName(), exception});
            rebuildAttributeInstance(owner, attribute, new AttributeState(baseValue, List.of(), Map.of(), Set.of()));
            return new AttributeState(baseValue, List.of(), Map.of(), Set.of());
        }
        try {
            for (AttributeModifier modifier : ((AttributeInstanceAccessor)((Object)attributeInstance)).echoplayer$getPermanentModifiers()) {
                if (!isCopiedAttributeModifier(attribute, modifier) || !modifiersById.containsKey(modifier.getId())) {
                    continue;
                }
                permanentModifierIds.add(modifier.getId());
            }
        } catch (RuntimeException exception) {
            Constants.LOG.error("Could not read permanent modifiers for {} on {}", new Object[]{BuiltInRegistries.ATTRIBUTE.getKey(attribute), owner.getGameProfile().getName(), exception});
        }
        return new AttributeState(baseValue, List.copyOf(modifiersById.values()), Map.copyOf(modifiersById), Set.copyOf(permanentModifierIds));
    }

    static AttributeInstance rebuildAttributeInstance(ServerPlayer owner, Attribute attribute, AttributeState sourceState) {
        AttributeMap attributeMap = owner.getAttributes();
        AttributeMapAccessor mapAccessor = (AttributeMapAccessor)((Object)attributeMap);
        AttributeInstance previous = attributeMap.getInstance(attribute);
        AttributeInstance replacement = new AttributeInstance(attribute, mapAccessor::echoplayer$onAttributeModified);
        mapAccessor.echoplayer$getAttributes().put(attribute, replacement);
        if (previous != null) {
            mapAccessor.echoplayer$getDirtyAttributes().remove(previous);
        }
        replacement.setBaseValue(sourceState.baseValue());
        for (AttributeModifier modifier : sourceState.modifiers()) {
            AttributeModifier clone = new AttributeModifier(modifier.getId(), modifier.getName(), modifier.getAmount(), modifier.getOperation());
            if (sourceState.permanentModifierIds().contains(modifier.getId())) {
                replacement.addPermanentModifier(clone);
                continue;
            }
            replacement.addTransientModifier(clone);
        }
        ((AttributeInstanceAccessor)((Object)replacement)).echoplayer$setDirty();
        return replacement;
    }

    static boolean isCopiedAttributeModifier(Attribute attribute, AttributeModifier modifier) {
        return modifier != null && modifier.getId() != null && (attribute != Attributes.MOVEMENT_SPEED || !MOVEMENT_SPEED_STATE_MODIFIER_IDS.contains(modifier.getId()));
    }

    static boolean repairSprintingAttribute(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return false;
        }
        try {
            attribute.getModifier(SPRINTING_SPEED_MODIFIER_ID);
            attribute.getModifiers();
            return false;
        } catch (RuntimeException exception) {
            Constants.LOG.error("Rebuilding corrupted movement speed attribute for {}", (Object)player.getGameProfile().getName(), (Object)exception);
            rebuildAttributeInstance(player, Attributes.MOVEMENT_SPEED, new AttributeState(attribute.getBaseValue(), List.of(), Map.of(), Set.of()));
            return true;
        }
    }

    static void copySprintingState(ServerPlayer source, ServerPlayer target) {
        AttributeInstance attribute = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        boolean rebuilt = repairSprintingAttribute(target);
        boolean sprinting = source.isSprinting();
        boolean modifierPresent = false;
        AttributeInstance currentAttribute = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (currentAttribute != null) {
            try {
                modifierPresent = currentAttribute.getModifier(SPRINTING_SPEED_MODIFIER_ID) != null;
            } catch (RuntimeException exception) {
                Constants.LOG.error("Rebuilding corrupted movement speed attribute for {}", (Object)target.getGameProfile().getName(), (Object)exception);
                rebuildAttributeInstance(target, Attributes.MOVEMENT_SPEED, new AttributeState(currentAttribute.getBaseValue(), List.of(), Map.of(), Set.of()));
                rebuilt = true;
            }
        }
        if (rebuilt || target.isSprinting() != sprinting || modifierPresent != sprinting) {
            target.setSprinting(sprinting);
        }
    }

    static void copyAbilities(ServerPlayer source, ServerPlayer target) {
        CompoundTag abilities = new CompoundTag();
        source.getAbilities().addSaveData(abilities);
        target.getAbilities().loadSaveData(abilities);
    }

    static boolean copyAbilitiesIfDifferent(ServerPlayer source, ServerPlayer target) {
        Abilities sourceAbilities = source.getAbilities();
        Abilities targetAbilities = target.getAbilities();
        boolean changed = sourceAbilities.invulnerable != targetAbilities.invulnerable
            || sourceAbilities.flying != targetAbilities.flying
            || sourceAbilities.mayfly != targetAbilities.mayfly
            || sourceAbilities.instabuild != targetAbilities.instabuild
            || sourceAbilities.mayBuild != targetAbilities.mayBuild
            || Float.compare(sourceAbilities.getFlyingSpeed(), targetAbilities.getFlyingSpeed()) != 0
            || Float.compare(sourceAbilities.getWalkingSpeed(), targetAbilities.getWalkingSpeed()) != 0;
        if (changed) {
            copyAbilities(source, target);
        }
        return changed;
    }

    static void synchronizeUsingItem(ServerPlayer source, ServerPlayer target) {
        if (source.isUsingItem()) {
            if (!target.isUsingItem() || target.getUsedItemHand() != source.getUsedItemHand()) {
                target.stopUsingItem();
                target.startUsingItem(source.getUsedItemHand());
            }
        } else if (target.isUsingItem()) {
            target.stopUsingItem();
        }
    }

    static void updateEchoEquipment(EchoServerPlayer echoPlayer) {
        ((LivingEntityInvoker)((Object)echoPlayer)).echoplayer$detectEquipmentUpdates();
    }

    static void setGameModeIfNeeded(ServerPlayer player, GameType gameType) {
        if (player.gameMode.getGameModeForPlayer() != gameType) {
            player.setGameMode(gameType);
        }
    }

    static void syncRealPlayerPackets(ServerPlayer realPlayer) {
        realPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, realPlayer.gameMode.getGameModeForPlayer().getId()));
        realPlayer.connection.send(new ClientboundSetHealthPacket(realPlayer.getHealth(), realPlayer.getFoodData().getFoodLevel(), realPlayer.getFoodData().getSaturationLevel()));
        realPlayer.connection.send(new ClientboundSetExperiencePacket(realPlayer.experienceProgress, realPlayer.totalExperience, realPlayer.experienceLevel));
        realPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(realPlayer.getAbilities()));
    }

    static void hideControllerBody(ServerPlayer realPlayer) {
        if (!realPlayer.isSilent()) {
            realPlayer.setSilent(true);
        }
    }

    static void syncConnectionPosition(ServerPlayer realPlayer, double x, double y, double z) {
        ServerGamePacketListenerImplAccessor accessor = (ServerGamePacketListenerImplAccessor)((Object)realPlayer.connection);
        accessor.echoplayer$setFirstGoodX(x);
        accessor.echoplayer$setFirstGoodY(y);
        accessor.echoplayer$setFirstGoodZ(z);
        accessor.echoplayer$setLastGoodX(x);
        accessor.echoplayer$setLastGoodY(y);
        accessor.echoplayer$setLastGoodZ(z);
    }

    static void transferSleepingState(ServerPlayer from, ServerPlayer to) {
        Optional<BlockPos> sleepingPos = from.getSleepingPos();
        if (sleepingPos.isEmpty()) {
            return;
        }
        BlockPos bedPos = sleepingPos.get();
        int sleepTimer = ((PlayerAccessor)((Object)from)).echoplayer$getSleepCounter();
        from.clearSleepingPos();
        from.setPose(Pose.STANDING);
        to.absMoveTo(from.getX(), from.getY(), from.getZ(), from.getYRot(), from.getXRot());
        to.setPose(Pose.SLEEPING);
        to.setSleepingPos(bedPos);
        ((PlayerAccessor)((Object)to)).echoplayer$setSleepCounter(sleepTimer);
        to.setDeltaMovement(from.getDeltaMovement());
        var bedState = to.level().getBlockState(bedPos);
        if (bedState.isBed(to.level(), bedPos, to)) {
            bedState.setBedOccupied(to.level(), bedPos, to, true);
        }
    }

    static void applyGhostBlockFix(ServerPlayer realPlayer, ServerLevel level, BlockPos posUnder) {
        realPlayer.connection.send(new ClientboundBlockUpdatePacket(posUnder, Blocks.BARRIER.defaultBlockState()));
        realPlayer.server.tell(new TickTask(realPlayer.server.getTickCount() + 40, () -> {
            if (!realPlayer.hasDisconnected() && realPlayer.level() == level) {
                realPlayer.connection.send(new ClientboundBlockUpdatePacket(level, posUnder));
            }
        }));
    }

    static void syncExperienceState(ServerPlayer realPlayer, EchoServerPlayer echoPlayer) {
        if (echoPlayer.experienceLevel != realPlayer.experienceLevel) {
            echoPlayer.experienceLevel = realPlayer.experienceLevel;
        }
        if (Float.compare(echoPlayer.experienceProgress, realPlayer.experienceProgress) != 0) {
            echoPlayer.experienceProgress = realPlayer.experienceProgress;
        }
        if (echoPlayer.totalExperience != realPlayer.totalExperience) {
            echoPlayer.totalExperience = realPlayer.totalExperience;
        }
    }

    record AttributeState(double baseValue, List<AttributeModifier> modifiers, Map<UUID, AttributeModifier> modifiersById, Set<UUID> permanentModifierIds) {
    }
}
