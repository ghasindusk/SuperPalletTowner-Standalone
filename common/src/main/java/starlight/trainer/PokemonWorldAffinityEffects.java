package starlight.trainer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;

/** Movement support for party Pokémon released into the Minecraft world. */
public final class PokemonWorldAffinityEffects {
    private static final ResourceLocation FLYING_MOVEMENT = ResourceLocation.fromNamespaceAndPath(
            SuperPalletTowner.MOD_ID, "flying_pokemon_movement");
    private static final ResourceLocation LINK_KB_RESISTANCE = ResourceLocation.fromNamespaceAndPath(
            SuperPalletTowner.MOD_ID, "link_pokemon_knockback_resistance");

    private PokemonWorldAffinityEffects() {}

    public static void update(ServerPlayer player, Set<String> active) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon == null || pokemon.getEntity() == null) continue;
            var entity = pokemon.getEntity();
            var movement = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            var types = TrainerState.types(pokemon);
            double bonus = 0;
            double kbResistance = 0;
            if (!pokemon.isFainted() && entity.getBattleId() == null) {
                var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(player, active), types,
                        player.level().isNight(), false, false);
                bonus = Math.min(.25, AffinityBalance.pokemonWorldMovement(active, types) + link.movement());
                kbResistance = Math.min(.40, link.knockbackResistance());
            }
            set(movement, FLYING_MOVEMENT, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            set(entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE), LINK_KB_RESISTANCE, kbResistance,
                    AttributeModifier.Operation.ADD_VALUE);
        }
    }

    private static void set(net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
                            ResourceLocation id, double value, AttributeModifier.Operation operation) {
        if (attribute == null) return;
        var existing = attribute.getModifier(id);
        if (existing != null && (value == 0 || existing.amount() != value || existing.operation() != operation)) {
            attribute.removeModifier(id);
            existing = null;
        }
        if (value > 0 && existing == null) {
            attribute.addTransientModifier(new AttributeModifier(id, value, operation));
        }
    }
}
