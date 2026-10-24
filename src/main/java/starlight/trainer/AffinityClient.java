package starlight.trainer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client entry points are registered only on the physical client. */
public final class AffinityClient {
    private static final KeyMapping OPEN = new KeyMapping(
            "key.super_pallet_towner.open_affinity",
            // Default Y (since 1.2.0): free in vanilla, Cobblemon and NeoOrigins. Players can rebind it in Controls.
            InputConstants.KEY_Y,
            "key.categories.super_pallet_towner");

    private AffinityClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AffinityClient::registerKeys);
        modBus.addListener(AffinityClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(AffinityClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(AffinityClient::onLoggingOut);
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
