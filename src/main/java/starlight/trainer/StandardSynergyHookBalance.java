package starlight.trainer;

import java.util.Set;

/** Numeric Standard synergy effects shared by the runtime hooks and unit tests. */
final class StandardSynergyHookBalance {
    private StandardSynergyHookBalance() {}

    static double savingFor(String recipe, String synergy) {
        // Signature targets replace, rather than add to, the Steel affinity saving.
        if ("fire_rock_steel".equals(recipe)) return .30;
        if ("fire_steel".equals(recipe)) return .25;
        double additional = "excavator_harmony".equals(synergy)
                || "thermal_processing".equals(synergy)
                || "reinforced_systems".equals(synergy) ? .05 : 0;
        return Math.min(.30, AffinityBalance.STEEL_DURABILITY_SAVING + additional);
    }

    static double cooldownFactor(String recipe, String synergy) {
        // Signature cooldown targets are already inclusive of the Psychic affinity.
        if ("dark_ghost_psychic".equals(recipe)) return .80;
        if ("psychic_fairy".equals(recipe) || "fairy_light_psychic".equals(recipe)) return .85;
        double extraReduction = "insight_harmony".equals(synergy) ? .05
                : "spectral_flow".equals(synergy) ? .03 : 0;
        return Math.max(0, AffinityBalance.PSYCHIC_COOLDOWN - extraReduction);
    }

    static double knockbackBonus(Set<String> types, String recipe, String synergy) {
        double baseKb = (types.contains("ground") ? AffinityBalance.GROUND_KNOCKBACK_RESISTANCE : 0)
                + (types.contains("sound") ? AffinityBalance.SOUND_KNOCKBACK_RESISTANCE : 0);
        double signature = switch (recipe) {
            case "sound_steel" -> Math.max(0, .30 - baseKb);
            case "electric_sound_steel" -> Math.max(0, .35 - baseKb);
            case "ice_rock_ground" -> Math.max(0, .40 - baseKb);
            default -> 0;
        };
        if (signature > 0) return signature;
        return "fortified_core".equals(synergy) ? Math.min(.10, Math.max(0, .40 - baseKb)) : 0;
    }
}
