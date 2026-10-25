package starlight.trainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the code's numbers against Super Pallet Towner Balance v1 (outputs/Super_Pallet_Towner_Balance_v1.md). */
class AffinityBalanceTest {
    private static final double EPS = 1e-9;

    private static double damage(String type, boolean inWater, boolean night, boolean physical) {
        return AffinityBalance.pokemonDamageBonus(Set.of(type), Set.of(type), inWater, night, physical);
    }

    // --- Pokémon return bonuses, one row per Balance v1 type ---

    @ParameterizedTest
    @CsvSource({
            "fire, 0.07", "electric, 0.07", "poison, 0.05", "dragon, 0.07", "normal, 0.03",
            "grass, 0", "ice, 0", "ground, 0", "flying, 0", "psychic, 0", "bug, 0", "rock, 0",
            "ghost, 0", "steel, 0", "fairy, 0"})
    void pokemonDamageBonusWithoutConditions(String type, double expected) {
        assertEquals(expected, damage(type, false, false, false), EPS);
    }

    @Test
    void conditionalPokemonDamageBonuses() {
        assertEquals(0.07, damage("water", true, false, false), EPS);
        assertEquals(0, damage("water", false, false, false), EPS);
        assertEquals(0.08, damage("dark", false, true, false), EPS);
        assertEquals(0, damage("dark", false, false, false), EPS);
        assertEquals(0.10, damage("fighting", false, false, true), EPS);
        assertEquals(0, damage("fighting", false, false, false), EPS);
    }

    @ParameterizedTest
    @CsvSource({
            "ice, 0.05", "ground, 0.07", "flying, 0.05", "bug, 0.05", "rock, 0.07", "ghost, 0.05",
            "dragon, 0.05", "steel, 0.10", "normal, 0.03",
            "fire, 0", "water, 0", "grass, 0", "electric, 0", "fighting, 0", "poison, 0",
            "psychic, 0", "dark, 0", "fairy, 0"})
    void pokemonDamageReduction(String type, double expected) {
        assertEquals(expected, AffinityBalance.pokemonDamageReduction(Set.of(type), Set.of(type)), EPS);
    }

    @ParameterizedTest
    @CsvSource({"water, 0.10", "grass, 0.15", "fairy, 0.10", "normal, 0", "fire, 0"})
    void pokemonRecovery(String type, double expected) {
        assertEquals(expected, AffinityBalance.pokemonRecoveryBonus(Set.of(type), Set.of(type)), EPS);
    }

    @Test
    void pokemonFlagsAndExp() {
        assertEquals(0.07, AffinityBalance.pokemonExpBonus(Set.of("psychic"), Set.of("psychic")), EPS);
        assertTrue(AffinityBalance.pokemonHarmfulDurationReduced(Set.of("fairy"), Set.of("fairy")));
        assertTrue(AffinityBalance.pokemonHarmfulDurationReduced(Set.of("poison"), Set.of("poison")));
        assertTrue(AffinityBalance.pokemonFireEnvironmentHalved(Set.of("fire"), Set.of("fire")));
        assertTrue(AffinityBalance.pokemonFreezeImmune(Set.of("ice"), Set.of("ice")));
        assertTrue(AffinityBalance.pokemonFlyingMovement(Set.of("flying"), Set.of("flying")));
        assertEquals(0.80, AffinityBalance.POKEMON_HARMFUL_DURATION, EPS);
    }

    // --- Matching rules ---

    @Test
    void bonusNeedsBothSelectionAndPokemonType() {
        assertEquals(0, AffinityBalance.pokemonDamageBonus(Set.of("fire"), Set.of("water"), true, true, true), EPS);
        assertEquals(0, AffinityBalance.pokemonDamageBonus(Set.of("water"), Set.of("fire"), true, true, true), EPS);
        assertEquals(0, AffinityBalance.pokemonExpBonus(Set.of("fire"), Set.of("psychic")), EPS);
    }

    @Test
    void dualTypeGetsBothSelectedBonuses() {
        // Spec example: Dragon + Fire = +7% +7% = +14%.
        assertEquals(0.14, AffinityBalance.pokemonDamageBonus(
                Set.of("dragon", "fire"), Set.of("dragon", "fire"), false, false, false), EPS);
    }

    @Test
    void fairyPoisonDurationAppliesOnce() {
        // A boolean: the -20% cannot be applied twice to a Fairy/Poison Pokémon.
        assertTrue(AffinityBalance.pokemonHarmfulDurationReduced(Set.of("fairy", "poison"), Set.of("fairy", "poison")));
    }

    @Test
    void perPokemonCaps() {
        // Three selections can exceed +15% on one dual-type only via conditional stacking.
        assertEquals(0.15, AffinityBalance.pokemonDamageBonus(
                Set.of("fighting", "dark", "normal"), Set.of("fighting", "dark"), false, true, true), EPS);
        assertEquals(0.15, AffinityBalance.pokemonDamageReduction(
                Set.of("steel", "rock", "ground"), Set.of("steel", "rock")), EPS);
        assertEquals(0.25, AffinityBalance.pokemonRecoveryBonus(
                Set.of("grass", "water", "fairy"), Set.of("grass", "water")), EPS);
        assertEquals(0.25, AffinityBalance.pokemonRecoveryBonus(
                Set.of("grass", "fairy"), Set.of("grass", "fairy")), EPS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cosmic", "digital", "nuclear", "void", "mystic"})
    void deferredTypesHaveNoEffects(String type) {
        Set<String> one = Set.of(type);
        assertEquals(0, AffinityBalance.pokemonDamageBonus(one, one, true, true, true), EPS);
        assertEquals(0, AffinityBalance.pokemonDamageReduction(one, one), EPS);
        assertEquals(0, AffinityBalance.pokemonRecoveryBonus(one, one), EPS);
        assertEquals(0, AffinityBalance.pokemonExpBonus(one, one), EPS);
        assertFalse(AffinityBalance.pokemonHarmfulDurationReduced(one, one));
        assertEquals(0, AffinityBalance.playerMovement(one, true), EPS);
        assertEquals(0, AffinityBalance.playerAttackSpeed(one), EPS);
        assertEquals(0, AffinityBalance.rockMining(one) + AffinityBalance.normalMining(one), EPS);
        assertEquals(0, AffinityBalance.playerNaturalRegen(one), EPS);
        assertEquals(1, AffinityBalance.playerDamageMultiplier(one, true, true), EPS);
    }

    @Test
    void cobblemaniaThreeTypesUseTheirSpecifiedNumbersAndExistingCaps() {
        assertEquals(0.10, AffinityBalance.playerMovement(Set.of("air"), false), EPS);
        assertEquals(0.50, AffinityBalance.playerDamageMultiplier(Set.of("air"), true, false), EPS);
        assertEquals(0.10, AffinityBalance.playerNaturalRegen(Set.of("light")), EPS);
        assertEquals(0.05, AffinityBalance.pokemonDamageReduction(Set.of("air"), Set.of("air")), EPS);
        assertEquals(0.10, AffinityBalance.pokemonWorldMovement(Set.of("air"), Set.of("air")), EPS);
        assertEquals(0.15, AffinityBalance.pokemonWorldMovement(Set.of("air", "flying"), Set.of("air", "flying")), EPS);
        assertEquals(0.10, AffinityBalance.pokemonRecoveryBonus(Set.of("light"), Set.of("light")), EPS);
        assertEquals(0.05, AffinityBalance.pokemonDamageBonusAgainstMob(Set.of("light"), Set.of("light"),
                false, false, false, true), EPS);
        assertEquals(0, AffinityBalance.pokemonDamageBonusAgainstMob(Set.of("light"), Set.of("light"),
                false, false, false, false), EPS);
        assertEquals(0.15, AffinityBalance.pokemonDamageBonusAgainstMob(Set.of("light", "dragon", "fire"),
                Set.of("light", "dragon", "fire"), false, false, false, true), EPS);
        assertTrue(AffinityBalance.pokemonSoundKnockback(Set.of("sound"), Set.of("sound")));
        assertFalse(AffinityBalance.pokemonSoundKnockback(Set.of("sound"), Set.of("fire")));
        assertEquals(0.20, AffinityBalance.SOUND_KNOCKBACK_RESISTANCE, EPS);
        assertEquals(0.15, AffinityBalance.SOUND_OUTGOING_KNOCKBACK, EPS);
        assertEquals(0.10, AffinityBalance.SOUND_POKEMON_KNOCKBACK, EPS);
    }

    // --- Player numbers ---

    @Test
    void playerMovementAndAttackSpeed() {
        assertEquals(0.10, AffinityBalance.playerMovement(Set.of("electric"), false), EPS);
        assertEquals(0.08, AffinityBalance.playerMovement(Set.of("bug"), false), EPS);
        assertEquals(0.05, AffinityBalance.playerMovement(Set.of("normal"), false), EPS);
        assertEquals(0.10, AffinityBalance.playerMovement(Set.of("dark"), true), EPS);
        assertEquals(0, AffinityBalance.playerMovement(Set.of("dark"), false), EPS);
        assertEquals(0.08, AffinityBalance.playerAttackSpeed(Set.of("electric")), EPS);
        assertEquals(0.12, AffinityBalance.playerAttackSpeed(Set.of("fighting")), EPS);
        assertEquals(0.05, AffinityBalance.playerAttackSpeed(Set.of("normal")), EPS);
        assertEquals(0.23, AffinityBalance.playerMovement(Set.of("electric", "bug", "normal"), false), EPS);
    }

    @Test
    void playerMiningIsAdditive() {
        double rockNormal = AffinityBalance.rockMining(Set.of("rock", "normal"))
                + AffinityBalance.normalMining(Set.of("rock", "normal"));
        assertEquals(0.25, rockNormal, EPS);
        // Ore with Rock + Normal: 1 + 0.20 + 0.10 + 0.05 = 1.35 overall.
        assertEquals(1.35, (1 + rockNormal) * AffinityBalance.oreMiningFactor(rockNormal), EPS);
        // Ore with Rock only: 1.30 overall.
        assertEquals(1.30, 1.20 * AffinityBalance.oreMiningFactor(0.20), EPS);
    }

    @Test
    void playerRegen() {
        assertEquals(0.15, AffinityBalance.playerNaturalRegen(Set.of("grass")), EPS);
        assertEquals(0.05, AffinityBalance.playerNaturalRegen(Set.of("normal")), EPS);
        assertEquals(0.20, AffinityBalance.playerNaturalRegen(Set.of("grass", "normal")), EPS);
    }

    @Test
    void playerDamageMultipliers() {
        assertEquals(0.95, AffinityBalance.playerDamageMultiplier(Set.of("dragon"), false, false), EPS);
        assertEquals(0.50, AffinityBalance.playerDamageMultiplier(Set.of("flying"), true, false), EPS);
        assertEquals(0.25, AffinityBalance.playerDamageMultiplier(Set.of("ghost"), true, false), EPS);
        assertEquals(1, AffinityBalance.playerDamageMultiplier(Set.of("ghost", "flying"), false, false), EPS);
        assertEquals(0.50, AffinityBalance.playerDamageMultiplier(Set.of("fire"), false, true), EPS);
        assertEquals(1, AffinityBalance.playerDamageMultiplier(Set.of("fire"), false, false), EPS);
        // Ghost + Flying + Dragon fall: 0.95 * 0.25 * 0.50.
        assertEquals(0.11875, AffinityBalance.playerDamageMultiplier(Set.of("ghost", "flying", "dragon"), true, false), EPS);
    }

    // --- Rounding: the previous round(x * 1.10) gave no bonus on typical 1-3 XP orbs ---

    @Test
    void psychicXpIsNotLostToRounding() {
        FractionalCarry carry = new FractionalCarry();
        UUID player = UUID.randomUUID();
        int total = 0;
        for (int i = 0; i < 100; i++) total += carry.scale(player, 1, AffinityBalance.PSYCHIC_PLAYER_XP);
        assertEquals(110, total);
        int mixed = 0;
        for (int i = 0; i < 100; i++) mixed += carry.scale(player, 3, AffinityBalance.PSYCHIC_PLAYER_XP);
        assertEquals(330, mixed);
    }

    @Test
    void pokemonExpCarryIsPerKey() {
        FractionalCarry carry = new FractionalCarry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        int totalA = 0;
        int totalB = 0;
        for (int i = 0; i < 100; i++) {
            totalA += carry.scale(a, 5, AffinityBalance.PSYCHIC_POKEMON_EXP);
            totalB += carry.scale(b, 1, AffinityBalance.PSYCHIC_POKEMON_EXP);
        }
        assertEquals(535, totalA);
        assertEquals(107, totalB);
        assertEquals(0, carry.scale(a, 0, 0.07));
        assertEquals(10, carry.scale(a, 10, 0));
    }
}
