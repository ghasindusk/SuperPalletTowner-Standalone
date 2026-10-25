package starlight.trainer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure logic for the halo drawn behind Cobblemon's type icons ({@code TypeIcon}, used by the
 * party/summary, PC and other Pokémon screens) when the icon's type is part of the player's
 * active type resonance or active resonance link. No Minecraft classes, so it is unit-tested.
 * The drawing itself is in {@code mixin.TypeIconMixin}.
 */
public final class TypeIconHighlight {
    /** Halo tier. A link type is also a resonance type; the link tone wins. */
    public enum Tier { NONE, RESONANCE, LINK }

    /** Warm white-gold for resonance types, the HUD's link cyan for link types (RGB). */
    public static final int RESONANCE_RGB = 0xFFE08A;
    public static final int LINK_RGB = 0x9BECFF;
    /** One slow breath; the screen glow in AffinityScreen is 650 ms, this one loops. */
    public static final long PULSE_PERIOD_MS = 1800L;
    /** Halo alpha range (0–255) before the icon's own opacity is applied. */
    public static final int ALPHA_MIN = 50;
    public static final int ALPHA_MAX = 140;

    private static final Selection EMPTY = new Selection(Map.of());

    private TypeIconHighlight() {}

    /** Which types get a halo. Lookups are case-insensitive. */
    public static final class Selection {
        private final Map<String, Tier> tiers;

        private Selection(Map<String, Tier> tiers) {
            this.tiers = tiers;
        }

        public Tier tier(String type) {
            if (type == null || tiers.isEmpty()) return Tier.NONE;
            return tiers.getOrDefault(type.toLowerCase(Locale.ROOT), Tier.NONE);
        }

        public boolean isEmpty() {
            return tiers.isEmpty();
        }
    }

    /**
     * @param resonance active resonance type ids (from {@link AffinityHud#active()})
     * @param linkRecipeTypes the type lists of the active link recipes
     */
    public static Selection select(Collection<String> resonance,
                                   Collection<? extends Collection<String>> linkRecipeTypes) {
        Map<String, Tier> tiers = new HashMap<>();
        if (resonance != null) {
            for (String type : resonance) {
                if (type != null && !type.isBlank()) tiers.put(type.toLowerCase(Locale.ROOT), Tier.RESONANCE);
            }
        }
        if (linkRecipeTypes != null) {
            for (Collection<String> recipe : linkRecipeTypes) {
                if (recipe == null) continue;
                for (String type : recipe) {
                    if (type != null && !type.isBlank()) tiers.put(type.toLowerCase(Locale.ROOT), Tier.LINK);
                }
            }
        }
        return tiers.isEmpty() ? EMPTY : new Selection(Map.copyOf(tiers));
    }

    public static Selection empty() {
        return EMPTY;
    }

    /** Smooth 0..1..0 breath over {@link #PULSE_PERIOD_MS}; 0 at multiples of the period. */
    public static float pulse(long millis) {
        long phase = Math.floorMod(millis, PULSE_PERIOD_MS);
        return (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * phase / PULSE_PERIOD_MS));
    }

    /** Halo alpha (0–255) for a pulse value and the icon's opacity, both clamped to 0..1. */
    public static int haloAlpha(float pulse, float opacity) {
        float p = clamp01(pulse);
        float o = clamp01(opacity);
        return Math.round((ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * p) * o);
    }

    /**
     * Sparkle alpha (0–255): visible only near the top of the breath, offset by a quarter
     * period so two icons on one row do not flash in lockstep with their halos.
     */
    public static int sparkleAlpha(long millis, float opacity) {
        float p = pulse(millis + PULSE_PERIOD_MS / 4);
        float shown = (p - 0.7F) / 0.3F;
        return Math.round(220 * clamp01(shown) * clamp01(opacity));
    }

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int rgb(Tier tier) {
        return tier == Tier.LINK ? LINK_RGB : RESONANCE_RGB;
    }

    // Geometry, mirroring Cobblemon 1.8.1 TypeIcon.render: the texture cell is 36 px (18 when
    // small) drawn at scale 0.5; centeredX shifts left by a quarter cell, plus
    // doubleCenteredOffset when a secondary type exists; the secondary icon sits at
    // x + secondaryOffset and is drawn before (under) the primary.

    /** On-screen icon diameter in GUI pixels. */
    public static float iconSize(boolean small) {
        return small ? 9F : 18F;
    }

    public static float centerShift(boolean small, boolean centeredX, boolean hasSecondary,
                                    float doubleCenteredOffset) {
        if (!centeredX) return 0F;
        int diameter = small ? 18 : 36;
        return diameter / 2 * 0.5F + (hasSecondary ? doubleCenteredOffset : 0F);
    }

    public static float primaryLeft(float x, boolean small, boolean centeredX, boolean hasSecondary,
                                    float doubleCenteredOffset) {
        return x - centerShift(small, centeredX, hasSecondary, doubleCenteredOffset);
    }

    public static float secondaryLeft(float x, boolean small, boolean centeredX, float secondaryOffset,
                                      float doubleCenteredOffset) {
        return x + secondaryOffset - centerShift(small, centeredX, true, doubleCenteredOffset);
    }

    /**
     * Halo discs in full-size GUI pixels around an 18 px icon (radius 9); a small icon draws
     * the same discs at scale 0.5. The inner disc is the bright rim, the outer the soft fall-off.
     */
    public static final int HALO_INNER_RADIUS = 10;
    public static final int HALO_OUTER_RADIUS = 12;

    public static float drawScale(boolean small) {
        return small ? 0.5F : 1F;
    }

    /**
     * Half-width of a filled disc of {@code radius} on row {@code row}, where rows run
     * {@code -radius .. radius-1} and the disc is centred on 0. Sampled at the row's middle.
     */
    public static int discHalfWidth(int radius, int row) {
        double y = row + 0.5;
        double inside = (double) radius * radius - y * y;
        return inside <= 0 ? 0 : (int) Math.round(Math.sqrt(inside));
    }

    private static float clamp01(float value) {
        return value < 0F ? 0F : value > 1F ? 1F : value;
    }
}
