package starlight.trainer;

import com.cyberday1.neoorigins.client.ResourceHudEditorScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;

/** Drag-and-scale editor launched from NeoOrigins' HUD editor. */
public final class AffinityHudEditorScreen extends Screen {
    private static final List<String> SAMPLE_TYPES = List.of("fire", "rock", "steel");
    private static final Set<String> NO_LINKS = Set.of();

    private boolean dragging;
    private double startMouseX;
    private double startMouseY;
    private double startCenterX;
    private double startCenterY;
    private boolean migratedLegacyPosition;

    public AffinityHudEditorScreen() {
        super(Component.translatable("ui.super_pallet_towner.neo_hud_title"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> scale(-HudLayout.SCALE_STEP))
                .bounds(cx - 62, 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> scale(HudLayout.SCALE_STEP))
                .bounds(cx + 42, 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ui.super_pallet_towner.hud_reset"),
                button -> HudSettings.reset()).bounds(6, 6, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 81, 6, 75, 20).build());
    }

    private void scale(int delta) {
        HudSettings.set(HudSettings.scale() + delta, HudSettings.anchor(),
                HudSettings.offsetX(), HudSettings.offsetY());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render draws the blurred background; it must happen before the live HUD preview.
        super.render(graphics, mouseX, mouseY, partialTick);
        List<String> types = AffinityHud.active();
        boolean sample = types.isEmpty();
        if (sample) types = SAMPLE_TYPES;
        Set<String> links = !sample && minecraft != null && minecraft.player != null
                ? ResonanceLinkEffects.active(minecraft.player) : NO_LINKS;
        if (!migratedLegacyPosition) {
            migratedLegacyPosition = true;
            if (HudSettings.anchor() != HudLayout.Anchor.CUSTOM
                    && (HudSettings.offsetX() != 0 || HudSettings.offsetY() != 0)) {
                // Old drag settings were relative to the hotbar/edge. Keep their current visual
                // centre once, then store it independently of the changing preset geometry.
                HudLayout.Placement initial = AffinityHud.previewPlacement(types, links, width, height);
                if (initial != null) {
                    HudSettings.previewCustomCenter(initial.x() + initial.width() / 2.0,
                            initial.y() + initial.height() / 2.0, width, height);
                    HudSettings.saveCurrent();
                }
            }
        }
        AffinityHud.draw(graphics, types, links, width, height);
        int[] bounds = AffinityHud.lastBounds();
        int outline = dragging ? 0xFF9BECFF : 0xFFFFD45A;
        graphics.renderOutline(bounds[0] - 2, bounds[1] - 2,
                bounds[2] + 4, bounds[3] + 4, outline);
        graphics.fill(0, 33, width, 65, 0xB0101626);
        graphics.drawCenteredString(font, title, width / 2, 37, 0xFFF8F0DE);
        graphics.drawCenteredString(font,
                Component.translatable("ui.super_pallet_towner.neo_hud_hint"), width / 2, 51, 0xFF9AA6BC);
        graphics.drawCenteredString(font,
                Component.translatable("ui.super_pallet_towner.hud_scale", HudSettings.scale()),
                width / 2, 12, 0xFFF8F0DE);
        if (sample) graphics.drawCenteredString(font,
                Component.translatable("ui.super_pallet_towner.neo_hud_sample"),
                width / 2, height - 18, 0xFFFFD45A);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int[] b = AffinityHud.lastBounds();
        if (button == 0 && mouseX >= b[0] - 3 && mouseX < b[0] + b[2] + 3
                && mouseY >= b[1] - 3 && mouseY < b[1] + b[3] + 3) {
            dragging = true;
            startMouseX = mouseX;
            startMouseY = mouseY;
            startCenterX = b[0] + b[2] / 2.0;
            startCenterY = b[1] + b[3] / 2.0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            HudSettings.previewCustomCenter(startCenterX + mouseX - startMouseX,
                    startCenterY + mouseY - startMouseY, width, height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            HudSettings.saveCurrent();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (dragging) {
            dragging = false;
            HudSettings.saveCurrent();
        }
        if (minecraft != null) minecraft.setScreen(new ResourceHudEditorScreen());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
