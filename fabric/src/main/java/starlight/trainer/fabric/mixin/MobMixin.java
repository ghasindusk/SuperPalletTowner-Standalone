package starlight.trainer.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import starlight.trainer.PlayerAffinityEvents;

/** NeoForge's {@code LivingChangeTargetEvent}: Ghost trainers are noticed from closer only. */
@Mixin(Mob.class)
public abstract class MobMixin {
    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private LivingEntity superPalletTowner$target(LivingEntity target) {
        Mob self = (Mob) (Object) this;
        if (target == null || self.level().isClientSide()) return target;
        return PlayerAffinityEvents.target(self, target);
    }
}
