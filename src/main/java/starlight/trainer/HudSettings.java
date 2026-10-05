package starlight.trainer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only HUD settings in {@code config/super_pallet_towner-client.toml}: size, anchor and a
 * fine offset. The values are cached in plain fields so the HUD reads them every frame without
 * touching the config; the resonance screen's HUD tab changes them and saves the file.
 */
public final class HudSettings {
    public static final String FILE = SuperPalletTowner.MOD_ID + "-client.toml";
    static final ModConfigSpec SPEC;
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
        SPEC = builder.build();
    }

    private static volatile int scale = HudLayout.SCALE_DEFAULT;
    private static volatile HudLayout.Anchor anchor = HudLayout.Anchor.HOTBAR;
    private static volatile int offsetX;
    private static volatile int offsetY;
    private static volatile int centerX = HudLayout.POSITION_UNIT / 2;
    private static volatile int centerY = HudLayout.POSITION_UNIT / 2;
    private static volatile boolean animations = true;
    /** Bumped on every change so the HUD can keep its cached placement until then. */
    private static volatile int version;

    private HudSettings() {}

    static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, FILE);
        modBus.addListener(ModConfigEvent.Loading.class, event -> reload(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> reload(event.getConfig()));
    }

    private static void reload(ModConfig config) {
        if (config.getSpec() != SPEC) return;
        try {
            scale = HudLayout.clampScale(SCALE.get());
            anchor = HudLayout.Anchor.of(ANCHOR.get());
            offsetX = HudLayout.clampOffset(OFFSET_X.get());
            offsetY = HudLayout.clampOffset(OFFSET_Y.get());
            centerX = CENTER_X.get();
            centerY = CENTER_Y.get();
            animations = ANIMATIONS.get();
            version++;
        } catch (IllegalStateException | NullPointerException ignored) {
            // Not loaded yet: keep the defaults.
        }
    }

    public static int scale() { return scale; }
    public static HudLayout.Anchor anchor() { return anchor; }
    public static int offsetX() { return offsetX; }
    public static int offsetY() { return offsetY; }
    public static int centerX() { return centerX; }
    public static int centerY() { return centerY; }
    public static int version() { return version; }
    public static boolean animations() { return animations; }

    /** Turns the HUD animations on or off and saves the choice. */
    public static void setAnimations(boolean on) {
        animations = on;
        try {
            ANIMATIONS.set(on);
            SPEC.save();
        } catch (IllegalStateException | NullPointerException ignored) {
            // Config not loaded: the choice lasts this session only.
        }
    }

    /** Preview a dragged position independently of the hotbar and the number of icons. */
    public static void previewCustomCenter(double x, double y, int guiWidth, int guiHeight) {
        anchor = HudLayout.Anchor.CUSTOM;
        centerX = HudLayout.positionFraction(x, guiWidth);
        centerY = HudLayout.positionFraction(y, guiHeight);
        offsetX = 0;
        offsetY = 0;
        version++;
    }

    public static void setCustomCenterFractions(int x, int y) {
        anchor = HudLayout.Anchor.CUSTOM;
        centerX = Math.max(0, Math.min(HudLayout.POSITION_UNIT, x));
        centerY = Math.max(0, Math.min(HudLayout.POSITION_UNIT, y));
        offsetX = 0;
        offsetY = 0;
        version++;
        saveCurrent();
    }

    /** Changes the settings now and writes them to the client config file. */
    public static void set(int newScale, HudLayout.Anchor newAnchor, int newOffsetX, int newOffsetY) {
        scale = HudLayout.clampScale(newScale);
        anchor = newAnchor == null ? HudLayout.Anchor.HOTBAR : newAnchor;
        offsetX = HudLayout.clampOffset(newOffsetX);
        offsetY = HudLayout.clampOffset(newOffsetY);
        version++;
        saveCurrent();
    }

    /** Persist the current preview state once the drag ends. */
    public static void saveCurrent() {
        try {
            SCALE.set(scale);
            ANCHOR.set(anchor.id);
            OFFSET_X.set(offsetX);
            OFFSET_Y.set(offsetY);
            CENTER_X.set(centerX);
            CENTER_Y.set(centerY);
            SPEC.save();
        } catch (IllegalStateException | NullPointerException ignored) {
            // Config file not loaded (should not happen in game): the change lasts this session only.
        }
    }

    public static void reset() {
        set(HudLayout.SCALE_DEFAULT, HudLayout.Anchor.HOTBAR, 0, 0);
    }
}
