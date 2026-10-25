package starlight.trainer;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Baseline capture bonus plus the Psychic return bonuses. */
public final class TrainerProgression {
    private static final FractionalCarry PLAYER_XP = new FractionalCarry();
    private static final FractionalCarry POKEMON_EXP = new FractionalCarry();

    private TrainerProgression() {}

    /** Experience points a player is about to receive. */
    public static int playerXp(Player entity, int amount) {
        if (!(entity instanceof ServerPlayer player)
                || amount <= 0
                || !TrainerState.active(player).contains("psychic")) return amount;
        return PLAYER_XP.scale(player.getUUID(), amount, AffinityBalance.PSYCHIC_PLAYER_XP);
    }

    /** Once, after Cobblemon is loaded. */
    public static void setup() {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(gain -> {
            ServerPlayer owner = gain.getPokemon().getOwnerPlayer();
            if (owner == null || !TrainerState.isTrainer(owner)) return;
            double bonus = AffinityBalance.pokemonExpBonus(TrainerState.active(owner),
                    TrainerState.types(gain.getPokemon()));
            if (bonus > 0) {
                gain.setExperience(POKEMON_EXP.scale(gain.getPokemon().getUuid(), gain.getExperience(), bonus));
            }
        });
        CobblemonEvents.POKEMON_CATCH_RATE.subscribe(catchEvent -> {
            if (!(catchEvent.getThrower() instanceof ServerPlayer player) || !TrainerState.isTrainer(player)) return;
            catchEvent.setCatchRate(Math.min(catchEvent.getCatchRate() * 1.03F, 255F));
        });
    }

    public static void forget(ServerPlayer player) {
        PLAYER_XP.forget(player.getUUID());
    }
}
