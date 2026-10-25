package starlight.trainer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * Fabric entry point. There is no NeoOrigins on Fabric, so every player is a Super Pallet Towner
 * (unless {@code allPlayersWithoutNeoOrigins = false} in the common config). Damage, knockback,
 * mining and similar hooks that Fabric API has no event for are mixins in
 * {@code starlight.trainer.fabric.mixin}.
 */
public final class SuperPalletTownerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TrainerPlatform.install(new FabricPlatform());
        FabricConfig.loadCommon();
        Registry.register(BuiltInRegistries.ITEM, SuperPalletTowner.TRAINER_EMBLEM, new Item(new Item.Properties().stacksTo(1)));
        registerPackets();
        TrainerProgression.setup();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> FabricConfig.loadCommon());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> !PveAffinityDamage.cancelsIncoming(entity, source));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
                PlayerAffinityEvents.afterDamage(entity, source, damageTaken));
        ServerPlayerEvents.COPY_FROM.register(
                (oldPlayer, newPlayer, alive) -> SuperPalletTowner.onPlayerClone(oldPlayer, newPlayer));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> SuperPalletTowner.onPlayerLogout(handler.player));
    }

    private static void registerPackets() {
        PayloadTypeRegistry.playC2S().register(AffinityPackets.Open.TYPE, AffinityPackets.Open.CODEC);
        PayloadTypeRegistry.playC2S().register(AffinityPackets.Change.TYPE, AffinityPackets.Change.CODEC);
        PayloadTypeRegistry.playS2C().register(AffinityPackets.Snapshot.TYPE, AffinityPackets.Snapshot.CODEC);
        PayloadTypeRegistry.playS2C().register(AffinityPackets.ActiveTypes.TYPE, AffinityPackets.ActiveTypes.CODEC);
        PayloadTypeRegistry.playS2C().register(AffinityPackets.ActiveLinks.TYPE, AffinityPackets.ActiveLinks.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AffinityPackets.Open.TYPE,
                (payload, context) -> AffinityPackets.handleOpen(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(AffinityPackets.Change.TYPE,
                (payload, context) -> AffinityPackets.handleChange(context.player(), payload));
    }
}
