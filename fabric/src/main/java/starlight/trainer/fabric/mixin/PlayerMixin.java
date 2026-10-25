package starlight.trainer.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starlight.trainer.FabricPersistentData;
import starlight.trainer.PlayerAffinityEvents;
import starlight.trainer.ResonanceLinkEffects;
import starlight.trainer.SuperPalletTowner;
import starlight.trainer.TrainerProgression;

/** Player hooks NeoForge provides as events: tick end, saved data, experience, dig speed and harvest. */
@Mixin(Player.class)
public abstract class PlayerMixin implements FabricPersistentData {
    @Unique
    private CompoundTag superPalletTowner$data = new CompoundTag();

    @Override
    public CompoundTag superPalletTowner$persistentData() {
        return superPalletTowner$data;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void superPalletTowner$save(CompoundTag tag, CallbackInfo ci) {
        if (!superPalletTowner$data.isEmpty()) tag.put(SAVE_KEY, superPalletTowner$data.copy());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void superPalletTowner$load(CompoundTag tag, CallbackInfo ci) {
        superPalletTowner$data = tag.getCompound(SAVE_KEY).copy();
    }

    /** NeoForge's {@code PlayerTickEvent.Post}. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void superPalletTowner$tick(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) SuperPalletTowner.onPlayerTick(player);
    }

    /** NeoForge's {@code PlayerXpEvent.XpChange}. */
    @ModifyVariable(method = "giveExperiencePoints", at = @At("HEAD"), argsOnly = true)
    private int superPalletTowner$xp(int amount) {
        return TrainerProgression.playerXp((Player) (Object) this, amount);
    }

    /** NeoForge's {@code PlayerEvent.BreakSpeed}: ores for Rock, one tier up for Rock + Steel. */
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void superPalletTowner$digSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        float speed = PlayerAffinityEvents.breakSpeed(player, state, cir.getReturnValueF());
        speed = ResonanceLinkEffects.breakSpeed(player, state, speed);
        cir.setReturnValue(speed);
    }

    /** NeoForge's {@code PlayerEvent.HarvestCheck}. */
    @Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
    private void superPalletTowner$harvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ResonanceLinkEffects.canHarvest((Player) (Object) this, state, cir.getReturnValueZ()));
    }
}
