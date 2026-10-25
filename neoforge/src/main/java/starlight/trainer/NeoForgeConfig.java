package starlight.trainer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge config files behind {@link TrainerSettings} (common) and {@link HudSettings} (client). */
final class NeoForgeConfig {
    private NeoForgeConfig() {}

    // ------------------------------------------------------------- common

    static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue ALL_PLAYERS_WITHOUT_ORIGINS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Who is a Super Pallet Towner.").push("trainer");
        ALL_PLAYERS_WITHOUT_ORIGINS = builder.comment(
                        "Used only when NeoOrigins is NOT installed: true = every player is a Super Pallet Towner,",
                        "false = nobody is (the mod's mechanics are switched off).",
                        "With NeoOrigins installed this is ignored and players become one by choosing the Origin.")
                .define(TrainerSettings.KEY_ALL_PLAYERS, true);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    static void registerCommon(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, TrainerSettings.FILE);
        modBus.addListener(ModConfigEvent.Loading.class, event -> refreshCommon(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> refreshCommon(event.getConfig()));
    }

    private static void refreshCommon(ModConfig config) {
        if (config.getSpec() == COMMON_SPEC) TrainerSettings.load(ALL_PLAYERS_WITHOUT_ORIGINS.get());
    }

    // ------------------------------------------------------------- client

    static final ModConfigSpec CLIENT_SPEC;
    private static final ModConfigSpec.IntValue SCALE;
    private static final ModConfigSpec.ConfigValue<String> ANCHOR;
    private static final ModConfigSpec.IntValue OFFSET_X;
    private static final ModConfigSpec.IntValue OFFSET_Y;
    private static final ModConfigSpec.IntValue CENTER_X;
    private static final ModConfigSpec.IntValue CENTER_Y;
    private static final ModConfigSpec.BooleanValue ANIMATIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Resonance HUD (type symbols of the active type resonance).").push("hud");
        SCALE = builder.comment("HUD size in percent.")
                .defineInRange("hudScale", HudLayout.SCALE_DEFAULT, HudLayout.SCALE_MIN, HudLayout.SCALE_MAX);
        ANCHOR = builder.comment("Where the HUD sits: hotbar (default), top_left, top_right,"
                        + " bottom_left, bottom_right, or custom (position saved by dragging).")
                // NeoForge probes accepted values with null while creating a fresh config.
                // List.of(...).contains(null) throws, so hand it a null-tolerant list.
                .defineInList("hudAnchor", HudLayout.Anchor.HOTBAR.id,
                        new java.util.ArrayList<>(HudLayout.ANCHOR_IDS));
        OFFSET_X = builder.comment("Extra horizontal shift in GUI pixels (the HUD always stays on screen).")
                .defineInRange("hudOffsetX", 0, -HudLayout.OFFSET_LIMIT, HudLayout.OFFSET_LIMIT);
        OFFSET_Y = builder.comment("Extra vertical shift in GUI pixels (the HUD always stays on screen).")
                .defineInRange("hudOffsetY", 0, -HudLayout.OFFSET_LIMIT, HudLayout.OFFSET_LIMIT);
        CENTER_X = builder.comment("Custom HUD centre X in ten-thousandths of the GUI width.")
                .defineInRange("hudCenterX", HudLayout.POSITION_UNIT / 2, 0, HudLayout.POSITION_UNIT);
        CENTER_Y = builder.comment("Custom HUD centre Y in ten-thousandths of the GUI height.")
                .defineInRange("hudCenterY", HudLayout.POSITION_UNIT / 2, 0, HudLayout.POSITION_UNIT);
        ANIMATIONS = builder.comment("Animate the HUD (pop-in of new types, link activation ring, flowing link light).")
                .define("hudAnimations", true);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    static void registerClient(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, HudSettings.FILE);
        modBus.addListener(ModConfigEvent.Loading.class, event -> reloadClient(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> reloadClient(event.getConfig()));
        HudSettings.install(new HudSettings.Store() {
            @Override
            public void saveAnimations(boolean on) {
                try {
                    ANIMATIONS.set(on);
                    CLIENT_SPEC.save();
                } catch (IllegalStateException | NullPointerException ignored) {
                    // Config not loaded: the choice lasts this session only.
                }
            }

            @Override
            public void save(int scale, String anchor, int offsetX, int offsetY, int centerX, int centerY) {
                try {
                    SCALE.set(scale);
                    ANCHOR.set(anchor);
                    OFFSET_X.set(offsetX);
                    OFFSET_Y.set(offsetY);
                    CENTER_X.set(centerX);
                    CENTER_Y.set(centerY);
                    CLIENT_SPEC.save();
                } catch (IllegalStateException | NullPointerException ignored) {
                    // Config file not loaded (should not happen in game): the change lasts this session only.
                }
            }
        });
    }

    private static void reloadClient(ModConfig config) {
        if (config.getSpec() != CLIENT_SPEC) return;
        try {
            HudSettings.load(SCALE.get(), ANCHOR.get(), OFFSET_X.get(), OFFSET_Y.get(),
                    CENTER_X.get(), CENTER_Y.get(), ANIMATIONS.get());
        } catch (IllegalStateException | NullPointerException ignored) {
            // Not loaded yet: keep the defaults.
        }
    }
}
