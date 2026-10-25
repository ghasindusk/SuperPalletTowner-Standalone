package starlight.trainer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;

/** NeoForge side of {@link TrainerPlatform}. NeoOrigins is reached only behind {@link OriginsCompat#loaded()}. */
final class NeoForgePlatform implements TrainerPlatform {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** The runtime uses Mojang names, as in dev. */
    private static final Field DURATION = durationField();

    @Override
    public boolean originsLoaded() {
        return OriginsCompat.loaded();
    }

    @Override
    public String primaryOrigin(ServerPlayer player) {
        return NeoOriginsBridge.primaryOrigin(player);
    }

    @Override
    public void originsTick(ServerPlayer player) {
        if (OriginsCompat.loaded()) OriginCooldownAffinity.tick(player);
    }

    @Override
    public void originsMigrate(ServerPlayer player) {
        if (OriginsCompat.loaded()) NeoOriginsBridge.migrateLegacyOrigin(player);
    }

    @Override
    public void originsForget(ServerPlayer player) {
        if (OriginsCompat.loaded()) OriginCooldownAffinity.forget(player);
    }

    @Override
    public CompoundTag persistentData(ServerPlayer player) {
        return player.getPersistentData();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public Holder<Attribute> swimSpeed() {
        return NeoForgeMod.SWIM_SPEED;
    }

    @Override
    public void setEffectDuration(MobEffectInstance effect, int ticks) {
        if (DURATION == null) return;
        try {
            DURATION.setInt(effect, ticks);
        } catch (IllegalAccessException e) {
            LOGGER.error("Cannot shorten effect duration", e);
        }
    }

    private static Field durationField() {
        try {
            Field field = MobEffectInstance.class.getDeclaredField("duration");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("MobEffectInstance.duration not found; harmful-duration reductions are off", e);
            return null;
        }
    }

    @Override
    public boolean mayPlaceBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockSnapshot old = BlockSnapshot.create(level.dimension(), level, pos);
        return !EventHooks.onBlockPlace(player, old, Direction.UP);
    }
}
