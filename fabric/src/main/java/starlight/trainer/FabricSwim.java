package starlight.trainer;

import net.minecraft.world.entity.LivingEntity;

/**
 * Water's faster swimming on Fabric. NeoForge raises its synced swim-speed attribute; Fabric has
 * none, so the local client (which moves its own player) scales the swim stroke while the server
 * reports Water as active.
 */
public final class FabricSwim {
    private static volatile boolean localWater;

    private FabricSwim() {}

    static void setLocalWater(boolean active) {
        localWater = active;
    }

    /** The swim stroke for one tick in water. */
    public static float stroke(LivingEntity entity, float speed) {
        if (!localWater || !entity.level().isClientSide() || !entity.isControlledByLocalInstance()
                || !(entity instanceof net.minecraft.world.entity.player.Player)) return speed;
        return speed * (float) (1 + AffinityBalance.WATER_SWIM_SPEED);
    }
}
