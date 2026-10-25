package starlight.trainer;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers {@link AffinityPackets} with NeoForge's network. */
final class NeoForgePackets {
    private NeoForgePackets() {}

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AffinityPackets.PROTOCOL);
        registrar.playToServer(AffinityPackets.Open.TYPE, AffinityPackets.Open.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AffinityPackets.handleOpen(player);
        });
        registrar.playToServer(AffinityPackets.Change.TYPE, AffinityPackets.Change.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AffinityPackets.handleChange(player, payload);
        });
        // Lambdas, not method references: a method reference resolves AffinityClient (client-only
        // classes) at registration time and crashes dedicated servers.
        registrar.playToClient(AffinityPackets.Snapshot.TYPE, AffinityPackets.Snapshot.CODEC,
                (payload, context) -> AffinityClient.receiveSnapshot(payload));
        registrar.playToClient(AffinityPackets.ActiveTypes.TYPE, AffinityPackets.ActiveTypes.CODEC,
                (payload, context) -> AffinityClient.receiveActiveTypes(payload));
        registrar.playToClient(AffinityPackets.ActiveLinks.TYPE, AffinityPackets.ActiveLinks.CODEC,
                (payload, context) -> AffinityClient.receiveActiveLinks(payload));
    }
}
