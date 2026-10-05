package starlight.trainer;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Geometry of the resonance HUD: the badge arrangement for each shape, its size at a scale, and
 * where it goes for an anchor (clamped to the screen). Pure Java, so it is unit tested; the
 * drawing is in {@link AffinityHud}. At 100 % with the default anchor the icons land exactly where
 * the earlier HUD put them ({@link AffinityView#hudPlacement}, {@link AffinityView#linkHudPlacement}).
 */
public final class HudLayout {
    /** Where the HUD sits. HOTBAR is the original spot: right of the hotbar, clear of the minimap. */
    public enum Anchor {
        HOTBAR("hotbar"), TOP_LEFT("top_left"), TOP_RIGHT("top_right"),
        BOTTOM_LEFT("bottom_left"), BOTTOM_RIGHT("bottom_right"), CUSTOM("custom");

        public final String id;

        Anchor(String id) {
            this.id = id;
        }

        public static Anchor of(String id) {
            if (id != null) {
                for (Anchor anchor : values()) if (anchor.id.equals(id.toLowerCase(Locale.ROOT))) return anchor;
            }
            return HOTBAR;
        }

        public Anchor next(boolean backwards) {
            // The preset selector does not cycle through the drag-only custom position.
            Anchor[] presets = {HOTBAR, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT};
            int index = this == CUSTOM ? 0 : ordinal();
            return presets[Math.floorMod(index + (backwards ? -1 : 1), presets.length)];
        }
    }

    public static final List<String> ANCHOR_IDS = List.of("hotbar", "top_left", "top_right", "bottom_left", "bottom_right", "custom");

    /** SINGLES: 1–3 unlinked badges. LINK: a linked pair (+ an unlinked third). HARMONIC: a linked triangle. */
    public enum Shape { SINGLES, LINK, HARMONIC }

    public static final int ICON = AffinityView.HUD_ICON;
    /** Room around each icon for its round badge (radius {@link #BADGE_RADIUS}). */
    public static final int PAD = 2;
    public static final int BADGE_RADIUS = ICON / 2 + PAD;
    /** Gap between unlinked icons (their badges then stay 1 px apart), and the gap a link connector spans. */
    public static final int GAP = 5;
    public static final int LINK_GAP = 12;
    /** Vertical gap between the top and bottom rows of the harmonic triangle. */
    public static final int ROW_GAP = 8;

    public static final int SCALE_MIN = 50;
    public static final int SCALE_MAX = 200;
    public static final int SCALE_DEFAULT = 100;
    public static final int SCALE_STEP = 10;
    public static final int OFFSET_LIMIT = 1000;
    /** Fixed-point screen fractions used for the centre of a custom placement. */
    public static final int POSITION_UNIT = 10000;
    /** Height of the hotbar with the hearts, food and armour rows above it. */
    public static final int HOTBAR_STACK = 50;

    private HudLayout() {}

    public static int clampScale(int percent) {
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, percent));
    }

    public static int clampOffset(int offset) {
        return Math.max(-OFFSET_LIMIT, Math.min(OFFSET_LIMIT, offset));
    }

    public static int positionFraction(double coordinate, int dimension) {
        if (dimension <= 0) return POSITION_UNIT / 2;
        return Math.max(0, Math.min(POSITION_UNIT,
                (int) Math.round(coordinate * POSITION_UNIT / dimension)));
    }

    /**
     * Top-left corners of the icons in layout units (before scaling), {x0, y0, x1, y1, …}, inside
     * a content box whose corner is (0, 0). Badge padding is included. LINK puts the linked pair
     * first with the connector between icon 0 and 1; the unlinked third follows after a short gap.
     */
    public static int[] iconOffsets(Shape shape, int count, boolean vertical) {
        int n = Math.max(0, Math.min(AffinityView.MAX_SLOTS, count));
        int[] result = new int[n * 2];
        if (shape == Shape.HARMONIC && n == 3) {
            int width = 2 * ICON + LINK_GAP;
            result[0] = PAD + (width - ICON) / 2;
            result[1] = PAD;
            result[2] = PAD;
            result[3] = PAD + ICON + ROW_GAP;
            result[4] = PAD + ICON + LINK_GAP;
            result[5] = PAD + ICON + ROW_GAP;
            return result;
        }
        int along = PAD;
        for (int i = 0; i < n; i++) {
            result[i * 2] = vertical ? PAD : along;
            result[i * 2 + 1] = vertical ? along : PAD;
            along += ICON + (shape == Shape.LINK && i == 0 ? LINK_GAP : GAP);
        }
        return result;
    }

    /** {width, height} of the content box in layout units. */
    public static int[] contentSize(Shape shape, int count, boolean vertical) {
        int n = Math.max(0, Math.min(AffinityView.MAX_SLOTS, count));
        if (n == 0) return new int[] {0, 0};
        if (shape == Shape.HARMONIC && n == 3) return new int[] {2 * ICON + LINK_GAP + 2 * PAD, 2 * ICON + ROW_GAP + 2 * PAD};
        int length = n * ICON + (n - 1) * GAP + (shape == Shape.LINK && n >= 2 ? LINK_GAP - GAP : 0) + 2 * PAD;
        int across = ICON + 2 * PAD;
        return vertical ? new int[] {across, length} : new int[] {length, across};
    }

    /** Where to draw: content box corner and size on screen (GUI px), and the scale factor. */
    public record Placement(int x, int y, int width, int height, float scale, boolean vertical) {
        public boolean intersects(int rx, int ry, int rw, int rh) {
            return x < rx + rw && rx < x + width && y < ry + rh && ry < y + height;
        }
    }

    /**
     * @param scalePercent 50–200 (clamped)
     * @param offsetX added after anchoring, in GUI pixels; the result is clamped to the screen
     */
    public static Placement place(int guiWidth, int guiHeight, Shape shape, int count, int scalePercent,
                                  Anchor anchor, int offsetX, int offsetY) {
        return place(guiWidth, guiHeight, shape, count, scalePercent, anchor, offsetX, offsetY,
                POSITION_UNIT / 2, POSITION_UNIT / 2);
    }

    public static Placement place(int guiWidth, int guiHeight, Shape shape, int count, int scalePercent,
                                  Anchor anchor, int offsetX, int offsetY, int centerX, int centerY) {
        if (count <= 0) return null;
        float scale = clampScale(scalePercent) / 100F;
        boolean vertical = false;
        int[] size = contentSize(shape, count, false);
        int w = scaled(size[0], scale);
        int h = scaled(size[1], scale);
        int pad = scaled(PAD, scale);
        int x;
        int y;
        switch (anchor) {
            case CUSTOM -> {
                // Keep the centre stable as link shape, icon count, scale, or GUI size changes.
                x = Math.round(guiWidth * Math.max(0, Math.min(POSITION_UNIT, centerX))
                        / (float) POSITION_UNIT - w / 2F);
                y = Math.round(guiHeight * Math.max(0, Math.min(POSITION_UNIT, centerY))
                        / (float) POSITION_UNIT - h / 2F);
            }
            case TOP_LEFT -> {
                x = AffinityView.HUD_MARGIN;
                y = AffinityView.HUD_MARGIN;
            }
            case TOP_RIGHT -> {
                // Below vanilla's potion icons, which fill the top-right corner.
                x = guiWidth - AffinityView.HUD_MARGIN - w;
                y = AffinityView.HUD_COLUMN_TOP - pad;
            }
            case BOTTOM_LEFT -> {
                x = AffinityView.HUD_MARGIN;
                y = guiHeight - AffinityView.HUD_MARGIN - h;
            }
            case BOTTOM_RIGHT -> {
                // Left of the corner kept for the minimap; above the hotbar, hearts and food if it
                // would reach over them (narrow windows).
                x = guiWidth - AffinityView.HUD_RIGHT_RESERVE - w;
                y = guiHeight - AffinityView.HUD_MARGIN - h;
                if (x < guiWidth / 2 + 91 + AffinityView.HUD_HOTBAR_CLEARANCE) y = guiHeight - HOTBAR_STACK - h;
            }
            default -> {
                // The original spot: the bottom row centred on the hotbar, right of it.
                int iconX = guiWidth / 2 + 91 + AffinityView.HUD_HOTBAR_CLEARANCE;
                x = iconX - pad;
                int bottomRowTop = shape == Shape.HARMONIC && count == 3 ? PAD + ICON + ROW_GAP : PAD;
                y = guiHeight - 11 - Math.round((bottomRowTop + ICON / 2F) * scale);
                if (x + w - pad > guiWidth - AffinityView.HUD_RIGHT_RESERVE) {
                    // No room beside the hotbar: a column at the right edge below the potion icons.
                    vertical = shape != Shape.HARMONIC || count != 3;
                    size = contentSize(shape, count, vertical);
                    w = scaled(size[0], scale);
                    h = scaled(size[1], scale);
                    x = guiWidth - AffinityView.HUD_MARGIN - w + pad;
                    y = AffinityView.HUD_COLUMN_TOP - pad;
                }
            }
        }
        x += offsetX;
        y += offsetY;
        x = Math.max(0, Math.min(Math.max(0, guiWidth - w), x));
        y = Math.max(0, Math.min(Math.max(0, guiHeight - h), y));
        return new Placement(x, y, w, h, scale, vertical);
    }

    private static int scaled(int units, float scale) {
        return Math.round(units * scale);
    }

    // ------------------------------------------------------------------ colours

    private static final Map<String, Integer> TYPE_RGB = Map.ofEntries(
            Map.entry("normal", 0xA8A77A), Map.entry("fire", 0xEE8130), Map.entry("water", 0x6390F0),
            Map.entry("grass", 0x7AC74C), Map.entry("electric", 0xF7D02C), Map.entry("ice", 0x96D9D6),
            Map.entry("fighting", 0xC22E28), Map.entry("poison", 0xA33EA1), Map.entry("ground", 0xE2BF65),
            Map.entry("flying", 0xA98FF3), Map.entry("psychic", 0xF95587), Map.entry("bug", 0xA6B91A),
            Map.entry("rock", 0xB6A136), Map.entry("ghost", 0x735797), Map.entry("dragon", 0x6F35FC),
            Map.entry("dark", 0x705746), Map.entry("steel", 0xB7B7CE), Map.entry("fairy", 0xD685AD),
            Map.entry("air", 0x9FD3E6), Map.entry("light", 0xFFF3A0), Map.entry("sound", 0xC8A2FF));

    /** The usual colour of a type (RGB), grey-blue for unknown ones. */
    public static int typeRgb(String type) {
        return type == null ? 0x9AA6BC : TYPE_RGB.getOrDefault(type.toLowerCase(Locale.ROOT), 0x9AA6BC);
    }

    /** Half-widths of a filled disc of {@link #BADGE_RADIUS}, one per row from the top. */
    public static int[] discRows(int radius) {
        int[] rows = new int[radius * 2];
        for (int row = -radius; row < radius; row++) rows[row + radius] = TypeIconHighlight.discHalfWidth(radius, row);
        return rows;
    }
}
