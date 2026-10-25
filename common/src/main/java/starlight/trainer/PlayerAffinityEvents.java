package starlight.trainer;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player effects whose values depend on an action rather than a persistent attribute. Each method
 * takes the action's values and returns the changed one; NeoForge calls them from its events,
 * Fabric from its callbacks and mixins.
 */
public final class PlayerAffinityEvents {
    private static final Map<UUID, EnumMap<EquipmentSlot, Double>> ARMOR_FRACTIONS = new HashMap<>();
    private PlayerAffinityEvents() {}

    /**
     * The general Rock/Normal bonus is the synced BLOCK_BREAK_SPEED attribute. Ores get
     * Rock's extra +10% here, on both client and server, so both compute the same progress;
     * the client learns Rock is active from the synced modifier.
     */
    public static float breakSpeed(Player player, BlockState state, float speed) {
        if (!state.is(TrainerTags.ORES)) return speed;
        AttributeInstance breakSpeed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (breakSpeed == null) return speed;
        AttributeModifier rock = breakSpeed.getModifier(PlayerAffinityEffects.ROCK_MINING);
        if (rock == null) return speed;
        AttributeModifier normal = breakSpeed.getModifier(PlayerAffinityEffects.NORMAL_MINING);
        double general = rock.amount() + (normal == null ? 0 : normal.amount());
        return (float) (speed * AffinityBalance.oreMiningFactor(general));
    }

    /** Whether the effect is blocked outright. */
    public static boolean blocksEffect(LivingEntity entity, MobEffectInstance effect) {
        if (!(entity instanceof ServerPlayer player)) return false;
        Set<String> active = TrainerState.active(player);
        return (effect.is(MobEffects.POISON) && active.contains("poison"))
                || (effect.is(MobEffects.DARKNESS) && active.contains("light"))
                || (effect.is(MobEffects.BLINDNESS)
                && (ResonanceLinkEffects.current(player).equals("fairy_light")
                || ResonanceLinkEffects.current(player).equals("fairy_light_psychic")));
    }

    /** After damage was dealt; {@code dealt} is the damage that reached the target. */
    public static void afterDamage(LivingEntity entity, DamageSource source, float dealt) {
        if (!(source.getEntity() instanceof ServerPlayer player)
                || !(entity instanceof Mob target)
                || source.getDirectEntity() != player
                || dealt <= 0) return;
        Set<String> active = TrainerState.active(player);
        if (active.contains("ice") && player.getRandom().nextFloat() < AffinityBalance.ICE_SLOW_CHANCE) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AffinityBalance.ICE_SLOW_TICKS, 0));
        }
        if (active.contains("poison") && player.getRandom().nextFloat() < AffinityBalance.POISON_PROC_CHANCE) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, AffinityBalance.POISON_PROC_TICKS, 0));
        }
    }

    /**
     * Players have a base ATTACK_KNOCKBACK of 0, so a multiplied attribute modifier does
     * nothing. Scale the knockback itself when this tick's hit came from a Fighting trainer.
     */
    public static float knockback(LivingEntity target, float strength) {
        if (target.getLastHurtByMobTimestamp() != target.tickCount) return strength;
        if (target.getLastHurtByMob() instanceof ServerPlayer player) {
            Set<String> active = TrainerState.active(player);
            double bonus = (active.contains("fighting") ? AffinityBalance.FIGHTING_KNOCKBACK : 0)
                    + (target instanceof Mob && !(target instanceof PokemonEntity) && active.contains("sound")
                    ? AffinityBalance.SOUND_OUTGOING_KNOCKBACK : 0);
            if (target instanceof Mob && !(target instanceof PokemonEntity)
                    && ResonanceLinkEffects.current(player).equals("electric_sound")) bonus = Math.max(bonus, .20);
            if (bonus > 0) return strength * (float) (1 + bonus);
        } else if (target instanceof Mob && !(target instanceof PokemonEntity)
                && target.getLastHurtByMob() instanceof PokemonEntity attacker
                && attacker.getBattleId() == null && !attacker.getPokemon().isFainted()) {
            ServerPlayer owner = attacker.getPokemon().getOwnerPlayer();
            if (owner != null) {
                var types = TrainerState.types(attacker.getPokemon());
                Set<String> ownerActive = TrainerState.active(owner);
                double base = AffinityBalance.pokemonSoundKnockback(ownerActive, types)
                        ? AffinityBalance.SOUND_POKEMON_KNOCKBACK : 0;
                var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(owner, ownerActive), types,
                        attacker.level().isNight(), true, false);
                double extra = Math.min(.40, base + link.knockback());
                if (extra > 0) return strength * (float) (1 + extra);
            }
        }
        return strength;
    }

    public static float heal(LivingEntity entity, float amount) {
        if (amount <= 0 || !(entity instanceof ServerPlayer player)) return amount;
        double bonus = TrainerState.active(player).contains("fairy") ? AffinityBalance.FAIRY_RECEIVED_HEALING : 0;
        String link = ResonanceLinkEffects.current(player);
        if (link.equals("fairy_light")) bonus = Math.max(bonus, .25);
        else if (link.equals("fairy_light_psychic")) bonus = Math.max(bonus, .30);
        else if (link.equals("fairy_grass_water")) bonus = Math.max(bonus, .20);
        return bonus > 0 ? (float) (amount * (1 + bonus)) : amount;
    }

    /** A new or updated effect, before it is stored: harmful durations may be shortened in place. */
    public static void effectAdded(LivingEntity entity, MobEffectInstance effect) {
        if (effect.isInfiniteDuration()
                || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        if (entity instanceof ServerPlayer player) {
            String link = ResonanceLinkEffects.current(player);
            if (effect.is(MobEffects.BLINDNESS) && TrainerState.active(player).contains("light")) {
                scaleDuration(effect, AffinityBalance.LIGHT_BLINDNESS_DURATION);
            }
            double factor = TrainerState.active(player).contains("fairy")
                    ? AffinityBalance.FAIRY_PLAYER_HARMFUL_DURATION : 1;
            if (link.equals("psychic_fairy")) factor = Math.min(factor, .60);
            if (factor < 1) scaleDuration(effect, factor);
        } else if (entity instanceof PokemonEntity pokemonEntity && pokemonEntity.getBattleId() == null) {
            ServerPlayer owner = pokemonEntity.getPokemon().getOwnerPlayer();
            if (owner == null || pokemonEntity.getPokemon().isFainted()) return;
            var types = TrainerState.types(pokemonEntity.getPokemon());
            Set<String> ownerActive = TrainerState.active(owner);
            double factor = AffinityBalance.pokemonHarmfulDurationReduced(ownerActive, types)
                    ? AffinityBalance.POKEMON_HARMFUL_DURATION : 1;
            var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(owner, ownerActive), types,
                    pokemonEntity.level().isNight(), false, false);
            factor = Math.min(factor, link.harmfulDuration());
            if (factor < 1) scaleDuration(effect, factor);
        }
    }

    /**
     * {@code MobEffectInstance.mapDuration} only returns the mapped value, so the loader writes
     * the duration field directly.
     */
    private static void scaleDuration(MobEffectInstance effect, double factor) {
        TrainerPlatform.get().setEffectDuration(effect, Math.max(1, (int) Math.ceil(effect.getDuration() * factor)));
    }

    /** The target the mob may take: {@code null} when a Ghost trainer is out of its reduced range. */
    public static LivingEntity target(Mob mob, LivingEntity target) {
        if (!(target instanceof ServerPlayer player)
                || !TrainerState.active(player).contains("ghost")) return target;
        String link = ResonanceLinkEffects.current(player);
        double rangeFactor = link.equals("dark_ghost_psychic") ? .50
                : link.equals("dark_ghost") ? .60 : AffinityBalance.GHOST_DETECTION_RANGE;
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * rangeFactor;
        return mob.distanceToSqr(player) > range * range ? null : target;
    }

    /** Durability damage one armor piece takes from a hit; Steel trainers wear armor more slowly. */
    public static float armorDamage(LivingEntity entity, EquipmentSlot slot, ItemStack armor, float damage) {
        if (!(entity instanceof ServerPlayer player)
                || !TrainerState.active(player).contains("steel")
                || damage <= 0 || armor.isEmpty()) return damage;
        EnumMap<EquipmentSlot, Double> fractions = ARMOR_FRACTIONS.computeIfAbsent(
                player.getUUID(), ignored -> new EnumMap<>(EquipmentSlot.class));
        String link = ResonanceLinkEffects.current(player);
        double saving = EquipmentDurabilityEffects.savingFor(
                link, StandardResonanceBalance.synergyId(link));
        double discounted = damage * (1 - saving) + fractions.getOrDefault(slot, 0.0);
        int applied = (int) Math.floor(discounted + 1e-9);
        fractions.put(slot, discounted - applied);
        return applied;
    }

    public static void forget(ServerPlayer player) {
        ARMOR_FRACTIONS.remove(player.getUUID());
    }
}
