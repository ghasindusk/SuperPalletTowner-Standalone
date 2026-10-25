package starlight.trainer.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starlight.trainer.FabricSwim;
import starlight.trainer.PlayerAffinityEvents;
import starlight.trainer.PveAffinityDamage;

/**
 * Living-entity hooks NeoForge provides as events. All of them act on the server only, like the
 * NeoForge events they stand in for; the swim stroke is the one client-side hook.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    private LivingEntity superPalletTowner$self() {
        return (LivingEntity) (Object) this;
    }

    /**
     * NeoForge's {@code LivingDamageEvent.Pre}: the damage after armor and enchantments. Both
     * {@code LivingEntity.actuallyHurt} and {@code Player.actuallyHurt} call this just before absorption.
     */
    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
    private void superPalletTowner$damage(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = superPalletTowner$self();
        if (self.level().isClientSide()) return;
        cir.setReturnValue(PveAffinityDamage.scaleDamage(self, source, cir.getReturnValueF()));
    }

    /** NeoForge's {@code LivingKnockBackEvent}. */
    @ModifyVariable(method = "knockback(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double superPalletTowner$knockback(double strength) {
        LivingEntity self = superPalletTowner$self();
        if (self.level().isClientSide()) return strength;
        return PlayerAffinityEvents.knockback(self, (float) strength);
    }

    /** NeoForge's {@code LivingHealEvent}. */
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float superPalletTowner$heal(float amount) {
        LivingEntity self = superPalletTowner$self();
        if (self.level().isClientSide()) return amount;
        return PlayerAffinityEvents.heal(self, amount);
    }

    /** NeoForge's {@code MobEffectEvent.Applicable}. */
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void superPalletTowner$applicable(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = superPalletTowner$self();
        if (!self.level().isClientSide() && PlayerAffinityEvents.blocksEffect(self, effect)) cir.setReturnValue(false);
    }

    /** NeoForge's {@code MobEffectEvent.Added}: after {@code canBeAffected}, before the effect is stored. */
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void superPalletTowner$effectAdded(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = superPalletTowner$self();
        if (!self.level().isClientSide()) PlayerAffinityEvents.effectAdded(self, effect);
    }

    /** NeoForge's {@code ArmorHurtEvent}: the durability one armor piece loses from a hit. */
    @WrapOperation(method = "doHurtEquipment", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void superPalletTowner$armorDamage(ItemStack armor, int amount, LivingEntity entity, EquipmentSlot slot,
                                                Operation<Void> original) {
        int changed = entity.level().isClientSide() ? amount
                : (int) PlayerAffinityEvents.armorDamage(entity, slot, armor, amount);
        original.call(armor, changed, entity, slot);
    }

    /** NeoForge's swim-speed attribute: the stroke in water (the first {@code moveRelative} in {@code travel}). */
    @ModifyArg(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V", ordinal = 0),
            index = 0)
    private float superPalletTowner$swim(float speed) {
        return FabricSwim.stroke(superPalletTowner$self(), speed);
    }
}
