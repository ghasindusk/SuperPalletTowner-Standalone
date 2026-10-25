package starlight.trainer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Loader-neutral player lifecycle. The NeoForge and Fabric entry points register the item, configs,
 * packets and hooks, then call these methods from their own events.
 */
public final class SuperPalletTowner {
    public static final String MOD_ID = "super_pallet_towner";
    static final ResourceLocation ORIGIN_LAYER = ResourceLocation.fromNamespaceAndPath("neoorigins", "origin");
    static final ResourceLocation TRAINER_ORIGIN = ResourceLocation.fromNamespaceAndPath("starlight", "super_pallet_towner");
    /** Icon of the Origin in the selection screen; also obtainable with /give. */
    public static final ResourceLocation TRAINER_EMBLEM = ResourceLocation.fromNamespaceAndPath(MOD_ID, "trainer_emblem");

    private SuperPalletTowner() {}

    public static void onPlayerClone(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        TrainerState.copyOnClone(oldPlayer, newPlayer);
        NaturalAffinityEffects.forget(oldPlayer);
        PokemonNaturalRecovery.forget(oldPlayer);
        PlayerAffinityEvents.forget(oldPlayer);
        EquipmentDurabilityEffects.forget(oldPlayer);
        TrainerPlatform.get().originsForget(oldPlayer);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        TrainerState.forget(player);
        NaturalAffinityEffects.forget(player);
        PokemonNaturalRecovery.forget(player);
        PlayerAffinityEvents.forget(player);
        EquipmentDurabilityEffects.forget(player);
        TrainerPlatform.get().originsForget(player);
        TrainerProgression.forget(player);
        AffinityPackets.forget(player);
    }

    /** At the end of every server player tick. */
    public static void onPlayerTick(ServerPlayer player) {
        if (!TrainerState.isTrainer(player)) TrainerState.disarmCollapse(player);
        NaturalAffinityEffects.tick(player);
        PokemonNaturalRecovery.tick(player);
        EquipmentDurabilityEffects.tick(player);
        TrainerPlatform.get().originsTick(player);
        PlayerAffinityEffects.tick(player);
        ResonanceLinkEffects.tick(player);
        ResonanceWorldEffects.tick(player);
        PokemonWorldAffinityEffects.update(player, TrainerState.active(player));
        if (player.tickCount % 20 != 0) return;
        TrainerPlatform.get().originsMigrate(player);
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
}
