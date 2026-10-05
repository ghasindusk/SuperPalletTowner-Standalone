package starlight.trainer;

/**
 * Grass "hunger consumption -10%": discounts exhaustion gained between two
 * server ticks without touching food changes that vanilla did not make.
 *
 * <p>Vanilla {@code FoodData.tick} crosses the 4-point boundary at most once per
 * tick: exhaustion -= 4, then saturation = max(saturation - 1, 0), or food - 1
 * when saturation is already 0. Only that exact signature is treated as
 * consumption and may be refunded. Anything else that lowers food or
 * saturation (commands, other mods, Origin powers) is left alone.
 */
final class GrassHunger {
    static final float RATE = (float) (1 - AffinityBalance.GRASS_EXHAUSTION_SAVING);

    record Sample(float exhaustion, float saturation, int foodLevel) {}

    private GrassHunger() {}

    /** Returns the corrected sample, or {@code current} unchanged. */
    static Sample adjust(Sample previous, Sample current) {
        return adjust(previous, current, RATE);
    }

    static Sample adjust(Sample previous, Sample current, float rate) {
        float prevEx = previous.exhaustion();
        float ex = current.exhaustion();
        if (crossedBoundary(previous, current)) {
            float gained = ex + 4 - prevEx;
            float discounted = prevEx + gained * rate;
            if (discounted <= 4) {
                // The discount keeps this gain under the boundary: undo the crossing.
                return new Sample(discounted, previous.saturation(), previous.foodLevel());
            }
            return new Sample(discounted - 4, current.saturation(), current.foodLevel());
        }
        if (ex > prevEx) {
            return new Sample(ex - (ex - prevEx) * (1 - rate), current.saturation(), current.foodLevel());
        }
        return current;
    }

    /** True only for the single vanilla 4-point step, not for external food changes. */
    static boolean crossedBoundary(Sample previous, Sample current) {
        float ex = current.exhaustion();
        // Vanilla subtracts 4 from a value above 4, so exhaustion drops and stays positive.
        if (!(ex > 0 && ex < previous.exhaustion() && ex + 4 >= previous.exhaustion())) return false;
        float prevSat = previous.saturation();
        if (prevSat > 0) {
            return current.foodLevel() == previous.foodLevel()
                    && current.saturation() == Math.max(prevSat - 1.0F, 0.0F);
        }
        return current.saturation() == 0 && current.foodLevel() == previous.foodLevel() - 1;
    }
}
