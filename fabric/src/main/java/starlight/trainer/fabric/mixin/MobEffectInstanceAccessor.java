package starlight.trainer.fabric.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Writes the remaining duration; the field name is remapped for Fabric's runtime names, unlike reflection. */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
    @Accessor("duration")
    void superPalletTowner$setDuration(int duration);
}
