package starlight.trainer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import starlight.trainer.fabric.mixin.MobEffectInstanceAccessor;

/** Fabric side of {@link TrainerPlatform}. NeoOrigins does not exist here, so the origin hooks keep their defaults. */
final class FabricPlatform implements TrainerPlatform {
    @Override
    public CompoundTag persistentData(ServerPlayer player) {
        return ((FabricPersistentData) player).superPalletTowner$persistentData();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    /** None: Fabric has no swim-speed attribute, so {@link FabricSwim} scales swimming in the travel mixin. */
    @Override
    public Holder<Attribute> swimSpeed() {
        return null;
    }

    @Override
    public void setEffectDuration(MobEffectInstance effect, int ticks) {
        ((MobEffectInstanceAccessor) effect).superPalletTowner$setDuration(ticks);
    }

    /** Vanilla's own checks (spawn protection, world border, adventure mode). */
    @Override
    public boolean mayPlaceBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.mayBuild() && level.mayInteract(player, pos);
    }
}
