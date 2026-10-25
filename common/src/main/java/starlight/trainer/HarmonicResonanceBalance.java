package starlight.trainer;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Four-layer calculation for Standard three-type recipes only. Pair balance is separate. */
final class HarmonicResonanceBalance {
    enum Channel { DEFENSE, MOBILITY, RECOVERY, UTILITY, OFFENSE }

    /** Order is Survival > Mobility > Recovery > Utility > Offense, then fixed order in each tier. */
    enum Synergy {
        FORTIFIED_CORE("fortified_core", "defense", "toughness", "stability"),
        REINFORCED_SYSTEMS("reinforced_systems", "armor", "durability", "technology"),
        PRIMAL_CORE("primal_core", "vitality", "defense", "power"),
        AERIAL_MOMENTUM("aerial_momentum", "mobility", "speed", "flight"),
        CURRENT_HARMONY("current_harmony", "aquatic", "flow", "recovery"),
        SHADOW_HARMONY("shadow_harmony", "night", "stealth", "ambush"),
        SPECTRAL_FLOW("spectral_flow", "spirit", "stealth", "cooldown"),
        LIVING_CYCLE("living_cycle", "nature", "growth", "healing"),
        REGENERATIVE_HARMONY("regenerative_harmony", "recovery", "healing", "cleanse"),
        PURIFYING_LIGHT("purifying_light", "radiance", "healing", "cleanse"),
        EXCAVATOR_HARMONY("excavator_harmony", "mining", "toughness", "durability"),
        THERMAL_PROCESSING("thermal_processing", "heat", "smelting", "technology"),
        INSIGHT_HARMONY("insight_harmony", "xp", "cooldown", "detection"),
        ADAPTIVE_HARMONY("adaptive_harmony", "versatility", "amplify"),
        COMBAT_RHYTHM("combat_rhythm", "melee", "power", "offense"),
        DISRUPTIVE_RESONANCE("disruptive_resonance", "debuff", "control", "detection"),
        SHOCKWAVE_HARMONY("shockwave_harmony", "impact", "control", "power"),
        OVERCLOCK_HARMONY("overclock_harmony", "energy", "speed", "technology");

        final String id;
        final List<String> required;
        Synergy(String id, String... required) { this.id = id; this.required = List.of(required); }

        boolean matches(List<String> types, Map<String, List<String>> traits) {
            Set<String> union = new HashSet<>(), contributors = new HashSet<>();
            for (String type : types) {
                List<String> own = traits.get(type);
                if (own == null) return false;
                union.addAll(own);
                if (required.stream().anyMatch(own::contains)) contributors.add(type);
            }
            if (!union.containsAll(required)) return false;
            if (this == REINFORCED_SYSTEMS) {
                // Armor, durability and technology all belong to Steel. Require another type's support.
                return types.stream().filter(type -> !type.equals("steel"))
                        .anyMatch(type -> traits.get(type).contains("stability")
                                || traits.get(type).contains("defense"));
            }
            if (this == ADAPTIVE_HARMONY) return types.contains("normal") && types.size() == 3;
            return contributors.size() >= 2;
        }
    }

    record Result(StandardResonanceBalance.Bonus bonus, Channel dominant, Synergy synergy) {}
    private HarmonicResonanceBalance() {}

    static Result calculate(ResonanceAutogenCatalog.Entry entry) {
        Map<String, List<String>> traits = ResonanceAutogenCatalog.typeTraits();
        EnumMap<Channel, Set<String>> supporters = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) supporters.put(channel, new HashSet<>());
        for (String type : entry.types()) {
            for (String trait : traits.get(type)) supporters.get(channel(trait)).add(type);
        }
        Channel dominant = null;
        int most = 1;
        for (Channel channel : Channel.values()) { // enum order is the tie-break
            int count = supporters.get(channel).size();
            if (count > most) { dominant = channel; most = count; }
        }
        double factor = most == 3 ? 1.25 : most == 2 ? 1.15 : 1;
        double mobility = supporters.get(Channel.MOBILITY).isEmpty() ? 0 : .015;
        double offense = supporters.get(Channel.OFFENSE).isEmpty() ? 0 : .015;
        double utility = supporters.get(Channel.UTILITY).isEmpty() ? 0 : .015;
        double defense = supporters.get(Channel.DEFENSE).isEmpty() ? 0 : .5;
        double recovery = supporters.get(Channel.RECOVERY).isEmpty() ? 0 : .015;
        if (dominant == Channel.MOBILITY) mobility *= factor;
        if (dominant == Channel.OFFENSE) offense *= factor;
        if (dominant == Channel.UTILITY) utility *= factor;
        if (dominant == Channel.DEFENSE) defense *= factor;
        if (dominant == Channel.RECOVERY) recovery *= factor;
        var base = make(mobility, offense, utility, recovery, defense,
                offense * (.01 / .015) + utility * (.003 / .015),
                defense * (.01 / .5) + mobility * (.003 / .015),
                recovery, mobility, 0, 0, 1);
        Synergy selected = null;
        for (Synergy candidate : Synergy.values()) {
            if (candidate.matches(entry.types(), traits)) { selected = candidate; break; }
        }
        var extra = selected == null ? StandardResonanceBalance.Bonus.NONE
                : synergyBonus(selected, entry.types(), traits);
        return new Result(combine(base, extra), dominant, selected);
    }

    private static StandardResonanceBalance.Bonus synergyBonus(Synergy synergy, List<String> types,
                                                                Map<String, List<String>> traits) {
        // Each synergy has only one nonzero Pokemon return category. Player effects are conservative.
        return switch (synergy) {
            case FORTIFIED_CORE -> make(0, 0, 0, 0, 1, 0, .03, 0, 0, 0, 0, 1);
            case REINFORCED_SYSTEMS -> make(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, .10, 1);
            case DISRUPTIVE_RESONANCE -> make(0, 0, 0, 0, .5, 0, 0, 0, 0, 0, 0, .90);
            case PRIMAL_CORE -> make(0, 0, 0, 0, 1, 0, .03, 0, 0, 0, 0, 1);
            case AERIAL_MOMENTUM -> make(.04, 0, 0, 0, 0, 0, 0, 0, .05, 0, 0, 1);
            case CURRENT_HARMONY -> make(.03, 0, 0, 0, 0, 0, 0, .05, 0, 0, 0, 1);
            case SHADOW_HARMONY -> make(.03, 0, 0, 0, 0, .03, 0, 0, 0, 0, 0, 1);
            case OVERCLOCK_HARMONY -> make(.03, .03, 0, 0, 0, 0, 0, 0, .05, 0, 0, 1);
            case SPECTRAL_FLOW -> make(.03, 0, 0, 0, 0, 0, .02, 0, 0, 0, 0, 1);
            case LIVING_CYCLE -> make(0, 0, 0, .06, 0, 0, 0, .08, 0, 0, 0, 1);
            case REGENERATIVE_HARMONY -> make(0, 0, 0, .06, 0, 0, 0, .08, 0, 0, 0, 1);
            case PURIFYING_LIGHT -> make(0, 0, 0, .05, 0, 0, 0, 0, 0, 0, 0, .90);
            case EXCAVATOR_HARMONY -> make(0, 0, .05, 0, 0, 0, .02, 0, 0, 0, 0, 1);
            case THERMAL_PROCESSING -> make(0, 0, .04, 0, 0, .03, 0, 0, 0, 0, 0, 1);
            case INSIGHT_HARMONY -> make(0, 0, .03, 0, 0, 0, 0, .05, 0, 0, 0, 1);
            case COMBAT_RHYTHM -> make(0, .05, 0, 0, 0, .05, 0, 0, 0, 0, 0, 1);
            case SHOCKWAVE_HARMONY -> make(0, .03, 0, 0, 0, 0, 0, 0, 0, .10, 0, 1);
            case ADAPTIVE_HARMONY -> adaptive(types, traits);
        };
    }

    private static StandardResonanceBalance.Bonus adaptive(List<String> types,
                                                             Map<String, List<String>> traits) {
        EnumMap<Channel, Integer> support = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) support.put(channel, 0);
        for (String type : types) if (!type.equals("normal")) {
            Set<Channel> own = EnumSet.noneOf(Channel.class);
            for (String trait : traits.get(type)) own.add(channel(trait));
            for (Channel channel : own) support.put(channel, support.get(channel) + 1);
        }
        Channel best = null;
        int count = 0;
        for (Channel channel : Channel.values()) {
            if (support.get(channel) > count) { best = channel; count = support.get(channel); }
        }
        if (best == null) return StandardResonanceBalance.Bonus.NONE;
        return switch (best) {
            case DEFENSE -> make(0, 0, 0, 0, .5, 0, .02, 0, 0, 0, 0, 1);
            case MOBILITY -> make(.03, 0, 0, 0, 0, 0, 0, 0, .03, 0, 0, 1);
            case RECOVERY -> make(0, 0, 0, .04, 0, 0, 0, .04, 0, 0, 0, 1);
            case UTILITY -> make(0, 0, .03, 0, 0, .01, 0, 0, 0, 0, 0, 1);
            case OFFENSE -> make(0, .03, 0, 0, 0, .03, 0, 0, 0, 0, 0, 1);
        };
    }

    private static StandardResonanceBalance.Bonus make(double movement, double attack, double mining,
            double recovery, double armor, double damage, double reduction, double pokemonRecovery,
            double pokemonMovement, double knockback, double kbResistance, double harmfulDuration) {
        return new StandardResonanceBalance.Bonus(
                new StandardResonanceBalance.PlayerBonus(movement, attack, mining, recovery, armor),
                new ResonanceLinkBalance.PokemonBonus(damage, reduction, pokemonRecovery,
                        pokemonMovement, knockback, kbResistance, harmfulDuration));
    }

    private static StandardResonanceBalance.Bonus combine(StandardResonanceBalance.Bonus base,
                                                           StandardResonanceBalance.Bonus extra) {
        var a = base.player(); var b = extra.player();
        var x = base.pokemon(); var y = extra.pokemon();
        return make(Math.min(.08, a.movement() + b.movement()),
                Math.min(.08, a.attackSpeed() + b.attackSpeed()),
                Math.min(.08, a.mining() + b.mining()),
                Math.min(.08, a.recovery() + b.recovery()),
                Math.min(2, a.armor() + b.armor()),
                Math.min(.05, x.damage() + y.damage()),
                Math.min(.05, x.reduction() + y.reduction()),
                Math.min(.10, x.recovery() + y.recovery()),
                Math.min(.10, x.movement() + y.movement()),
                x.knockback() + y.knockback(), x.knockbackResistance() + y.knockbackResistance(),
                Math.min(x.harmfulDuration(), y.harmfulDuration()));
    }

    private static Channel channel(String trait) {
        return switch (trait) {
            case "aquatic", "flow", "speed", "mobility", "flight", "stealth", "fall", "night", "evasion" -> Channel.MOBILITY;
            case "heat", "offense", "energy", "melee", "power", "ambush", "spirit", "radiance", "impact" -> Channel.OFFENSE;
            case "smelting", "growth", "efficiency", "mining", "technology", "durability", "versatility", "amplify", "xp", "cooldown", "detection" -> Channel.UTILITY;
            case "control", "cold", "defense", "debuff", "resistance", "stability", "toughness", "armor" -> Channel.DEFENSE;
            case "recovery", "nature", "vitality", "healing", "cleanse" -> Channel.RECOVERY;
            default -> throw new IllegalStateException("Unknown resonance trait: " + trait);
        };
    }
}
