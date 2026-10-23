package starlight.trainer;

import net.neoforged.fml.ModList;

/**
 * Whether NeoOrigins is installed. NeoOrigins is optional: every class that touches its API
 * ({@link NeoOriginsBridge}, {@link NeoOriginsClientBridge}, {@link OriginCooldownAffinity}) is only
 * reached behind this check, so the JVM never tries to load NeoOrigins classes when it is absent.
 */
public final class OriginsCompat {
    public static final String MOD_ID = "neoorigins";
    private static Boolean loaded;

    private OriginsCompat() {}

    public static boolean loaded() {
        Boolean value = loaded;
        if (value == null) {
            ModList list = ModList.get();
            value = list != null && list.isLoaded(MOD_ID);
            loaded = value;
        }
        return value;
    }
}
