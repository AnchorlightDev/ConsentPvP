package org.modularsoft.consentpvp.listeners;

import dev.anchorlight.stonelib.vanish.VanishStatus;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.modularsoft.consentpvp.attack.AttackerResolver;
import org.modularsoft.consentpvp.attack.AttackerResolver.Attacker;
import org.modularsoft.consentpvp.attack.DenialNotifier;
import org.modularsoft.consentpvp.attack.PotionFilter;
import org.modularsoft.consentpvp.combat.CombatTagManager;
import org.modularsoft.consentpvp.consent.ConsentService;
import org.modularsoft.consentpvp.protection.RespawnProtection;
import org.modularsoft.consentpvp.util.Messages;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Enforces consent on every way one player can hurt another: damage of any kind, knockback,
 * setting them alight, and potions.
 *
 * <p>Every handler resolves the responsible player through {@link AttackerResolver}, so direct
 * hits, projectiles, TNT, pets, crystals, lava, fire and anchors share one set of rules. Handlers
 * run at {@code HIGH} with {@code ignoreCancelled}, so damage another plugin (WorldGuard, say) has
 * already cancelled is left alone and does not produce a denial message.
 */
public final class CombatListener implements Listener {

    private final AttackerResolver resolver;
    private final ConsentService consent;
    private final DenialNotifier notifier;
    private final RespawnProtection respawn;
    private final CombatTagManager combat;
    private final PotionFilter potions;
    private final Messages messages;

    public CombatListener(AttackerResolver resolver, ConsentService consent, DenialNotifier notifier,
                          RespawnProtection respawn, CombatTagManager combat, PotionFilter potions,
                          Messages messages) {
        this.resolver = resolver;
        this.consent = consent;
        this.notifier = notifier;
        this.respawn = respawn;
        this.combat = combat;
        this.potions = potions;
        this.messages = messages;
    }

    // ------------------------------------------------------------------ damage

    /**
     * Every kind of damage to a player, including the by-entity and by-block subclasses. Damage with
     * no responsible player, or that the player caused themselves - their own bed, a natural
     * explosion, lava nobody placed - is never touched.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }
        Attacker attacker = resolver.resolveAttacker(event);
        if (attacker == null || attacker.id().equals(defender.getUniqueId())) {
            return;
        }
        Player attackerPlayer = attacker.player();
        if (attackerPlayer != null && isVanishedTo(attackerPlayer, defender)) {
            event.setCancelled(true);
            return;
        }
        boolean sweep = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
        if (!consent.consentsTo(attacker.id(), defender.getUniqueId())) {
            event.setCancelled(true);
            // A sweep's bystanders are not the target, so they are not told about it.
            if (!sweep) {
                notifier.denied(defender, attacker, continuous(attacker.kind()));
            }
            return;
        }
        if (respawn.isProtected(defender.getUniqueId())) {
            event.setCancelled(true);
            if (!sweep) {
                notifier.respawnProtected(defender, attackerPlayer);
            }
            return;
        }
        endAttackerProtection(attackerPlayer);
    }

    /** Tags both fighters once damage has survived every other plugin. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageApplied(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }
        Attacker attacker = resolver.resolveAttacker(event);
        if (attacker == null || attacker.id().equals(defender.getUniqueId())) {
            return;
        }
        combat.tag(defender);
        if (attacker.player() != null) {
            combat.tag(attacker.player());
        }
    }

    // --------------------------------------------------------------- knockback

    /** Knockback from a hit, a wind charge or a pet, which is applied separately from the damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPushed(EntityPushedByEntityAttackEvent event) {
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }
        Attacker attacker = resolver.resolveAttacker(event.getPushedBy());
        if (attacker == null || attacker.id().equals(defender.getUniqueId())) {
            return;
        }
        if ((attacker.player() != null && isVanishedTo(attacker.player(), defender))
                || !consent.canFight(attacker.id(), defender.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------- fire

    /**
     * Flame arrows and fire aspect set the target alight before the damage event, so cancelling the
     * hit alone would still leave a non-consenting player burning.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }
        Attacker attacker = resolver.resolveAttacker(event.getCombuster());
        if (attacker == null || attacker.id().equals(defender.getUniqueId())) {
            return;
        }
        if (!consent.canFight(attacker.id(), defender.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Lighting fire directly under a player who has not consented. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        if (cause != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && cause != BlockIgniteEvent.IgniteCause.FIREBALL) {
            return;
        }
        Player igniter = event.getPlayer();
        if (igniter == null) {
            return;
        }
        Block block = event.getBlock();
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(centre, 0.5, 0.5, 0.5)) {
            if (entity instanceof Player defender && !defender.equals(igniter)
                    && !consent.canFight(igniter.getUniqueId(), defender.getUniqueId())) {
                event.setCancelled(true);
                notifier.denied(defender, new Attacker(igniter.getUniqueId(), igniter, AttackerResolver.Kind.FIRE), false);
                return;
            }
        }
    }

    // ----------------------------------------------------------------- potions

    /**
     * Harmful splash effects are blocked between players without consent; beneficial ones, such as
     * healing, always apply - including the beneficial part of a mixed potion.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        Attacker thrower = resolver.resolveAttacker(event.getPotion());
        if (thrower == null) {
            return;
        }
        List<PotionEffect> effects = PotionFilter.effectsOf(event.getPotion());
        if (!potions.anyHarmful(effects)) {
            return;
        }
        List<Player> denied = new ArrayList<>();
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (!(entity instanceof Player defender) || defender.getUniqueId().equals(thrower.id())) {
                continue;
            }
            if (!consent.canFight(thrower.id(), defender.getUniqueId())) {
                double intensity = event.getIntensity(defender);
                event.setIntensity(defender, 0);
                potions.applyBeneficial(defender, effects, intensity);
                denied.add(defender);
            }
        }
        if (!denied.isEmpty() && thrower.player() != null) {
            notifier.deniedSplash(thrower.player(), denied);
        }
    }

    /**
     * Lingering clouds. {@link AreaEffectCloud#getSource()} is the thrower itself, not the potion.
     * The cloud re-applies every few ticks, so its denial notices are throttled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        Attacker thrower = resolver.resolveAttacker(cloud);
        if (thrower == null) {
            return;
        }
        List<PotionEffect> effects = PotionFilter.effectsOf(cloud);
        if (!potions.anyHarmful(effects)) {
            return;
        }
        Iterator<LivingEntity> affected = event.getAffectedEntities().iterator();
        while (affected.hasNext()) {
            LivingEntity entity = affected.next();
            if (!(entity instanceof Player defender) || defender.getUniqueId().equals(thrower.id())) {
                continue;
            }
            if (!consent.canFight(thrower.id(), defender.getUniqueId())) {
                affected.remove();
                // Vanilla lingering effects last a quarter of the potion's duration.
                potions.applyBeneficial(defender, effects, 0.25);
                notifier.denied(defender, thrower, true);
            }
        }
    }

    // ----------------------------------------------------------------- helpers

    private void endAttackerProtection(Player attacker) {
        if (attacker != null && respawn.end(attacker.getUniqueId())) {
            messages.send(attacker, "respawn_protection_ended");
        }
    }

    private static boolean isVanishedTo(Player attacker, Player defender) {
        return !defender.canSee(attacker) || VanishStatus.isVanished(attacker);
    }

    /** Sources that hurt every tick, whose notices would otherwise flood chat. */
    private static boolean continuous(AttackerResolver.Kind kind) {
        return kind == AttackerResolver.Kind.LAVA || kind == AttackerResolver.Kind.FIRE
                || kind == AttackerResolver.Kind.POTION;
    }
}
