package starlight.trainer;

import java.util.List;

/** Conservative v5 Standard bonuses. One exact recipe contributes once; Signature overrides it. */
public final class StandardResonanceBalance {
    public record PlayerBonus(double movement, double attackSpeed, double mining, double recovery, double armor) {
        static final PlayerBonus NONE = new PlayerBonus(0, 0, 0, 0, 0);
    }

    public record Bonus(PlayerBonus player, ResonanceLinkBalance.PokemonBonus pokemon) {
        static final Bonus NONE = new Bonus(PlayerBonus.NONE, ResonanceLinkBalance.PokemonBonus.NONE);
    }

    private enum Channel { MOBILITY, OFFENSE, UTILITY, DEFENSE, RECOVERY }
    private StandardResonanceBalance() {}

    static void requireKnownTag(String trait) {
        channel(trait);
    }

    public static Bonus forId(String id) {
        var entry = ResonanceAutogenCatalog.findId(id);
        if (entry == null || entry.signature()) return Bonus.NONE;
        return entry.types().size() == 3
                ? HarmonicResonanceBalance.calculate(entry).bonus()
                : fromTraits(entry.primaryTraits());
    }

    /** Selected Standard harmonic synergy, or null for a pair, Signature or unknown recipe. */
    public static String synergyId(String id) {
        var entry = ResonanceAutogenCatalog.findId(id);
        if (entry == null || entry.signature() || entry.types().size() != 3) return null;
        var synergy = HarmonicResonanceBalance.calculate(entry).synergy();
        return synergy == null ? null : synergy.id;
    }

    /** Dominant category in snake_case, or null when no category has two participating types. */
    public static String dominantChannel(String id) {
        var entry = ResonanceAutogenCatalog.findId(id);
        if (entry == null || entry.signature() || entry.types().size() != 3) return null;
        var dominant = HarmonicResonanceBalance.calculate(entry).dominant();
        return dominant == null ? null : dominant.name().toLowerCase(java.util.Locale.ROOT);
    }

    static Bonus fromTraits(List<String> traits) {
        int mobility = 0, offense = 0, utility = 0, defense = 0, recovery = 0;
        for (String trait : traits) {
            switch (channel(trait)) {
                case MOBILITY -> mobility++;
                case OFFENSE -> offense++;
                case UTILITY -> utility++;
                case DEFENSE -> defense++;
                case RECOVERY -> recovery++;
            }
        }
        PlayerBonus player = new PlayerBonus(
                Math.min(.08, mobility * .015), Math.min(.08, offense * .015),
                Math.min(.08, utility * .015), Math.min(.08, recovery * .015),
                Math.min(2, defense * .5));
        var pokemon = new ResonanceLinkBalance.PokemonBonus(
                Math.min(.05, offense * .01 + utility * .003),
                Math.min(.05, defense * .01 + mobility * .003),
                Math.min(.10, recovery * .015), Math.min(.10, mobility * .015), 0, 0, 1);
        return new Bonus(player, pokemon);
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
