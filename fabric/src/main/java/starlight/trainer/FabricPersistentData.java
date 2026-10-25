package starlight.trainer;

import net.minecraft.nbt.CompoundTag;

/** Implemented on every player by {@code PlayerMixin}: a compound saved with the player, like NeoForge's persistent data. */
public interface FabricPersistentData {
    /** The NBT key the compound is saved under in the player's data. */
    String SAVE_KEY = "SuperPalletTownerData";

    CompoundTag superPalletTowner$persistentData();
}
