package starlight.trainer;

/**
 * Client-only HUD settings in {@code config/super_pallet_towner-client.toml}: size, anchor and a
 * fine offset. The values are cached in plain fields so the HUD reads them every frame without
 * touching the config; the resonance screen's HUD tab changes them and saves the file. The loader
 * owns the file and installs a {@link Store}.
 */
public final class HudSettings {
    public static final String FILE = SuperPalletTowner.MOD_ID + "-client.toml";

    /** Writes the values to the client config file. */
    public interface Store {
        void saveAnimations(boolean on);

        void save(int scale, String anchor, int offsetX, int offsetY, int centerX, int centerY);
    }

    private static volatile Store store;

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

    public static void install(Store fileStore) { store = fileStore; }

    /** Values read from the config file; out-of-range values are clamped. */
    public static void load(int newScale, String newAnchor, int newOffsetX, int newOffsetY,
                            int newCenterX, int newCenterY, boolean newAnimations) {
        scale = HudLayout.clampScale(newScale);
        anchor = HudLayout.Anchor.of(newAnchor);
        offsetX = HudLayout.clampOffset(newOffsetX);
        offsetY = HudLayout.clampOffset(newOffsetY);
        centerX = Math.max(0, Math.min(HudLayout.POSITION_UNIT, newCenterX));
        centerY = Math.max(0, Math.min(HudLayout.POSITION_UNIT, newCenterY));
        animations = newAnimations;
        version++;
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
        Store fileStore = store;
        // No store (should not happen in game): the choice lasts this session only.
        if (fileStore != null) fileStore.saveAnimations(on);
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
        Store fileStore = store;
        // No store (should not happen in game): the change lasts this session only.
        if (fileStore != null) fileStore.save(scale, anchor.id, offsetX, offsetY, centerX, centerY);
    }

    public static void reset() {
        set(HudLayout.SCALE_DEFAULT, HudLayout.Anchor.HOTBAR, 0, 0);
    }
}
