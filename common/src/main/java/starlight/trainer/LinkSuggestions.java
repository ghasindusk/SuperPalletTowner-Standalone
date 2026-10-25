package starlight.trainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The link recipes that can be made from the current resonance slots: every pair and, with three
 * slots, the three-type harmonic. Signature recipes come first. Pure Java (unit tested); applying
 * one only sends the same {@code link_remove}/{@code link_add} requests the manual flow sends.
 */
public final class LinkSuggestions {
    /** ACTIVE: the server says it works now. READY: can be applied. RESTING: a type is not active. */
    public enum State { ACTIVE, READY, RESTING }

    public record Suggestion(List<String> types, String id, boolean signature, State state) {
        public Suggestion {
            types = List.copyOf(types);
        }

        public boolean harmonic() {
            return types.size() == ResonanceLinks.MAX_LINK;
        }
    }

    private LinkSuggestions() {}

    /**
     * @param selected the resonance slots in slot order
     * @param active the types the server says are active
     * @param activeLinks recipe ids the server says apply now
     */
    public static List<Suggestion> of(List<String> selected, Set<String> active, Set<String> activeLinks) {
        List<String> types = new ArrayList<>();
        for (String type : selected) if (type != null && !types.contains(type)) types.add(type);
        List<Suggestion> result = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            for (int j = i + 1; j < types.size(); j++) {
                add(result, List.of(types.get(i), types.get(j)), active, activeLinks);
                for (int k = j + 1; k < types.size(); k++) {
                    add(result, List.of(types.get(i), types.get(j), types.get(k)), active, activeLinks);
                }
            }
        }
        // Signature first, then the three-type harmonic, then slot order (stable sort keeps it).
        result.sort(Comparator.comparing((Suggestion s) -> !s.signature())
                .thenComparing(s -> !s.harmonic()));
        return List.copyOf(result);
    }

    private static void add(List<Suggestion> result, List<String> types, Set<String> active, Set<String> activeLinks) {
        var entry = ResonanceAutogenCatalog.find(types);
        if (entry == null) return;
        State state = activeLinks.contains(entry.id()) ? State.ACTIVE
                : active.containsAll(types) ? State.READY : State.RESTING;
        result.add(new Suggestion(types, entry.id(), entry.signature(), state));
    }

    /** One request to the server: {@code action} is {@code link_remove} or {@code link_add}. */
    public record Request(String action, String type) {}

    /**
     * Requests that turn {@code link} into exactly {@code target}: removals first (so a full link
     * has room), then additions in the target's order. Empty when they are already equal.
     */
    public static List<Request> plan(List<String> link, Collection<String> target) {
        List<Request> result = new ArrayList<>();
        for (String type : link) if (!target.contains(type)) result.add(new Request("link_remove", type));
        for (String type : target) if (!link.contains(type)) result.add(new Request("link_add", type));
        return result;
    }
}
