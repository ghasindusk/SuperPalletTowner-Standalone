package starlight.trainer;

import com.cyberday1.neoorigins.client.ResourceHudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-side NeoOrigins integration. Only registered when {@link OriginsCompat#loaded()} is true. */
final class NeoOriginsClientBridge {
    private NeoOriginsClientBridge() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(NeoOriginsClientBridge::onScreenInit);
    }

    /** Adds a path from NeoOrigins' existing HUD editor to the resonance HUD editor. */
    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ResourceHudEditorScreen screen)) return;
        int x = Math.max(4, screen.width - 100);
        int y = screen.width >= 425 ? 6 : 30;
        event.addListener(Button.builder(
                Component.translatable("ui.super_pallet_towner.neo_hud_button"),
                button -> {
                    // NeoOrigins persists its own changed HUD positions in onClose().
                    screen.onClose();
                    Minecraft.getInstance().setScreen(new AffinityHudEditorScreen(ResourceHudEditorScreen::new));
                }).bounds(x, y, 96, 20).build());
    }
}
