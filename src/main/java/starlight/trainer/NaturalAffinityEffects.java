package starlight.trainer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Food exhaustion and natural healing supplements for Grass and Normal. */
public final class NaturalAffinityEffects {
    private static final Map<UUID, GrassHunger.Sample> LAST_FOOD = new HashMap<>();

    private NaturalAffinityEffects() {}

    public static void tick(ServerPlayer player) {
        Set<String> active = TrainerState.active(player);
        var food = player.getFoodData();
        UUID id = player.getUUID();
        GrassHunger.Sample current = new GrassHunger.Sample(food.getExhaustionLevel(),
                food.getSaturationLevel(), food.getFoodLevel());
        GrassHunger.Sample previous = LAST_FOOD.get(id);
        String link = ResonanceLinkEffects.current(player);
        double hungerSaving = link.equals("water_grass") || link.equals("fairy_grass_water") ? .20
                : active.contains("grass") ? AffinityBalance.GRASS_EXHAUSTION_SAVING : 0;
        if (hungerSaving > 0 && previous != null) {
            GrassHunger.Sample adjusted = GrassHunger.adjust(previous, current, (float) (1 - hungerSaving));
            if (adjusted != current) {
                food.setExhaustion(Math.max(0, adjusted.exhaustion()));
                food.setSaturation(adjusted.saturation());
                food.setFoodLevel(adjusted.foodLevel());
            }
        }
        LAST_FOOD.put(id, new GrassHunger.Sample(food.getExhaustionLevel(),
                food.getSaturationLevel(), food.getFoodLevel()));

        double bonus = AffinityBalance.playerNaturalRegen(active);
        if (link.equals("fairy_grass_water")) bonus = Math.max(bonus, .30);
        else bonus += Math.min(StandardResonanceBalance.forId(link).player().recovery(), Math.max(0, .40 - bonus));
        if (bonus == 0 || player.getHealth() >= player.getMaxHealth()
                || !player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) return;

        // Match the two vanilla natural-regeneration conditions. The bonus adds
        // fractional health and does not trigger when natural regeneration is off.
        if (food.getFoodLevel() >= 20 && food.getSaturationLevel() > 0 && player.tickCount % 10 == 0) {
            player.heal((float) (Math.min(food.getSaturationLevel(), 6) / 6 * bonus));
        } else if (food.getFoodLevel() >= 18 && player.tickCount % 80 == 0) {
            player.heal((float) bonus);
        }
    }

    public static void forget(ServerPlayer player) {
        LAST_FOOD.remove(player.getUUID());
    }
}
