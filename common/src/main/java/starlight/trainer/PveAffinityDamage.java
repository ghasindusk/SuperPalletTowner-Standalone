package starlight.trainer;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/** Damage bonuses only when a party Pokémon and a Minecraft mob fight in the world. */
public final class PveAffinityDamage {
    private PveAffinityDamage() {}

    /**
     * Full immunities cancel before vanilla plays the hurt flash, sound and knockback;
     * zeroing the damage later would still flash every burning tick.
     */
    public static boolean cancelsIncoming(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer player) {
            if (TrainerState.isReturnProtected(player)
                    && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return true;
            }
            Set<String> active = TrainerState.active(player);
            String link = ResonanceLinkEffects.current(player);
            if ((active.contains("fire") && source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.LAVA))
                    || (active.contains("ice") && source.is(DamageTypes.FREEZE))
                    || ((link.equals("fire_rock") || link.equals("fire_rock_steel")) && source.is(DamageTypes.HOT_FLOOR))
                    || ((link.equals("air_electric") || link.equals("air_electric_flying")) && source.is(DamageTypes.LIGHTNING_BOLT))
                    || ((link.equals("air_flying") || link.equals("air_electric_flying")
                    || link.equals("dark_ghost_psychic")) && source.is(DamageTypeTags.IS_FALL))) {
                return true;
            }
        } else if (entity instanceof PokemonEntity target
                && source.is(DamageTypes.FREEZE)
                && target.getBattleId() == null
                && !target.getPokemon().isFainted()) {
            ServerPlayer owner = target.getPokemon().getOwnerPlayer();
            if (owner != null && AffinityBalance.pokemonFreezeImmune(
                    TrainerState.active(owner), TrainerState.types(target.getPokemon()))) {
                return true;
            }
        } else if (entity instanceof PokemonEntity target
                && source.getEntity() == null && source.is(DamageTypeTags.IS_FIRE)
                && target.getBattleId() == null && !target.getPokemon().isFainted()) {
            ServerPlayer owner = target.getPokemon().getOwnerPlayer();
            if (owner != null && TrainerState.activeLinks(owner, TrainerState.active(owner))
                    .contains("dragon_fire_flying")
                    && TrainerState.types(target.getPokemon()).stream()
                    .anyMatch(Set.of("dragon", "fire", "flying")::contains)) {
                return true;
            }
        }
        return false;
    }

    /** The damage that reaches health (after armor and enchantments), scaled by the resonance effects. */
    public static float scaleDamage(LivingEntity entity, DamageSource source, float damage) {
        if (entity instanceof ServerPlayer player) {
            double multiplier = AffinityBalance.playerDamageMultiplier(TrainerState.active(player),
                    source.is(DamageTypeTags.IS_FALL), source.is(DamageTypes.LAVA));
            multiplier *= ResonanceLinkEffects.playerDamageMultiplier(player, source);
            return multiplier < 1 ? (float) (damage * multiplier) : damage;
        }
        if (source.getEntity() instanceof ServerPlayer player
                && entity instanceof Mob mob
                && !(mob instanceof PokemonEntity)) {
            boolean melee = source.getDirectEntity() == player;
            double bonus = ResonanceLinkEffects.playerAttackBonus(player, mob, melee);
            return bonus > 0 ? (float) (damage * (1 + bonus)) : damage;
        }
        if (entity instanceof PokemonEntity environmentTarget
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
                if (factor < 1) return damage * (float) factor;
            }
        }
        if (source.getEntity() instanceof PokemonEntity attacker
                && entity instanceof Mob target
                && !(entity instanceof PokemonEntity)) {
            if (attacker.getBattleId() != null || attacker.getPokemon().isFainted()) return damage;
            ServerPlayer owner = attacker.getPokemon().getOwnerPlayer();
            if (owner == null) return damage;
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
            if (bonus > 0) return (float) (damage * (1 + bonus));
        } else if (entity instanceof PokemonEntity defender
                && source.getEntity() instanceof Mob
                && !(source.getEntity() instanceof PokemonEntity)) {
            if (defender.getBattleId() != null || defender.getPokemon().isFainted()) return damage;
            ServerPlayer owner = defender.getPokemon().getOwnerPlayer();
            if (owner == null) return damage;
            double reduction = AffinityBalance.pokemonDamageReduction(TrainerState.active(owner),
                    TrainerState.types(defender.getPokemon()));
            var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(owner, TrainerState.active(owner)),
                    TrainerState.types(defender.getPokemon()), defender.level().isNight(), false, false);
            reduction = ResonanceLinkBalance.cappedReduction(reduction, link);
            if (reduction > 0) return (float) (damage * (1 - reduction));
        }
        return damage;
    }
}
