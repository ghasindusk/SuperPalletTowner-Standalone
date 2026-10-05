package starlight.trainer;

import com.cobblemon.mod.common.api.types.ElementalType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client drawing for {@link TypeIconHighlight}, called from {@code mixin.TypeIconMixin} around
 * Cobblemon's {@code TypeIcon.render}. A halo is drawn behind each matching icon before Cobblemon
 * draws it, and a small twinkle in front afterwards. Icons whose type is not active get nothing.
 */
public final class TypeIconHighlightClient {
    /** Implemented by Cobblemon's TypeIcon once the mixin is applied (checked by the harness). */
    public interface Marker {}

    private static List<String> cachedResonance;
    private static Set<String> cachedLinks;
    private static TypeIconHighlight.Selection cached = TypeIconHighlight.empty();

    // Read-only counters for the verification harness. Render thread only.
    private static long renderCalls;
    private static long highlightedIcons;
    private static String lastHighlightedType = "";
    private static TypeIconHighlight.Tier lastHighlightedTier = TypeIconHighlight.Tier.NONE;

    private TypeIconHighlightClient() {}

    /** The current selection; rebuilt only when the resonance list or link set is replaced. */
    public static TypeIconHighlight.Selection selection() {
        Minecraft mc = Minecraft.getInstance();
        List<String> resonance = AffinityHud.active();
        if (resonance.isEmpty() || mc.player == null) return TypeIconHighlight.empty();
        Set<String> links = ResonanceLinkEffects.active(mc.player);
        if (resonance != cachedResonance || links != cachedLinks) {
            List<List<String>> recipes = new ArrayList<>(links.size());
            for (String id : links) {
                var recipe = ResonanceAutogenCatalog.findId(id);
                if (recipe != null) recipes.add(recipe.types());
            }
            cached = TypeIconHighlight.select(resonance, recipes);
            cachedResonance = resonance;
            cachedLinks = links;
        }
        return cached;
    }

    public static long renderCalls() { return renderCalls; }
    public static long highlightedIcons() { return highlightedIcons; }
    public static String lastHighlighted() { return lastHighlightedType + ":" + lastHighlightedTier; }

    static String id(ElementalType type) {
        // Same mapping as the server uses for party types (ilight -> light, ...).
        return type == null ? null : TrainerState.canonicalType(type.getName());
    }

    /** Before TypeIcon.render: halos under the secondary, then the primary icon. */
    public static void before(GuiGraphics graphics, Number x, Number y, ElementalType type,
                              ElementalType secondary, boolean centeredX, boolean small,
                              float secondaryOffset, float doubleCenteredOffset, float opacity) {
        renderCalls++;
        TypeIconHighlight.Selection selection = selection();
        if (selection.isEmpty() || opacity <= 0F) return;
        int alpha = TypeIconHighlight.haloAlpha(TypeIconHighlight.pulse(Util.getMillis()), opacity);
        float top = y.floatValue();
        if (secondary != null) {
            TypeIconHighlight.Tier tier = selection.tier(id(secondary));
            if (tier != TypeIconHighlight.Tier.NONE) {
                halo(graphics, TypeIconHighlight.secondaryLeft(x.floatValue(), small, centeredX,
                        secondaryOffset, doubleCenteredOffset), top, small, tier, alpha);
                noteHighlight(secondary, tier);
            }
        }
        TypeIconHighlight.Tier tier = selection.tier(id(type));
        if (tier != TypeIconHighlight.Tier.NONE) {
            halo(graphics, TypeIconHighlight.primaryLeft(x.floatValue(), small, centeredX, secondary != null,
                    doubleCenteredOffset), top, small, tier, alpha);
            noteHighlight(type, tier);
        }
    }

    /** After TypeIcon.render: a brief four-point twinkle on the upper-right of matching icons. */
    public static void after(GuiGraphics graphics, Number x, Number y, ElementalType type,
                             ElementalType secondary, boolean centeredX, boolean small,
                             float secondaryOffset, float doubleCenteredOffset, float opacity) {
        TypeIconHighlight.Selection selection = selection();
        if (selection.isEmpty()) return;
        int alpha = TypeIconHighlight.sparkleAlpha(Util.getMillis(), opacity);
        if (alpha <= 0) return;
        float top = y.floatValue();
        if (secondary != null) {
            TypeIconHighlight.Tier tier = selection.tier(id(secondary));
            if (tier != TypeIconHighlight.Tier.NONE) {
                sparkle(graphics, TypeIconHighlight.secondaryLeft(x.floatValue(), small, centeredX,
                        secondaryOffset, doubleCenteredOffset), top, small, tier, alpha);
            }
        }
        TypeIconHighlight.Tier tier = selection.tier(id(type));
        if (tier != TypeIconHighlight.Tier.NONE) {
            sparkle(graphics, TypeIconHighlight.primaryLeft(x.floatValue(), small, centeredX, secondary != null,
                    doubleCenteredOffset), top, small, tier, alpha);
        }
    }

    private static void noteHighlight(ElementalType type, TypeIconHighlight.Tier tier) {
        highlightedIcons++;
        lastHighlightedType = type.getName();
        lastHighlightedTier = tier;
    }

    private static void halo(GuiGraphics graphics, float left, float top, boolean small,
                             TypeIconHighlight.Tier tier, int alpha) {
        float half = TypeIconHighlight.iconSize(small) / 2F;
        int rgb = TypeIconHighlight.rgb(tier);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + half, top + half, 0F);
        float scale = TypeIconHighlight.drawScale(small);
        pose.scale(scale, scale, 1F);
        disc(graphics, TypeIconHighlight.HALO_OUTER_RADIUS, TypeIconHighlight.argb(alpha * 9 / 20, rgb));
        disc(graphics, TypeIconHighlight.HALO_INNER_RADIUS, TypeIconHighlight.argb(alpha, rgb));
        pose.popPose();
    }

    private static void disc(GuiGraphics graphics, int radius, int color) {
        for (int row = -radius; row < radius; row++) {
            int halfWidth = TypeIconHighlight.discHalfWidth(radius, row);
            if (halfWidth > 0) graphics.fill(-halfWidth, row, halfWidth, row + 1, color);
        }
    }

    private static void sparkle(GuiGraphics graphics, float left, float top, boolean small,
                                TypeIconHighlight.Tier tier, int alpha) {
        float size = TypeIconHighlight.iconSize(small);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + size * 0.85F, top + size * 0.15F, 0F);
        float scale = TypeIconHighlight.drawScale(small);
        pose.scale(scale, scale, 1F);
        int arm = TypeIconHighlight.argb(alpha * 3 / 4, TypeIconHighlight.rgb(tier));
        graphics.fill(-3, 0, 4, 1, arm);
        graphics.fill(0, -3, 1, 4, arm);
        graphics.fill(0, 0, 1, 1, TypeIconHighlight.argb(alpha, 0xFFFFFF));
        pose.popPose();
    }
}
