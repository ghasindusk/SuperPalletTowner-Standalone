package starlight.trainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Resonance v5's complete, deterministic recipe catalog. Signature IDs keep their previous
 * meaning; other combinations use one generated Standard effect. The primary-trait order is
 * round-robin across sorted type IDs, preserving each type's listed trait order. This ensures
 * every participating type contributes before secondary tags.
 */
public final class ResonanceAutogenCatalog {
    public static final int EXPECTED_TYPES = 21;
    public static final int EXPECTED_PAIRS = 210;
    public static final int EXPECTED_HARMONICS = 1330;

    public record Entry(String key, String id, List<String> types, List<String> primaryTraits,
                        boolean signature) {
        public Entry {
            types = List.copyOf(types);
            primaryTraits = List.copyOf(primaryTraits);
        }
    }

    private static final Map<String, List<String>> TYPE_TRAITS = loadTraits();
    private static final Map<String, Entry> BY_KEY = generate();
    private static final Map<String, Entry> BY_ID = indexIds();

    private ResonanceAutogenCatalog() {}

    public static Map<String, List<String>> typeTraits() { return TYPE_TRAITS; }
    public static Collection<Entry> entries() { return BY_KEY.values(); }
    public static Entry find(Collection<String> types) { return BY_KEY.get(key(types)); }
    public static Entry findId(String id) { return id == null ? null : BY_ID.get(id); }

    private static Map<String, Entry> indexIds() {
        Map<String, Entry> result = new TreeMap<>();
        for (Entry entry : BY_KEY.values()) {
            if (result.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalStateException("Duplicate resonance ID: " + entry.id());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Stable save-independent lookup key; unknown types return no catalog entry. */
    public static String key(Collection<String> types) {
        if (types == null || types.size() < 2 || types.size() > 3
                || types.stream().anyMatch(type -> type == null || !TYPE_TRAITS.containsKey(type))) return "";
        Set<String> unique = new java.util.TreeSet<>(types);
        return unique.size() == types.size() ? String.join("_", unique) : "";
    }

    private static Map<String, List<String>> loadTraits() {
        Properties properties = new Properties();
        try (InputStream in = ResonanceAutogenCatalog.class.getResourceAsStream(
                "/data/super_pallet_towner/resonance/type_traits.properties")) {
            if (in == null) throw new IllegalStateException("Missing v5 type traits resource");
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot load v5 type traits", error);
        }
        Map<String, List<String>> result = new TreeMap<>();
        for (String type : properties.stringPropertyNames()) {
            if (!type.matches("[a-z]+")) throw new IllegalStateException("Invalid type id: " + type);
            List<String> traits = List.of(properties.getProperty(type).split(","));
            if (traits.isEmpty() || traits.stream().anyMatch(trait -> !trait.matches("[a-z]+"))
                    || Set.copyOf(traits).size() != traits.size()) {
                throw new IllegalStateException("Invalid traits for " + type);
            }
            traits.forEach(StandardResonanceBalance::requireKnownTag);
            result.put(type, traits);
        }
        if (result.size() != EXPECTED_TYPES) throw new IllegalStateException(
                "Expected 21 type traits, found " + result.size());
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Entry> generate() {
        Map<String, ResonanceLinks.Recipe> signature = new TreeMap<>();
        for (ResonanceLinks.Recipe recipe : ResonanceLinks.RECIPES) {
            String key = key(recipe.types());
            if (key.isEmpty() || signature.putIfAbsent(key, recipe) != null) {
                throw new IllegalStateException("Duplicate or unknown Signature: " + recipe.id());
            }
        }
        List<String> types = List.copyOf(TYPE_TRAITS.keySet());
        Map<String, Entry> result = new TreeMap<>();
        for (int i = 0; i < types.size(); i++) {
            for (int j = i + 1; j < types.size(); j++) {
                add(result, signature, List.of(types.get(i), types.get(j)));
                for (int k = j + 1; k < types.size(); k++) {
                    add(result, signature, List.of(types.get(i), types.get(j), types.get(k)));
                }
            }
        }
        long pairs = result.values().stream().filter(entry -> entry.types().size() == 2).count();
        long harmonics = result.size() - pairs;
        if (pairs != EXPECTED_PAIRS || harmonics != EXPECTED_HARMONICS) {
            throw new IllegalStateException("Wrong v5 catalog size: " + pairs + "/" + harmonics);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Entry> result, Map<String, ResonanceLinks.Recipe> signature,
                            List<String> types) {
        String key = key(types);
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        int maxTags = types.stream().mapToInt(type -> TYPE_TRAITS.get(type).size()).max().orElse(0);
        for (int position = 0; position < maxTags; position++) {
            for (String type : types) {
                List<String> tags = TYPE_TRAITS.get(type);
                if (position < tags.size()) merged.add(tags.get(position));
            }
        }
        List<String> traits = new ArrayList<>(merged);
        traits = List.copyOf(traits.subList(0, Math.min(types.size() == 2 ? 3 : 5, traits.size())));
        ResonanceLinks.Recipe special = signature.get(key);
        Entry entry = new Entry(key, special == null ? key : special.id(), types, traits, special != null);
        if (result.putIfAbsent(key, entry) != null) throw new IllegalStateException("Duplicate key " + key);
    }
}
