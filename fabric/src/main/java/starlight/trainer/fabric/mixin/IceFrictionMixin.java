package starlight.trainer.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import starlight.trainer.ResonanceLinkEffects;

/**
 * Per-player friction only: other entities and the ice block's state are unchanged. The NeoForge
 * edition hooks NeoForge's state-aware {@code getFriction}; vanilla asks the block itself.
 */
@Mixin(LivingEntity.class)
public abstract class IceFrictionMixin {
    @ModifyExpressionValue(method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float superPalletTowner$iceFriction(float original) {
        if (!((Object) this instanceof Player player) || !player.onGround()) return original;
        String recipe = ResonanceLinkEffects.current(player);
        if (!recipe.equals("ice_water") && !recipe.equals("ice_rock_ground")) return original;
        BlockPos below = player.getBlockPosBelowThatAffectsMyMovement();
        BlockState state = player.level().getBlockState(below);
        return state.is(BlockTags.ICE) ? .6F : original;
    }
}
