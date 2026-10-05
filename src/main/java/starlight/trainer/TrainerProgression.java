package starlight.trainer;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Baseline capture bonus plus the Psychic return bonuses. */
public final class TrainerProgression {
    private static final FractionalCarry PLAYER_XP = new FractionalCarry();
    private static final FractionalCarry POKEMON_EXP = new FractionalCarry();

    private TrainerProgression() {}

    public static void onPlayerXp(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0
                || !TrainerState.active(player).contains("psychic")) return;
        event.setAmount(PLAYER_XP.scale(player.getUUID(), event.getAmount(), AffinityBalance.PSYCHIC_PLAYER_XP));
    }

    public static void setup(FMLCommonSetupEvent event) {
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
