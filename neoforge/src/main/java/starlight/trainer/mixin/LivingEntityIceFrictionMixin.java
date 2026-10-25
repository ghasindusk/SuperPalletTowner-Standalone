package starlight.trainer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import starlight.trainer.ResonanceLinkEffects;

/** Per-player friction only: other entities and the ice block's state are unchanged. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityIceFrictionMixin {
    @ModifyExpressionValue(method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"))
    private float superPalletTowner$iceFriction(float original) {
        if (!((Object) this instanceof Player player) || !player.onGround()) return original;
        String recipe = ResonanceLinkEffects.current(player);
        if (!recipe.equals("ice_water") && !recipe.equals("ice_rock_ground")) return original;
        BlockPos below = player.getBlockPosBelowThatAffectsMyMovement();
        BlockState state = player.level().getBlockState(below);
        return state.is(BlockTags.ICE) ? .6F : original;
    }
}
