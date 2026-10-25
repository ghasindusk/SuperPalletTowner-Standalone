package starlight.trainer;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** The shared {@code c:} convention tags (the same ids as NeoForge's {@code Tags} and Fabric's conventional tags). */
public final class TrainerTags {
    public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, c("ores"));
    public static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registries.ITEM, c("raw_materials"));
    public static final TagKey<Item> CROPS = TagKey.create(Registries.ITEM, c("crops"));

    private TrainerTags() {}

    private static ResourceLocation c(String path) {
        return ResourceLocation.fromNamespaceAndPath("c", path);
    }
}
