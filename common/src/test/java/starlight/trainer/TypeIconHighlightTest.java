package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static starlight.trainer.TypeIconHighlight.Tier.LINK;
import static starlight.trainer.TypeIconHighlight.Tier.NONE;
import static starlight.trainer.TypeIconHighlight.Tier.RESONANCE;

/** Halo selection, timing and geometry for Cobblemon type icons (TypeIconMixin). */
class TypeIconHighlightTest {
    @Test
    void resonanceTypesOnly() {
        var s = TypeIconHighlight.select(List.of("fire", "water"), List.of());
        assertEquals(RESONANCE, s.tier("fire"));
        assertEquals(RESONANCE, s.tier("water"));
        assertEquals(NONE, s.tier("grass"));
        assertEquals(NONE, s.tier(null));
    }

    @Test
    void linkTypesWinOverResonance() {
        var s = TypeIconHighlight.select(List.of("normal", "rock", "fire"), List.of(List.of("normal", "rock")));
        assertEquals(LINK, s.tier("normal"));
        assertEquals(LINK, s.tier("rock"));
        assertEquals(RESONANCE, s.tier("fire"));
        assertEquals(NONE, s.tier("ground"));
    }

    @Test
    void linkRecipeTypeOutsideResonanceStillHighlighted() {
        var s = TypeIconHighlight.select(List.of("ice"), List.of(List.of("ice", "water")));
        assertEquals(LINK, s.tier("water"));
    }

    @Test
    void caseInsensitive() {
        var s = TypeIconHighlight.select(List.of("Fire"), List.of(List.of("WATER", "Ice")));
        assertEquals(RESONANCE, s.tier("FIRE"));
        assertEquals(RESONANCE, s.tier("fire"));
        assertEquals(LINK, s.tier("water"));
        assertEquals(LINK, s.tier("ICE"));
    }

    @Test
    void emptyState() {
        assertTrue(TypeIconHighlight.select(List.of(), List.of()).isEmpty());
        assertTrue(TypeIconHighlight.select(null, null).isEmpty());
        assertSame(TypeIconHighlight.empty(), TypeIconHighlight.select(List.of(), List.of()));
        assertEquals(NONE, TypeIconHighlight.empty().tier("fire"));
        java.util.List<String> blanks = new java.util.ArrayList<>();
        blanks.add(null);
        blanks.add(" ");
        assertTrue(TypeIconHighlight.select(blanks, java.util.Collections.singletonList(null)).isEmpty());
    }

    @Test
    void pulseStaysInBoundsAndLoops() {
        for (long t = -5000; t <= 10000; t += 37) {
            float p = TypeIconHighlight.pulse(t);
            assertTrue(p >= 0F && p <= 1F, "pulse " + p + " at " + t);
        }
        assertEquals(0F, TypeIconHighlight.pulse(0), 1e-6);
        assertEquals(1F, TypeIconHighlight.pulse(TypeIconHighlight.PULSE_PERIOD_MS / 2), 1e-6);
        assertEquals(TypeIconHighlight.pulse(123), TypeIconHighlight.pulse(123 + TypeIconHighlight.PULSE_PERIOD_MS), 1e-6);
    }

    @Test
    void alphaBoundsAndOpacity() {
        assertEquals(TypeIconHighlight.ALPHA_MIN, TypeIconHighlight.haloAlpha(0F, 1F));
        assertEquals(TypeIconHighlight.ALPHA_MAX, TypeIconHighlight.haloAlpha(1F, 1F));
        assertEquals(TypeIconHighlight.ALPHA_MAX, TypeIconHighlight.haloAlpha(7F, 3F));
        assertEquals(0, TypeIconHighlight.haloAlpha(1F, 0F));
        assertEquals(Math.round(TypeIconHighlight.ALPHA_MAX * .5F), TypeIconHighlight.haloAlpha(1F, .5F));
        for (long t = 0; t < TypeIconHighlight.PULSE_PERIOD_MS * 2; t += 11) {
            int a = TypeIconHighlight.sparkleAlpha(t, 1F);
            assertTrue(a >= 0 && a <= 220);
            assertEquals(0, TypeIconHighlight.sparkleAlpha(t, 0F));
        }
        assertEquals(0x80FFE08A, TypeIconHighlight.argb(0x80, TypeIconHighlight.rgb(RESONANCE)));
        assertEquals(0xFF9BECFF, TypeIconHighlight.argb(999, TypeIconHighlight.rgb(LINK)));
    }

    /** Positions from TypeIcon.render's bytecode (Cobblemon 1.8.1). */
    @Test
    void geometryMatchesTypeIcon() {
        assertEquals(18F, TypeIconHighlight.iconSize(false));
        assertEquals(9F, TypeIconHighlight.iconSize(true));
        // Not centered: drawn at x, secondary at x + secondaryOffset.
        assertEquals(100F, TypeIconHighlight.primaryLeft(100F, false, false, true, 5F));
        assertEquals(115F, TypeIconHighlight.secondaryLeft(100F, false, false, 15F, 5F));
        // Centered single: shifted by a quarter of the 36/18 px texture cell.
        assertEquals(91F, TypeIconHighlight.primaryLeft(100F, false, true, false, 5F));
        assertEquals(95.5F, TypeIconHighlight.primaryLeft(100F, true, true, false, 5F));
        // Centered dual: plus doubleCenteredOffset for both icons.
        assertEquals(86F, TypeIconHighlight.primaryLeft(100F, false, true, true, 5F));
        assertEquals(101F, TypeIconHighlight.secondaryLeft(100F, false, true, 15F, 5F));
        assertEquals(90.5F, TypeIconHighlight.secondaryLeft(100F, true, true, 0F, 5F));
        assertEquals(.5F, TypeIconHighlight.drawScale(true));
    }

    @Test
    void discRowsAreSymmetricAndWithinRadius() {
        int r = TypeIconHighlight.HALO_OUTER_RADIUS;
        for (int row = -r; row < r; row++) {
            int w = TypeIconHighlight.discHalfWidth(r, row);
            assertTrue(w >= 0 && w <= r);
            assertEquals(w, TypeIconHighlight.discHalfWidth(r, -row - 1));
        }
        assertEquals(r, TypeIconHighlight.discHalfWidth(r, 0));
        assertTrue(TypeIconHighlight.HALO_INNER_RADIUS > 9 && TypeIconHighlight.HALO_OUTER_RADIUS > TypeIconHighlight.HALO_INNER_RADIUS);
    }
}
