package starlight.trainer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Refunds a capped fraction of durability consumed by held equipment over repeated uses. */
public final class EquipmentDurabilityEffects {
    private static final class State {
        ItemStack stack;
        int damage;
        double credit;
    }

    private static final Map<UUID, State[]> STATES = new HashMap<>();

    private EquipmentDurabilityEffects() {}

    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!TrainerState.active(player).contains("steel")) {
            STATES.remove(id);
            return;
        }
        State[] hands = STATES.computeIfAbsent(id, ignored -> new State[] { new State(), new State() });
        String link = ResonanceLinkEffects.current(player);
        double saving = StandardSynergyHookBalance.savingFor(link, StandardResonanceBalance.synergyId(link));
        update(hands[0], player.getMainHandItem(), saving);
        update(hands[1], player.getOffhandItem(), saving);
    }

    /** Shared with the armor durability hook so held items and armor use the same total. */
    static double savingFor(String recipe, String synergy) {
        return StandardSynergyHookBalance.savingFor(recipe, synergy);
    }

    private static void update(State state, ItemStack stack, double saving) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            state.stack = null;
            state.credit = 0;
            return;
        }
        int damage = stack.getDamageValue();
        if (state.stack != stack || damage < state.damage) {
            state.stack = stack;
            state.damage = damage;
            state.credit = 0;
            return;
        }
        int consumed = damage - state.damage;
        if (consumed > 0) {
            state.credit += consumed * saving;
            int refund = (int) Math.floor(state.credit + 1e-9);
            if (refund > 0) {
                stack.setDamageValue(Math.max(0, damage - refund));
                state.credit -= refund;
            }
        }
        state.damage = stack.getDamageValue();
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }
}
