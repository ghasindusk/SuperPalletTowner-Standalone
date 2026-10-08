package starlight.trainer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.data.OriginDataManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.Set;

@Mod(SuperPalletTowner.MOD_ID)
public final class SuperPalletTowner {
    public static final String MOD_ID = "super_pallet_towner";
    static final ResourceLocation ORIGIN_LAYER = ResourceLocation.fromNamespaceAndPath("neoorigins", "origin");
    static final ResourceLocation TRAINER_ORIGIN = ResourceLocation.fromNamespaceAndPath("starlight", "super_pallet_towner");
    private static final ResourceLocation LEGACY_ORIGIN = ResourceLocation.fromNamespaceAndPath("neoorigins", "monster_tamer");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    /** Icon of the Origin in the selection screen; also obtainable with /give. */
    public static final DeferredItem<Item> TRAINER_EMBLEM = ITEMS.registerSimpleItem("trainer_emblem", new Item.Properties().stacksTo(1));

    public SuperPalletTowner(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener(AffinityPackets::register);
        modBus.addListener(TrainerProgression::setup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Client-only HUD size/position (config/super_pallet_towner-client.toml).
            HudSettings.register(container, modBus);
            AffinityClient.init(modBus);
        }
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(PveAffinityDamage::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(PveAffinityDamage::onDamage);
        NeoForge.EVENT_BUS.addListener(TrainerProgression::onPlayerXp);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onMeleeDamage);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onHeal);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onKnockBack);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onTargetChange);
        NeoForge.EVENT_BUS.addListener(PlayerAffinityEvents::onArmorHurt);
        NeoForge.EVENT_BUS.addListener(ResonanceLinkEffects::onHarvestCheck);
        NeoForge.EVENT_BUS.addListener(ResonanceLinkEffects::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(ResonanceWorldEffects::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(ResonanceWorldEffects::onVibration);
    }

    private void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer oldPlayer
                && event.getEntity() instanceof ServerPlayer newPlayer) {
            TrainerState.copyOnClone(oldPlayer, newPlayer);
            NaturalAffinityEffects.forget(oldPlayer);
            PokemonNaturalRecovery.forget(oldPlayer);
            PlayerAffinityEvents.forget(oldPlayer);
            EquipmentDurabilityEffects.forget(oldPlayer);
            OriginCooldownAffinity.forget(oldPlayer);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TrainerState.forget(player);
            NaturalAffinityEffects.forget(player);
            PokemonNaturalRecovery.forget(player);
            PlayerAffinityEvents.forget(player);
            EquipmentDurabilityEffects.forget(player);
            OriginCooldownAffinity.forget(player);
            TrainerProgression.forget(player);
            AffinityPackets.forget(player);
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TrainerState.isTrainer(player)) TrainerState.disarmCollapse(player);
        NaturalAffinityEffects.tick(player);
        PokemonNaturalRecovery.tick(player);
        EquipmentDurabilityEffects.tick(player);
        OriginCooldownAffinity.tick(player);
        PlayerAffinityEffects.tick(player);
        ResonanceLinkEffects.tick(player);
        ResonanceWorldEffects.tick(player);
        PokemonWorldAffinityEffects.update(player, TrainerState.active(player));
        if (player.tickCount % 20 != 0) return;
        migrateLegacyOrigin(player);
        if (!TrainerState.isTrainer(player)) {
            AffinityPackets.syncLinks(player, Set.of());
            PlayerAffinityEffects.update(player, Set.of());
            PokemonWorldAffinityEffects.update(player, Set.of());
            AffinityPackets.syncActive(player, Set.of());
            ResonanceLinkEffects.updatePlayer(player, Set.of());
            return;
        }

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Set<String> eligible = new HashSet<>();
        int partyCount = 0;
        int consciousCount = 0;
        for (Pokemon pokemon : party) {
            if (pokemon == null) continue;
            partyCount++;
            if (pokemon.isFainted()) continue;
            consciousCount++;
            for (var type : pokemon.getTypes()) eligible.add(type.getName());
        }
        Set<String> active = TrainerState.active(player, eligible);
        AffinityPackets.syncLinks(player, TrainerState.activeLinks(player, active));
        PlayerAffinityEffects.update(player, active);
        PokemonWorldAffinityEffects.update(player, active);
        // The HUD follows the same 20-tick evaluation that switches the effects on and off.
        AffinityPackets.syncActive(player, active);
        ResonanceLinkEffects.updatePlayer(player, active);
        TrainerState.update(player, eligible, partyCount, consciousCount);
    }

    /** Replace only the Origin layer while keeping Class, evolution and other attached data. */
    private static void migrateLegacyOrigin(ServerPlayer player) {
        var data = player.getData(OriginAttachments.originData());
        if (!LEGACY_ORIGIN.equals(data.getOrigin(ORIGIN_LAYER))) return;
        if (OriginDataManager.INSTANCE.getOrigin(TRAINER_ORIGIN) == null) return;
        data.setOrigin(ORIGIN_LAYER, TRAINER_ORIGIN);
        ActiveOriginService.applyOriginPowers(player, ORIGIN_LAYER, LEGACY_ORIGIN, TRAINER_ORIGIN);
        ActiveOriginService.invalidate(player.getUUID());
        NeoOriginsNetwork.syncToPlayer(player);
    }

}
