package starlight.trainer.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starlight.trainer.ResonanceWorldEffects;

import java.util.ArrayList;
import java.util.List;

/** NeoForge's {@code BlockDropsEvent}: smelted ores and extra crops for the player who broke the block. */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void superPalletTowner$drops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity,
                                                Entity breaker, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        var plan = ResonanceWorldEffects.dropPlan(breaker, level, state);
        if (plan == null) return;
        List<ItemStack> drops = new ArrayList<>(cir.getReturnValue());
        ResonanceWorldEffects.applyToStacks(plan, drops);
        cir.setReturnValue(drops);
    }
}
