package starlight.trainer;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Server-side NeoOrigins calls. Only used when {@link OriginsCompat#loaded()} is true. */
final class NeoOriginsBridge {
    private static final ResourceLocation LEGACY_ORIGIN = ResourceLocation.fromNamespaceAndPath("neoorigins", "monster_tamer");

    private NeoOriginsBridge() {}

    /** The player's Origins on every layer (a modpack may offer Super Pallet Towner on a sub-origin layer). */
    static List<String> origins(ServerPlayer player) {
        return player.getData(OriginAttachments.originData()).getOrigins().values().stream()
                .filter(java.util.Objects::nonNull).map(ResourceLocation::toString).toList();
    }

    /** Replace only the Origin layer while keeping Class, evolution and other attached data. */
    static void migrateLegacyOrigin(ServerPlayer player) {
        var data = player.getData(OriginAttachments.originData());
        if (!LEGACY_ORIGIN.equals(data.getOrigin(SuperPalletTowner.ORIGIN_LAYER))) return;
        if (OriginDataManager.INSTANCE.getOrigin(SuperPalletTowner.TRAINER_ORIGIN) == null) return;
        data.setOrigin(SuperPalletTowner.ORIGIN_LAYER, SuperPalletTowner.TRAINER_ORIGIN);
        ActiveOriginService.applyOriginPowers(player, SuperPalletTowner.ORIGIN_LAYER, LEGACY_ORIGIN, SuperPalletTowner.TRAINER_ORIGIN);
        ActiveOriginService.invalidate(player.getUUID());
        NeoOriginsNetwork.syncToPlayer(player);
    }
}
