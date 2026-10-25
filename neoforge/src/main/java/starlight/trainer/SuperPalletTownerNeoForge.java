package starlight.trainer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge entry point: registers everything and forwards NeoForge events to the shared code. */
@Mod(SuperPalletTowner.MOD_ID)
public final class SuperPalletTownerNeoForge {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SuperPalletTowner.MOD_ID);
    /** Icon of the Origin in the selection screen; also obtainable with /give. */
    public static final DeferredItem<Item> TRAINER_EMBLEM = ITEMS.registerSimpleItem(
            SuperPalletTowner.TRAINER_EMBLEM.getPath(), new Item.Properties().stacksTo(1));

    public SuperPalletTownerNeoForge(IEventBus modBus, ModContainer container) {
        TrainerPlatform.install(new NeoForgePlatform());
        ITEMS.register(modBus);
        NeoForgeConfig.registerCommon(container, modBus);
        modBus.addListener(NeoForgePackets::register);
        modBus.addListener(FMLCommonSetupEvent.class, event -> TrainerProgression.setup());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Client-only HUD size/position (config/super_pallet_towner-client.toml).
            NeoForgeConfig.registerClient(container, modBus);
            NeoForgeClient.init(modBus);
            if (OriginsCompat.loaded()) NeoOriginsClientBridge.init();
        }
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForgeEvents.register(NeoForge.EVENT_BUS);
    }

    private void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer oldPlayer
                && event.getEntity() instanceof ServerPlayer newPlayer) {
            SuperPalletTowner.onPlayerClone(oldPlayer, newPlayer);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SuperPalletTowner.onPlayerLogout(player);
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) SuperPalletTowner.onPlayerTick(player);
    }
}
