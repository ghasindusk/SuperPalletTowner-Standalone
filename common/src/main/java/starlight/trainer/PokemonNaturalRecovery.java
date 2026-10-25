package starlight.trainer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Supplements Cobblemon's own timed party healing after its timer resets. */
public final class PokemonNaturalRecovery {
    private record Sample(int timer, int health, double fractionalBonus) {}
    private static final Map<UUID, Map<UUID, Sample>> LAST = new HashMap<>();

    private PokemonNaturalRecovery() {}

    public static void tick(ServerPlayer player) {
        if (!TrainerState.isTrainer(player)) {
            LAST.remove(player.getUUID());
            return;
        }
        Set<String> active = TrainerState.active(player);
        Map<UUID, Sample> samples = LAST.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        Set<UUID> partyIds = new HashSet<>();
        int resetTimer = Cobblemon.INSTANCE.getConfig().getHealTimer();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon == null) continue;
            UUID id = pokemon.getUuid();
            partyIds.add(id);
            if (pokemon.getEntity() != null && pokemon.getEntity().getBattleId() != null) {
                samples.remove(id);
                continue;
            }
            int timer = pokemon.getHealTimer();
            int health = pokemon.getCurrentHealth();
            Sample previous = samples.get(id);
            double remainder = previous == null ? 0 : previous.fractionalBonus();
            var types = TrainerState.types(pokemon);
            var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(player, active), types,
                    player.level().isNight(), false, false);
            double bonus = ResonanceLinkBalance.cappedRecovery(
                    AffinityBalance.pokemonRecoveryBonus(active, types), link);
            if (previous != null && bonus > 0 && !pokemon.isFainted()
                    && resetTimer > 0 && timer == resetTimer && previous.timer() <= 1
                    && health > previous.health() && health < pokemon.getMaxHealth()) {
                double earned = (health - previous.health()) * bonus + remainder;
                int extra = (int) Math.floor(earned + 1e-9);
                if (extra > 0) {
                    pokemon.setCurrentHealth(Math.min(pokemon.getMaxHealth(), health + extra));
                    health = pokemon.getCurrentHealth();
                }
                remainder = earned - extra;
            }
            if (bonus == 0 || health >= pokemon.getMaxHealth()) remainder = 0;
            samples.put(id, new Sample(timer, health, remainder));
        }
        samples.keySet().retainAll(partyIds);
    }

    public static void forget(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }
}
