package starlight.trainer;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The same two TOML files as on NeoForge ({@code super_pallet_towner-common.toml} and
 * {@code super_pallet_towner-client.toml}), read and written by a small reader for flat
 * {@code key = value} lines. A missing file is created with the defaults.
 */
final class FabricConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FabricConfig() {}

    // ------------------------------------------------------------- common

    static void loadCommon() {
        Path file = file(TrainerSettings.FILE);
        Map<String, String> values = read(file);
        boolean allPlayers = bool(values.get(TrainerSettings.KEY_ALL_PLAYERS), true);
        TrainerSettings.load(allPlayers);
        if (!Files.exists(file)) writeCommon(file, allPlayers);
    }

    private static void writeCommon(Path file, boolean allPlayers) {
        write(file, """
                #Who is a Super Pallet Towner.
                [trainer]
                \t#Fabric has no NeoOrigins: true = every player is a Super Pallet Towner,
                \t#false = nobody is (the mod's mechanics are switched off).
                \t%s = %s
                """.formatted(TrainerSettings.KEY_ALL_PLAYERS, allPlayers));
    }

    // ------------------------------------------------------------- client

    static void loadClient() {
        Path file = file(HudSettings.FILE);
        Map<String, String> values = read(file);
        HudSettings.load(
                integer(values.get("hudScale"), HudLayout.SCALE_DEFAULT),
                values.getOrDefault("hudAnchor", HudLayout.Anchor.HOTBAR.id),
                integer(values.get("hudOffsetX"), 0),
                integer(values.get("hudOffsetY"), 0),
                integer(values.get("hudCenterX"), HudLayout.POSITION_UNIT / 2),
                integer(values.get("hudCenterY"), HudLayout.POSITION_UNIT / 2),
                bool(values.get("hudAnimations"), true));
        HudSettings.install(new HudSettings.Store() {
            @Override
            public void saveAnimations(boolean on) {
                writeClient(file);
            }

            @Override
            public void save(int scale, String anchor, int offsetX, int offsetY, int centerX, int centerY) {
                writeClient(file);
            }
        });
        if (!Files.exists(file)) writeClient(file);
    }

    private static void writeClient(Path file) {
        write(file, """
                #Resonance HUD (type symbols of the active type resonance).
                [hud]
                \t#HUD size in percent.
                \t#Range: %d ~ %d
                \thudScale = %d
                \t#Where the HUD sits: hotbar (default), top_left, top_right, bottom_left, bottom_right, or custom (position saved by dragging).
                \thudAnchor = "%s"
                \t#Extra horizontal shift in GUI pixels (the HUD always stays on screen).
                \thudOffsetX = %d
                \t#Extra vertical shift in GUI pixels (the HUD always stays on screen).
                \thudOffsetY = %d
                \t#Custom HUD centre X in ten-thousandths of the GUI width.
                \thudCenterX = %d
                \t#Custom HUD centre Y in ten-thousandths of the GUI height.
                \thudCenterY = %d
                \t#Animate the HUD (pop-in of new types, link activation ring, flowing link light).
                \thudAnimations = %s
                """.formatted(HudLayout.SCALE_MIN, HudLayout.SCALE_MAX, HudSettings.scale(), HudSettings.anchor().id,
                HudSettings.offsetX(), HudSettings.offsetY(), HudSettings.centerX(), HudSettings.centerY(),
                HudSettings.animations()));
    }

    // ------------------------------------------------------------- file helpers

    private static Path file(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }

    /** {@code key = value} pairs of the file, ignoring comments and [sections]; empty when missing or unreadable. */
    static Map<String, String> read(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(file)) return values;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String text = line.strip();
                int equals = text.indexOf('=');
                if (text.isEmpty() || text.startsWith("#") || text.startsWith("[") || equals < 0) continue;
                String value = text.substring(equals + 1).strip();
                int comment = value.startsWith("\"") ? -1 : value.indexOf('#');
                if (comment >= 0) value = value.substring(0, comment).strip();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(text.substring(0, equals).strip(), value);
            }
        } catch (IOException e) {
            LOGGER.warn("Cannot read {}; using the defaults", file, e);
        }
        return values;
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Cannot write {}", file, e);
        }
    }

    static boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> fallback;
        };
    }

    static int integer(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
