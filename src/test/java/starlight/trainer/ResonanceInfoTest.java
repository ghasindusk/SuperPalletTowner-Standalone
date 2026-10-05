package starlight.trainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The type-resonance screen text against Balance v1 (outputs/Super_Pallet_Towner_Balance_v1.md).
 * Each expected line is written out by hand from the spec table, so a change to the numbers in
 * {@link AffinityBalance} or to the descriptor shows up here.
 */
class ResonanceInfoTest {
    private static String render(ResonanceInfo.Line line) {
        return line.key() + (line.args().isEmpty() ? "" : " " + String.join(" ", line.args()));
    }

    private static List<String> render(List<ResonanceInfo.Line> lines) {
        return lines.stream().map(ResonanceInfoTest::render).toList();
    }

    /** Balance v1, player column: key and the numbers it shows. */
    private static final Map<String, List<String>> PLAYER = Map.ofEntries(
            Map.entry("fire", List.of("fire.immunity", "stat.lava 50")),
            Map.entry("water", List.of("water.breathing", "stat.swim 15")),
            Map.entry("grass", List.of("grass.hunger 10", "stat.regen 15")),
            Map.entry("electric", List.of("stat.move 10", "stat.attack_speed 8")),
            Map.entry("ice", List.of("ice.freeze", "ice.slow 25 3")),
            Map.entry("fighting", List.of("fighting.knockback 20", "stat.attack_speed 12")),
            Map.entry("poison", List.of("poison.immunity", "poison.proc 15 4")),
            Map.entry("ground", List.of("ground.armor 2", "ground.knockback 20")),
            Map.entry("flying", List.of("flying.flight", "stat.fall 50")),
            Map.entry("psychic", List.of("psychic.xp 10", "psychic.cooldown 10")),
            Map.entry("bug", List.of("bug.jump 15", "stat.move 8")),
            Map.entry("rock", List.of("stat.mining 20", "stat.ore_mining 10 30")),
            Map.entry("ghost", List.of("ghost.detection 20", "stat.fall 75")),
            Map.entry("dragon", List.of("dragon.health 4 2", "stat.damage_taken 5")),
            Map.entry("dark", List.of("dark.night_vision", "stat.move_night 10")),
            Map.entry("steel", List.of("steel.toughness 2", "steel.durability 15")),
            Map.entry("fairy", List.of("fairy.duration 25", "fairy.healing 15")),
            Map.entry("normal", List.of("stat.move 5", "stat.attack_speed 5", "stat.mining 5", "stat.regen 5")));

    /** Balance v1, Pokémon column. */
    private static final Map<String, List<String>> POKEMON = Map.ofEntries(
            Map.entry("fire", List.of("mon.damage 7", "mon.fire_environment 50")),
            Map.entry("water", List.of("mon.damage_water 7", "mon.recovery 10")),
            Map.entry("grass", List.of("mon.recovery 15")),
            Map.entry("electric", List.of("mon.damage 7")),
            Map.entry("ice", List.of("mon.reduction 5", "mon.freeze")),
            Map.entry("fighting", List.of("mon.damage_physical 10")),
            Map.entry("poison", List.of("mon.damage 5", "mon.duration 20")),
            Map.entry("ground", List.of("mon.reduction 7")),
            Map.entry("flying", List.of("mon.reduction 5", "mon.move 5")),
            Map.entry("psychic", List.of("mon.exp 7")),
            Map.entry("bug", List.of("mon.reduction 5")),
            Map.entry("rock", List.of("mon.reduction 7")),
            Map.entry("ghost", List.of("mon.reduction 5")),
            Map.entry("dragon", List.of("mon.damage 7", "mon.reduction 5")),
            Map.entry("dark", List.of("mon.damage_night 8")),
            Map.entry("steel", List.of("mon.reduction 10")),
            Map.entry("fairy", List.of("mon.recovery 10", "mon.duration 20")),
            Map.entry("normal", List.of("mon.damage 3", "mon.reduction 3")));

    @ParameterizedTest
    @ValueSource(strings = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"})
    void everyStandardTypeShowsExactlyItsBalanceV1Effects(String type) {
        ResonanceInfo.TypeInfo info = ResonanceInfo.of(type);
        assertNotNull(info, type);
        assertEquals(Set.copyOf(PLAYER.get(type)), Set.copyOf(render(info.player())), type + " player");
        assertEquals(PLAYER.get(type).size(), info.player().size(), type + " player count");
        assertEquals(Set.copyOf(POKEMON.get(type)), Set.copyOf(render(info.pokemon())), type + " pokemon");
        assertEquals(POKEMON.get(type).size(), info.pokemon().size(), type + " pokemon count");
        assertEquals(1, ResonanceInfo.of(type.toUpperCase()).player().size() > 0 ? 1 : 0);
    }

    @Test
    void capsAreListedForTheCategoriesATypeContributesTo() {
        assertTrue(render(ResonanceInfo.of("fire").notes()).contains("cap.damage 15"));
        assertFalse(render(ResonanceInfo.of("fire").notes()).contains("cap.reduction 15"));
        assertTrue(render(ResonanceInfo.of("steel").notes()).contains("cap.reduction 15"));
        assertTrue(render(ResonanceInfo.of("grass").notes()).contains("cap.recovery 25"));
        assertTrue(render(ResonanceInfo.of("dragon").notes()).containsAll(List.of("cap.damage 15", "cap.reduction 15")));
        assertTrue(render(ResonanceInfo.of("fighting").notes()).contains("cap.damage 15"));
        assertFalse(render(ResonanceInfo.of("psychic").notes()).stream().anyMatch(line -> line.startsWith("cap.")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cosmic", "digital", "nuclear", "void", "mystic"})
    void deferredTypesHaveNoEffects(String type) {
        assertNull(ResonanceInfo.of(type));
        assertTrue(ResonanceInfo.playerSpecials(type).isEmpty());
        assertTrue(ResonanceInfo.playerTotals(Set.of(type)).isEmpty());
        assertTrue(ResonanceInfo.pokemonNow(Set.of(type), List.of(type)).isEmpty());
    }

    @Test
    void cobblemaniaTypesShowOnlyImplementedV4Effects() {
        assertEquals(List.of("stat.move 10", "stat.fall 50"), render(ResonanceInfo.of("air").player()));
        assertEquals(List.of("mon.reduction 5", "mon.move 10"), render(ResonanceInfo.of("air").pokemon()));
        assertEquals(List.of("light.darkness", "light.blindness 50", "stat.regen 10"),
                render(ResonanceInfo.of("light").player()));
        assertEquals(List.of("mon.recovery 10", "mon.damage_undead 5"),
                render(ResonanceInfo.of("light").pokemon()));
        assertEquals(List.of("sound.knockback_resistance 20", "sound.knockback 15"),
                render(ResonanceInfo.of("sound").player()));
        assertEquals(List.of("mon.knockback 10"), render(ResonanceInfo.of("sound").pokemon()));
    }

    @Test
    void playerTotalsAddUpTypesThatShareAStat() {
        assertEquals(List.of("total.move_day_night 23 33", "stat.attack_speed 13", "stat.mining 5", "stat.regen 5"),
                render(ResonanceInfo.playerTotals(Set.of("electric", "bug", "normal", "dark"))));
        assertEquals(List.of("stat.move_night 10"), render(ResonanceInfo.playerTotals(Set.of("dark"))));
        assertEquals(List.of("stat.fall 87.5"), render(ResonanceInfo.playerTotals(Set.of("ghost", "flying"))));
        // Dragon's -5% is its own line (a special), not folded into the fall total.
        assertEquals(List.of("stat.fall 75"), render(ResonanceInfo.playerTotals(Set.of("ghost", "dragon"))));
        assertEquals(List.of("stat.move 5", "stat.attack_speed 5", "total.mining_ore 25 35", "stat.regen 5"),
                render(ResonanceInfo.playerTotals(Set.of("rock", "normal"))));
        assertEquals(List.of("stat.regen 20", "stat.move 5", "stat.attack_speed 5", "stat.mining 5").stream().sorted().toList(),
                render(ResonanceInfo.playerTotals(Set.of("grass", "normal"))).stream().sorted().toList());
    }

    @Test
    void summedStatsAreNotRepeatedAsSpecials() {
        assertEquals(List.of("fire.immunity", "stat.lava 50"), render(ResonanceInfo.playerSpecials("fire")));
        assertEquals(List.of("dragon.health 4 2", "stat.damage_taken 5"), render(ResonanceInfo.playerSpecials("dragon")));
        assertTrue(ResonanceInfo.playerSpecials("normal").isEmpty());
        assertEquals(List.of("grass.hunger 10"), render(ResonanceInfo.playerSpecials("grass")));
    }

    @Test
    void pokemonNowShowsPerPokemonTotalsAndTheCap() {
        // Charizard (Fire/Flying) with Fire and Flying active.
        assertEquals(List.of("mon.damage 7", "mon.reduction 5", "mon.fire_environment 50", "mon.move 5"),
                render(ResonanceInfo.pokemonNow(Set.of("fire", "flying"), List.of("fire", "flying"))));
        // Fire + Dragon + Fighting on a Pokémon of all three: 14% always, 24% on melee -> capped 15%.
        assertEquals(List.of("mon.damage 14", "now.damage_physical 15", "capped 24 15", "mon.reduction 5",
                        "mon.fire_environment 50"),
                render(ResonanceInfo.pokemonNow(Set.of("fire", "dragon", "fighting"),
                        List.of("fire", "dragon", "fighting"))));
        // Gyarados (Water/Flying) with Water and Dark active: only the in-water condition applies.
        assertEquals(List.of("now.damage_water 7", "mon.recovery 10"),
                render(ResonanceInfo.pokemonNow(Set.of("water", "dark"), List.of("water", "flying"))));
        // Two conditions: the combined line appears.
        assertEquals(List.of("now.damage_water 7", "now.damage_night 8", "now.damage_all 15", "mon.recovery 10"),
                render(ResonanceInfo.pokemonNow(Set.of("water", "dark"), List.of("water", "dark"))));
        // Steel + Rock reduction 17% -> capped at 15%.
        assertEquals(List.of("mon.reduction 15", "capped 17 15"),
                render(ResonanceInfo.pokemonNow(Set.of("steel", "rock"), List.of("steel", "rock"))));
        // Fairy/Poison: one -20% duration line.
        assertEquals(1, render(ResonanceInfo.pokemonNow(Set.of("fairy", "poison"), List.of("fairy", "poison")))
                .stream().filter(line -> line.startsWith("mon.duration")).count());
        // A selected type the Pokémon lacks gives nothing.
        assertTrue(ResonanceInfo.pokemonNow(Set.of("fire"), List.of("water")).isEmpty());
    }

    @Test
    void activeEffectsIncludeLinkReturnsAndTheirFinalCaps() {
        var fireSteel = render(ResonanceInfo.pokemonNow(Set.of("fire", "steel"),
                List.of("fire", "steel"), Set.of("fire_steel")));
        assertTrue(fireSteel.contains("mon.damage 15"), fireSteel.toString());
        assertTrue(fireSteel.contains("mon.reduction 15"), fireSteel.toString());
        assertTrue(fireSteel.stream().anyMatch(line -> line.startsWith("capped ")), fireSteel.toString());

        var abyss = render(ResonanceInfo.pokemonNow(Set.of("dark", "ghost"),
                List.of("dark", "ghost"), Set.of("dark_ghost")));
        assertTrue(abyss.contains("mon.reduction 5"), abyss.toString());
        assertTrue(abyss.contains("now.reduction_night 15"), abyss.toString());
        assertTrue(abyss.contains("now.damage_night 15"), abyss.toString());

        var molten = render(ResonanceInfo.pokemonNow(Set.of("fire", "rock"),
                List.of("fire", "rock"), Set.of("fire_rock")));
        assertTrue(molten.contains("mon.fire_environment 75"), molten.toString());

        var defense = render(ResonanceInfo.pokemonNow(Set.of("fighting", "dragon"),
                List.of("dragon"), Set.of("dragon_fighting")));
        assertTrue(defense.contains("mon.knockback_resistance 15"), defense.toString());
    }

    @Test
    void playerTotalsShowLinkTargetValues() {
        assertTrue(render(ResonanceInfo.playerTotals(Set.of("electric", "sound"),
                Set.of("electric_sound"))).contains("stat.attack_speed 12"));
        assertTrue(render(ResonanceInfo.playerTotals(Set.of("fairy", "grass", "water"),
                Set.of("fairy_grass_water"))).contains("stat.regen 30"));
        assertTrue(render(ResonanceInfo.playerTotals(Set.of("air", "flying"),
                Set.of("air_flying"))).contains("stat.fall 100"));
    }

    @Test
    void percentFormatting() {
        assertEquals("7", ResonanceInfo.pct(0.07));
        assertEquals("20", ResonanceInfo.pct(1 - 0.80));
        assertEquals("87.5", ResonanceInfo.pct(0.875));
        assertEquals("10", ResonanceInfo.pct(1 - 0.90));
    }

    // ---- lang files: every key the screen can use exists in both languages with matching arguments

    private static final Pattern ENTRY = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** The lang files are flat string maps; Gson is not on the test classpath. */
    private static Map<String, String> lang(String locale) throws Exception {
        String text = Files.readString(Path.of("src/main/resources/assets/super_pallet_towner/lang/" + locale + ".json"),
                StandardCharsets.UTF_8);
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = ENTRY.matcher(text);
        while (m.find()) result.put(m.group(1), m.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
        assertTrue(result.size() > 50, locale + " entries " + result.size());
        return result;
    }

    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");

    private static int argCount(String text) {
        Matcher m = ARG.matcher(text.replace("%%", ""));
        int count = 0;
        int max = 0;
        while (m.find()) {
            count++;
            if (m.group(1) != null) max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return Math.max(count == 0 ? 0 : (max > 0 ? max : count), 0);
    }

    private static List<ResonanceInfo.Line> allLines() {
        List<ResonanceInfo.Line> lines = new ArrayList<>();
        for (String type : AffinityView.STANDARD_TYPES) {
            var info = ResonanceInfo.of(type);
            lines.addAll(info.player());
            lines.addAll(info.pokemon());
            lines.addAll(info.notes());
            for (String other : AffinityView.STANDARD_TYPES) {
                lines.addAll(ResonanceInfo.playerTotals(new java.util.HashSet<>(List.of(type, other))));
                for (String third : AffinityView.STANDARD_TYPES) {
                    lines.addAll(ResonanceInfo.pokemonNow(new java.util.HashSet<>(List.of(type, other, third)),
                            List.of(type, other, third)));
                }
            }
        }
        lines.add(ResonanceInfo.Line.of("scope"));
        for (var entry : ResonanceAutogenCatalog.entries()) {
            lines.add(ResonanceInfo.linkSummary(entry.id()));
            lines.addAll(ResonanceInfo.linkDetails(entry.id()));
        }
        return lines;
    }

    @Test
    void everyLineHasATranslationInBothLanguagesWithTheRightArguments() throws Exception {
        Map<String, String> ja = lang("ja_jp");
        Map<String, String> en = lang("en_us");
        List<ResonanceInfo.Line> lines = allLines();
        Set<String> keys = lines.stream().map(ResonanceInfo.Line::translationKey).collect(Collectors.toSet());
        for (ResonanceInfo.Line line : lines) {
            for (Map<String, String> file : List.of(ja, en)) {
                String key = line.translationKey();
                assertTrue(file.containsKey(key), key);
                assertEquals(line.args().size(), argCount(file.get(key)), key);
            }
        }
        assertTrue(keys.size() > 60, "keys " + keys.size());
        assertEquals(ja.keySet(), en.keySet(), "ja_jp and en_us have the same keys");
    }

    @Test
    void standardHarmonicShowsSpecialPokemonReturnsWhenPresent() {
        int knockback = 0, resistance = 0, duration = 0;
        for (var entry : ResonanceAutogenCatalog.entries()) {
            if (entry.signature() || entry.types().size() != 3) continue;
            var pokemon = StandardResonanceBalance.forId(entry.id()).pokemon();
            var keys = ResonanceInfo.linkDetails(entry.id()).stream()
                    .map(ResonanceInfo.Line::key).collect(Collectors.toSet());
            if (pokemon.knockback() > 0) {
                assertTrue(keys.contains("mon.knockback"), entry.id());
                knockback++;
            }
            if (pokemon.knockbackResistance() > 0) {
                assertTrue(keys.contains("mon.knockback_resistance"), entry.id());
                resistance++;
            }
            if (pokemon.harmfulDuration() < 1) {
                assertTrue(keys.contains("mon.duration"), entry.id());
                duration++;
            }
        }
        assertTrue(knockback > 0 && resistance > 0 && duration > 0,
                "Each special Pokémon return needs a reachable Standard harmonic");
    }

    @Test
    void japaneseUsesTypeResonanceWording() throws Exception {
        Map<String, String> ja = lang("ja_jp");
        assertEquals("タイプ共鳴", ja.get("ui.super_pallet_towner.title"));
        assertEquals("タイプ共鳴を開く", ja.get("key.super_pallet_towner.open_affinity"));
        assertTrue(ja.get("ui.super_pallet_towner.slots").startsWith("共鳴枠"));
        assertTrue(ja.get("ui.super_pallet_towner.section_player").contains("ポケモンからの恩恵"));
        for (String key : ja.keySet()) {
            assertFalse(ja.get(key).contains("Affinity"), key + " still says Affinity");
        }
    }
}
