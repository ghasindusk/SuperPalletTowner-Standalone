package starlight.trainer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client entry points are registered only on the physical client. */
public final class AffinityClient {
    private static final KeyMapping OPEN = new KeyMapping(
            "key.super_pallet_towner.open_affinity",
            InputConstants.UNKNOWN.getValue(),
            "key.categories.super_pallet_towner");

    private AffinityClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AffinityClient::registerKeys);
        modBus.addListener(AffinityClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(AffinityClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(AffinityClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(AffinityClient::onScreenInit);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, AffinityHud.LAYER_ID, AffinityHud::render);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AffinityHud.clear();
        ResonanceLinkEffects.setClient(java.util.Set.of());
    }

    /** Adds a path from NeoOrigins' existing HUD editor to the resonance HUD editor. */
    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof com.cyberday1.neoorigins.client.ResourceHudEditorScreen screen)) return;
        int x = Math.max(4, screen.width - 100);
        int y = screen.width >= 425 ? 6 : 30;
        event.addListener(net.minecraft.client.gui.components.Button.builder(
                net.minecraft.network.chat.Component.translatable("ui.super_pallet_towner.neo_hud_button"),
                button -> {
                    // NeoOrigins persists its own changed HUD positions in onClose().
                    screen.onClose();
                    Minecraft.getInstance().setScreen(new AffinityHudEditorScreen());
                }).bounds(x, y, 96, 20).build());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                PacketDistributor.sendToServer(new AffinityPackets.Open());
            }
        }
    }

    public static void receiveSnapshot(AffinityPackets.Snapshot snapshot, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AffinityScreen screen) {
            screen.refresh(snapshot.json());
        } else {
            client.setScreen(new AffinityScreen(snapshot.json()));
        }
    }

    public static void receiveActiveTypes(AffinityPackets.ActiveTypes payload, IPayloadContext context) {
        AffinityHud.receive(payload.types());
    }

    public static void receiveActiveLinks(AffinityPackets.ActiveLinks payload, IPayloadContext context) {
        ResonanceLinkEffects.setClient(java.util.Set.copyOf(payload.recipes()));
    }
}
