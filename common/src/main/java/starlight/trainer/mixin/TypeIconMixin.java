package starlight.trainer.mixin;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight.trainer.TypeIconHighlightClient;

/**
 * Halo for type icons whose type is in the active resonance or link, in every Cobblemon screen
 * that draws through TypeIcon. {@code require = 0}: a changed Cobblemon skips the effect instead
 * of failing to start. Cobblemon's own drawing is left untouched.
 */
@Mixin(value = TypeIcon.class, remap = false)
public abstract class TypeIconMixin implements TypeIconHighlightClient.Marker {
    @Shadow @Final private Number x;
    @Shadow @Final private Number y;
    @Shadow @Final private ElementalType type;
    @Shadow @Final private ElementalType secondaryType;
    @Shadow @Final private boolean centeredX;
    @Shadow @Final private boolean small;
    @Shadow @Final private float secondaryOffset;
    @Shadow @Final private float doubleCenteredOffset;
    @Shadow @Final private float opacity;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), require = 0)
    private void superPalletTowner$halo(GuiGraphics graphics, CallbackInfo ci) {
        TypeIconHighlightClient.before(graphics, x, y, type, secondaryType, centeredX, small,
                secondaryOffset, doubleCenteredOffset, opacity);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("TAIL"), require = 0)
    private void superPalletTowner$sparkle(GuiGraphics graphics, CallbackInfo ci) {
        TypeIconHighlightClient.after(graphics, x, y, type, secondaryType, centeredX, small,
                secondaryOffset, doubleCenteredOffset, opacity);
    }
}
