package starlight.trainer;

/**
 * Who counts as a Super Pallet Towner (trainer).
 *
 * <p>With NeoOrigins installed, only the primary {@code neoorigins:origin} layer may enable this
 * add-on. Without NeoOrigins, the {@code allPlayersWithoutNeoOrigins} setting decides for everyone.
 *
 * <p>Uses String IDs so this logic can be unit-tested without loading Minecraft runtime classes.
 */
public final class TrainerOriginLogic {
    public static final String TRAINER_ORIGIN_ID = "starlight:super_pallet_towner";

    private TrainerOriginLogic() {}

    public static boolean isTrainer(String primary) {
        return TRAINER_ORIGIN_ID.equals(primary);
    }

    public static boolean isTrainer(boolean originsLoaded, String primary, boolean allPlayersWithoutOrigins) {
        return originsLoaded ? isTrainer(primary) : allPlayersWithoutOrigins;
    }
}
