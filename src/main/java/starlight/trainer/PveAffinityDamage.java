package starlight.trainer;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Set;

/** Damage bonuses only when a party Pokémon and a Minecraft mob fight in the world. */
public final class PveAffinityDamage {
    private PveAffinityDamage() {}

    /**
     * Full immunities cancel before vanilla plays the hurt flash, sound and knockback;
     * zeroing the damage later would still flash every burning tick.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (event.getEntity() instanceof ServerPlayer player) {
            if (TrainerState.isReturnProtected(player)
                    && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
                return;
            }
            Set<String> active = TrainerState.active(player);
            String link = ResonanceLinkEffects.current(player);
            if ((active.contains("fire") && source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.LAVA))
                    || (active.contains("ice") && source.is(DamageTypes.FREEZE))
                    || ((link.equals("fire_rock") || link.equals("fire_rock_steel")) && source.is(DamageTypes.HOT_FLOOR))
                    || ((link.equals("air_electric") || link.equals("air_electric_flying")) && source.is(DamageTypes.LIGHTNING_BOLT))
                    || ((link.equals("air_flying") || link.equals("air_electric_flying")
                    || link.equals("dark_ghost_psychic")) && source.is(DamageTypeTags.IS_FALL))) {
                event.setCanceled(true);
            }
        } else if (event.getEntity() instanceof PokemonEntity target
                && source.is(DamageTypes.FREEZE)
                && target.getBattleId() == null
                && !target.getPokemon().isFainted()) {
            ServerPlayer owner = target.getPokemon().getOwnerPlayer();
            if (owner != null && AffinityBalance.pokemonFreezeImmune(
                    TrainerState.active(owner), TrainerState.types(target.getPokemon()))) {
                event.setCanceled(true);
            }
        } else if (event.getEntity() instanceof PokemonEntity target
                && source.getEntity() == null && source.is(DamageTypeTags.IS_FIRE)
                && target.getBattleId() == null && !target.getPokemon().isFainted()) {
            ServerPlayer owner = target.getPokemon().getOwnerPlayer();
            if (owner != null && TrainerState.activeLinks(owner, TrainerState.active(owner))
                    .contains("dragon_fire_flying")
                    && TrainerState.types(target.getPokemon()).stream()
                    .anyMatch(Set.of("dragon", "fire", "flying")::contains)) {
                event.setCanceled(true);
            }
        }
    }

    public static void onDamage(LivingDamageEvent.Pre event) {
        var source = event.getSource();
        if (event.getEntity() instanceof ServerPlayer player) {
            double multiplier = AffinityBalance.playerDamageMultiplier(TrainerState.active(player),
                    source.is(DamageTypeTags.IS_FALL), source.is(DamageTypes.LAVA));
            multiplier *= ResonanceLinkEffects.playerDamageMultiplier(player, source);
            if (multiplier < 1) event.setNewDamage((float) (event.getNewDamage() * multiplier));
            return;
        }
        if (source.getEntity() instanceof ServerPlayer player
                && event.getEntity() instanceof Mob mob
                && !(mob instanceof PokemonEntity)) {
            boolean melee = source.getDirectEntity() == player;
            double bonus = ResonanceLinkEffects.playerAttackBonus(player, mob, melee);
            if (bonus > 0) event.setNewDamage((float) (event.getNewDamage() * (1 + bonus)));
            return;
        }
        if (event.getEntity() instanceof PokemonEntity environmentTarget
                && source.getEntity() == null
                && environmentTarget.getBattleId() == null
                && !environmentTarget.getPokemon().isFainted()) {
            ServerPlayer owner = environmentTarget.getPokemon().getOwnerPlayer();
            if (owner != null && source.is(DamageTypeTags.IS_FIRE)) {
                var types = TrainerState.types(environmentTarget.getPokemon());
                var active = TrainerState.active(owner);
                double factor = AffinityBalance.pokemonFireEnvironmentHalved(active, types)
                        ? AffinityBalance.POKEMON_FIRE_ENVIRONMENT : 1;
                var links = TrainerState.activeLinks(owner, active);
                if (types.stream().anyMatch(Set.of("fire", "rock")::contains) && links.contains("fire_rock")) {
                    factor = Math.min(factor, .25);
                }
                if (factor < 1) {
                    event.setNewDamage(event.getNewDamage() * (float) factor);
                    return;
                }
            }
        }
        if (source.getEntity() instanceof PokemonEntity attacker
                && event.getEntity() instanceof Mob target
                && !(event.getEntity() instanceof PokemonEntity)) {
            if (attacker.getBattleId() != null || attacker.getPokemon().isFainted()) return;
            ServerPlayer owner = attacker.getPokemon().getOwnerPlayer();
            if (owner == null) return;
            // Fight or Flight 0.11.0 uses mob_attack for melee (physical) moves and
            // indirect_magic for special moves, projectiles and area attacks.
            boolean physical = source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
            double bonus = AffinityBalance.pokemonDamageBonusAgainstMob(TrainerState.active(owner),
                    TrainerState.types(attacker.getPokemon()), attacker.isInWater(),
                    attacker.level().isNight(), physical, target.getType().is(EntityTypeTags.UNDEAD));
            var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(owner, TrainerState.active(owner)),
                    TrainerState.types(attacker.getPokemon()), attacker.level().isNight(), physical,
                    target.getType().is(EntityTypeTags.UNDEAD));
            bonus = ResonanceLinkBalance.cappedDamage(bonus, link);
            if (bonus > 0) event.setNewDamage((float) (event.getNewDamage() * (1 + bonus)));
        } else if (event.getEntity() instanceof PokemonEntity defender
                && source.getEntity() instanceof Mob
                && !(source.getEntity() instanceof PokemonEntity)) {
            if (defender.getBattleId() != null || defender.getPokemon().isFainted()) return;
            ServerPlayer owner = defender.getPokemon().getOwnerPlayer();
            if (owner == null) return;
            double reduction = AffinityBalance.pokemonDamageReduction(TrainerState.active(owner),
                    TrainerState.types(defender.getPokemon()));
            var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(owner, TrainerState.active(owner)),
                    TrainerState.types(defender.getPokemon()), defender.level().isNight(), false, false);
            reduction = ResonanceLinkBalance.cappedReduction(reduction, link);
            if (reduction > 0) event.setNewDamage((float) (event.getNewDamage() * (1 - reduction)));
        }
    }
}
