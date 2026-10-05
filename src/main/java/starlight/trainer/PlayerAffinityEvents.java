package starlight.trainer;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Player effects whose values depend on an action rather than a persistent attribute. */
public final class PlayerAffinityEvents {
    private static final Map<UUID, EnumMap<EquipmentSlot, Double>> ARMOR_FRACTIONS = new HashMap<>();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Field DURATION = durationField();
    private PlayerAffinityEvents() {}

    /**
     * The general Rock/Normal bonus is the synced BLOCK_BREAK_SPEED attribute. Ores get
     * Rock's extra +10% here, on both client and server, so both compute the same progress;
     * the client learns Rock is active from the synced modifier.
     */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!event.getState().is(Tags.Blocks.ORES)) return;
        AttributeInstance breakSpeed = event.getEntity().getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (breakSpeed == null) return;
        AttributeModifier rock = breakSpeed.getModifier(PlayerAffinityEffects.ROCK_MINING);
        if (rock == null) return;
        AttributeModifier normal = breakSpeed.getModifier(PlayerAffinityEffects.NORMAL_MINING);
        double general = rock.amount() + (normal == null ? 0 : normal.amount());
        event.setNewSpeed((float) (event.getNewSpeed() * AffinityBalance.oreMiningFactor(general)));
    }

    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Set<String> active = TrainerState.active(player);
        if ((event.getEffectInstance().is(MobEffects.POISON) && active.contains("poison"))
                || (event.getEffectInstance().is(MobEffects.DARKNESS) && active.contains("light"))
                || (event.getEffectInstance().is(MobEffects.BLINDNESS)
                && (ResonanceLinkEffects.current(player).equals("fairy_light")
                || ResonanceLinkEffects.current(player).equals("fairy_light_psychic")))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    public static void onMeleeDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity() instanceof Mob target)
                || event.getSource().getDirectEntity() != player
                || event.getNewDamage() <= 0) return;
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
    public static void onKnockBack(LivingKnockBackEvent event) {
        var target = event.getEntity();
        if (target.getLastHurtByMobTimestamp() != target.tickCount) return;
        if (target.getLastHurtByMob() instanceof ServerPlayer player) {
            Set<String> active = TrainerState.active(player);
            double bonus = (active.contains("fighting") ? AffinityBalance.FIGHTING_KNOCKBACK : 0)
                    + (target instanceof Mob && !(target instanceof PokemonEntity) && active.contains("sound")
                    ? AffinityBalance.SOUND_OUTGOING_KNOCKBACK : 0);
            if (target instanceof Mob && !(target instanceof PokemonEntity)
                    && ResonanceLinkEffects.current(player).equals("electric_sound")) bonus = Math.max(bonus, .20);
            if (bonus > 0) event.setStrength(event.getStrength() * (float) (1 + bonus));
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
                double strength = Math.min(.40, base + link.knockback());
                if (strength > 0) event.setStrength(event.getStrength() * (float) (1 + strength));
            }
        }
    }

    public static void onHeal(LivingHealEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof ServerPlayer player)) return;
        double bonus = TrainerState.active(player).contains("fairy") ? AffinityBalance.FAIRY_RECEIVED_HEALING : 0;
        String link = ResonanceLinkEffects.current(player);
        if (link.equals("fairy_light")) bonus = Math.max(bonus, .25);
        else if (link.equals("fairy_light_psychic")) bonus = Math.max(bonus, .30);
        else if (link.equals("fairy_grass_water")) bonus = Math.max(bonus, .20);
        if (bonus > 0) event.setAmount((float) (event.getAmount() * (1 + bonus)));
    }

    public static void onEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect.isInfiniteDuration()
                || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            String link = ResonanceLinkEffects.current(player);
            if (effect.is(MobEffects.BLINDNESS) && TrainerState.active(player).contains("light")) {
                scaleDuration(effect, AffinityBalance.LIGHT_BLINDNESS_DURATION);
            }
            double factor = TrainerState.active(player).contains("fairy")
                    ? AffinityBalance.FAIRY_PLAYER_HARMFUL_DURATION : 1;
            if (link.equals("psychic_fairy")) factor = Math.min(factor, .60);
            if (factor < 1) scaleDuration(effect, factor);
        } else if (event.getEntity() instanceof PokemonEntity entity && entity.getBattleId() == null) {
            ServerPlayer owner = entity.getPokemon().getOwnerPlayer();
            if (owner == null || entity.getPokemon().isFainted()) return;
            var types = TrainerState.types(entity.getPokemon());
            Set<String> ownerActive = TrainerState.active(owner);
            double factor = AffinityBalance.pokemonHarmfulDurationReduced(ownerActive, types)
                    ? AffinityBalance.POKEMON_HARMFUL_DURATION : 1;
            var link = ResonanceLinkBalance.pokemon(TrainerState.activeLinks(owner, ownerActive), types,
                    entity.level().isNight(), false, false);
            factor = Math.min(factor, link.harmfulDuration());
            if (factor < 1) scaleDuration(effect, factor);
        }
    }

    /**
     * {@code MobEffectInstance.mapDuration} only returns the mapped value, so the
     * duration field is written directly. The runtime uses Mojang names, as in dev.
     */
    private static void scaleDuration(MobEffectInstance effect, double factor) {
        if (DURATION == null) return;
        try {
            DURATION.setInt(effect, Math.max(1, (int) Math.ceil(effect.getDuration() * factor)));
        } catch (IllegalAccessException e) {
            LOGGER.error("Cannot shorten effect duration", e);
        }
    }

    private static Field durationField() {
        try {
            Field field = MobEffectInstance.class.getDeclaredField("duration");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("MobEffectInstance.duration not found; harmful-duration reductions are off", e);
            return null;
        }
    }

    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)
                || !TrainerState.active(player).contains("ghost")) return;
        String link = ResonanceLinkEffects.current(player);
        double rangeFactor = link.equals("dark_ghost_psychic") ? .50
                : link.equals("dark_ghost") ? .60 : AffinityBalance.GHOST_DETECTION_RANGE;
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * rangeFactor;
        if (mob.distanceToSqr(player) > range * range) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    public static void onArmorHurt(ArmorHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !TrainerState.active(player).contains("steel")) return;
        EnumMap<EquipmentSlot, Double> fractions = ARMOR_FRACTIONS.computeIfAbsent(
                player.getUUID(), ignored -> new EnumMap<>(EquipmentSlot.class));
        for (var entry : event.getArmorMap().entrySet()) {
            EquipmentSlot slot = entry.getKey();
            float original = entry.getValue().newDamage;
            if (original <= 0 || entry.getValue().armorItemStack.isEmpty()) continue;
            String link = ResonanceLinkEffects.current(player);
            double saving = EquipmentDurabilityEffects.savingFor(
                    link, StandardResonanceBalance.synergyId(link));
            double discounted = original * (1 - saving) + fractions.getOrDefault(slot, 0.0);
            int applied = (int) Math.floor(discounted + 1e-9);
            fractions.put(slot, discounted - applied);
            event.setNewDamage(slot, applied);
        }
    }

    public static void forget(ServerPlayer player) {
        ARMOR_FRACTIONS.remove(player.getUUID());
    }
}
