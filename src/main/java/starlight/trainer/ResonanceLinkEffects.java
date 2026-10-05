package starlight.trainer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Effects of resonance links. The server decides which links are active (every 20 ticks with the
 * other resonance effects, and right after a change) and tells the owning client, because block
 * breaking progress is computed on both sides and must agree; drops are decided on the server only.
 *
 * <p>Rock + Steel: the main-hand tool counts as one vanilla tier higher, both for harvesting
 * (NeoForge {@code PlayerEvent.HarvestCheck}, which gates drops in
 * {@code ServerPlayerGameMode.destroyBlock} and the ×30/×100 divisor in
 * {@code BlockBehaviour.getDestroyProgress}) and for speed ({@code PlayerEvent.BreakSpeed}).
 * The item itself is not changed: no component, NBT or tier is rewritten.
 */
public final class ResonanceLinkEffects {
    private static final ResourceLocation MOVE = id("link_movement");
    private static final ResourceLocation ATTACK = id("link_attack_speed");
    private static final ResourceLocation ARMOR = id("link_armor");
    private static final ResourceLocation TOUGHNESS = id("link_toughness");
    private static final ResourceLocation KB = id("link_knockback_resistance");
    private static final ResourceLocation JUMP = id("link_jump");
    private static final ResourceLocation MINING = id("link_mining");
    private static final ResourceLocation SUBMERGED = id("link_submerged_mining");
    private static final String FLY_BASE = "LinkFlyBase";
    private static final String FLY_APPLIED = "LinkFlyApplied";
    private static final Map<UUID, Set<String>> SERVER = new HashMap<>();
    /** The local player's active links on a client, from {@link AffinityPackets.ActiveLinks}. */
    private static volatile Set<String> client = Set.of();

    private ResonanceLinkEffects() {}

    private static ResourceLocation id(String suffix) {
        return ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, suffix);
    }

    public static void setServer(ServerPlayer player, Set<String> links) {
        if (links.isEmpty()) SERVER.remove(player.getUUID());
        else SERVER.put(player.getUUID(), Set.copyOf(links));
    }

    public static void setClient(Set<String> links) {
        client = Set.copyOf(links);
    }

    public static void forget(ServerPlayer player) {
        updateFlightSpeed(player, "");
        SERVER.remove(player.getUUID());
    }

    public static Set<String> active(Player player) {
        if (player instanceof ServerPlayer) return SERVER.getOrDefault(player.getUUID(), Set.of());
        // Only the local player's own breaking is computed on a client.
        return player.level().isClientSide() && player.isLocalPlayer() ? client : Set.of();
    }

    public static String current(Player player) {
        Set<String> links = active(player);
        return links.size() == 1 ? links.iterator().next() : "";
    }

    private static boolean toolTier(Player player) {
        String id = current(player);
        return id.equals(ResonanceLinks.ROCK_STEEL) || id.equals("fire_rock_steel");
    }

    /** Called after single-type attributes on each server evaluation. Values shared with those
     * types are treated as a target total; the tier-linked mining increment is additional. */
    public static void updatePlayer(ServerPlayer player, Set<String> types) {
        String recipe = current(player);
        var standard = StandardResonanceBalance.forId(recipe).player();
        double movement = switch (recipe) {
            case "air_electric", "air_electric_flying" -> Math.max(0, .15 - AffinityBalance.playerMovement(types, player.level().isNight()));
            default -> 0;
        };
        movement += Math.min(standard.movement(), Math.max(0, .25 - AffinityBalance.playerMovement(types, player.level().isNight())));
        double attack = switch (recipe) {
            case "electric_sound" -> Math.max(0, .12 - AffinityBalance.playerAttackSpeed(types));
            case "electric_sound_steel" -> Math.max(0, .15 - AffinityBalance.playerAttackSpeed(types));
            case "normal_fighting" -> Math.max(0, .10 - AffinityBalance.playerAttackSpeed(types));
            default -> 0;
        };
        attack += Math.min(standard.attackSpeed(), Math.max(0, .25 - AffinityBalance.playerAttackSpeed(types)));
        double armor = ("ice_rock_ground".equals(recipe) ? 2 : 0) + standard.armor();
        double toughness = switch (recipe) {
            case "electric_sound_steel" -> 1;
            case "ice_rock_ground" -> 2;
            default -> 0;
        };
        double kb = StandardSynergyHookBalance.knockbackBonus(types, recipe,
                StandardResonanceBalance.synergyId(recipe));
        double jump = "bug_grass".equals(recipe) ? Math.max(0, .20 - AffinityBalance.BUG_JUMP_STRENGTH) : 0;
        double mining = switch (recipe) {
            case "normal_fighting" -> Math.max(0, .10 - AffinityBalance.normalMining(types));
            default -> 0;
        };
        mining += Math.min(standard.mining(), Math.max(0, .40 - AffinityBalance.rockMining(types)
                - AffinityBalance.normalMining(types)));
        modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVE, movement, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK, attack, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player.getAttribute(Attributes.ARMOR), ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
        modifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS, toughness, AttributeModifier.Operation.ADD_VALUE);
        modifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KB, kb, AttributeModifier.Operation.ADD_VALUE);
        modifier(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP, jump, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), MINING, mining, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        double submerged = recipe.equals("water_grass") || recipe.equals("fairy_grass_water")
                ? Math.max(0, 1 - player.getAttributeBaseValue(Attributes.SUBMERGED_MINING_SPEED)) : 0;
        modifier(player.getAttribute(Attributes.SUBMERGED_MINING_SPEED), SUBMERGED, submerged,
                AttributeModifier.Operation.ADD_VALUE);
        if ((recipe.equals("fairy_light") || recipe.equals("fairy_light_psychic"))
                && player.hasEffect(MobEffects.BLINDNESS)) player.removeEffect(MobEffects.BLINDNESS);
        updateFlightSpeed(player, recipe);
    }

    private static void updateFlightSpeed(ServerPlayer player, String recipe) {
        double boost = recipe.equals("air_electric_flying") ? .30
                : recipe.equals("air_flying") ? .25
                : recipe.equals("dragon_fire_flying") ? .20 : 0;
        var data = TrainerState.data(player);
        var abilities = player.getAbilities();
        boolean owned = data.contains(FLY_BASE) && data.contains(FLY_APPLIED);
        if (boost == 0 || !abilities.mayfly) {
            if (owned && Math.abs(abilities.getFlyingSpeed() - data.getFloat(FLY_APPLIED)) < .00001) {
                abilities.setFlyingSpeed(data.getFloat(FLY_BASE));
                player.onUpdateAbilities();
            }
            data.remove(FLY_BASE);
            data.remove(FLY_APPLIED);
            return;
        }
        float base = owned ? data.getFloat(FLY_BASE) : abilities.getFlyingSpeed();
        if (owned && Math.abs(abilities.getFlyingSpeed() - data.getFloat(FLY_APPLIED)) >= .00001) {
            // Another mod changed flight speed; do not take ownership of that change.
            data.remove(FLY_BASE);
            data.remove(FLY_APPLIED);
            return;
        }
        float applied = (float) (base * (1 + boost));
        data.putFloat(FLY_BASE, base);
        data.putFloat(FLY_APPLIED, applied);
        if (abilities.getFlyingSpeed() != applied) {
            abilities.setFlyingSpeed(applied);
            player.onUpdateAbilities();
        }
    }

    private static void modifier(AttributeInstance instance, ResourceLocation id, double amount,
                                 AttributeModifier.Operation operation) {
        if (instance == null) return;
        var current = instance.getModifier(id);
        if (current != null && current.amount() == amount && current.operation() == operation) return;
        if (current != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    public static double playerDamageMultiplier(ServerPlayer player, DamageSource source) {
        String recipe = current(player);
        if (source.is(DamageTypes.LAVA) && (recipe.equals("fire_rock") || recipe.equals("fire_rock_steel"))) {
            return .50; // Fire's existing x.50 makes x.25 total.
        }
        if (source.is(DamageTypeTags.IS_FALL) && (recipe.equals("air_flying")
                || recipe.equals("air_electric_flying") || recipe.equals("dark_ghost_psychic"))) return 0;
        return 1;
    }

    /** PvE-only player offensive synergy. */
    public static double playerAttackBonus(ServerPlayer player, Mob target, boolean melee) {
        if (!melee || target instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity) return 0;
        String recipe = current(player);
        if (recipe.equals("dragon_fighting")) {
            return Math.min(.15, .10 + (target.getMaxHealth() > 50 ? .05 : 0));
        }
        if (recipe.equals("dragon_fire_flying") && target.getMaxHealth() > 50) return .15;
        return 0;
    }

    /** Lightweight effects with no block mutation. */
    public static void tick(ServerPlayer player) {
        String recipe = current(player);
        if (player.tickCount % 20 == 0 && (recipe.equals("electric_steel")
                || recipe.equals("electric_sound_steel"))) {
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(4), entity -> entity.isAlive())) {
                var toward = player.position().subtract(item.position());
                if (toward.lengthSqr() > .25) item.setDeltaMovement(item.getDeltaMovement().add(toward.normalize().scale(.10)));
            }
        }
        if (player.tickCount % 40 == 0 && recipe.equals("fairy_light_psychic")) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(4), entity -> entity.getType().is(EntityTypeTags.UNDEAD))) {
                if (!(mob instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity)) mob.setRemainingFireTicks(Math.max(mob.getRemainingFireTicks(), 40));
            }
        }
    }

    /** Never takes a harvest away; only grants it when the next tier's tool of the same kind could. */
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest() || !toolTier(event.getEntity())) return;
        if (correctOneTierUp(event.getEntity().getMainHandItem(), event.getTargetBlock())) event.setCanHarvest(true);
    }

    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!toolTier(player)) return;
        ItemStack stack = player.getMainHandItem();
        Tool tool = stack.get(DataComponents.TOOL);
        if (!ResonanceLinks.isVanillaTierTag(tierTag(tool))) return;
        ResonanceLinks.Tier next = nextTier(tool);
        // Same base value Player.getDigSpeed starts from (Inventory.getDestroySpeed of the selected stack).
        float base = stack.getDestroySpeed(event.getState());
        if (base <= 1) return;
        float efficiency = base > 1.0F ? (float) player.getAttributeValue(Attributes.MINING_EFFICIENCY) : 0.0F;
        double factor = ResonanceLinks.speedFactor(base, efficiency, next);
        double extra = current(player).equals("fire_rock_steel") ? .15 : .10;
        event.setNewSpeed((float) (event.getNewSpeed() * factor * (1 + extra)));
    }

    /**
     * The tool's own drop rules with its tier's "incorrect" tag swapped for the next tier's,
     * evaluated like {@link Tool#isCorrectForDrops}. A pickaxe stays a pickaxe: a block outside
     * its mineable tag is still not correct.
     */
    public static boolean correctOneTierUp(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        String tag = tierTag(tool);
        ResonanceLinks.Tier next = nextTier(tool);
        if (next == null) return false;
        TagKey<Block> nextTag = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.ResourceLocation.parse(next.incorrectTag()));
        for (Tool.Rule rule : tool.rules()) {
            if (rule.correctForDrops().isEmpty()) continue;
            boolean tierRule = !rule.correctForDrops().get() && tag.equals(tagId(rule));
            boolean matches = tierRule ? state.is(nextTag) : state.is(rule.blocks());
            if (matches) return rule.correctForDrops().get();
        }
        return false;
    }

    /** The vanilla tier tag of the tool's deny-drops rule, or {@code null} (not a vanilla-tiered tool). */
    public static String tierTag(Tool tool) {
        Tool.Rule rule = tierRule(tool);
        return rule == null ? null : tagId(rule);
    }

    private static Tool.Rule tierRule(Tool tool) {
        if (tool == null) return null;
        for (Tool.Rule rule : tool.rules()) {
            if (rule.correctForDrops().isPresent() && !rule.correctForDrops().get()
                    && ResonanceLinks.isVanillaTierTag(tagId(rule))) return rule;
        }
        return null;
    }

    /** One tier above the tool on the vanilla ladder, or {@code null}. */
    public static ResonanceLinks.Tier nextTier(Tool tool) {
        return ResonanceLinks.nextTier(tierTag(tool));
    }

    private static String tagId(Tool.Rule rule) {
        return rule.blocks().unwrapKey().map(key -> key.location().toString()).orElse(null);
    }
}
