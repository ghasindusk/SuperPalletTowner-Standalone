package starlight.trainer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Cobblemon type symbols for the Affinity buffs that are active right now. The list comes from
 * the server ({@link AffinityPackets.ActiveTypes}); the client never decides it. No mob
 * effects are added for this, so nothing is duplicated in the vanilla potion icon area.
 *
 * <p>Each type sits on a round dark badge with a thin ring: gold for resonance, cyan for a linked
 * type (the same tones as the halo on Cobblemon's type icons), and a faint inner ring in the
 * type's own colour. A linked pair is joined by a connector; a three-type harmonic forms a
 * triangle. Size and position come from {@link HudSettings} via {@link HudLayout}. The shape and
 * placement are cached and rebuilt only when the types, links, window or settings change.
 */
public final class AffinityHud {
    static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, "active_affinity");
    private static volatile List<String> active = List.of();

    private static final int BACKING = 0xA0101626;
    private static final int RESONANCE_RING = 0xE0000000 | TypeIconHighlight.RESONANCE_RGB;
    private static final int LINK_RING = 0xF0000000 | TypeIconHighlight.LINK_RGB;
    private static final int LINK_GLOW = 0x509BECFF;
    private static final int LINK_CORE = 0xFF000000 | TypeIconHighlight.LINK_RGB;
    private static final int[] OUTER = HudLayout.discRows(HudLayout.BADGE_RADIUS);
    private static final int[] MIDDLE = HudLayout.discRows(HudLayout.BADGE_RADIUS - 1);
    private static final int[] INNER = HudLayout.discRows(HudLayout.BADGE_RADIUS - 2);
    /** Disc rows for the activation ring, radius BADGE_RADIUS + k for k in 0..BURST_EXTRA. */
    private static final int BURST_EXTRA = 8;
    private static final int[][] BURST_ROWS = new int[BURST_EXTRA + 1][];
    static {
        for (int k = 0; k <= BURST_EXTRA; k++) BURST_ROWS[k] = HudLayout.discRows(HudLayout.BADGE_RADIUS + k);
    }

    // Animation state (render thread only). Times are Util.getMillis().
    private static final java.util.Map<String, Long> appearedAt = new java.util.HashMap<>();
    private static final long[] badgeAppear = new long[AffinityView.MAX_SLOTS];
    private static String linkKey = "";
    private static long linkSince = Long.MIN_VALUE / 2;

    // Shape cache (render thread only), rebuilt when the type list or link set object changes.
    private static List<String> modelTypes;
    private static Set<String> modelLinks;
    private static HudLayout.Shape shape = HudLayout.Shape.SINGLES;
    private static final String[] order = new String[AffinityView.MAX_SLOTS];
    private static final boolean[] linked = new boolean[AffinityView.MAX_SLOTS];
    private static final int[] typeRing = new int[AffinityView.MAX_SLOTS];
    private static int count;
    // Placement cache.
    private static int placedWidth = -1;
    private static int placedHeight = -1;
    private static int placedVersion = -1;
    private static HudLayout.Shape placedShape;
    private static int placedCount = -1;
    private static HudLayout.Placement placement;
    private static int[] offsets = new int[0];
    /** Last drawn box {x, y, w, h} in GUI px, for the verification harness. */
    private static final int[] lastBounds = new int[4];

    private AffinityHud() {}

    static void receive(List<String> types) {
        active = List.copyOf(types);
    }

    static void clear() {
        active = List.of();
    }

    public static List<String> active() {
        return active;
    }

    public static int[] lastBounds() {
        return lastBounds.clone();
    }

    public static String shapeName() {
        return shape.name();
    }

    /** Layout box and scale for the given types and links, without drawing to the screen. */
    public static HudLayout.Placement previewPlacement(List<String> types, Set<String> links, int guiWidth, int guiHeight) {
        model(types, links);
        return count == 0 ? null : placement(guiWidth, guiHeight);
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        List<String> types = active;
        if (types.isEmpty() || mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || mc.getDebugOverlay().showDebugScreen()) return;
        // Gameplay only. Also hidden while chat is open: its input bar spans the whole bottom row.
        if (mc.screen != null) return;
        draw(graphics, types, ResonanceLinkEffects.active(mc.player), graphics.guiWidth(), graphics.guiHeight());
    }

    /**
     * Draws the HUD for these active types and active link recipe ids. Also used by the HUD tab of
     * the resonance screen as a live preview, with the screen's own lists.
     */
    public static void draw(GuiGraphics graphics, List<String> types, Set<String> links, int guiWidth, int guiHeight) {
        model(types, links);
        if (count == 0) return;
        HudLayout.Placement p = placement(guiWidth, guiHeight);
        if (p == null) return;
        lastBounds[0] = p.x();
        lastBounds[1] = p.y();
        lastBounds[2] = p.width();
        lastBounds[3] = p.height();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(p.x(), p.y(), 0F);
        pose.scale(p.scale(), p.scale(), 1F);
        int half = HudLayout.ICON / 2;
        boolean animate = HudSettings.animations();
        long now = Util.getMillis();
        if (shape == HudLayout.Shape.LINK && count >= 2) {
            connector(graphics, offsets[0] + half, offsets[1] + half, offsets[2] + half, offsets[3] + half);
            if (animate) flowOnConnector(graphics, offsets[0] + half, offsets[1] + half,
                    offsets[2] + half, offsets[3] + half, now);
        } else if (shape == HudLayout.Shape.HARMONIC) {
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                line(graphics, offsets[i * 2] + half, offsets[i * 2 + 1] + half,
                        offsets[j * 2] + half, offsets[j * 2 + 1] + half);
            }
            int cx = (offsets[0] + offsets[2] + offsets[4]) / 3 + half;
            int cy = (offsets[1] + offsets[3] + offsets[5]) / 3 + half;
            if (animate) {
                flowOnTriangle(graphics, half, now);
                star(graphics, cx, cy, HudAnimation.starBreath(now));
            } else {
                graphics.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFFFFFFF);
                graphics.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFFFFFFF);
            }
        }
        long sinceLink = now - linkSince;
        for (int i = 0; i < count; i++) {
            int x = offsets[i * 2];
            int y = offsets[i * 2 + 1];
            if (animate && linked[i] && sinceLink >= 0 && sinceLink < HudAnimation.BURST_MS) {
                burst(graphics, x + half, y + half, sinceLink);
            }
            float pop = animate ? HudAnimation.popScale(now - badgeAppear[i]) : 1F;
            if (pop != 1F) {
                pose.pushPose();
                pose.translate(x + half, y + half, 0F);
                pose.scale(pop, pop, 1F);
                pose.translate(-(x + half), -(y + half), 0F);
            }
            badge(graphics, x + half, y + half, linked[i] ? LINK_RING : RESONANCE_RING, typeRing[i]);
            AffinityTypeIcons.draw(graphics, order[i], x, y, HudLayout.ICON);
            if (pop != 1F) pose.popPose();
        }
        pose.popPose();
    }

    private static void model(List<String> types, Set<String> links) {
        if (types == modelTypes && links == modelLinks) return;
        modelTypes = types;
        modelLinks = links;
        count = Math.min(AffinityView.MAX_SLOTS, types.size());
        shape = HudLayout.Shape.SINGLES;
        java.util.Arrays.fill(linked, false);
        List<String> recipe = null;
        if (links.size() == 1) {
            var entry = ResonanceAutogenCatalog.findId(links.iterator().next());
            if (entry != null && types.containsAll(entry.types())) recipe = entry.types();
        }
        int n = 0;
        if (recipe != null && recipe.size() == 3 && count == 3) {
            shape = HudLayout.Shape.HARMONIC;
        } else if (recipe != null && recipe.size() == 2) {
            shape = HudLayout.Shape.LINK;
            // Linked pair first (in slot order), then the unlinked type.
            for (int i = 0; i < count; i++) if (recipe.contains(types.get(i))) order[n++] = types.get(i);
        }
        for (int i = 0; i < count; i++) if (!contains(order, n, types.get(i))) order[n++] = types.get(i);
        for (int i = 0; i < count; i++) {
            linked[i] = shape == HudLayout.Shape.HARMONIC || (shape == HudLayout.Shape.LINK && i < 2);
            typeRing[i] = 0x90000000 | HudLayout.typeRgb(order[i]);
        }
        trackAnimation(recipe);
        placedCount = -1;
    }

    /**
     * Remembers when each type first appeared (kept while it stays active) and when the current
     * link started, so badges pop in once and the activation ring plays once per change.
     */
    private static void trackAnimation(List<String> recipe) {
        long now = Util.getMillis();
        java.util.Set<String> current = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            current.add(order[i]);
            badgeAppear[i] = appearedAt.computeIfAbsent(order[i], key -> now);
        }
        appearedAt.keySet().retainAll(current);
        String key = recipe == null ? "" : shape.name() + ':' + String.join("+", recipe);
        if (!key.equals(linkKey)) {
            linkKey = key;
            linkSince = key.isEmpty() ? Long.MIN_VALUE / 2 : now;
        }
    }

    private static boolean contains(String[] array, int length, String value) {
        for (int i = 0; i < length; i++) if (value.equals(array[i])) return true;
        return false;
    }

    private static HudLayout.Placement placement(int guiWidth, int guiHeight) {
        int version = HudSettings.version();
        if (guiWidth != placedWidth || guiHeight != placedHeight || version != placedVersion
                || shape != placedShape || count != placedCount) {
            placement = HudLayout.place(guiWidth, guiHeight, shape, count, HudSettings.scale(), HudSettings.anchor(),
                    HudSettings.offsetX(), HudSettings.offsetY(), HudSettings.centerX(), HudSettings.centerY());
            offsets = placement == null ? new int[0] : HudLayout.iconOffsets(shape, count, placement.vertical());
            placedWidth = guiWidth;
            placedHeight = guiHeight;
            placedVersion = version;
            placedShape = shape;
            placedCount = count;
        }
        return placement;
    }

    /** Dark disc, a ring in the tier colour, and a faint inner ring in the type colour. */
    private static void badge(GuiGraphics graphics, int cx, int cy, int ring, int typeColour) {
        int r = HudLayout.BADGE_RADIUS;
        for (int row = 0; row < OUTER.length; row++) {
            int y = cy - r + row;
            int outer = OUTER[row];
            int middle = row >= 1 && row <= MIDDLE.length ? MIDDLE[row - 1] : 0;
            int inner = row >= 2 && row < INNER.length + 2 ? INNER[row - 2] : 0;
            graphics.fill(cx - middle, y, cx + middle, y + 1, BACKING);
            if (middle == 0) {
                graphics.fill(cx - outer, y, cx + outer, y + 1, ring);
            } else {
                graphics.fill(cx - outer, y, cx - middle, y + 1, ring);
                graphics.fill(cx + middle, y, cx + outer, y + 1, ring);
            }
            if (middle > 0) {
                if (inner == 0) {
                    graphics.fill(cx - middle, y, cx + middle, y + 1, typeColour);
                } else {
                    graphics.fill(cx - middle, y, cx - inner, y + 1, typeColour);
                    graphics.fill(cx + inner, y, cx + middle, y + 1, typeColour);
                }
            }
        }
    }

    /** A soft cyan bar between two badge centres (horizontal or vertical), with a bright core. */
    private static void connector(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        int r = HudLayout.BADGE_RADIUS;
        if (y0 == y1) {
            graphics.fill(x0 + r, y0 - 2, x1 - r, y0 + 2, LINK_GLOW);
            graphics.fill(x0 + r, y0 - 1, x1 - r, y0 + 1, LINK_CORE);
            int mid = (x0 + x1) / 2;
            graphics.fill(mid - 1, y0 - 2, mid + 1, y0 + 2, 0xFFFFFFFF);
        } else {
            graphics.fill(x0 - 2, y0 + r, x0 + 2, y1 - r, LINK_GLOW);
            graphics.fill(x0 - 1, y0 + r, x0 + 1, y1 - r, LINK_CORE);
            int mid = (y0 + y1) / 2;
            graphics.fill(x0 - 2, mid - 1, x0 + 2, mid + 1, 0xFFFFFFFF);
        }
    }

    /** A small bright light travelling along the connector, from the first badge to the second. */
    private static void flowOnConnector(GuiGraphics graphics, int x0, int y0, int x1, int y1, long now) {
        int r = HudLayout.BADGE_RADIUS;
        float t = HudAnimation.flow(now);
        if (y0 == y1) {
            if (x1 - x0 <= 2 * r) return;
            dot(graphics, HudAnimation.lerp(x0 + r, x1 - r, t), y0);
        } else {
            if (y1 - y0 <= 2 * r) return;
            dot(graphics, x0, HudAnimation.lerp(y0 + r, y1 - r, t));
        }
    }

    /** The light circles the harmonic triangle once per period (badges cover its corners). */
    private static void flowOnTriangle(GuiGraphics graphics, int half, long now) {
        float t = HudAnimation.flow(now) * 3F;
        int edge = Math.min(2, (int) t);
        float local = t - edge;
        int next = (edge + 1) % 3;
        dot(graphics,
                HudAnimation.lerp(offsets[edge * 2] + half, offsets[next * 2] + half, local),
                HudAnimation.lerp(offsets[edge * 2 + 1] + half, offsets[next * 2 + 1] + half, local));
    }

    private static void dot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 2, y - 1, x + 2, y + 1, LINK_GLOW);
        graphics.fill(x - 1, y - 2, x + 1, y + 2, LINK_GLOW);
        graphics.fill(x - 1, y - 1, x + 1, y + 1, 0xFFFFFFFF);
    }

    /** Harmonic centre: a four-point star that breathes between dim and bright. */
    private static void star(GuiGraphics graphics, int cx, int cy, float breath) {
        int arm = 2 + Math.round(breath);
        int colour = HudAnimation.argb(150 + Math.round(105 * breath), 0xFFFFFF);
        graphics.fill(cx - arm, cy, cx + arm + 1, cy + 1, colour);
        graphics.fill(cx, cy - arm, cx + 1, cy + arm + 1, colour);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2,
                HudAnimation.argb(Math.round(90 * breath), TypeIconHighlight.LINK_RGB));
    }

    /** An expanding, fading cyan ring around a badge, played once when a link becomes active. */
    private static void burst(GuiGraphics graphics, int cx, int cy, long elapsed) {
        int alpha = HudAnimation.burstAlpha(elapsed);
        if (alpha <= 0) return;
        int extra = Math.min(BURST_EXTRA, HudAnimation.burstRadius(elapsed, BURST_EXTRA));
        int colour = HudAnimation.argb(alpha, TypeIconHighlight.LINK_RGB);
        int[] rows = BURST_ROWS[extra];
        int r = HudLayout.BADGE_RADIUS + extra;
        for (int row = 0; row < rows.length; row++) {
            int y = cy - r + row;
            int w = rows[row];
            // Ring outline: the part of this row outside the neighbouring rows, at least 1 px.
            int up = row > 0 ? rows[row - 1] : 0;
            int down = row + 1 < rows.length ? rows[row + 1] : 0;
            int inner = Math.max(0, Math.min(w - 1, Math.min(up, down)));
            graphics.fill(cx - w, y, cx - inner, y + 1, colour);
            graphics.fill(cx + inner, y, cx + w, y + 1, colour);
        }
    }

    /** One-pixel line (Bresenham) for the harmonic triangle, drawn under the badges. */
    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0 - 1, y0, x0 + 1, y0 + 1, LINK_GLOW);
            graphics.fill(x0, y0, x0 + 1, y0 + 1, LINK_CORE);
            if (x0 == x1 && y0 == y1) return;
            int step = 2 * error;
            if (step >= dy) { error += dy; x0 += sx; }
            if (step <= dx) { error += dx; y0 += sy; }
        }
    }
}
