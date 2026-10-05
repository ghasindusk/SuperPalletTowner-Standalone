package starlight.trainer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative Affinity slots and party-collapse transition. */
public final class TrainerState {
    private static final String DATA_KEY = "SuperPalletTowner";
    private static final String SLOTS_KEY = "AffinitySlots";
    private static final String LINK_KEY = "ResonanceLink";
    private static final Map<UUID, Boolean> HAD_CONSCIOUS_PARTY = new HashMap<>();
    private static final Map<UUID, Integer> RETURN_PROTECTION_UNTIL = new HashMap<>();
    private static final int RETURN_PROTECTION_TICKS = 100;

    private TrainerState() {}

    /** The mod's persistent compound, attached to the player so writes are saved. */
    static CompoundTag data(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA_KEY, 10)) root.put(DATA_KEY, new CompoundTag());
        return root.getCompound(DATA_KEY);
    }

    /** Vanilla invulnerableTime only blocks hits up to the last damage taken, so track real protection. */
    public static boolean isReturnProtected(ServerPlayer player) {
        Integer until = RETURN_PROTECTION_UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (player.server.getTickCount() < until) return true;
        RETURN_PROTECTION_UNTIL.remove(player.getUUID());
        return false;
    }

    public static String canonicalType(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ilight" -> "light";
            case "iair" -> "air";
            case "isound" -> "sound";
            default -> normalized;
        };
    }

    /** The Pokémon's canonical type names, for {@link AffinityBalance}. */
    public static Set<String> types(Pokemon pokemon) {
        Set<String> types = new HashSet<>(2);
        for (var type : pokemon.getTypes()) types.add(canonicalType(type.getName()));
        return types;
    }

    public static boolean isTrainer(ServerPlayer player) {
        var data = player.getData(OriginAttachments.originData());
        ResourceLocation primary = data.getOrigin(SuperPalletTowner.ORIGIN_LAYER);
        return TrainerOriginLogic.isTrainer(primary == null ? null : primary.toString());
    }

    public static List<String> selected(ServerPlayer player) {
        List<String> result = new ArrayList<>(3);
        ListTag list = player.getPersistentData().getCompound(DATA_KEY).getList(SLOTS_KEY, 8);
        for (int i = 0; i < list.size() && result.size() < 3; i++) {
            String type = canonicalType(list.getString(i));
            if (!type.isBlank() && !result.contains(type)) result.add(type);
        }
        return result;
    }

    public static boolean select(ServerPlayer player, String type, Set<String> eligible) {
        type = canonicalType(type);
        if (!eligible.contains(type)) return false;
        List<String> slots = selected(player);
        if (slots.contains(type)) return true;
        if (slots.size() >= 3) return false;
        slots.add(type);
        save(player, slots);
        return true;
    }

    public static void remove(ServerPlayer player, String type) {
        List<String> slots = selected(player);
        slots.remove(canonicalType(type));
        save(player, slots);
        saveLink(player, link(player));
    }

    public static boolean replace(ServerPlayer player, int slot, String type, Set<String> eligible) {
        type = canonicalType(type);
        if (!eligible.contains(type)) return false;
        List<String> slots = selected(player);
        if (slot < 0 || slot >= slots.size() || slots.contains(type)) return false;
        slots.set(slot, type);
        save(player, slots);
        saveLink(player, link(player));
        return true;
    }

    // ------------------------------------------------------------------ resonance link

    /** The linked types in link-slot order; only types still in a resonance slot count. */
    public static List<String> link(ServerPlayer player) {
        List<String> stored = new ArrayList<>(ResonanceLinks.MAX_LINK);
        ListTag list = player.getPersistentData().getCompound(DATA_KEY).getList(LINK_KEY, 8);
        for (int i = 0; i < list.size(); i++) stored.add(list.getString(i));
        return ResonanceLinks.sanitize(stored, selected(player));
    }

    /** Adds an active resonance type to the link. The server's active set decides, never the client. */
    public static boolean linkAdd(ServerPlayer player, String type, Set<String> active) {
        type = canonicalType(type);
        List<String> link = link(player);
        if (!ResonanceLinks.canAdd(link, type, selected(player), active)) return false;
        link.add(type);
        saveLink(player, link);
        return true;
    }

    public static void linkRemove(ServerPlayer player, String type) {
        List<String> link = link(player);
        if (link.remove(canonicalType(type))) saveLink(player, link);
    }

    public static void linkClear(ServerPlayer player) {
        saveLink(player, List.of());
    }

    /** Recipe ids in effect now for this player (trainer origin, link matches, every type active). */
    public static Set<String> activeLinks(ServerPlayer player, Set<String> active) {
        if (!isTrainer(player)) return Set.of();
        return ResonanceLinks.activeRecipes(link(player), active);
    }

    private static void saveLink(ServerPlayer player, List<String> link) {
        CompoundTag data = data(player);
        if (link.isEmpty()) {
            // Nothing is written for players who never use links.
            data.remove(LINK_KEY);
            return;
        }
        ListTag list = new ListTag();
        for (String type : link) list.add(StringTag.valueOf(type));
        data.put(LINK_KEY, list);
    }

    public static Set<String> active(ServerPlayer player, Set<String> eligible) {
        if (!isTrainer(player)) return Set.of();
        Set<String> active = new HashSet<>(selected(player));
        Set<String> normalizedEligible = new HashSet<>();
        for (String type : eligible) normalizedEligible.add(canonicalType(type));
        active.retainAll(normalizedEligible);
        return active;
    }

    public static Set<String> active(ServerPlayer player) {
        if (!isTrainer(player)) return Set.of();
        Set<String> eligible = new HashSet<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon == null || pokemon.isFainted()) continue;
            for (var type : pokemon.getTypes()) eligible.add(canonicalType(type.getName()));
        }
        return active(player, eligible);
    }

    private static void save(ServerPlayer player, List<String> slots) {
        CompoundTag root = player.getPersistentData();
        CompoundTag data = root.getCompound(DATA_KEY);
        ListTag list = new ListTag();
        for (String type : slots) list.add(StringTag.valueOf(type));
        data.put(SLOTS_KEY, list);
        root.put(DATA_KEY, data);
    }

    public static void update(ServerPlayer player, Set<String> eligible, int partyCount, int consciousCount) {
        if (partyCount == 0) {
            HAD_CONSCIOUS_PARTY.put(player.getUUID(), false);
            return;
        }
        boolean hadConscious = HAD_CONSCIOUS_PARTY.getOrDefault(player.getUUID(), false);
        if (consciousCount > 0) {
            HAD_CONSCIOUS_PARTY.put(player.getUUID(), true);
        } else if (hadConscious) {
            if (returnToSpawn(player)) HAD_CONSCIOUS_PARTY.put(player.getUUID(), false);
        }
    }

    /** Losing trainer eligibility ends the current conscious-party observation window. */
    public static void disarmCollapse(ServerPlayer player) {
        HAD_CONSCIOUS_PARTY.remove(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        HAD_CONSCIOUS_PARTY.remove(player.getUUID());
        RETURN_PROTECTION_UNTIL.remove(player.getUUID());
    }

    /** Player persistent data is not automatically carried to the new player entity on death. */
    public static void copyOnClone(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        CompoundTag oldData = oldPlayer.getPersistentData();
        if (oldData.contains(DATA_KEY, 10)) {
            newPlayer.getPersistentData().put(DATA_KEY, oldData.getCompound(DATA_KEY).copy());
        }
        HAD_CONSCIOUS_PARTY.remove(oldPlayer.getUUID());
    }

    private static boolean returnToSpawn(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(player.getRespawnDimension());
        if (level == null) level = player.server.overworld();
        BlockPos position = player.getRespawnPosition();
        if (position == null) position = level.getSharedSpawnPos();
        Vec3 destination = findSafeLanding(level, player, position);
        if (destination == null) {
            level = player.server.overworld();
            destination = findSafeLanding(level, player, level.getSharedSpawnPos());
        }
        if (destination == null) return false;
        player.teleportTo(level, destination.x, destination.y, destination.z,
                player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.invulnerableTime = Math.max(player.invulnerableTime, RETURN_PROTECTION_TICKS);
        RETURN_PROTECTION_UNTIL.put(player.getUUID(), player.server.getTickCount() + RETURN_PROTECTION_TICKS);
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null) pokemon.heal();
        }
        return true;
    }

    private static Vec3 findSafeLanding(ServerLevel level, ServerPlayer player, BlockPos center) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = 2; dy >= -2; dy--) {
                        BlockPos feet = center.offset(dx, dy, dz);
                        if (!level.getWorldBorder().isWithinBounds(feet)
                                || !level.getBlockState(feet.below()).isSolid()
                                || !level.getFluidState(feet).isEmpty()
                                || !level.getFluidState(feet.above()).isEmpty()) continue;
                        Vec3 point = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        if (level.noCollision(player, player.getBoundingBox().move(point.subtract(player.position())))) {
                            return point;
                        }
                    }
                }
            }
        }
        return null;
    }
}
