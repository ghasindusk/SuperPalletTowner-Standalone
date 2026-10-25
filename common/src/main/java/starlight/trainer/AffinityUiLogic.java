package starlight.trainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a click or key in the resonance screen asks the server for. Pure Java, so it is unit
 * tested; the server still validates every request ({@link AffinityPackets}).
 */
public final class AffinityUiLogic {
    /** GLFW key codes used by the screen (kept here so the logic is testable without LWJGL). */
    public static final int KEY_1 = 49;
    public static final int KEY_KP_1 = 321;

    public enum Kind { SELECT, REPLACE, REMOVE, FULL, NONE }

    /** One request: {@link #packetAction()} is the {@code AffinityPackets.Change} action string. */
    public record Action(Kind kind, int slot, String type) {
        public boolean sends() {
            return kind == Kind.SELECT || kind == Kind.REPLACE || kind == Kind.REMOVE;
        }

        public String packetAction() {
            return switch (kind) {
                case SELECT -> "select";
                case REPLACE -> "replace:" + slot;
                case REMOVE -> "remove";
                default -> "";
            };
        }
    }

    private static final Action NONE = new Action(Kind.NONE, -1, "");

    private AffinityUiLogic() {}

    /**
     * A click on a candidate. A type already in a slot is taken out of that slot; with a slot
     * picked for replacement the type goes there; otherwise it goes to the first free slot
     * (the server appends). When every slot is filled nothing is sent and the screen explains.
     */
    public static Action candidateClick(List<String> selected, int replacementSlot, String type) {
        if (type == null || type.isBlank()) return NONE;
        int slot = selected.indexOf(type);
        if (slot >= 0) return new Action(Kind.REMOVE, slot, type);
        if (replacementSlot >= 0 && replacementSlot < selected.size()) {
            return new Action(Kind.REPLACE, replacementSlot, type);
        }
        if (selected.size() < AffinityView.MAX_SLOTS) return new Action(Kind.SELECT, selected.size(), type);
        return new Action(Kind.FULL, -1, type);
    }

    /** The "×" on a slot or its number key: removes only that slot, if it is filled. */
    public static Action slotRemove(List<String> selected, int slot) {
        if (slot < 0 || slot >= selected.size()) return NONE;
        return new Action(Kind.REMOVE, slot, selected.get(slot));
    }

    /** Slot index for the number keys 1–3 (main row or keypad), or -1. */
    public static int digitSlot(int keyCode) {
        if (keyCode >= KEY_1 && keyCode < KEY_1 + AffinityView.MAX_SLOTS) return keyCode - KEY_1;
        if (keyCode >= KEY_KP_1 && keyCode < KEY_KP_1 + AffinityView.MAX_SLOTS) return keyCode - KEY_KP_1;
        return -1;
    }

    /** Tab / Shift+Tab cycling. */
    public static int nextTab(int tab, int count, boolean backwards) {
        return Math.floorMod(tab + (backwards ? -1 : 1), Math.max(1, count));
    }

    /** How many party Pokémon have {@code type}: why the type is a candidate. */
    public static int providers(Collection<? extends Collection<String>> partyTypes, String type) {
        String canonical = type.toLowerCase(Locale.ROOT);
        int count = 0;
        for (Collection<String> types : partyTypes) if (types.contains(canonical)) count++;
        return count;
    }

    /** The active set after a click (display preview only; the server decides for real). */
    public static Set<String> activeAfter(Action action, List<String> selected, Set<String> active,
                                          Collection<String> available) {
        List<String> slots = new ArrayList<>(selected);
        switch (action.kind()) {
            case SELECT -> slots.add(action.type());
            case REPLACE -> slots.set(action.slot(), action.type());
            case REMOVE -> slots.remove(action.type());
            default -> { return active; }
        }
        // Same rule as the server: a slot is active when a conscious party Pokémon has the type.
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String type : slots) if (available.contains(type)) result.add(type);
        return Set.copyOf(result);
    }

    /** Party members that get at least one return buff, counted from their types. */
    public static int membersWithActiveType(Collection<? extends Collection<String>> partyTypes, Set<String> active) {
        int count = 0;
        for (Collection<String> types : partyTypes) {
            for (String type : types) {
                if (active.contains(type)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }
}
