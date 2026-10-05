package starlight.trainer;

import java.util.Collection;
import java.util.Set;

/**
 * Super Pallet Towner Balance v1 numbers for the 18 standard types, plus the
 * concrete Cobblemania type numbers supplied in Resonance v4. Kept free of
 * Minecraft classes so the table and the per-Pokémon caps can be unit tested.
 * Type names are canonical lowercase. Other proposed types have no effects.
 */
public final class AffinityBalance {
    public static final double POKEMON_DAMAGE_CAP = 0.15;
    public static final double POKEMON_REDUCTION_CAP = 0.15;
    public static final double POKEMON_RECOVERY_CAP = 0.25;

    public static final double ROCK_MINING = 0.20;
    public static final double ROCK_ORE_MINING = 0.10;
    public static final double NORMAL_MINING = 0.05;
    public static final double PSYCHIC_PLAYER_XP = 0.10;
    public static final double PSYCHIC_POKEMON_EXP = 0.07;
    public static final double FAIRY_RECEIVED_HEALING = 0.15;
    public static final double FAIRY_PLAYER_HARMFUL_DURATION = 0.75;
    public static final double POKEMON_HARMFUL_DURATION = 0.80;

    // Player effects applied outside this class; shared here so the type-resonance screen
    // describes exactly the numbers the effect code uses.
    public static final double WATER_SWIM_SPEED = 0.15;
    public static final double GRASS_EXHAUSTION_SAVING = 0.10;
    public static final double ICE_SLOW_CHANCE = 0.25;
    public static final int ICE_SLOW_TICKS = 60;
    public static final double FIGHTING_KNOCKBACK = 0.20;
    public static final double POISON_PROC_CHANCE = 0.15;
    public static final int POISON_PROC_TICKS = 80;
    public static final int GROUND_ARMOR = 2;
    public static final double GROUND_KNOCKBACK_RESISTANCE = 0.20;
    public static final int DRAGON_MAX_HEALTH = 4;
    public static final double BUG_JUMP_STRENGTH = 0.15;
    public static final double GHOST_DETECTION_RANGE = 0.80;
    public static final int STEEL_TOUGHNESS = 2;
    public static final double STEEL_DURABILITY_SAVING = 0.15;
    public static final double PSYCHIC_COOLDOWN = 0.90;
    public static final double POKEMON_FIRE_ENVIRONMENT = 0.50;
    public static final double POKEMON_FLYING_MOVEMENT = 0.05;
    public static final double AIR_MOVEMENT = 0.10;
    public static final double AIR_FALL_REDUCTION = 0.50;
    public static final double AIR_POKEMON_MOVEMENT = 0.10;
    public static final double LIGHT_NATURAL_REGEN = 0.10;
    public static final double LIGHT_BLINDNESS_DURATION = 0.50;
    public static final double LIGHT_POKEMON_RECOVERY = 0.10;
    public static final double LIGHT_UNDEAD_DAMAGE = 0.05;
    public static final double SOUND_KNOCKBACK_RESISTANCE = 0.20;
    public static final double SOUND_OUTGOING_KNOCKBACK = 0.15;
    public static final double SOUND_POKEMON_KNOCKBACK = 0.10;

    private AffinityBalance() {}

    public static double playerMovement(Set<String> active, boolean night) {
        return (active.contains("electric") ? 0.10 : 0)
                + (active.contains("bug") ? 0.08 : 0)
                + (active.contains("normal") ? 0.05 : 0)
                + (active.contains("air") ? AIR_MOVEMENT : 0)
                + (night && active.contains("dark") ? 0.10 : 0);
    }

    public static double playerAttackSpeed(Set<String> active) {
        return (active.contains("electric") ? 0.08 : 0)
                + (active.contains("fighting") ? 0.12 : 0)
                + (active.contains("normal") ? 0.05 : 0);
    }

    public static double rockMining(Set<String> active) {
        return active.contains("rock") ? ROCK_MINING : 0;
    }

    public static double normalMining(Set<String> active) {
        return active.contains("normal") ? NORMAL_MINING : 0;
    }

    /**
     * Rock's extra ore bonus is additive with the other mining bonuses
     * (Rock + Normal on an ore = 1 + 0.20 + 0.10 + 0.05). Given the sum of this
     * mod's general mining bonuses, returns the factor to apply on ores.
     */
    public static double oreMiningFactor(double generalMining) {
        return (1 + generalMining + ROCK_ORE_MINING) / (1 + generalMining);
    }

    public static double playerNaturalRegen(Set<String> active) {
        return (active.contains("grass") ? 0.15 : 0) + (active.contains("normal") ? 0.05 : 0)
                + (active.contains("light") ? LIGHT_NATURAL_REGEN : 0);
    }

    /** Reductions of different sources multiply, so they can never exceed full immunity. */
    public static double playerDamageMultiplier(Set<String> active, boolean fall, boolean lava) {
        double multiplier = active.contains("dragon") ? 0.95 : 1;
        if (fall) {
            if (active.contains("ghost")) multiplier *= 0.25;
            if (active.contains("flying")) multiplier *= 0.50;
            if (active.contains("air")) multiplier *= 1 - AIR_FALL_REDUCTION;
        }
        if (lava && active.contains("fire")) multiplier *= 0.50;
        return multiplier;
    }

    /**
     * Pokémon vs Mob damage bonus for one Pokémon. Bonuses of selected types the
     * Pokémon has are added, then capped. {@code physical} is a melee hit; Fighting
     * applies only to those.
     */
    public static double pokemonDamageBonus(Set<String> active, Collection<String> types,
                                            boolean inWater, boolean night, boolean physical) {
        return Math.min(pokemonDamageBonusRaw(active, types, inWater, night, physical), POKEMON_DAMAGE_CAP);
    }

    /** {@link #pokemonDamageBonus} before the cap, so the screen can say when the cap applies. */
    public static double pokemonDamageBonusRaw(Set<String> active, Collection<String> types,
                                               boolean inWater, boolean night, boolean physical) {
        double bonus = 0;
        if (matches(active, types, "fire")) bonus += 0.07;
        if (inWater && matches(active, types, "water")) bonus += 0.07;
        // Interpretation: Electric's "action/attack speed equivalent +7%" as a damage rate.
        if (matches(active, types, "electric")) bonus += 0.07;
        if (physical && matches(active, types, "fighting")) bonus += 0.10;
        if (matches(active, types, "poison")) bonus += 0.05;
        if (matches(active, types, "dragon")) bonus += 0.07;
        if (night && matches(active, types, "dark")) bonus += 0.08;
        if (matches(active, types, "normal")) bonus += 0.03;
        return bonus;
    }

    /** Light's return bonus applies only against undead Minecraft Mobs. */
    public static double pokemonDamageBonusAgainstMob(Set<String> active, Collection<String> types,
                                                        boolean inWater, boolean night, boolean physical,
                                                        boolean undead) {
        double raw = pokemonDamageBonusRaw(active, types, inWater, night, physical);
        if (undead && matches(active, types, "light")) raw += LIGHT_UNDEAD_DAMAGE;
        return Math.min(raw, POKEMON_DAMAGE_CAP);
    }

    /** Pokémon damage reduction against Mobs for one Pokémon, capped. */
    public static double pokemonDamageReduction(Set<String> active, Collection<String> types) {
        return Math.min(pokemonDamageReductionRaw(active, types), POKEMON_REDUCTION_CAP);
    }

    public static double pokemonDamageReductionRaw(Set<String> active, Collection<String> types) {
        double reduction = 0;
        if (matches(active, types, "ice")) reduction += 0.05;
        if (matches(active, types, "ground")) reduction += 0.07;
        if (matches(active, types, "flying")) reduction += 0.05;
        if (matches(active, types, "bug")) reduction += 0.05;
        if (matches(active, types, "rock")) reduction += 0.07;
        if (matches(active, types, "ghost")) reduction += 0.05;
        if (matches(active, types, "dragon")) reduction += 0.05;
        if (matches(active, types, "steel")) reduction += 0.10;
        if (matches(active, types, "normal")) reduction += 0.03;
        if (matches(active, types, "air")) reduction += 0.05;
        return reduction;
    }

    /** Extra natural recovery for one Pokémon, capped. */
    public static double pokemonRecoveryBonus(Set<String> active, Collection<String> types) {
        return Math.min(pokemonRecoveryBonusRaw(active, types), POKEMON_RECOVERY_CAP);
    }

    public static double pokemonRecoveryBonusRaw(Set<String> active, Collection<String> types) {
        double bonus = 0;
        if (matches(active, types, "water")) bonus += 0.10;
        if (matches(active, types, "grass")) bonus += 0.15;
        if (matches(active, types, "fairy")) bonus += 0.10;
        if (matches(active, types, "light")) bonus += LIGHT_POKEMON_RECOVERY;
        return bonus;
    }

    /** Fairy and Poison both give -20%; a Fairy/Poison Pokémon gets it once. */
    public static boolean pokemonHarmfulDurationReduced(Set<String> active, Collection<String> types) {
        return matches(active, types, "fairy") || matches(active, types, "poison");
    }

    public static double pokemonExpBonus(Set<String> active, Collection<String> types) {
        return matches(active, types, "psychic") ? PSYCHIC_POKEMON_EXP : 0;
    }

    public static boolean pokemonFireEnvironmentHalved(Set<String> active, Collection<String> types) {
        return matches(active, types, "fire");
    }

    public static boolean pokemonFreezeImmune(Set<String> active, Collection<String> types) {
        return matches(active, types, "ice");
    }

    public static boolean pokemonFlyingMovement(Set<String> active, Collection<String> types) {
        return matches(active, types, "flying");
    }

    public static double pokemonWorldMovement(Set<String> active, Collection<String> types) {
        return (pokemonFlyingMovement(active, types) ? POKEMON_FLYING_MOVEMENT : 0)
                + (matches(active, types, "air") ? AIR_POKEMON_MOVEMENT : 0);
    }

    public static boolean pokemonSoundKnockback(Set<String> active, Collection<String> types) {
        return matches(active, types, "sound");
    }

    private static boolean matches(Set<String> active, Collection<String> types, String type) {
        return active.contains(type) && types.contains(type);
    }
}
