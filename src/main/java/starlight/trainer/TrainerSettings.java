package starlight.trainer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Common settings in {@code config/super_pallet_towner-common.toml}. */
public final class TrainerSettings {
    public static final String FILE = SuperPalletTowner.MOD_ID + "-common.toml";
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ALL_PLAYERS_WITHOUT_ORIGINS;
    private static volatile boolean allPlayersWithoutOrigins = true;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Who is a Super Pallet Towner.").push("trainer");
        ALL_PLAYERS_WITHOUT_ORIGINS = builder.comment(
                        "Used only when NeoOrigins is NOT installed: true = every player is a Super Pallet Towner,",
                        "false = nobody is (the mod's mechanics are switched off).",
                        "With NeoOrigins installed this is ignored and players become one by choosing the Origin.")
                .define("allPlayersWithoutNeoOrigins", true);
        builder.pop();
        SPEC = builder.build();
    }

    private TrainerSettings() {}

    static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, FILE);
        modBus.addListener(TrainerSettings::onLoad);
        modBus.addListener(TrainerSettings::onReload);
    }

    private static void onLoad(ModConfigEvent.Loading event) { refresh(event.getConfig()); }

    private static void onReload(ModConfigEvent.Reloading event) { refresh(event.getConfig()); }

    private static void refresh(ModConfig config) {
        if (config.getSpec() == SPEC) allPlayersWithoutOrigins = ALL_PLAYERS_WITHOUT_ORIGINS.get();
    }

    public static boolean allPlayersWithoutOrigins() { return allPlayersWithoutOrigins; }
}
