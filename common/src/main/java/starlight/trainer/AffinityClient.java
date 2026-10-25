package starlight.trainer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client entry points; each loader registers them only on the physical client. */
public final class AffinityClient {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.super_pallet_towner.open_affinity",
            // Default Y (since 1.2.0): free in vanilla, Cobblemon and NeoOrigins. Players can rebind it in Controls.
            InputConstants.KEY_Y,
            "key.categories.super_pallet_towner");

    private AffinityClient() {}

    public static void onLoggingOut() {
        AffinityHud.clear();
        ResonanceLinkEffects.setClient(java.util.Set.of());
    }

    /** At the end of every client tick. */
    public static void onClientTick() {
        while (OPEN.consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                TrainerPlatform.get().sendToServer(new AffinityPackets.Open());
            }
        }
    }

    public static void receiveSnapshot(AffinityPackets.Snapshot snapshot) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AffinityScreen screen) {
            screen.refresh(snapshot.json());
        } else {
            client.setScreen(new AffinityScreen(snapshot.json()));
        }
    }

    public static void receiveActiveTypes(AffinityPackets.ActiveTypes payload) {
        AffinityHud.receive(payload.types());
    }

    public static void receiveActiveLinks(AffinityPackets.ActiveLinks payload) {
        ResonanceLinkEffects.setClient(java.util.Set.copyOf(payload.recipes()));
    }
}
