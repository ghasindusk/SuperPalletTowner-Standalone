package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardSynergyHooksTest {
    @Test
    void durabilityAddsFivePointsOnlyForSelectedEquipmentSynergies() {
        assertEquals(.15, StandardSynergyHookBalance.savingFor("rock_steel_air", ""), 1e-9);
        assertEquals(.20, StandardSynergyHookBalance.savingFor("rock_steel_air", "excavator_harmony"), 1e-9);
        assertEquals(.20, StandardSynergyHookBalance.savingFor("fire_steel_air", "thermal_processing"), 1e-9);
        assertEquals(.20, StandardSynergyHookBalance.savingFor("ground_steel_air", "reinforced_systems"), 1e-9);
        assertEquals(.30, StandardSynergyHookBalance.savingFor("fire_rock_steel", "excavator_harmony"), 1e-9);
        assertEquals(.25, StandardSynergyHookBalance.savingFor("fire_steel", "thermal_processing"), 1e-9);
    }

    @Test
    void psychicCooldownKeepsSignatureTargets() {
        assertEquals(.90, StandardSynergyHookBalance.cooldownFactor("air_psychic_sound", ""), 1e-9);
        assertEquals(.85, StandardSynergyHookBalance.cooldownFactor("air_psychic_sound", "insight_harmony"), 1e-9);
        assertEquals(.87, StandardSynergyHookBalance.cooldownFactor("air_ghost_psychic", "spectral_flow"), 1e-9);
        assertEquals(.80, StandardSynergyHookBalance.cooldownFactor("dark_ghost_psychic", "spectral_flow"), 1e-9);
        assertEquals(.85, StandardSynergyHookBalance.cooldownFactor("psychic_fairy", "insight_harmony"), 1e-9);
    }

    @Test
    void fortifiedKnockbackRespectsSingleTypeFortyPercentCapAndSignatures() {
        assertEquals(.10, StandardSynergyHookBalance.knockbackBonus(Set.of("ground", "rock", "air"),
                "air_ground_rock", "fortified_core"), 1e-9);
        assertEquals(0, StandardSynergyHookBalance.knockbackBonus(Set.of("ground", "rock", "sound"),
                "ground_rock_sound", "fortified_core"), 1e-9);
        assertEquals(.20, StandardSynergyHookBalance.knockbackBonus(Set.of("ground", "rock", "ice"),
                "ice_rock_ground", "fortified_core"), 1e-9);
        assertEquals(0, StandardSynergyHookBalance.knockbackBonus(Set.of("ground"),
                "air_ground_rock", ""), 1e-9);
    }
}
