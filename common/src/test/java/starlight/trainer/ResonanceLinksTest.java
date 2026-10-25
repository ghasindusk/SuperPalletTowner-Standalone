package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resonance link rules (タイプの共鳴リンク) and the Rock + Steel tool-tier step. */
class ResonanceLinksTest {
    private static final List<String> SLOTS = List.of("rock", "steel", "fire");
    private static final Set<String> ALL_ACTIVE = Set.of("rock", "steel", "fire");

    // ---- link slots

    @Test
    void onlySelectedAndActiveTypesCanBeAdded() {
        assertTrue(ResonanceLinks.canAdd(List.of(), "rock", SLOTS, ALL_ACTIVE));
        assertFalse(ResonanceLinks.canAdd(List.of(), "water", SLOTS, Set.of("rock", "steel", "fire", "water")),
                "not in a resonance slot");
        assertFalse(ResonanceLinks.canAdd(List.of(), "rock", SLOTS, Set.of("steel", "fire")), "selected but resting");
        assertFalse(ResonanceLinks.canAdd(List.of("rock"), "rock", SLOTS, ALL_ACTIVE), "already linked");
        assertFalse(ResonanceLinks.canAdd(List.of("rock", "steel", "fire"), "fire", SLOTS, ALL_ACTIVE), "full");
    }

    @Test
    void sanitizeDropsUnselectedDuplicatesAndExtras() {
        assertEquals(List.of("rock", "steel"), ResonanceLinks.sanitize(List.of("ROCK", "water", "rock", "steel"), SLOTS));
        assertEquals(List.of("rock", "steel", "fire"),
                ResonanceLinks.sanitize(List.of("rock", "steel", "fire", "rock"), List.of("rock", "steel", "fire", "x")));
        assertEquals(List.of("steel"), ResonanceLinks.sanitize(List.of("rock", "steel"), List.of("steel", "fire")),
                "a type that leaves its resonance slot leaves the link");
    }

    // ---- recipes

    @Test
    void rockSteelMatchesExactlyInAnyOrder() {
        assertEquals(ResonanceLinks.ROCK_STEEL, ResonanceLinks.recipe(List.of("rock", "steel")).id());
        assertEquals(ResonanceLinks.ROCK_STEEL, ResonanceLinks.recipe(List.of("steel", "rock")).id());
        assertNull(ResonanceLinks.recipe(List.of("rock")), "one type is not a link");
        assertEquals("fire_rock_steel", ResonanceLinks.recipe(List.of("rock", "steel", "fire")).id());
        assertEquals("fire_rock", ResonanceLinks.recipe(List.of("rock", "fire")).id());
        assertEquals("normal_rock", ResonanceLinks.recipe(List.of("rock", "normal")).id(),
                "v5 Standard combinations are generated");
        assertNull(ResonanceLinks.recipe(List.of("rock", "rock")));
    }

    @Test
    void sixteenPairsAndEightHarmonicsAreDefined() {
        assertEquals(24, ResonanceLinks.RECIPES.size());
        assertEquals(16, ResonanceLinks.RECIPES.stream().filter(recipe -> recipe.types().size() == 2).count());
        assertEquals(8, ResonanceLinks.RECIPES.stream().filter(recipe -> recipe.types().size() == 3).count());
        assertEquals(Set.of("rock", "steel"), ResonanceLinks.RECIPES.get(0).types());
    }

    @Test
    void statusAndActiveRecipes() {
        assertEquals(ResonanceLinks.Status.EMPTY, ResonanceLinks.status(List.of(), ALL_ACTIVE));
        assertEquals(ResonanceLinks.Status.TOO_FEW, ResonanceLinks.status(List.of("rock"), ALL_ACTIVE));
        assertEquals(ResonanceLinks.Status.NO_RECIPE, ResonanceLinks.status(List.of("rock", "unknown"), ALL_ACTIVE));
        assertEquals(ResonanceLinks.Status.ACTIVE, ResonanceLinks.status(List.of("rock", "steel"), ALL_ACTIVE));
        assertEquals(Set.of(ResonanceLinks.ROCK_STEEL), ResonanceLinks.activeRecipes(List.of("rock", "steel"), ALL_ACTIVE));
        // Steel's only Pokémon fainted: the link stays but rests.
        Set<String> noSteel = Set.of("rock", "fire");
        assertEquals(ResonanceLinks.Status.RESTING, ResonanceLinks.status(List.of("rock", "steel"), noSteel));
        assertEquals(Set.of(), ResonanceLinks.activeRecipes(List.of("rock", "steel"), noSteel));
        assertEquals(Set.of("fire_rock_steel"), ResonanceLinks.activeRecipes(List.of("rock", "steel", "fire"), ALL_ACTIVE));
    }

    // ---- Rock + Steel tier step

    @Test
    void tierLadderStepsOnceAndStopsAtNetherite() {
        assertEquals(ResonanceLinks.STONE, ResonanceLinks.nextTier("minecraft:incorrect_for_wooden_tool"));
        assertEquals(ResonanceLinks.IRON, ResonanceLinks.nextTier("minecraft:incorrect_for_stone_tool"));
        assertEquals(ResonanceLinks.DIAMOND, ResonanceLinks.nextTier("minecraft:incorrect_for_iron_tool"));
        assertEquals(ResonanceLinks.NETHERITE, ResonanceLinks.nextTier("minecraft:incorrect_for_diamond_tool"));
        assertNull(ResonanceLinks.nextTier("minecraft:incorrect_for_netherite_tool"), "netherite is the top");
        assertEquals(ResonanceLinks.STONE, ResonanceLinks.nextTier("minecraft:incorrect_for_gold_tool"),
                "gold harvests like wood");
        assertNull(ResonanceLinks.nextTier("somemod:incorrect_for_mythril_tool"), "custom tiers are not guessed");
        assertNull(ResonanceLinks.nextTier(null));
    }

    @Test
    void onlyTheSixVanillaTierTagsCount() {
        for (String tier : List.of("wooden", "gold", "stone", "iron", "diamond", "netherite")) {
            assertTrue(ResonanceLinks.isVanillaTierTag("minecraft:incorrect_for_" + tier + "_tool"), tier);
        }
        assertFalse(ResonanceLinks.isVanillaTierTag("somemod:incorrect_for_copper_tool"));
        assertFalse(ResonanceLinks.isVanillaTierTag(null));
    }

    @Test
    void tierSpeedsAreVanilla() {
        // net.minecraft.world.item.Tiers in 1.21.1.
        assertEquals(2.0F, ResonanceLinks.WOOD.speed());
        assertEquals(4.0F, ResonanceLinks.STONE.speed());
        assertEquals(6.0F, ResonanceLinks.IRON.speed());
        assertEquals(8.0F, ResonanceLinks.DIAMOND.speed());
        assertEquals(9.0F, ResonanceLinks.NETHERITE.speed());
        assertEquals(12.0F, ResonanceLinks.GOLD.speed());
    }

    @Test
    void steppedSpeedIsNextTierButNeverSlower() {
        assertEquals(4.0F, ResonanceLinks.steppedBaseSpeed(2.0F, ResonanceLinks.STONE), "wood -> stone");
        assertEquals(6.0F, ResonanceLinks.steppedBaseSpeed(4.0F, ResonanceLinks.IRON), "stone -> iron");
        assertEquals(9.0F, ResonanceLinks.steppedBaseSpeed(8.0F, ResonanceLinks.NETHERITE), "diamond -> netherite");
        assertEquals(12.0F, ResonanceLinks.steppedBaseSpeed(12.0F, ResonanceLinks.STONE), "gold keeps its speed");
        assertEquals(1.0F, ResonanceLinks.steppedBaseSpeed(1.0F, ResonanceLinks.STONE), "not effective on the block");
        assertEquals(9.0F, ResonanceLinks.steppedBaseSpeed(9.0F, null), "netherite unchanged");
    }

    @Test
    void speedFactorKeepsEfficiencyAdditive() {
        assertEquals(2.0, ResonanceLinks.speedFactor(2.0F, 0.0F, ResonanceLinks.STONE), 1e-9);
        assertEquals(1.5, ResonanceLinks.speedFactor(4.0F, 0.0F, ResonanceLinks.IRON), 1e-9);
        assertEquals(1.125, ResonanceLinks.speedFactor(8.0F, 0.0F, ResonanceLinks.NETHERITE), 1e-9);
        // Efficiency V adds 26 to the base: wood (2+26) becomes stone (4+26).
        assertEquals(30.0 / 28.0, ResonanceLinks.speedFactor(2.0F, 26.0F, ResonanceLinks.STONE), 1e-9);
        assertEquals(1.0, ResonanceLinks.speedFactor(12.0F, 0.0F, ResonanceLinks.STONE), "gold");
        assertEquals(1.0, ResonanceLinks.speedFactor(1.0F, 0.0F, ResonanceLinks.STONE), "hand-speed block");
        assertEquals(1.0, ResonanceLinks.speedFactor(9.0F, 0.0F, null), "netherite");
    }

    @Test
    void rockMiningStacksMultiplicatively() {
        // Wooden pickaxe on stone with Rock (+20% block break speed): 2 -> 4, then x1.2.
        double rock = 1 + AffinityBalance.ROCK_MINING;
        double withLink = 2.0 * ResonanceLinks.speedFactor(2.0F, 0.0F, ResonanceLinks.STONE) * rock;
        assertEquals(4.8, withLink, 1e-9);
        // On an ore, Rock's extra +10% is additive with the general bonus: (1 + 0.2 + 0.1).
        double ore = rock * AffinityBalance.oreMiningFactor(AffinityBalance.ROCK_MINING);
        assertEquals(1.3, ore, 1e-9);
        assertEquals(4.0 * 1.5 * 1.3, 4.0 * ResonanceLinks.speedFactor(4.0F, 0.0F, ResonanceLinks.IRON) * ore, 1e-9);
    }

    @Test
    void linkTextComesFromTheSameNumbers() {
        List<ResonanceInfo.Line> lines = ResonanceInfo.linkDetails(ResonanceLinks.ROCK_STEEL);
        assertEquals(List.of("2", "4", "6", "8", "9"), lines.get(1).args());
        assertEquals(List.of("12"), lines.get(2).args());
        assertEquals(List.of("20", "10"), lines.get(4).args());
        assertEquals("link.rock_steel.summary", ResonanceInfo.linkSummary(ResonanceLinks.ROCK_STEEL).key());
        assertNull(ResonanceInfo.linkSummary("rock_fire"));
        assertTrue(ResonanceInfo.linkDetails("rock_fire").isEmpty());
    }
}
