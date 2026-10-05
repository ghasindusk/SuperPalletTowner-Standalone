package starlight.trainer;

/**
 * Origin validation logic for Super Pallet Towner (trainer) powers.
 *
 * <p>Only the primary {@code neoorigins:origin} layer may enable this add-on.
 *
 * <p>Uses String IDs so this logic can be unit-tested without loading Minecraft runtime classes.
 */
public final class TrainerOriginLogic {
    public static final String TRAINER_ORIGIN_ID = "starlight:super_pallet_towner";

    private TrainerOriginLogic() {}

    public static boolean isTrainer(String primary) {
        return TRAINER_ORIGIN_ID.equals(primary);
    }
}
