package starlight.trainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Display rules shared by the Affinity screen and the HUD. Pure Java so it is unit tested;
 * nothing here decides what is selected — the server does.
 */
public final class AffinityView {
    /** The 18 Balance v1 types, in Cobblemon's atlas order. */
    public static final List<String> STANDARD_TYPES = List.of("normal", "fire", "water", "grass", "electric",
            "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost",
            "dragon", "dark", "steel", "fairy");
    private static final Set<String> STANDARD = Set.copyOf(STANDARD_TYPES);
    /** Resonance v4 defines these only when a Cobblemania Pokémon supplies the type. */
    public static final List<String> COBBLEMANIA_TYPES = List.of("air", "light", "sound");
    private static final Set<String> CONFIGURED = Set.of("normal", "fire", "water", "grass", "electric",
            "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost",
            "dragon", "dark", "steel", "fairy", "air", "light", "sound");
    public static final int MAX_SLOTS = 3;

    /**
     * Cobblemon 1.8.1 {@code textures/gui/types.png}: one row of 36×36 cells, cell index =
     * {@code ElementalType.textureXMultiplier}, which follows {@link #STANDARD_TYPES} order.
     */
    public static final int ATLAS_CELL = 36;
    public static final int ATLAS_CELLS = 18;
    /**
     * Symbol size in GUI pixels: half a cell, the size Cobblemon's own TypeIcon draws, so at
     * GUI scale 2 each atlas pixel lands on exactly one screen pixel.
     */
    public static final int TYPE_ICON = ATLAS_CELL / 2;
    /** HUD icon size and spacing in GUI pixels. */
    public static final int HUD_ICON = TYPE_ICON;
    public static final int HUD_GAP = 3;
    /** Space right of the hotbar left free for the attack indicator and a right-side offhand slot. */
    public static final int HUD_HOTBAR_CLEARANCE = 32;
    public static final int HUD_MARGIN = 2;
    /**
     * Bottom-right corner kept free for corner HUDs of other mods. In the user's pack the
     * Xaero minimap and the money box sit there (about 80 GUI px wide, seen in screenshots).
     */
    public static final int HUD_RIGHT_RESERVE = 84;
    /** Top of the right-edge column: below vanilla's two potion-icon rows (y 1–25 and 27–51). */
    public static final int HUD_COLUMN_TOP = 54;

    public enum SlotState { ACTIVE, INACTIVE, EMPTY }

    private AffinityView() {}

    public static boolean isStandard(String type) {
        return STANDARD.contains(type.toLowerCase(Locale.ROOT));
    }

    public static boolean isConfigured(String type) {
        return CONFIGURED.contains(type.toLowerCase(Locale.ROOT));
    }

    /**
     * Cell in Cobblemon's type atlas for a standard type, or -1 (drawn as the "?" fallback).
     * Mirrors Cobblemon 1.8.1; the client prefers Cobblemon's registered value at runtime.
     */
    public static int atlasIndex(String type) {
        return STANDARD_TYPES.indexOf(type.toLowerCase(Locale.ROOT));
    }

    /** Candidates in the usual type order (Normal, Fire, Water, …), then any other type by name. */
    public static List<String> displayOrder(Collection<String> types) {
        List<String> result = new ArrayList<>(types);
        result.sort(java.util.Comparator.<String>comparingInt(type -> {
            String canonical = type.toLowerCase(Locale.ROOT);
            int index = STANDARD_TYPES.indexOf(canonical);
            if (index < 0) {
                int extra = COBBLEMANIA_TYPES.indexOf(canonical);
                if (extra >= 0) index = STANDARD_TYPES.size() + extra;
            }
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparing(java.util.Comparator.naturalOrder()));
        return result;
    }

    public static SlotState slotState(List<String> selected, Collection<String> available, int slot) {
        if (slot < 0 || slot >= selected.size()) return SlotState.EMPTY;
        return available.contains(selected.get(slot)) ? SlotState.ACTIVE : SlotState.INACTIVE;
    }

    /** Active types in slot order, each once, at most three: what the HUD shows. */
    public static List<String> hudTypes(List<String> selected, Collection<String> active) {
        List<String> result = new ArrayList<>(MAX_SLOTS);
        for (String type : selected) {
            String canonical = type.toLowerCase(Locale.ROOT);
            if (active.contains(canonical) && !result.contains(canonical) && result.size() < MAX_SLOTS) {
                result.add(canonical);
            }
        }
        return result;
    }

    /**
     * Where the first HUD symbol goes, or {@code null} when nothing is shown. Beside the
     * hotbar the symbols run left to right; otherwise they run downwards.
     */
    public record HudPlacement(int x, int y, boolean besideHotbar) {
        public int step() { return HUD_ICON + HUD_GAP; }
        public int nextX(int i) { return besideHotbar ? x + i * step() : x; }
        public int nextY(int i) { return besideHotbar ? y : y + i * step(); }
    }

    /**
     * Right of the hotbar, vertically centred on it, clear of the attack indicator and a
     * right-side offhand slot, and left of the bottom-right corner reserve. Chat is bottom-left
     * and potion icons top-right, so neither is touched. When that row does not fit, the symbols
     * form a column at the right edge below the potion icons; the left side is avoided because
     * Cobblemon's party HUD runs down it.
     */
    public static HudPlacement hudPlacement(int guiWidth, int guiHeight, int count) {
        if (count <= 0) return null;
        int length = count * HUD_ICON + (count - 1) * HUD_GAP;
        int x = guiWidth / 2 + 91 + HUD_HOTBAR_CLEARANCE;
        if (x + length <= guiWidth - HUD_RIGHT_RESERVE) {
            return new HudPlacement(x, guiHeight - 22 + (22 - HUD_ICON) / 2, true);
        }
        return new HudPlacement(guiWidth - HUD_MARGIN - HUD_ICON, HUD_COLUMN_TOP, false);
    }

    /** A linked pair sits on the lower row; the third active type, if any, sits above it. */
    public record LinkHudPlacement(int leftX, int rightX, int bottomY, int topX, int topY,
                                   boolean besideHotbar) {}

    public static LinkHudPlacement linkHudPlacement(int guiWidth, int guiHeight) {
        int gap = 12;
        int width = HUD_ICON * 2 + gap;
        int x = guiWidth / 2 + 91 + HUD_HOTBAR_CLEARANCE;
        boolean beside = x + width <= guiWidth - HUD_RIGHT_RESERVE;
        if (!beside) x = guiWidth - HUD_MARGIN - width;
        int bottom = beside ? guiHeight - 22 + (22 - HUD_ICON) / 2 : HUD_COLUMN_TOP + HUD_ICON + 8;
        return new LinkHudPlacement(x, x + HUD_ICON + gap, bottom,
                x + (width - HUD_ICON) / 2, bottom - HUD_ICON - 8, beside);
    }

    /** Candidate grid shape for a pane of the given inner size. */
    public record Grid(int columns, int visibleRows, int totalRows, int maxScroll) {}

    public static Grid grid(int paneWidth, int paneHeight, int rowHeight, int minColumnWidth, int count) {
        int columns = Math.max(1, Math.min(2, paneWidth / Math.max(1, minColumnWidth)));
        int visibleRows = Math.max(1, paneHeight / Math.max(1, rowHeight));
        int totalRows = (count + columns - 1) / columns;
        return new Grid(columns, visibleRows, totalRows, Math.max(0, totalRows - visibleRows));
    }

    public static int clampScroll(int scroll, Grid grid) {
        return Math.max(0, Math.min(grid.maxScroll(), scroll));
    }
}
