package starlight.trainer;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/** Block-world effects. Drop changes are made in the loader's own mutable drop list. */
public final class ResonanceWorldEffects {
    private ResonanceWorldEffects() {}

    /** Silence only player-caused vanilla vibration events while an Abyss link is active.
     * This is the server's game-event channel used by sculk sensors and Wardens. */
    public static boolean cancelsVibration(Entity cause, Holder<GameEvent> gameEvent) {
        if (!(cause instanceof ServerPlayer player) || !gameEvent.is(GameEventTags.VIBRATIONS)) return false;
        String recipe = ResonanceLinkEffects.current(player);
        return recipe.equals("dark_ghost") || recipe.equals("dark_ghost_psychic");
    }

    /** What a broken block's drops may become for this player, or {@code null} for nothing. */
    public record DropPlan(ServerPlayer player, ServerLevel level, BlockState state, String recipe,
                           boolean smelt, boolean crops) {}

    public static DropPlan dropPlan(Entity breaker, ServerLevel level, BlockState state) {
        if (!(breaker instanceof ServerPlayer player)) return null;
        String recipe = ResonanceLinkEffects.current(player);
        boolean smelt = recipe.equals("fire_steel") || recipe.equals("fire_rock_steel");
        boolean crops = recipe.equals("bug_grass") && state.getBlock() instanceof CropBlock crop
                && crop.isMaxAge(state);
        return smelt || crops ? new DropPlan(player, level, state, recipe, smelt, crops) : null;
    }

    /** The smelted replacement for one drop, or {@code null} to keep it. */
    public static ItemStack smelted(DropPlan plan, ItemStack stack) {
        if (!plan.smelt() || !plan.state().is(TrainerTags.ORES) || !stack.is(TrainerTags.RAW_MATERIALS)
                || !(plan.recipe().equals("fire_rock_steel") || plan.player().getRandom().nextBoolean())) return null;
        var match = plan.level().getRecipeManager().getRecipeFor(
                RecipeType.SMELTING, new SingleRecipeInput(stack), plan.level());
        if (match.isEmpty()) return null;
        ItemStack result = match.get().value().getResultItem(plan.level().registryAccess());
        return !result.isEmpty() && result.getCount() == 1 ? result.copyWithCount(stack.getCount()) : null;
    }

    /** How many extra crops to drop next to this drop (judged on the original stack). */
    public static int extraCrops(DropPlan plan, ItemStack stack) {
        if (!plan.crops() || !stack.is(TrainerTags.CROPS)) return 0;
        int extra = 0;
        for (int i = 0; i < stack.getCount(); i++) if (plan.player().getRandom().nextFloat() < .25F) extra++;
        return extra;
    }

    /** Applies the plan to a list of stacks (Fabric); extra crops are appended. */
    public static void applyToStacks(DropPlan plan, List<ItemStack> drops) {
        int size = drops.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = drops.get(i);
            ItemStack replacement = smelted(plan, stack);
            if (replacement != null) drops.set(i, replacement);
            int extra = extraCrops(plan, stack);
            if (extra > 0) drops.add(stack.copyWithCount(extra));
        }
    }

    /** A daylight-only Frost Walker-like path. Placement is offered to claim-protection hooks. */
    public static void tick(ServerPlayer player) {
        if (!ResonanceLinkEffects.current(player).equals("ice_water") || player.tickCount % 4 != 0
                || player.isInWater() || !(player.level() instanceof ServerLevel level)
                || !level.dimensionType().hasSkyLight()) return;
        BlockPos center = player.blockPosition().below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                var fluid = level.getFluidState(pos);
                if (!fluid.is(FluidTags.WATER) || !fluid.isSource()
                        || !level.getBlockState(pos.above()).isAir()
                        || !level.canSeeSky(pos.above()) || level.getMaxLocalRawBrightness(pos) < 12) continue;
                if (!TrainerPlatform.get().mayPlaceBlock(player, level, pos)) continue;
                if (level.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 3)) {
                    level.scheduleTick(pos, Blocks.FROSTED_ICE, 40);
                }
            }
        }
    }
}
