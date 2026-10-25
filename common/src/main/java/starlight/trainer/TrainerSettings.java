package starlight.trainer;

/**
 * Common settings in {@code config/super_pallet_towner-common.toml}. The loader reads the file and
 * hands the values here (NeoForge: a config spec; Fabric: a small TOML reader).
 */
public final class TrainerSettings {
    public static final String FILE = SuperPalletTowner.MOD_ID + "-common.toml";
    public static final String KEY_ALL_PLAYERS = "allPlayersWithoutNeoOrigins";
    private static volatile boolean allPlayersWithoutOrigins = true;

    private TrainerSettings() {}

    public static void load(boolean allPlayers) { allPlayersWithoutOrigins = allPlayers; }

    public static boolean allPlayersWithoutOrigins() { return allPlayersWithoutOrigins; }
}
