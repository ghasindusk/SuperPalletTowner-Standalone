package starlight.trainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Type resonance links (タイプの共鳴リンク): two or three of the player's active resonance types
 * put into link slots unlock an extra effect when the set matches a recipe exactly. Pure Java,
 * so the rules are unit tested; the server applies them and the client only displays them.
 *
 * <p>The approved 16 pair and eight harmonic recipes are exact matches. A three-type recipe
 * replaces a pair rather than activating its three constituent pairs.
 */
public final class ResonanceLinks {
    public static final int MIN_LINK = 2;
    public static final int MAX_LINK = 3;

    /** Rock + Steel: the held tool mines and drops like one vanilla tier higher. */
    public static final String ROCK_STEEL = "rock_steel";

    public record Recipe(String id, Set<String> types) {}

    public static final List<Recipe> RECIPES = loadSignatures();

    private static List<Recipe> loadSignatures() {
        var stream = ResonanceLinks.class.getResourceAsStream(
                "/data/super_pallet_towner/resonance/signature_recipes.tsv");
        if (stream == null) throw new IllegalStateException("Missing Signature recipe resource");
        List<Recipe> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Set<String>> combinations = new HashSet<>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 2 || !fields[0].matches("[a-z]+(?:_[a-z]+)*")) {
                    throw new IllegalStateException("Invalid Signature line: " + line);
                }
                List<String> types = List.of(fields[1].split(",", -1));
                Set<String> unique = Set.copyOf(types);
                if (types.size() < MIN_LINK || types.size() > MAX_LINK || unique.size() != types.size()
                        || types.stream().anyMatch(type -> !type.matches("[a-z]+"))
                        || !ids.add(fields[0]) || !combinations.add(unique)) {
                    throw new IllegalStateException("Duplicate or invalid Signature: " + line);
                }
                result.add(new Recipe(fields[0], unique));
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot load Signature recipes", error);
        }
        if (result.size() != 24) throw new IllegalStateException("Expected 24 Signatures, found " + result.size());
        return List.copyOf(result);
    }

    public static boolean has(Set<String> activeRecipes, String recipe) {
        return activeRecipes.contains(recipe);
    }

    /** Why a link does or does not work, for the screen. */
    public enum Status { EMPTY, TOO_FEW, NO_RECIPE, RESTING, ACTIVE }

    private ResonanceLinks() {}

    /**
     * The link as stored: canonical names, no duplicates, only types still in a resonance slot,
     * at most {@link #MAX_LINK}. A type leaves the link when it leaves its resonance slot.
     */
    public static List<String> sanitize(Collection<String> link, Collection<String> selected) {
        List<String> result = new ArrayList<>(MAX_LINK);
        for (String raw : link) {
            if (raw == null) continue;
            String type = raw.toLowerCase(Locale.ROOT);
            if (type.isBlank() || result.contains(type) || !selected.contains(type)) continue;
            if (result.size() >= MAX_LINK) break;
            result.add(type);
        }
        return result;
    }

    /**
     * Whether {@code type} may be added: it must be in a resonance slot and active now (a conscious
     * party Pokémon has it), not linked already, and a link slot must be free.
     */
    public static boolean canAdd(List<String> link, String type, Collection<String> selected, Set<String> active) {
        return selected.contains(type) && active.contains(type) && !link.contains(type) && link.size() < MAX_LINK;
    }

    /** The recipe whose types equal the linked set exactly, or {@code null}. Activity is not checked. */
    public static Recipe recipe(Collection<String> link) {
        if (link.size() < MIN_LINK || link.size() > MAX_LINK) return null;
        Set<String> set = new HashSet<>(link);
        if (set.size() != link.size()) return null;
        for (Recipe recipe : RECIPES) if (recipe.types().equals(set)) return recipe;
        var standard = ResonanceAutogenCatalog.find(set);
        return standard == null ? null : new Recipe(standard.id(), set);
    }

    public static Status status(List<String> link, Set<String> active) {
        if (link.isEmpty()) return Status.EMPTY;
        if (link.size() < MIN_LINK) return Status.TOO_FEW;
        if (recipe(link) == null) return Status.NO_RECIPE;
        return active.containsAll(link) ? Status.ACTIVE : Status.RESTING;
    }

    /** Recipe ids in effect now: the link matches a recipe and every linked type is active. */
    public static Set<String> activeRecipes(List<String> link, Set<String> active) {
        if (status(link, active) != Status.ACTIVE) return Set.of();
        return Set.of(recipe(link).id());
    }

    // ------------------------------------------------------------------ Rock + Steel tool tier

    /** One vanilla tool tier, identified by the block tag its drop rule denies. */
    public record Tier(String name, String incorrectTag, float speed) {}

    public static final Tier WOOD = new Tier("wood", "minecraft:incorrect_for_wooden_tool", 2.0F);
    public static final Tier GOLD = new Tier("gold", "minecraft:incorrect_for_gold_tool", 12.0F);
    public static final Tier STONE = new Tier("stone", "minecraft:incorrect_for_stone_tool", 4.0F);
    public static final Tier IRON = new Tier("iron", "minecraft:incorrect_for_iron_tool", 6.0F);
    public static final Tier DIAMOND = new Tier("diamond", "minecraft:incorrect_for_diamond_tool", 8.0F);
    public static final Tier NETHERITE = new Tier("netherite", "minecraft:incorrect_for_netherite_tool", 9.0F);

    /**
     * One step up the vanilla harvest ladder wood → stone → iron → diamond → netherite, keyed by
     * the tool's vanilla tier tag. Gold harvests like wood in vanilla, so it steps to stone.
     * Netherite is the top: no step.
     *
     * <p>The tag's name is used, not its contents: in this pack several {@code needs_*} tags share
     * blocks, so "which blocks does it deny" misplaced stone tools (harness run 8c).
     */
    private static final Map<String, Tier> NEXT = Map.of(
            WOOD.incorrectTag(), STONE,
            GOLD.incorrectTag(), STONE,
            STONE.incorrectTag(), IRON,
            IRON.incorrectTag(), DIAMOND,
            DIAMOND.incorrectTag(), NETHERITE);

    /** Whether a deny-drops tag is one of the six vanilla tier tags. */
    public static boolean isVanillaTierTag(String incorrectTag) {
        return incorrectTag != null && (NEXT.containsKey(incorrectTag) || NETHERITE.incorrectTag().equals(incorrectTag));
    }

    /**
     * The tier one step above a tool whose drop rule denies {@code incorrectTag}, or {@code null}
     * when the tool is already at the top or its tier is not one of the vanilla tags (custom mod
     * tiers are left alone rather than guessed).
     */
    public static Tier nextTier(String incorrectTag) {
        return incorrectTag == null ? null : NEXT.get(incorrectTag);
    }

    /**
     * The tool's base mining speed after the step: the next tier's speed, but never slower than
     * the tool already is (gold stays 12). Speeds of 1 or less mean the tool is not effective on
     * the block, and stay unchanged.
     */
    public static float steppedBaseSpeed(float base, Tier next) {
        if (next == null || base <= 1.0F) return base;
        return Math.max(base, next.speed());
    }

    /**
     * Factor for the final dig speed. Vanilla adds the Efficiency attribute to the tool's base
     * speed (only when it is above 1) and then only multiplies (Haste, mining fatigue, block
     * break speed incl. Rock +20%, underwater, airborne), so scaling the final speed by
     * (stepped + efficiency) / (base + efficiency) is exactly "the same tool one tier up".
     */
    public static double speedFactor(float base, float efficiency, Tier next) {
        float stepped = steppedBaseSpeed(base, next);
        if (stepped == base) return 1.0;
        return (stepped + efficiency) / (double) (base + efficiency);
    }
}
