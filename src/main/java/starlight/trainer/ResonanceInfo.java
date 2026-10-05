package starlight.trainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What each type resonance does, as translatable lines for the type-resonance screen.
 * Every number is read from {@link AffinityBalance}, the same values the effect code uses,
 * so the screen cannot drift from the game. Pure Java so it is unit tested; the client turns
 * a {@link Line} into a translatable component.
 */
public final class ResonanceInfo {
    public static final String PREFIX = "resonance.super_pallet_towner.";

    /** One translatable line: {@code PREFIX + key} with string arguments. */
    public record Line(String key, List<String> args) {
        static Line of(String key, Object... args) {
            List<String> strings = new ArrayList<>(args.length);
            for (Object arg : args) strings.add(String.valueOf(arg));
            return new Line(key, List.copyOf(strings));
        }

        public String translationKey() {
            return PREFIX + key;
        }
    }

    /** Effects of one type: trainer side, Pokémon side, and how the effect code reads the spec. */
    public record TypeInfo(List<Line> player, List<Line> pokemon, List<Line> notes) {}

    /**
     * Trainer stats that several types add to. The "active effects" view shows these as one
     * total each (see {@link #playerTotals}) instead of per type.
     */
    private static final Set<String> SUMMED = Set.of("stat.move", "stat.move_night", "stat.attack_speed",
            "stat.mining", "stat.ore_mining", "stat.regen", "stat.fall");

    private ResonanceInfo() {}

    /** Percent of a fraction, at most one decimal and without trailing zeros: 0.07 → "7", 0.875 → "87.5". */
    public static String pct(double fraction) {
        return new BigDecimal(fraction * 100).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String seconds(int ticks) {
        return new BigDecimal(ticks / 20.0).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** The effects of a configured type, or {@code null} for a type without defined effects. */
    public static TypeInfo of(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        if (!AffinityView.isConfigured(t)) return null;
        Set<String> one = Set.of(t);
        List<Line> player = new ArrayList<>();
        List<Line> pokemon = new ArrayList<>();
        List<Line> notes = new ArrayList<>();

        // ---- trainer
        switch (t) {
            case "fire" -> {
                player.add(Line.of("fire.immunity"));
                player.add(Line.of("stat.lava", pct(1 - AffinityBalance.playerDamageMultiplier(one, false, true))));
            }
            case "water" -> {
                player.add(Line.of("water.breathing"));
                player.add(Line.of("stat.swim", pct(AffinityBalance.WATER_SWIM_SPEED)));
            }
            case "grass" -> player.add(Line.of("grass.hunger", pct(AffinityBalance.GRASS_EXHAUSTION_SAVING)));
            case "ice" -> {
                player.add(Line.of("ice.freeze"));
                player.add(Line.of("ice.slow", pct(AffinityBalance.ICE_SLOW_CHANCE),
                        seconds(AffinityBalance.ICE_SLOW_TICKS)));
            }
            case "fighting" -> player.add(Line.of("fighting.knockback", pct(AffinityBalance.FIGHTING_KNOCKBACK)));
            case "poison" -> {
                player.add(Line.of("poison.immunity"));
                player.add(Line.of("poison.proc", pct(AffinityBalance.POISON_PROC_CHANCE),
                        seconds(AffinityBalance.POISON_PROC_TICKS)));
            }
            case "ground" -> {
                player.add(Line.of("ground.armor", AffinityBalance.GROUND_ARMOR));
                player.add(Line.of("ground.knockback", pct(AffinityBalance.GROUND_KNOCKBACK_RESISTANCE)));
            }
            case "flying" -> player.add(Line.of("flying.flight"));
            case "psychic" -> {
                player.add(Line.of("psychic.xp", pct(AffinityBalance.PSYCHIC_PLAYER_XP)));
                player.add(Line.of("psychic.cooldown", pct(1 - AffinityBalance.PSYCHIC_COOLDOWN)));
            }
            case "bug" -> player.add(Line.of("bug.jump", pct(AffinityBalance.BUG_JUMP_STRENGTH)));
            case "ghost" -> player.add(Line.of("ghost.detection", pct(1 - AffinityBalance.GHOST_DETECTION_RANGE)));
            case "dragon" -> {
                player.add(Line.of("dragon.health", AffinityBalance.DRAGON_MAX_HEALTH,
                        AffinityBalance.DRAGON_MAX_HEALTH / 2));
                player.add(Line.of("stat.damage_taken",
                        pct(1 - AffinityBalance.playerDamageMultiplier(one, false, false))));
            }
            case "dark" -> player.add(Line.of("dark.night_vision"));
            case "steel" -> {
                player.add(Line.of("steel.toughness", AffinityBalance.STEEL_TOUGHNESS));
                player.add(Line.of("steel.durability", pct(AffinityBalance.STEEL_DURABILITY_SAVING)));
            }
            case "fairy" -> {
                player.add(Line.of("fairy.duration", pct(1 - AffinityBalance.FAIRY_PLAYER_HARMFUL_DURATION)));
                player.add(Line.of("fairy.healing", pct(AffinityBalance.FAIRY_RECEIVED_HEALING)));
            }
            case "light" -> {
                player.add(Line.of("light.darkness"));
                player.add(Line.of("light.blindness", pct(1 - AffinityBalance.LIGHT_BLINDNESS_DURATION)));
            }
            case "sound" -> {
                player.add(Line.of("sound.knockback_resistance", pct(AffinityBalance.SOUND_KNOCKBACK_RESISTANCE)));
                player.add(Line.of("sound.knockback", pct(AffinityBalance.SOUND_OUTGOING_KNOCKBACK)));
            }
            default -> { }
        }
        double moveDay = AffinityBalance.playerMovement(one, false);
        double moveNight = AffinityBalance.playerMovement(one, true);
        if (moveDay > 0) player.add(Line.of("stat.move", pct(moveDay)));
        else if (moveNight > 0) player.add(Line.of("stat.move_night", pct(moveNight)));
        double attack = AffinityBalance.playerAttackSpeed(one);
        if (attack > 0) player.add(Line.of("stat.attack_speed", pct(attack)));
        double mining = AffinityBalance.rockMining(one) + AffinityBalance.normalMining(one);
        if (mining > 0) player.add(Line.of("stat.mining", pct(mining)));
        if (AffinityBalance.rockMining(one) > 0) {
            player.add(Line.of("stat.ore_mining", pct(AffinityBalance.ROCK_ORE_MINING), pct(oreTotal(mining))));
        }
        double regen = AffinityBalance.playerNaturalRegen(one);
        if (regen > 0) player.add(Line.of("stat.regen", pct(regen)));
        double fall = fallReduction(one);
        if (fall > 0) player.add(Line.of("stat.fall", pct(fall)));

        // ---- matching Pokémon
        List<String> self = List.of(t);
        double damage = AffinityBalance.pokemonDamageBonusRaw(one, self, false, false, false);
        if (damage > 0) pokemon.add(Line.of("mon.damage", pct(damage)));
        double water = AffinityBalance.pokemonDamageBonusRaw(one, self, true, false, false) - damage;
        if (water > 0) pokemon.add(Line.of("mon.damage_water", pct(water)));
        double night = AffinityBalance.pokemonDamageBonusRaw(one, self, false, true, false) - damage;
        if (night > 0) pokemon.add(Line.of("mon.damage_night", pct(night)));
        double physical = AffinityBalance.pokemonDamageBonusRaw(one, self, false, false, true) - damage;
        if (physical > 0) pokemon.add(Line.of("mon.damage_physical", pct(physical)));
        double reduction = AffinityBalance.pokemonDamageReductionRaw(one, self);
        if (reduction > 0) pokemon.add(Line.of("mon.reduction", pct(reduction)));
        double recovery = AffinityBalance.pokemonRecoveryBonusRaw(one, self);
        if (recovery > 0) pokemon.add(Line.of("mon.recovery", pct(recovery)));
        if (AffinityBalance.pokemonFireEnvironmentHalved(one, self)) {
            pokemon.add(Line.of("mon.fire_environment", pct(AffinityBalance.POKEMON_FIRE_ENVIRONMENT)));
        }
        if (AffinityBalance.pokemonFreezeImmune(one, self)) pokemon.add(Line.of("mon.freeze"));
        if (AffinityBalance.pokemonHarmfulDurationReduced(one, self)) {
            pokemon.add(Line.of("mon.duration", pct(1 - AffinityBalance.POKEMON_HARMFUL_DURATION)));
        }
        if (AffinityBalance.pokemonFlyingMovement(one, self)) {
            pokemon.add(Line.of("mon.move", pct(AffinityBalance.POKEMON_FLYING_MOVEMENT)));
        }
        if ("air".equals(t)) pokemon.add(Line.of("mon.move", pct(AffinityBalance.AIR_POKEMON_MOVEMENT)));
        if ("light".equals(t)) pokemon.add(Line.of("mon.damage_undead", pct(AffinityBalance.LIGHT_UNDEAD_DAMAGE)));
        if ("sound".equals(t)) pokemon.add(Line.of("mon.knockback", pct(AffinityBalance.SOUND_POKEMON_KNOCKBACK)));
        double exp = AffinityBalance.pokemonExpBonus(one, self);
        if (exp > 0) pokemon.add(Line.of("mon.exp", pct(exp)));

        // ---- caps that this type's Pokémon effects count towards
        if (damage + water + night + physical > 0 || "light".equals(t)) {
            notes.add(Line.of("cap.damage", pct(AffinityBalance.POKEMON_DAMAGE_CAP)));
        }
        if (reduction > 0) notes.add(Line.of("cap.reduction", pct(AffinityBalance.POKEMON_REDUCTION_CAP)));
        if (recovery > 0) notes.add(Line.of("cap.recovery", pct(AffinityBalance.POKEMON_RECOVERY_CAP)));
        // ---- how the implementation reads this type's Balance v1 wording
        int count = NOTE_COUNT.getOrDefault(t, 0);
        for (int i = 1; i <= count; i++) notes.add(Line.of("note." + t + "." + i));
        return new TypeInfo(List.copyOf(player), List.copyOf(pokemon), List.copyOf(notes));
    }

    /** Interpretation notes per type, as {@code note.<type>.<n>} keys in the lang files. */
    static final java.util.Map<String, Integer> NOTE_COUNT = java.util.Map.ofEntries(
            java.util.Map.entry("fire", 2), java.util.Map.entry("water", 2), java.util.Map.entry("grass", 2),
            java.util.Map.entry("electric", 1), java.util.Map.entry("ice", 2), java.util.Map.entry("fighting", 2),
            java.util.Map.entry("poison", 1), java.util.Map.entry("flying", 2), java.util.Map.entry("psychic", 2),
            java.util.Map.entry("bug", 2), java.util.Map.entry("rock", 1), java.util.Map.entry("ghost", 1),
            java.util.Map.entry("dragon", 1), java.util.Map.entry("dark", 1), java.util.Map.entry("steel", 1),
            java.util.Map.entry("fairy", 1));

    private static double oreTotal(double generalMining) {
        return (1 + generalMining) * AffinityBalance.oreMiningFactor(generalMining) - 1;
    }

    private static double oreTotalWithStandard(double totalMining, double singleTypeMining) {
        return (1 + totalMining) * AffinityBalance.oreMiningFactor(singleTypeMining) - 1;
    }

    /** Fall-only reduction (Ghost, Flying), without the general damage reduction. */
    private static double fallReduction(Set<String> active) {
        return 1 - AffinityBalance.playerDamageMultiplier(active, true, false)
                / AffinityBalance.playerDamageMultiplier(active, false, false);
    }

    /** Trainer lines of a type that are not summed with other types. */
    public static List<Line> playerSpecials(String type) {
        TypeInfo info = of(type);
        if (info == null) return List.of();
        return info.player().stream().filter(line -> !SUMMED.contains(line.key())).toList();
    }

    /** Totals of the trainer stats that several active types add to. */
    public static List<Line> playerTotals(Set<String> active) {
        return playerTotals(active, Set.of());
    }

    /** Current trainer totals where a link defines an overall target value. */
    public static List<Line> playerTotals(Set<String> active, Set<String> links) {
        List<Line> lines = new ArrayList<>();
        double day = AffinityBalance.playerMovement(active, false);
        double night = AffinityBalance.playerMovement(active, true);
        var standard = links.size() == 1
                ? StandardResonanceBalance.forId(links.iterator().next()).player()
                : StandardResonanceBalance.PlayerBonus.NONE;
        if (links.contains("air_electric") || links.contains("air_electric_flying")) {
            day = Math.max(day, .15);
            night = Math.max(night, .15);
        }
        day += Math.min(standard.movement(), Math.max(0, .25 - day));
        night += Math.min(standard.movement(), Math.max(0, .25 - night));
        if (night > day && day > 0) lines.add(Line.of("total.move_day_night", pct(day), pct(night)));
        else if (night > day) lines.add(Line.of("stat.move_night", pct(night)));
        else if (day > 0) lines.add(Line.of("stat.move", pct(day)));
        double attack = AffinityBalance.playerAttackSpeed(active);
        if (links.contains("electric_sound")) attack = Math.max(attack, .12);
        if (links.contains("electric_sound_steel")) attack = Math.max(attack, .15);
        if (links.contains("normal_fighting")) attack = Math.max(attack, .10);
        attack += Math.min(standard.attackSpeed(), Math.max(0, .25 - attack));
        if (attack > 0) lines.add(Line.of("stat.attack_speed", pct(attack)));
        double mining = AffinityBalance.rockMining(active) + AffinityBalance.normalMining(active);
        if (links.contains("normal_fighting")) mining = Math.max(mining, .10);
        mining += Math.min(standard.mining(), Math.max(0, .40 - mining));
        if (AffinityBalance.rockMining(active) > 0) {
            lines.add(Line.of("total.mining_ore", pct(mining), pct(oreTotalWithStandard(mining,
                    AffinityBalance.rockMining(active) + AffinityBalance.normalMining(active)))));
        } else if (mining > 0) {
            lines.add(Line.of("stat.mining", pct(mining)));
        }
        double regen = AffinityBalance.playerNaturalRegen(active);
        if (links.contains("fairy_grass_water")) regen = Math.max(regen, .30);
        regen += Math.min(standard.recovery(), Math.max(0, .40 - regen));
        if (regen > 0) lines.add(Line.of("stat.regen", pct(regen)));
        double fall = fallReduction(active);
        if (links.contains("air_flying") || links.contains("air_electric_flying")
                || links.contains("dark_ghost_psychic")) fall = 1;
        if (fall > 0) lines.add(Line.of("stat.fall", pct(fall)));
        return lines;
    }

    /**
     * What one party Pokémon gets now from the active types: per-Pokémon totals after the
     * caps, with conditional damage shown separately. Empty when none of its types is active.
     */
    public static List<Line> pokemonNow(Set<String> active, Collection<String> types) {
        return pokemonNow(active, types, Set.of());
    }

    /** Current per-Pokémon totals, including the server-confirmed exact link recipe. */
    public static List<Line> pokemonNow(Set<String> active, Collection<String> types, Set<String> links) {
        List<Line> lines = new ArrayList<>();
        var link = ResonanceLinkBalance.pokemon(links, types, false, false, false);
        var linkNight = ResonanceLinkBalance.pokemon(links, types, true, false, false);
        var linkPhysical = ResonanceLinkBalance.pokemon(links, types, false, true, false);
        var linkAll = ResonanceLinkBalance.pokemon(links, types, true, true, false);
        double always = AffinityBalance.pokemonDamageBonusRaw(active, types, false, false, false) + link.damage();
        double water = AffinityBalance.pokemonDamageBonusRaw(active, types, true, false, false) + link.damage();
        double night = AffinityBalance.pokemonDamageBonusRaw(active, types, false, true, false) + linkNight.damage();
        double physical = AffinityBalance.pokemonDamageBonusRaw(active, types, false, false, true) + linkPhysical.damage();
        double all = AffinityBalance.pokemonDamageBonusRaw(active, types, true, true, true) + linkAll.damage();
        double cap = AffinityBalance.POKEMON_DAMAGE_CAP;
        if (always > 0) capped(lines, "mon.damage", always, cap);
        if (water > always) capped(lines, "now.damage_water", water, cap);
        if (night > always) capped(lines, "now.damage_night", night, cap);
        if (physical > always) capped(lines, "now.damage_physical", physical, cap);
        int conditions = (water > always ? 1 : 0) + (night > always ? 1 : 0) + (physical > always ? 1 : 0);
        if (conditions > 1) capped(lines, "now.damage_all", all, cap);
        double reduction = AffinityBalance.pokemonDamageReductionRaw(active, types) + link.reduction();
        if (reduction > 0) capped(lines, "mon.reduction", reduction, AffinityBalance.POKEMON_REDUCTION_CAP);
        double nightReduction = AffinityBalance.pokemonDamageReductionRaw(active, types) + linkNight.reduction();
        if (nightReduction > reduction) capped(lines, "now.reduction_night", nightReduction,
                AffinityBalance.POKEMON_REDUCTION_CAP);
        double recovery = AffinityBalance.pokemonRecoveryBonusRaw(active, types) + link.recovery();
        if (recovery > 0) capped(lines, "mon.recovery", recovery, AffinityBalance.POKEMON_RECOVERY_CAP);
        double fireFactor = AffinityBalance.pokemonFireEnvironmentHalved(active, types)
                ? AffinityBalance.POKEMON_FIRE_ENVIRONMENT : 1;
        if (links.contains("fire_rock") && types.stream().anyMatch(Set.of("fire", "rock")::contains)) {
            fireFactor = Math.min(fireFactor, .25);
        }
        if (links.contains("dragon_fire_flying") && types.stream()
                .anyMatch(Set.of("dragon", "fire", "flying")::contains)) fireFactor = 0;
        if (fireFactor < 1) lines.add(Line.of("mon.fire_environment", pct(1 - fireFactor)));
        if (AffinityBalance.pokemonFreezeImmune(active, types)) lines.add(Line.of("mon.freeze"));
        double harmful = Math.min(AffinityBalance.pokemonHarmfulDurationReduced(active, types)
                ? AffinityBalance.POKEMON_HARMFUL_DURATION : 1, link.harmfulDuration());
        if (harmful < 1) lines.add(Line.of("mon.duration", pct(1 - harmful)));
        double movement = Math.min(.25, AffinityBalance.pokemonWorldMovement(active, types) + link.movement());
        if (movement > 0) lines.add(Line.of("mon.move", pct(movement)));
        double outgoingKnockback = Math.min(.40, (AffinityBalance.pokemonSoundKnockback(active, types)
                ? AffinityBalance.SOUND_POKEMON_KNOCKBACK : 0) + link.knockback());
        if (outgoingKnockback > 0) lines.add(Line.of("mon.knockback", pct(outgoingKnockback)));
        double kbResistance = Math.min(.40, link.knockbackResistance());
        if (kbResistance > 0) lines.add(Line.of("mon.knockback_resistance", pct(kbResistance)));
        double undead = always + (active.contains("light") && types.contains("light")
                ? AffinityBalance.LIGHT_UNDEAD_DAMAGE : 0)
                + ResonanceLinkBalance.pokemon(links, types, false, false, true).damage() - link.damage();
        if (undead > always) {
            capped(lines, "mon.damage_undead", undead, AffinityBalance.POKEMON_DAMAGE_CAP);
        }
        double exp = AffinityBalance.pokemonExpBonus(active, types);
        if (exp > 0) lines.add(Line.of("mon.exp", pct(exp)));
        return lines;
    }

    // ------------------------------------------------------------------ resonance links

    /** One-line effect of a link recipe, or {@code null} for an unknown id. */
    public static Line linkSummary(String recipe) {
        var entry = ResonanceAutogenCatalog.findId(recipe);
        if (entry == null) return null;
        if (entry.signature()) return Line.of("link." + recipe + ".summary");
        if (entry.types().size() == 3) {
            String synergy = StandardResonanceBalance.synergyId(recipe);
            if (synergy != null && !synergy.isBlank()) {
                return Line.of("link.standard.synergy." + synergy);
            }
            return Line.of("link.standard.harmonic.summary");
        }
        return Line.of("link.standard.summary");
    }

    /**
     * Full description of a link recipe: what changes, the tier ladder and speeds (from
     * {@link ResonanceLinks}, the values the effect code uses), the edge cases and the condition.
     */
    public static List<Line> linkDetails(String recipe) {
        var entry = ResonanceAutogenCatalog.findId(recipe);
        if (entry != null && !entry.signature()) {
            var bonus = StandardResonanceBalance.forId(recipe);
            var player = bonus.player();
            var pokemon = bonus.pokemon();
            List<Line> lines = new ArrayList<>();
            if (entry.types().size() == 3) {
                String dominant = StandardResonanceBalance.dominantChannel(recipe);
                if (dominant != null && !dominant.isBlank()) {
                    lines.add(Line.of("link.standard.dominant." + dominant));
                }
                String synergy = StandardResonanceBalance.synergyId(recipe);
                if (synergy != null) {
                    Line extra = switch (synergy) {
                        case "excavator_harmony", "thermal_processing", "reinforced_systems" ->
                                Line.of("link.standard.extra.durability", "5");
                        case "insight_harmony" -> Line.of("link.standard.extra.cooldown", "5");
                        case "spectral_flow" -> Line.of("link.standard.extra.cooldown", "3");
                        case "fortified_core" -> Line.of("link.standard.extra.knockback_resistance", "10");
                        default -> null;
                    };
                    if (extra != null) lines.add(extra);
                }
            }
            lines.add(Line.of("link.standard.player", pct(player.movement()), pct(player.attackSpeed()),
                    pct(player.mining()), pct(player.recovery()),
                    BigDecimal.valueOf(player.armor()).stripTrailingZeros().toPlainString()));
            lines.add(Line.of("link.standard.pokemon", pct(pokemon.damage()), pct(pokemon.reduction()),
                    pct(pokemon.recovery()), pct(pokemon.movement())));
            if (entry.types().size() == 3) {
                if (pokemon.knockback() > 0) {
                    lines.add(Line.of("mon.knockback", pct(pokemon.knockback())));
                }
                if (pokemon.knockbackResistance() > 0) {
                    lines.add(Line.of("mon.knockback_resistance", pct(pokemon.knockbackResistance())));
                }
                if (pokemon.harmfulDuration() < 1) {
                    lines.add(Line.of("mon.duration", pct(1 - pokemon.harmfulDuration())));
                }
            }
            lines.add(Line.of("link.condition"));
            return List.copyOf(lines);
        }
        if (!ResonanceLinks.ROCK_STEEL.equals(recipe)) {
            return linkSummary(recipe) == null ? List.of()
                    : List.of(Line.of("link." + recipe + ".detail"), Line.of("link.condition"));
        }
        return List.of(
                Line.of("link.rock_steel.tier"),
                Line.of("link.rock_steel.speed", speed(ResonanceLinks.WOOD), speed(ResonanceLinks.STONE),
                        speed(ResonanceLinks.IRON), speed(ResonanceLinks.DIAMOND), speed(ResonanceLinks.NETHERITE)),
                Line.of("link.rock_steel.gold", speed(ResonanceLinks.GOLD)),
                Line.of("link.rock_steel.top"),
                Line.of("link.rock_steel.stack", pct(AffinityBalance.ROCK_MINING), pct(AffinityBalance.ROCK_ORE_MINING)),
                Line.of("link.rock_steel.item"),
                Line.of("link.rock_steel.scope"),
                Line.of("link.rock_steel.extra"),
                Line.of("link.condition"));
    }

    private static String speed(ResonanceLinks.Tier tier) {
        return new BigDecimal(tier.speed()).stripTrailingZeros().toPlainString();
    }

    /** A total; when the per-Pokémon cap applies, the capped value plus a line with the sum. */
    private static void capped(List<Line> lines, String key, double raw, double cap) {
        if (raw > cap + 1e-9) {
            lines.add(Line.of(key, pct(cap)));
            lines.add(Line.of("capped", pct(raw), pct(cap)));
        } else {
            lines.add(Line.of(key, pct(raw)));
        }
    }
}
