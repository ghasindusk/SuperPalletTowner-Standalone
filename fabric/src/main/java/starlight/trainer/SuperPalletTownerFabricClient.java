package starlight.trainer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/** Fabric client entry point: the key, the HUD, the client tick and the server-to-client packets. */
public final class SuperPalletTownerFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricConfig.loadClient();
        KeyBindingHelper.registerKeyBinding(AffinityClient.OPEN);
        // NeoForge draws the HUD as a layer above the hotbar; Fabric API draws callbacks after the vanilla HUD.
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            if (!Minecraft.getInstance().options.hideGui) AffinityHud.render(graphics, deltaTracker);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> AffinityClient.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AffinityClient.onLoggingOut();
            FabricSwim.setLocalWater(false);
        });
        ClientPlayNetworking.registerGlobalReceiver(AffinityPackets.Snapshot.TYPE,
                (payload, context) -> AffinityClient.receiveSnapshot(payload));
        ClientPlayNetworking.registerGlobalReceiver(AffinityPackets.ActiveTypes.TYPE, (payload, context) -> {
            AffinityClient.receiveActiveTypes(payload);
            FabricSwim.setLocalWater(payload.types().contains("water"));
        });
        ClientPlayNetworking.registerGlobalReceiver(AffinityPackets.ActiveLinks.TYPE,
                (payload, context) -> AffinityClient.receiveActiveLinks(payload));
    }
}
