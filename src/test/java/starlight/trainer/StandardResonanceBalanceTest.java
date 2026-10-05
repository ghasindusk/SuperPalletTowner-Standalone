package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandardResonanceBalanceTest {
    @Test
    void unknownTraitFailsBeforeItCanBecomeMiningBonus() {
        assertThrows(IllegalStateException.class,
                () -> StandardResonanceBalance.requireKnownTag("minnig"));
        assertThrows(IllegalStateException.class,
                () -> StandardResonanceBalance.fromTraits(List.of("minnig")));
    }

    @Test
    void allKnownCombinationsBecomeOneExactRecipe() {
        assertEquals("normal_rock", ResonanceLinks.recipe(List.of("rock", "normal")).id());
        assertEquals(Set.of("normal_rock"), ResonanceLinks.activeRecipes(
                List.of("rock", "normal"), Set.of("rock", "normal")));
        assertEquals(Set.of(), ResonanceLinks.activeRecipes(
                List.of("rock", "normal"), Set.of("rock")));
        assertEquals("fire_rock_water", ResonanceLinks.recipe(List.of("water", "fire", "rock")).id());
        assertEquals("fire_rock_steel", ResonanceLinks.recipe(List.of("fire", "rock", "steel")).id(),
                "Signature keeps its old ID");
    }

    @Test
    void conservativeStandardNumbersAndSignatureOverride() {
        var pair = StandardResonanceBalance.forId("fire_water");
        assertEquals(.015, pair.player().movement(), 1e-9);
        assertEquals(.030, pair.player().attackSpeed(), 1e-9);
        assertEquals(.020, pair.pokemon().damage(), 1e-9);
        assertEquals(.003, pair.pokemon().reduction(), 1e-9);
        assertEquals(.015, pair.pokemon().movement(), 1e-9);
        assertEquals(StandardResonanceBalance.Bonus.NONE, StandardResonanceBalance.forId("fire_rock"));
        assertEquals(StandardResonanceBalance.Bonus.NONE, StandardResonanceBalance.forId("unknown"));
        assertEquals(.045, StandardResonanceBalance.forId("normal_rock").player().mining(), 1e-9);
    }

    @Test
    void pokemonGetsOneReturnWithBondMultiplierAndGlobalCap() {
        var single = ResonanceLinkBalance.pokemon(Set.of("normal_rock"), List.of("rock"), false, false, false);
        var dual = ResonanceLinkBalance.pokemon(Set.of("normal_rock"), List.of("normal", "rock"), false, false, false);
        assertEquals(.009, single.damage(), 1e-9);
        assertEquals(.01125, dual.damage(), 1e-9);
        assertEquals(0, ResonanceLinkBalance.pokemon(Set.of("normal_rock"), List.of("water"), false, false, false).damage());
        assertEquals(.15, ResonanceLinkBalance.cappedDamage(.14, dual), 1e-9);
    }

    @Test
    void generatedTextDescribesActualNumbers() {
        var summary = ResonanceInfo.linkSummary("fire_water");
        assertEquals("link.standard.summary", summary.key());
        var details = ResonanceInfo.linkDetails("fire_water");
        assertEquals("link.standard.player", details.get(0).key());
        assertEquals(List.of("1.5", "3", "0", "0", "0"), details.get(0).args());
        assertEquals(List.of("2", "0.3", "0", "1.5"), details.get(1).args());
        assertFalse(details.isEmpty());
        assertTrue(ResonanceInfo.linkDetails("fire_rock").stream().anyMatch(line -> line.key().equals("link.fire_rock.detail")));
        assertTrue(ResonanceInfo.playerTotals(Set.of("normal", "rock"), Set.of("normal_rock"))
                .stream().anyMatch(line -> line.key().equals("total.mining_ore")
                        && line.args().equals(List.of("29.5", "39.9"))));
    }

    @Test
    void everyGeneratedStandardHasBoundedPlayerAndPokemonEffects() {
        long standards = 0;
        for (var entry : ResonanceAutogenCatalog.entries()) {
            if (entry.signature()) continue;
            standards++;
            var bonus = StandardResonanceBalance.forId(entry.id());
            var player = bonus.player();
            var pokemon = bonus.pokemon();
            assertTrue(player.movement() > 0 || player.attackSpeed() > 0 || player.mining() > 0
                    || player.recovery() > 0 || player.armor() > 0, entry.id());
            assertTrue(player.movement() <= .08 && player.attackSpeed() <= .08
                    && player.mining() <= .08 && player.recovery() <= .08 && player.armor() <= 2,
                    entry.id());
            assertTrue(pokemon.damage() <= .05 && pokemon.reduction() <= .05
                    && pokemon.recovery() <= .10 && pokemon.movement() <= .10, entry.id());
            assertEquals(entry.id(), ResonanceLinks.recipe(entry.types()).id());
        }
        assertEquals(1516, standards);
    }

    @Test
    void harmonicUsesOneBasePerChannelAndOneDominantCategory() {
        var result = StandardResonanceBalance.forId("fire_normal_rock");
        assertEquals("utility", StandardResonanceBalance.dominantChannel("fire_normal_rock"));
        assertEquals("adaptive_harmony", StandardResonanceBalance.synergyId("fire_normal_rock"));
        assertEquals(.015, result.player().attackSpeed(), 1e-9);
        assertEquals(.04875, result.player().mining(), 1e-9,
                "one utility base ×1.25 plus adaptive +3%; no per-tag stacking");
        assertEquals(.5, result.player().armor(), 1e-9);
        assertEquals(.03, StandardResonanceBalance.forId("fire_water").player().attackSpeed(), 1e-9,
                "pairs retain v5.1 per-tag addition");
        assertEquals(StandardResonanceBalance.Bonus.NONE,
                StandardResonanceBalance.forId("fire_rock_steel"), "Signature overrides all four Standard layers");
        assertEquals(null, StandardResonanceBalance.synergyId("fire_rock_steel"));
    }

    @Test
    void synergyRequiresMultipleTypesAndRevisedConditions() {
        assertEquals(null, StandardResonanceBalance.synergyId("fire_rock_water"),
                "Water alone contains aquatic, flow and recovery");
        assertEquals("living_cycle", StandardResonanceBalance.synergyId("bug_fairy_grass"),
                "living outranks the overlapping regenerative condition");
        assertEquals("reinforced_systems", StandardResonanceBalance.synergyId("bug_ground_steel"),
                "Steel requires support from another type's defense or stability");
        assertEquals("adaptive_harmony", StandardResonanceBalance.synergyId("bug_normal_steel"),
                "Steel alone does not satisfy reinforced systems");
    }

    @Test
    void allEighteenSynergiesWinAtLeastOneReachableStandardTriad() {
        Map<String, String> winners = Map.ofEntries(
                Map.entry("bug_ground_rock", "fortified_core"),
                Map.entry("bug_ground_steel", "reinforced_systems"),
                Map.entry("bug_poison_sound", "disruptive_resonance"),
                Map.entry("bug_dragon_ground", "primal_core"),
                Map.entry("bug_electric_flying", "aerial_momentum"),
                Map.entry("air_grass_water", "current_harmony"),
                Map.entry("bug_dark_ghost", "shadow_harmony"),
                Map.entry("bug_electric_steel", "overclock_harmony"),
                Map.entry("bug_ghost_psychic", "spectral_flow"),
                Map.entry("bug_fairy_grass", "living_cycle"),
                Map.entry("bug_fairy_water", "regenerative_harmony"),
                Map.entry("bug_fairy_light", "purifying_light"),
                Map.entry("bug_rock_steel", "excavator_harmony"),
                Map.entry("bug_fire_steel", "thermal_processing"),
                Map.entry("bug_psychic_sound", "insight_harmony"),
                Map.entry("bug_fighting_fire", "combat_rhythm"),
                Map.entry("bug_fighting_sound", "shockwave_harmony"),
                Map.entry("bug_normal_rock", "adaptive_harmony"));
        assertEquals(18, new HashSet<>(winners.values()).size());
        winners.forEach((recipe, expected) -> assertEquals(expected,
                StandardResonanceBalance.synergyId(recipe), recipe));
    }

    @Test
    void harmonicPokemonSynergyUsesOneReturnAxisAndStaysUnderStandardCeilings() {
        for (var entry : ResonanceAutogenCatalog.entries()) {
            if (entry.signature() || entry.types().size() != 3) continue;
            var bonus = StandardResonanceBalance.forId(entry.id());
            var player = bonus.player(); var pokemon = bonus.pokemon();
            assertTrue(player.movement() <= .08 && player.attackSpeed() <= .08
                    && player.mining() <= .08 && player.recovery() <= .08 && player.armor() <= 2, entry.id());
            assertTrue(pokemon.damage() <= .05 && pokemon.reduction() <= .05
                    && pokemon.recovery() <= .10 && pokemon.movement() <= .10, entry.id());
            assertTrue(pokemon.knockback() <= .10 && pokemon.knockbackResistance() <= .10
                    && pokemon.harmfulDuration() >= .90, entry.id());
        }
    }

    @Test
    void fixedPriorityProducesTheAuditedReachabilityDistribution() {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("aerial_momentum", 18), Map.entry("fortified_core", 18),
                Map.entry("regenerative_harmony", 33), Map.entry("excavator_harmony", 16),
                Map.entry("thermal_processing", 16), Map.entry("current_harmony", 36),
                Map.entry("shadow_harmony", 18), Map.entry("insight_harmony", 18),
                Map.entry("combat_rhythm", 17), Map.entry("disruptive_resonance", 18),
                Map.entry("reinforced_systems", 36), Map.entry("living_cycle", 35),
                Map.entry("purifying_light", 16), Map.entry("shockwave_harmony", 28),
                Map.entry("overclock_harmony", 12), Map.entry("spectral_flow", 18),
                Map.entry("primal_core", 34), Map.entry("adaptive_harmony", 172));
        Map<String, Integer> actual = new HashMap<>();
        int unmatched = 0;
        for (var entry : ResonanceAutogenCatalog.entries()) {
            if (entry.signature() || entry.types().size() != 3) continue;
            String synergy = StandardResonanceBalance.synergyId(entry.id());
            if (synergy == null) unmatched++;
            else actual.merge(synergy, 1, Integer::sum);
        }
        assertEquals(expected, actual);
        assertEquals(763, unmatched);
        assertEquals(559, actual.values().stream().mapToInt(Integer::intValue).sum());
    }
}
