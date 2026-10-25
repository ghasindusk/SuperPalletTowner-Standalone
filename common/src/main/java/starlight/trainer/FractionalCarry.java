package starlight.trainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a percentage bonus to integer rewards without losing it to rounding.
 * XP orbs are mostly worth 1-3 points, and round(2 * 1.10) is still 2.
 */
final class FractionalCarry {
    private final Map<UUID, Double> remainders = new HashMap<>();

    int scale(UUID key, int amount, double bonus) {
        if (amount <= 0 || bonus <= 0) return amount;
        double earned = amount * bonus + remainders.getOrDefault(key, 0.0);
        int extra = (int) Math.floor(earned + 1e-9);
        remainders.put(key, Math.max(0, earned - extra));
        return amount + extra;
    }

    void forget(UUID key) {
        remainders.remove(key);
    }
}
