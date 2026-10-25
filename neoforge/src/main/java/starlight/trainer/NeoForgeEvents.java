package starlight.trainer;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;

/** NeoForge game events forwarded to the shared hooks, registered in the same order as before 1.3.0. */
final class NeoForgeEvents {
    private NeoForgeEvents() {}

    static void register(IEventBus bus) {
        bus.addListener(NeoForgeEvents::onIncomingDamage);
        bus.addListener(NeoForgeEvents::onDamage);
        bus.addListener(NeoForgeEvents::onPlayerXp);
        bus.addListener(NeoForgeEvents::onOreBreakSpeed);
        bus.addListener(NeoForgeEvents::onEffectApplicable);
        bus.addListener(NeoForgeEvents::onMeleeDamage);
        bus.addListener(NeoForgeEvents::onHeal);
        bus.addListener(NeoForgeEvents::onKnockBack);
        bus.addListener(NeoForgeEvents::onEffectAdded);
        bus.addListener(NeoForgeEvents::onTargetChange);
        bus.addListener(NeoForgeEvents::onArmorHurt);
        bus.addListener(NeoForgeEvents::onHarvestCheck);
        bus.addListener(NeoForgeEvents::onLinkBreakSpeed);
        bus.addListener(NeoForgeEvents::onBlockDrops);
        bus.addListener(NeoForgeEvents::onVibration);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (PveAffinityDamage.cancelsIncoming(event.getEntity(), event.getSource())) event.setCanceled(true);
    }

    private static void onDamage(LivingDamageEvent.Pre event) {
        float damage = event.getNewDamage();
        float scaled = PveAffinityDamage.scaleDamage(event.getEntity(), event.getSource(), damage);
        if (scaled != damage) event.setNewDamage(scaled);
    }

    private static void onPlayerXp(PlayerXpEvent.XpChange event) {
        event.setAmount(TrainerProgression.playerXp(event.getEntity(), event.getAmount()));
    }

    private static void onOreBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.setNewSpeed(PlayerAffinityEvents.breakSpeed(event.getEntity(), event.getState(), event.getNewSpeed()));
    }

    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (PlayerAffinityEvents.blocksEffect(event.getEntity(), event.getEffectInstance())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static void onMeleeDamage(LivingDamageEvent.Post event) {
        PlayerAffinityEvents.afterDamage(event.getEntity(), event.getSource(), event.getNewDamage());
    }

    private static void onHeal(LivingHealEvent event) {
        event.setAmount(PlayerAffinityEvents.heal(event.getEntity(), event.getAmount()));
    }

    private static void onKnockBack(LivingKnockBackEvent event) {
        event.setStrength(PlayerAffinityEvents.knockback(event.getEntity(), event.getStrength()));
    }

    private static void onEffectAdded(MobEffectEvent.Added event) {
        PlayerAffinityEvents.effectAdded(event.getEntity(), event.getEffectInstance());
    }

    private static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getNewAboutToBeSetTarget() == null) return;
        if (PlayerAffinityEvents.target(mob, event.getNewAboutToBeSetTarget()) == null) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    private static void onArmorHurt(ArmorHurtEvent event) {
        for (var entry : event.getArmorMap().entrySet()) {
            float original = entry.getValue().newDamage;
            float changed = PlayerAffinityEvents.armorDamage(event.getEntity(), entry.getKey(),
                    entry.getValue().armorItemStack, original);
            if (changed != original) event.setNewDamage(entry.getKey(), changed);
        }
    }

    private static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        event.setCanHarvest(ResonanceLinkEffects.canHarvest(event.getEntity(), event.getTargetBlock(), event.canHarvest()));
    }

    private static void onLinkBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.setNewSpeed(ResonanceLinkEffects.breakSpeed(event.getEntity(), event.getState(), event.getNewSpeed()));
    }

    /** Drop changes are made in the event's original mutable drop list. */
    private static void onBlockDrops(BlockDropsEvent event) {
        var plan = ResonanceWorldEffects.dropPlan(event.getBreaker(), event.getLevel(), event.getState());
        if (plan == null) return;
        // Iterate a snapshot so extra crop drops cannot be processed again by this listener.
        for (ItemEntity drop : new ArrayList<>(event.getDrops())) {
            ItemStack stack = drop.getItem();
            ItemStack smelted = ResonanceWorldEffects.smelted(plan, stack);
            if (smelted != null) drop.setItem(smelted);
            int extra = ResonanceWorldEffects.extraCrops(plan, stack);
            if (extra > 0) event.getDrops().add(new ItemEntity(event.getLevel(), drop.getX(), drop.getY(),
                    drop.getZ(), stack.copyWithCount(extra)));
        }
    }

    private static void onVibration(VanillaGameEvent event) {
        if (ResonanceWorldEffects.cancelsVibration(event.getCause(), event.getVanillaEvent())) event.setCanceled(true);
    }
}
