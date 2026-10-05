package starlight.trainer;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.Set;

/** Numeric player effects that use named, reversible attribute modifiers. */
public final class PlayerAffinityEffects {
    private static final ResourceLocation MOVE = id("movement");
    private static final ResourceLocation ATTACK_SPEED = id("attack_speed");
    private static final ResourceLocation ARMOR = id("armor");
    private static final ResourceLocation TOUGHNESS = id("toughness");
    private static final ResourceLocation KNOCKBACK_RESISTANCE = id("knockback_resistance");
    private static final ResourceLocation MAX_HEALTH = id("max_health");
    private static final ResourceLocation JUMP = id("jump");
    private static final ResourceLocation SWIM = id("swim_speed");
    /** Read on both sides by {@link PlayerAffinityEvents#onBreakSpeed} for the ore bonus. */
    static final ResourceLocation ROCK_MINING = id("rock_mining");
    static final ResourceLocation NORMAL_MINING = id("normal_mining");
    private static final String OWNS_FLIGHT = "OwnsFlight";

    private PlayerAffinityEffects() {}

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(SuperPalletTowner.MOD_ID, name);
    }

    /**
     * Powder snow adds one freezing tick per tick and the client draws the frost
     * overlay from it, so a reset every 20 ticks still showed a faint overlay.
     */
    public static void tick(ServerPlayer player) {
        if (player.getTicksFrozen() > 0 && TrainerState.active(player).contains("ice")) {
            player.setTicksFrozen(0);
        }
    }

    public static void update(ServerPlayer player, Set<String> active) {
        double movement = AffinityBalance.playerMovement(active, player.level().isNight());
        double attack = AffinityBalance.playerAttackSpeed(active);
        modifier(player, Attributes.MOVEMENT_SPEED, MOVE, movement, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED, attack, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, Attributes.ARMOR, ARMOR, active.contains("ground") ? AffinityBalance.GROUND_ARMOR : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS, active.contains("steel") ? AffinityBalance.STEEL_TOUGHNESS : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE,
                (active.contains("ground") ? AffinityBalance.GROUND_KNOCKBACK_RESISTANCE : 0)
                        + (active.contains("sound") ? AffinityBalance.SOUND_KNOCKBACK_RESISTANCE : 0),
                AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.MAX_HEALTH, MAX_HEALTH, active.contains("dragon") ? AffinityBalance.DRAGON_MAX_HEALTH : 0,
                AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.JUMP_STRENGTH, JUMP, active.contains("bug") ? AffinityBalance.BUG_JUMP_STRENGTH : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, NeoForgeMod.SWIM_SPEED, SWIM, active.contains("water") ? AffinityBalance.WATER_SWIM_SPEED : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        // A synced attribute, not a server-only BreakSpeed change: the client decides
        // when a block breaks, so a server-side bonus never made mining faster.
        modifier(player, Attributes.BLOCK_BREAK_SPEED, ROCK_MINING, AffinityBalance.rockMining(active),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, Attributes.BLOCK_BREAK_SPEED, NORMAL_MINING, AffinityBalance.normalMining(active),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        // Applicable blocks new Poison; this clears Poison taken before the Affinity was active.
        if (active.contains("poison") && player.hasEffect(MobEffects.POISON)) player.removeEffect(MobEffects.POISON);
        if (active.contains("light") && player.hasEffect(MobEffects.DARKNESS)) player.removeEffect(MobEffects.DARKNESS);
        if (active.contains("water")) player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
        if (active.contains("dark")) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
        updateFlight(player, active.contains("flying"));
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
                                 double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.amount() == amount && current.operation() == operation) return;
        if (current != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /**
     * Ownership is saved with the player: vanilla persists mayfly across logout, so an
     * in-memory flag would leave Affinity flight permanently granted after a relog.
     */
    private static void updateFlight(ServerPlayer player, boolean active) {
        var data = TrainerState.data(player);
        var abilities = player.getAbilities();
        if (active) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                data.putBoolean(OWNS_FLIGHT, true);
                player.onUpdateAbilities();
            }
        } else if (data.getBoolean(OWNS_FLIGHT)) {
            data.remove(OWNS_FLIGHT);
            // A respawn resets mayfly; if it is already off there is nothing of ours to remove.
            if (abilities.mayfly && !player.isCreative() && !player.isSpectator()) {
                boolean wasFlying = abilities.flying;
                abilities.flying = false;
                abilities.mayfly = false;
                player.onUpdateAbilities();
                if (wasFlying && !player.onGround()) {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
                }
            }
        }
    }
}
