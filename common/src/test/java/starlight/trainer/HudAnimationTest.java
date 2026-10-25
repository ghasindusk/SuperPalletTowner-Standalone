package starlight.trainer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudAnimationTest {
    @Test
    void popStartsSmallOvershootsAndRestsAtOne() {
        assertEquals(0.35F, HudAnimation.popScale(-5), 1e-6);
        assertEquals(0.35F, HudAnimation.popScale(0), 1e-4);
        float peak = 0F;
        for (long t = 0; t < HudAnimation.POP_MS; t += 5) peak = Math.max(peak, HudAnimation.popScale(t));
        assertTrue(peak > 1.0F && peak < 1.15F, "small overshoot, got " + peak);
        assertEquals(1F, HudAnimation.popScale(HudAnimation.POP_MS), 0F);
        assertEquals(1F, HudAnimation.popScale(60_000), 0F);
    }

    @Test
    void easeOutBackEndpoints() {
        assertEquals(0F, HudAnimation.easeOutBack(0F), 1e-5);
        assertEquals(1F, HudAnimation.easeOutBack(1F), 1e-5);
    }

    @Test
    void burstGrowsFadesAndStops() {
        assertEquals(0, HudAnimation.burstAlpha(-1));
        assertEquals(0, HudAnimation.burstRadius(-1, 8));
        assertTrue(HudAnimation.burstAlpha(0) > 200);
        assertTrue(HudAnimation.burstRadius(600, 8) > HudAnimation.burstRadius(100, 8));
        assertTrue(HudAnimation.burstRadius(HudAnimation.BURST_MS - 1, 8) <= 8);
        assertTrue(HudAnimation.burstAlpha(600) < HudAnimation.burstAlpha(100));
        assertEquals(0, HudAnimation.burstAlpha(HudAnimation.BURST_MS));
        assertEquals(0, HudAnimation.burstRadius(HudAnimation.BURST_MS, 8));
    }

    @Test
    void flowAndBreathStayInRangeAndRepeat() {
        for (long t = -5000; t < 5000; t += 37) {
            float f = HudAnimation.flow(t);
            assertTrue(f >= 0F && f < 1F, "flow " + f);
            float b = HudAnimation.starBreath(t);
            assertTrue(b >= 0F && b <= 1F, "breath " + b);
        }
        assertEquals(HudAnimation.flow(123), HudAnimation.flow(123 + HudAnimation.FLOW_PERIOD_MS), 1e-6);
    }

    @Test
    void helpers() {
        assertEquals(5, HudAnimation.lerp(0, 10, 0.5F));
        assertEquals(10, HudAnimation.lerp(10, 20, 0F));
        assertEquals(0x80123456, HudAnimation.argb(0x80, 0xFF123456));
        assertEquals(0xFF000000 | 0xABCDEF, HudAnimation.argb(999, 0xABCDEF));
        assertEquals(0x00ABCDEF, HudAnimation.argb(-4, 0xABCDEF));
    }
}
