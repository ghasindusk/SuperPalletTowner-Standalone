package starlight.trainer;

import java.util.Collection;
import java.util.Set;

/** Extra returns from exactly one active link or harmonic. Single-type returns are calculated separately. */
public final class ResonanceLinkBalance {
    public record PokemonBonus(double damage, double reduction, double recovery,
                               double movement, double knockback, double knockbackResistance,
                               double harmfulDuration) {
        static final PokemonBonus NONE = new PokemonBonus(0, 0, 0, 0, 0, 0, 1);
        PokemonBonus multiply(double factor) {
            return new PokemonBonus(damage * factor, reduction * factor, recovery * factor,
                    movement * factor, knockback * factor, knockbackResistance * factor,
                    1 - (1 - harmfulDuration) * factor);
        }
    }

    private ResonanceLinkBalance() {}

    /** The owning player's selected, currently active recipe must be supplied by the server. */
    public static PokemonBonus pokemon(Set<String> activeRecipes, Collection<String> pokemonTypes,
                                       boolean night, boolean physical, boolean undead) {
        if (activeRecipes.size() != 1) return PokemonBonus.NONE;
        String id = activeRecipes.iterator().next();
        var entry = ResonanceAutogenCatalog.findId(id);
        if (entry == null || pokemonTypes.stream().noneMatch(entry.types()::contains)) return PokemonBonus.NONE;
        PokemonBonus raw = switch (id) {
            case "rock_steel" -> defensiveKnockback(0, .08, .15);
            case "fire_steel" -> bonus(.07, .05, 0, 0, 0, 1);
            case "fire_rock" -> bonus(.06, 0, 0, 0, 0, 1);
            case "air_flying" -> bonus(0, 0, 0, .15, 0, 1);
            case "air_electric" -> bonus(.08, 0, 0, .10, 0, 1);
            case "electric_steel" -> defensiveKnockback(0, .05, .20);
            case "fairy_light" -> bonus(0, 0, .15, 0, 0, .90);
            case "electric_sound" -> bonus(.07, 0, 0, 0, .15, 1);
            case "sound_steel" -> defensiveKnockback(0, .07, .20);
            case "water_grass" -> bonus(.07, 0, .10, 0, 0, 1);
            case "dark_ghost" -> night ? bonus(.08, .10, 0, 0, 0, 1) : PokemonBonus.NONE;
            case "ice_water" -> bonus(0, .06, 0, .10, 0, 1);
            case "dragon_fighting" -> defensiveKnockback(physical ? .10 : 0, 0, .15);
            case "psychic_fairy" -> bonus(0, .06, .12, 0, 0, 1);
            case "bug_grass" -> bonus(0, 0, .10, .08, 0, 1);
            case "normal_fighting" -> defensiveKnockback(.06, 0, .10);
            case "fire_rock_steel" -> bonus(.08, .08, 0, 0, 0, 1);
            case "air_electric_flying" -> bonus(.10, 0, 0, .15, 0, 1);
            case "fairy_grass_water" -> bonus(0, 0, .20, 0, 0, .70);
            case "dark_ghost_psychic" -> bonus(night ? .10 : 0, .10, 0, 0, 0, 1);
            case "electric_sound_steel" -> defensiveKnockback(.08, .06, .25);
            case "ice_rock_ground" -> defensiveKnockback(0, .12, .30);
            case "dragon_fire_flying" -> bonus(.10, 0, 0, 0, 0, 1);
            case "fairy_light_psychic" -> bonus(undead ? .10 : 0, 0, .15, 0, 0, 1);
            default -> StandardResonanceBalance.forId(id).pokemon();
        };
        long matched = pokemonTypes.stream().filter(entry.types()::contains).distinct().count();
        boolean bond = entry.types().size() == 2 ? matched == 2 : matched >= 2;
        return bond ? raw.multiply(1.25) : raw;
    }

    private static PokemonBonus bonus(double damage, double reduction, double recovery,
                                      double movement, double knockback, double harmfulDuration) {
        return new PokemonBonus(damage, reduction, recovery, movement, knockback, 0, harmfulDuration);
    }

    private static PokemonBonus defensiveKnockback(double damage, double reduction, double resistance) {
        return new PokemonBonus(damage, reduction, 0, 0, 0, resistance, 1);
    }

    public static double cappedDamage(double singleType, PokemonBonus link) {
        return Math.min(AffinityBalance.POKEMON_DAMAGE_CAP, singleType + link.damage());
    }

    public static double cappedReduction(double singleType, PokemonBonus link) {
        return Math.min(AffinityBalance.POKEMON_REDUCTION_CAP, singleType + link.reduction());
    }

    public static double cappedRecovery(double singleType, PokemonBonus link) {
        return Math.min(AffinityBalance.POKEMON_RECOVERY_CAP, singleType + link.recovery());
    }
}
