package starlight.trainer;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Reduces newly started NeoOrigins cooldowns for Psychic Affinity and its selected synergy. */
public final class OriginCooldownAffinity {
    private static final Field COOLDOWNS;
    private static final Map<UUID, Map<String, Integer>> LAST = new HashMap<>();

    static {
        Field field = null;
        try {
            field = PlayerOriginData.class.getDeclaredField("activeCooldowns");
            field.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
            // NeoOrigins changed its private storage; leave cooldowns untouched.
        }
        COOLDOWNS = field;
    }

    private OriginCooldownAffinity() {}

    @SuppressWarnings("unchecked")
    public static void tick(ServerPlayer player) {
        if (COOLDOWNS == null) return;
        try {
            PlayerOriginData data = player.getData(OriginAttachments.originData());
            Object raw = COOLDOWNS.get(data);
            if (!(raw instanceof Map<?, ?>)) return;
            Map<String, Integer> current = (Map<String, Integer>) raw;
            UUID id = player.getUUID();
            Map<String, Integer> previous = LAST.get(id);
            boolean psychic = TrainerState.active(player).contains("psychic");
            String link = ResonanceLinkEffects.current(player);
            double factor = StandardSynergyHookBalance.cooldownFactor(link, StandardResonanceBalance.synergyId(link));
            int now = player.tickCount;
            boolean changed = false;
            if (psychic && previous != null) {
                for (var entry : current.entrySet()) {
                    int expiry = entry.getValue();
                    Integer prior = previous.get(entry.getKey());
                    if (expiry <= now || (prior != null && expiry <= prior)) continue;
                    int remaining = expiry - now;
                    int shortened = (int) Math.ceil(remaining * factor);
                    if (shortened < remaining) {
                        entry.setValue(now + shortened);
                        changed = true;
                    }
                }
            }
            LAST.put(id, new HashMap<>(current));
            if (changed) NeoOriginsNetwork.syncToPlayer(player);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            LAST.remove(player.getUUID());
        }
    }

    public static void forget(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }
}
