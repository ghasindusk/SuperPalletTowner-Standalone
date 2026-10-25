package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResonanceLinkBalanceTest {
    @Test
    void allTwentyFourRecipesHaveAConcretePokemonReturn() {
        assertEquals(24, ResonanceLinks.RECIPES.size());
        Set<Set<String>> unique = new HashSet<>();
        for (var recipe : ResonanceLinks.RECIPES) {
            assertTrue(unique.add(recipe.types()), recipe.id());
            var value = ResonanceLinkBalance.pokemon(Set.of(recipe.id()), recipe.types(), true, true, true);
            assertTrue(value.damage() > 0 || value.reduction() > 0 || value.recovery() > 0
                    || value.movement() > 0 || value.knockback() > 0
                    || value.knockbackResistance() > 0 || value.harmfulDuration() < 1, recipe.id());
        }
        assertEquals(16, unique.stream().filter(types -> types.size() == 2).count());
        assertEquals(8, unique.stream().filter(types -> types.size() == 3).count());
    }

    @Test
    void fullAndDeepMultiplyOnlyTheAdditionalReturn() {
        var single = ResonanceLinkBalance.pokemon(Set.of("fire_steel"), List.of("fire"), false, false, false);
        var full = ResonanceLinkBalance.pokemon(Set.of("fire_steel"), List.of("fire", "steel"), false, false, false);
        assertEquals(.07, single.damage(), 1e-9);
        assertEquals(.0875, full.damage(), 1e-9);
        assertEquals(.07, AffinityBalance.pokemonDamageBonus(Set.of("fire", "steel"), List.of("fire", "steel"),
                false, false, false), 1e-9, "the single-type return does not receive the 1.25 multiplier");
        assertEquals(.15, ResonanceLinkBalance.cappedDamage(.07, full), 1e-9);

        var deep = ResonanceLinkBalance.pokemon(Set.of("fire_rock_steel"), List.of("fire", "steel"),
                false, false, false);
        assertEquals(.10, deep.damage(), 1e-9);
        assertEquals(.10, deep.reduction(), 1e-9);
        assertEquals(.15, ResonanceLinkBalance.cappedReduction(.10, deep), 1e-9);
    }

    @Test
    void noReturnForUnmatchedPokemonOrUnknownCombination() {
        var none = ResonanceLinkBalance.pokemon(Set.of("fire_steel"), List.of("water"), true, true, true);
        assertEquals(0, none.damage());
        assertEquals(0, none.reduction());
        assertEquals(0, ResonanceLinkBalance.pokemon(Set.of("fake"), List.of("fire"), true, true, true).damage());
        assertEquals(0, ResonanceLinkBalance.pokemon(Set.of(), List.of("fire"), true, true, true).damage());
        assertEquals(0, ResonanceLinkBalance.pokemon(Set.of("fire_steel", "rock_steel"),
                List.of("fire", "steel"), true, true, true).damage(), "one exact recipe at a time");
    }

    @Test
    void defensiveKnockbackIsDistinctFromOutgoingKnockback() {
        var steel = ResonanceLinkBalance.pokemon(Set.of("sound_steel"), List.of("steel"), false, false, false);
        assertEquals(.20, steel.knockbackResistance(), 1e-9);
        assertEquals(0, steel.knockback(), 1e-9);
        var amplifier = ResonanceLinkBalance.pokemon(Set.of("electric_sound"), List.of("sound"), false, false, false);
        assertEquals(.15, amplifier.knockback(), 1e-9);
        assertEquals(0, amplifier.knockbackResistance(), 1e-9);
        var brawler = ResonanceLinkBalance.pokemon(Set.of("dragon_fighting"), List.of("dragon"), false, true, false);
        assertEquals(.15, brawler.knockbackResistance(), 1e-9);
        assertEquals(0, brawler.knockback(), 1e-9);
    }

    @Test
    void abyssPairReturnIsNightOnlyWhileHarmonicDefenseIsAlwaysOn() {
        var pairDay = ResonanceLinkBalance.pokemon(Set.of("dark_ghost"), List.of("ghost"), false, false, false);
        var pairNight = ResonanceLinkBalance.pokemon(Set.of("dark_ghost"), List.of("ghost"), true, false, false);
        assertEquals(0, pairDay.damage(), 1e-9);
        assertEquals(0, pairDay.reduction(), 1e-9);
        assertEquals(.08, pairNight.damage(), 1e-9);
        assertEquals(.10, pairNight.reduction(), 1e-9);

        var harmonicDay = ResonanceLinkBalance.pokemon(Set.of("dark_ghost_psychic"), List.of("ghost"), false, false, false);
        var harmonicNight = ResonanceLinkBalance.pokemon(Set.of("dark_ghost_psychic"), List.of("ghost"), true, false, false);
        assertEquals(0, harmonicDay.damage(), 1e-9);
        assertEquals(.10, harmonicDay.reduction(), 1e-9);
        assertEquals(.10, harmonicNight.damage(), 1e-9);
    }
}
