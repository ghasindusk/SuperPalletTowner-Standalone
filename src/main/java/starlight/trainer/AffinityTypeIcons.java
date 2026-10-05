package starlight.trainer;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;

/**
 * Client-side symbol and name lookup for a type. Standard types are drawn straight from
 * Cobblemon's own type-symbol atlas ({@code cobblemon:textures/gui/types.png}, the sheet its
 * {@code TypeIcon} uses in the summary and Pokédex screens), so the marks match Cobblemon.
 * Anything else — the deferred extra types — gets our "?" orb.
 */
final class AffinityTypeIcons {
    static final ResourceLocation COBBLEMON_TYPES = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/types.png");
    static final ResourceLocation COBBLEMON_TYPES_SMALL = ResourceLocation.fromNamespaceAndPath("cobblemon",
            "textures/gui/types_small.png");
    static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID,
            "textures/gui/type_icons/unknown.png");
    private static final Set<String> EXTRA = Set.of("air", "light", "sound");

    private AffinityTypeIcons() {}

    /**
     * Cell in Cobblemon's atlas, or -1 for a non-standard type. Uses Cobblemon's registered
     * {@code textureXMultiplier}; the built-in table is only a fallback if the lookup fails.
     */
    static int atlasIndex(String type) {
        if (!AffinityView.isStandard(type)) return -1;
        try {
            ElementalType elemental = ElementalTypes.get(type.toLowerCase(Locale.ROOT));
            int index = elemental == null ? -1 : elemental.getTextureXMultiplier();
            if (index >= 0 && index < AffinityView.ATLAS_CELLS) return index;
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to the table that mirrors Cobblemon 1.8.1.
        }
        return AffinityView.atlasIndex(type);
    }

    /**
     * Draws the type symbol as a {@code size}×{@code size} square at (x, y). Below the normal
     * 18px size (small windows only) Cobblemon's 18px sheet is used, which is drawn for that.
     */
    static void draw(GuiGraphics graphics, String type, int x, int y, int size) {
        String canonical = type.toLowerCase(Locale.ROOT);
        if (EXTRA.contains(canonical)) {
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID,
                    "textures/gui/type_icons/" + canonical + ".png");
            graphics.blit(icon, x, y, size, size, 0, 0, 64, 64, 64, 64);
            return;
        }
        int index = atlasIndex(type);
        if (index < 0) {
            graphics.blit(UNKNOWN, x, y, size, size, 0, 0, AffinityView.ATLAS_CELL, AffinityView.ATLAS_CELL,
                    AffinityView.ATLAS_CELL, AffinityView.ATLAS_CELL);
            return;
        }
        boolean small = size < AffinityView.TYPE_ICON;
        int cell = small ? AffinityView.ATLAS_CELL / 2 : AffinityView.ATLAS_CELL;
        graphics.blit(small ? COBBLEMON_TYPES_SMALL : COBBLEMON_TYPES, x, y, size, size, index * cell, 0,
                cell, cell, cell * AffinityView.ATLAS_CELLS, cell);
    }

    /** Cobblemon's own type name (ほのお, Fire, …); unknown ids fall back to the raw name. */
    static Component name(String type) {
        String key = "cobblemon.type." + type.toLowerCase(Locale.ROOT);
        if (I18n.exists(key)) return Component.translatable(key);
        String local = "type.super_pallet_towner." + type.toLowerCase(Locale.ROOT);
        if (I18n.exists(local)) return Component.translatable(local);
        String raw = type.isEmpty() ? type : Character.toUpperCase(type.charAt(0)) + type.substring(1);
        return Component.literal(raw);
    }
}
