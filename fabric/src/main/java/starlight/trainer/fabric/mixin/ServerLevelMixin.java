package starlight.trainer.fabric.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight.trainer.ResonanceWorldEffects;

/** NeoForge's {@code VanillaGameEvent}: an Abyss link silences the player's vibrations. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void superPalletTowner$vibration(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context,
                                             CallbackInfo ci) {
        if (ResonanceWorldEffects.cancelsVibration(context.sourceEntity(), gameEvent)) ci.cancel();
    }
}
