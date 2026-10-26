package starlight.trainer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.Collection;
import java.util.List;

/**
 * The few things the shared code needs from the mod loader. NeoForge and Fabric each install one
 * implementation from their entry point before anything else runs.
 */
public interface TrainerPlatform {
    /** Whether NeoOrigins is installed (never on Fabric). */
    default boolean originsLoaded() { return false; }

    /** The player's NeoOrigins Origin ids on every layer; only called when {@link #originsLoaded()}. */
    default Collection<String> origins(ServerPlayer player) { return List.of(); }

    /** Every player tick, after the resonance effects (NeoOrigins: Psychic cooldown reduction). */
    default void originsTick(ServerPlayer player) {}

    /** Every 20 ticks (NeoOrigins: moves Monster Tamer players to Super Pallet Towner). */
    default void originsMigrate(ServerPlayer player) {}

    /** The player left or was replaced by a respawned copy. */
    default void originsForget(ServerPlayer player) {}

    /** A compound saved with the player (NeoForge's persistent data; a saved field on Fabric). */
    CompoundTag persistentData(ServerPlayer player);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);

    /** The swim-speed attribute, or {@code null} when the loader has none (Fabric scales the swim stroke itself). */
    Holder<Attribute> swimSpeed();

    /** Writes an effect's remaining duration (vanilla has no setter). */
    void setEffectDuration(MobEffectInstance effect, int ticks);

    /** Lets claim and protection mods veto a block the mod places for the player. */
    boolean mayPlaceBlock(ServerPlayer player, ServerLevel level, BlockPos pos);

    static TrainerPlatform get() {
        TrainerPlatform platform = Installed.instance;
        if (platform == null) throw new IllegalStateException("Super Pallet Towner platform not installed");
        return platform;
    }

    static void install(TrainerPlatform platform) {
        Installed.instance = platform;
    }

    final class Installed {
        private static volatile TrainerPlatform instance;

        private Installed() {}
    }
}
