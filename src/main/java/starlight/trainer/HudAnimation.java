package starlight.trainer;

/**
 * Timing curves for the resonance HUD animations, free of Minecraft classes so they can be unit
 * tested. All inputs are elapsed milliseconds; every curve settles to its resting value, so a HUD
 * that has been on screen for a while looks exactly like the static design.
 */
final class HudAnimation {
    /** A newly active type pops in over this time. */
    static final long POP_MS = 320L;
    /** A ring expands from linked badges when a link (or harmonic) becomes active. */
    static final long BURST_MS = 700L;
    /** One trip of the light along a link connector or around the harmonic triangle. */
    static final long FLOW_PERIOD_MS = 1400L;
    /** Breathing period of the harmonic centre star. */
    static final long STAR_PERIOD_MS = 2000L;

    private HudAnimation() {}

    /** Scale of a newly shown badge: grows from 0.35 with a small overshoot, then rests at 1. */
    static float popScale(long elapsedMs) {
        if (elapsedMs < 0) return 0.35F;
        if (elapsedMs >= POP_MS) return 1F;
        float t = elapsedMs / (float) POP_MS;
        return 0.35F + 0.65F * easeOutBack(t);
    }

    /** easeOutBack: 0 at t=0, 1 at t=1, about 1.1 near t=0.6. */
    static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1F;
        float u = t - 1F;
        return 1F + c3 * u * u * u + c1 * u * u;
    }

    /** Extra radius (GUI px before HUD scaling) of the activation ring; 0 once finished. */
    static int burstRadius(long elapsedMs, int maxExtra) {
        if (elapsedMs < 0 || elapsedMs >= BURST_MS) return 0;
        float t = elapsedMs / (float) BURST_MS;
        float eased = 1F - (1F - t) * (1F - t);
        return 1 + Math.round(eased * (maxExtra - 1));
    }

    /** Alpha (0-255) of the activation ring; fades out, 0 once finished. */
    static int burstAlpha(long elapsedMs) {
        if (elapsedMs < 0 || elapsedMs >= BURST_MS) return 0;
        float t = elapsedMs / (float) BURST_MS;
        return Math.round(220F * (1F - t));
    }

    /** Position 0..1 of the travelling light along a path, repeating every FLOW_PERIOD_MS. */
    static float flow(long nowMs) {
        return Math.floorMod(nowMs, FLOW_PERIOD_MS) / (float) FLOW_PERIOD_MS;
    }

    /** Brightness 0..1 of the harmonic star, a smooth sine breath. */
    static float starBreath(long nowMs) {
        double phase = Math.floorMod(nowMs, STAR_PERIOD_MS) / (double) STAR_PERIOD_MS;
        return (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
    }

    /** Linear interpolation of an int coordinate. */
    static int lerp(int a, int b, float t) {
        return a + Math.round((b - a) * t);
    }

    /** Combines an alpha (0-255) with an RGB colour. */
    static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
