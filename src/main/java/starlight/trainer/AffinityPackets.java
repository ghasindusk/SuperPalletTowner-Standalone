package starlight.trainer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AffinityPackets {
    private AffinityPackets() {}

    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, "open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(new Open());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Change(String action, String typeName) implements CustomPacketPayload {
        public static final Type<Change> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, "change"));
        public static final StreamCodec<ByteBuf, Change> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Change::action,
                ByteBufCodecs.STRING_UTF8, Change::typeName,
                Change::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, "snapshot"));
        public static final StreamCodec<ByteBuf, Snapshot> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Snapshot::json, Snapshot::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** The player's active Affinity types in slot order, for the HUD. Sent only when it changes. */
    public record ActiveTypes(List<String> types) implements CustomPacketPayload {
        public static final Type<ActiveTypes> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, "active_types"));
        public static final StreamCodec<ByteBuf, ActiveTypes> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(AffinityView.MAX_SLOTS)), ActiveTypes::types,
                ActiveTypes::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Resonance-link recipe ids active now, for the client's copy of the mining calculation. */
    public record ActiveLinks(List<String> recipes) implements CustomPacketPayload {
        public static final Type<ActiveLinks> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, "active_links"));
        public static final StreamCodec<ByteBuf, ActiveLinks> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)), ActiveLinks::recipes,
                ActiveLinks::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<UUID, List<String>> LAST_HUD = new HashMap<>();
    private static final Map<UUID, List<String>> LAST_LINKS = new HashMap<>();

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToServer(Open.TYPE, Open.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) sendSnapshot(player);
        });
        registrar.playToServer(Change.TYPE, Change.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Set<String> eligible = eligible(player);
            if (payload.action().equals("select")) {
                TrainerState.select(player, payload.typeName(), eligible);
            } else if (payload.action().equals("remove")) {
                TrainerState.remove(player, payload.typeName());
            } else if (payload.action().startsWith("replace:")) {
                try {
                    int slot = Integer.parseInt(payload.action().substring(8));
                    TrainerState.replace(player, slot, payload.typeName(), eligible);
                } catch (NumberFormatException ignored) { }
            } else if (payload.action().equals("link_add")) {
                TrainerState.linkAdd(player, payload.typeName(), TrainerState.active(player, eligible));
            } else if (payload.action().equals("link_remove")) {
                TrainerState.linkRemove(player, payload.typeName());
            } else if (payload.action().equals("link_clear")) {
                TrainerState.linkClear(player);
            }
            sendSnapshot(player);
            Set<String> active = TrainerState.active(player);
            syncActive(player, active);
            // Switch link effects at once instead of at the next 20-tick evaluation.
            syncLinks(player, TrainerState.activeLinks(player, active));
            ResonanceLinkEffects.updatePlayer(player, active);
        });
        // Lambdas, not method references: a method reference resolves AffinityClient (client-only
        // classes) at registration time and crashes dedicated servers.
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (payload, context) -> AffinityClient.receiveSnapshot(payload, context));
        registrar.playToClient(ActiveTypes.TYPE, ActiveTypes.CODEC, (payload, context) -> AffinityClient.receiveActiveTypes(payload, context));
        registrar.playToClient(ActiveLinks.TYPE, ActiveLinks.CODEC, (payload, context) -> AffinityClient.receiveActiveLinks(payload, context));
    }

    private static Set<String> eligible(ServerPlayer player) {
        Set<String> result = new LinkedHashSet<>();
        if (!TrainerState.isTrainer(player)) return result;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon == null || pokemon.isFainted()) continue;
            for (var type : pokemon.getTypes()) result.add(TrainerState.canonicalType(type.getName()));
        }
        return result;
    }

    public static void sendSnapshot(ServerPlayer player) {
        JsonObject snapshot = new JsonObject();
        Set<String> available = eligible(player);
        List<String> selected = TrainerState.selected(player);
        JsonArray availableJson = new JsonArray();
        available.stream().sorted().forEach(availableJson::add);
        JsonArray selectedJson = new JsonArray();
        selected.forEach(selectedJson::add);
        snapshot.add("available", availableJson);
        snapshot.add("selected", selectedJson);
        JsonArray activeJson = new JsonArray();
        Set<String> active = TrainerState.active(player, available);
        active.stream().sorted().forEach(activeJson::add);
        snapshot.add("active", activeJson);
        JsonArray linkJson = new JsonArray();
        TrainerState.link(player).forEach(linkJson::add);
        snapshot.add("link", linkJson);
        // The server's own verdict on which link effects apply (the screen shows this, not a guess).
        JsonArray linksJson = new JsonArray();
        TrainerState.activeLinks(player, active).stream().sorted().forEach(linksJson::add);
        snapshot.add("links", linksJson);
        // Conscious party Pokémon, for the screen's per-Pokémon return-buff totals (display only).
        JsonArray partyJson = new JsonArray();
        if (TrainerState.isTrainer(player)) {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (pokemon == null || pokemon.isFainted()) continue;
                JsonObject member = new JsonObject();
                member.addProperty("species", pokemon.getSpecies().getTranslationKey());
                if (pokemon.getNickname() != null) member.addProperty("nickname", pokemon.getNickname().getString());
                JsonArray types = new JsonArray();
                for (var type : pokemon.getTypes()) types.add(TrainerState.canonicalType(type.getName()));
                member.add("types", types);
                partyJson.add(member);
            }
        }
        snapshot.add("party", partyJson);
        PacketDistributor.sendToPlayer(player, new Snapshot(snapshot.toString()));
    }

    /** Tells the client which Affinity types are active now; the server stays the only judge. */
    public static void syncActive(ServerPlayer player, Set<String> active) {
        List<String> types = AffinityView.hudTypes(TrainerState.selected(player), active);
        if (types.equals(LAST_HUD.get(player.getUUID()))) return;
        LAST_HUD.put(player.getUUID(), types);
        PacketDistributor.sendToPlayer(player, new ActiveTypes(types));
    }

    /**
     * Applies the active link recipes on the server and tells the owning client (sent only on a
     * change). The client needs them because it computes its own block-breaking progress.
     */
    public static void syncLinks(ServerPlayer player, Set<String> links) {
        ResonanceLinkEffects.setServer(player, links);
        List<String> sorted = links.stream().sorted().toList();
        if (sorted.equals(LAST_LINKS.get(player.getUUID()))) return;
        LAST_LINKS.put(player.getUUID(), sorted);
        PacketDistributor.sendToPlayer(player, new ActiveLinks(sorted));
    }

    public static void forget(ServerPlayer player) {
        LAST_HUD.remove(player.getUUID());
        LAST_LINKS.remove(player.getUUID());
        ResonanceLinkEffects.forget(player);
    }
}
