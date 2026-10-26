package starlight.trainer;

import java.util.Collection;
import java.util.List;

/**
 * Who counts as a Super Pallet Towner (trainer).
 *
 * <p>With NeoOrigins installed, a player is a trainer when any Origin layer holds the Super Pallet
 * Towner Origin: normally the primary {@code neoorigins:origin} layer, but a modpack may also offer
 * it on a sub-origin layer of its own. Without NeoOrigins, the {@code allPlayersWithoutNeoOrigins}
 * setting decides for everyone.
 *
 * <p>Uses String IDs so this logic can be unit-tested without loading Minecraft runtime classes.
 */
public final class TrainerOriginLogic {
    public static final String TRAINER_ORIGIN_ID = "starlight:super_pallet_towner";

    private TrainerOriginLogic() {}

    public static boolean isTrainer(String primary) {
        return isTrainer(primary == null ? List.of() : List.of(primary));
    }

    /** {@code origins} are the player's Origins on every layer. */
    public static boolean isTrainer(Collection<String> origins) {
        return origins.contains(TRAINER_ORIGIN_ID);
    }

    public static boolean isTrainer(boolean originsLoaded, String primary, boolean allPlayersWithoutOrigins) {
        return originsLoaded ? isTrainer(primary) : allPlayersWithoutOrigins;
    }

    public static boolean isTrainer(boolean originsLoaded, Collection<String> origins, boolean allPlayersWithoutOrigins) {
        return originsLoaded ? isTrainer(origins) : allPlayersWithoutOrigins;
    }
}
