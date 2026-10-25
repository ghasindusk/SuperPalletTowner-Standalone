package starlight.trainer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge client registration: the key, the HUD layer and the client tick. */
final class NeoForgeClient {
    private NeoForgeClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener(NeoForgeClient::registerKeys);
        modBus.addListener(NeoForgeClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onLoggingOut);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(AffinityClient.OPEN);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, AffinityHud.LAYER_ID, AffinityHud::render);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AffinityClient.onLoggingOut();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        AffinityClient.onClientTick();
    }
}
