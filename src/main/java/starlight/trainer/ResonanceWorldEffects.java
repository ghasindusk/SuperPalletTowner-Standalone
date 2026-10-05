package starlight.trainer;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.VanillaGameEvent;

import java.util.ArrayList;

/** Block-world effects. All drop changes are made in the event's original mutable drop list. */
public final class ResonanceWorldEffects {
    private ResonanceWorldEffects() {}

    /** Silence only player-caused vanilla vibration events while an Abyss link is active.
     * This is the server's game-event channel used by sculk sensors and Wardens. */
    public static void onVibration(VanillaGameEvent event) {
        if (!(event.getCause() instanceof ServerPlayer player)
                || !event.getVanillaEvent().is(GameEventTags.VIBRATIONS)) return;
        String recipe = ResonanceLinkEffects.current(player);
        if (recipe.equals("dark_ghost") || recipe.equals("dark_ghost_psychic")) event.setCanceled(true);
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        String recipe = ResonanceLinkEffects.current(player);
        boolean smelt = recipe.equals("fire_steel") || recipe.equals("fire_rock_steel");
        boolean crops = recipe.equals("bug_grass") && event.getState().getBlock() instanceof CropBlock crop
                && crop.isMaxAge(event.getState());
        if (!smelt && !crops) return;

        // Iterate a snapshot so extra crop drops cannot be processed again by this listener.
        for (ItemEntity drop : new ArrayList<>(event.getDrops())) {
            ItemStack stack = drop.getItem();
            if (smelt && event.getState().is(Tags.Blocks.ORES) && stack.is(Tags.Items.RAW_MATERIALS)
                    && (recipe.equals("fire_rock_steel") || player.getRandom().nextBoolean())) {
                var match = event.getLevel().getRecipeManager().getRecipeFor(
                        RecipeType.SMELTING, new SingleRecipeInput(stack), event.getLevel());
                if (match.isPresent()) {
                    ItemStack result = match.get().value().getResultItem(event.getLevel().registryAccess());
                    if (!result.isEmpty() && result.getCount() == 1) {
                        drop.setItem(result.copyWithCount(stack.getCount()));
                    }
                }
            }
            if (crops && stack.is(Tags.Items.CROPS)) {
                int extra = 0;
                for (int i = 0; i < stack.getCount(); i++) if (player.getRandom().nextFloat() < .25F) extra++;
                if (extra > 0) event.getDrops().add(new ItemEntity(event.getLevel(), drop.getX(), drop.getY(),
                        drop.getZ(), stack.copyWithCount(extra)));
            }
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
                BlockSnapshot old = BlockSnapshot.create(level.dimension(), level, pos);
                if (EventHooks.onBlockPlace(player, old, Direction.UP)) continue;
                if (level.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 3)) {
                    level.scheduleTick(pos, Blocks.FROSTED_ICE, 40);
                }
            }
        }
    }
}
