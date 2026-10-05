package starlight.trainer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grass hunger -10%: natural consumption is discounted, external food changes are kept. */
class GrassHungerTest {
    private static final float EPS = 1e-5F;

    private static GrassHunger.Sample s(float exhaustion, float saturation, int food) {
        return new GrassHunger.Sample(exhaustion, saturation, food);
    }

    /** One vanilla FoodData.tick step after {@code added} exhaustion. */
    private static GrassHunger.Sample vanilla(GrassHunger.Sample from, float added) {
        float ex = Math.min(from.exhaustion() + added, 40);
        float sat = from.saturation();
        int food = from.foodLevel();
        if (ex > 4) {
            ex -= 4;
            if (sat > 0) sat = Math.max(sat - 1, 0);
            else food = Math.max(food - 1, 0);
        }
        return s(ex, sat, food);
    }

    @Test
    void exhaustionGainIsDiscounted() {
        GrassHunger.Sample out = GrassHunger.adjust(s(1, 5, 20), s(2, 5, 20));
        assertEquals(1.9F, out.exhaustion(), EPS);
        assertEquals(5, out.saturation(), EPS);
    }

    @Test
    void crossingUndoneWhenDiscountStaysUnderFour() {
        // 3.81 + 0.2 crosses in vanilla; 3.81 + 0.18 does not.
        GrassHunger.Sample out = GrassHunger.adjust(s(3.81F, 5, 20), vanilla(s(3.81F, 5, 20), 0.2F));
        assertEquals(3.99F, out.exhaustion(), EPS);
        assertEquals(5, out.saturation(), EPS);
        assertEquals(20, out.foodLevel());
    }

    @Test
    void crossingKeptWhenDiscountStillPassesFour() {
        GrassHunger.Sample out = GrassHunger.adjust(s(3.5F, 0, 18), vanilla(s(3.5F, 0, 18), 1.0F));
        assertEquals(0.4F, out.exhaustion(), EPS);
        assertEquals(17, out.foodLevel());
    }

    @Test
    void foodStepUsedWhenSaturationIsEmpty() {
        GrassHunger.Sample out = GrassHunger.adjust(s(3.81F, 0, 18), vanilla(s(3.81F, 0, 18), 0.2F));
        assertEquals(18, out.foodLevel());
        assertEquals(3.99F, out.exhaustion(), EPS);
    }

    @Test
    void partialSaturationStepIsVanilla() {
        assertTrue(GrassHunger.crossedBoundary(s(3.9F, 0.5F, 20), vanilla(s(3.9F, 0.5F, 20), 0.2F)));
    }

    /** The regression found in game: 20/10 set to 15/0 by a command came back as saturation 10. */
    @Test
    void externalFoodAndSaturationDropIsNotReverted() {
        GrassHunger.Sample current = s(0, 0, 15);
        assertSame(current, GrassHunger.adjust(s(0, 10, 20), current));
    }

    @Test
    void externalChangesNotMistakenForVanilla() {
        // Exhaustion unchanged: vanilla always moves it on a crossing.
        assertFalse(GrassHunger.crossedBoundary(s(2, 5, 20), s(2, 4, 20)));
        assertFalse(GrassHunger.crossedBoundary(s(2, 0, 20), s(2, 0, 19)));
        // Exhaustion fell but food moved by more than one vanilla step.
        assertFalse(GrassHunger.crossedBoundary(s(3.9F, 5, 20), s(0.1F, 3, 20)));
        assertFalse(GrassHunger.crossedBoundary(s(3.9F, 0, 20), s(0.1F, 0, 18)));
        assertFalse(GrassHunger.crossedBoundary(s(3.9F, 5, 20), s(0.1F, 4, 19)));
        // Exhaustion reset to 0 together with a food change (e.g. a command).
        assertFalse(GrassHunger.crossedBoundary(s(3.9F, 5, 20), s(0, 4, 20)));
        // Exhaustion dropped by more than 4: not a single vanilla step.
        assertFalse(GrassHunger.crossedBoundary(s(9, 5, 20), s(0.5F, 4, 20)));
    }

    @Test
    void externalDropWithExhaustionGainOnlyDiscountsTheGain() {
        GrassHunger.Sample out = GrassHunger.adjust(s(1, 10, 20), s(2, 0, 15));
        assertEquals(1.9F, out.exhaustion(), EPS);
        assertEquals(0, out.saturation(), EPS);
        assertEquals(15, out.foodLevel());
    }

    @Test
    void eatingIsLeftAlone() {
        GrassHunger.Sample current = s(1, 8, 20);
        assertSame(current, GrassHunger.adjust(s(1, 2, 14), current));
    }

    /** Long run: the same exhaustion stream costs 10% fewer food points with Grass. */
    @Test
    void longRunConsumptionIsNinetyPercent() {
        GrassHunger.Sample plain = s(0, 20, 20);
        GrassHunger.Sample grass = plain;
        for (int i = 0; i < 1200; i++) {
            plain = vanilla(plain, 0.1F);
            grass = GrassHunger.adjust(grass, vanilla(grass, 0.1F));
        }
        float plainUsed = 20 - plain.saturation() + 20 - plain.foodLevel() + plain.exhaustion() / 4;
        float grassUsed = 20 - grass.saturation() + 20 - grass.foodLevel() + grass.exhaustion() / 4;
        assertEquals(0.90, grassUsed / plainUsed, 0.01);
    }
}
