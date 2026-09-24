package org.modularsoft.consentpvp.attack;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Splits a potion's effects into the harmful ones consent guards and the beneficial ones that
 * always apply. Which effects count as harmful is configured under {@code potions.harmful-effects}.
 */
public final class PotionFilter {

    private final Supplier<Set<PotionEffectType>> harmful;

    public PotionFilter(Supplier<Set<PotionEffectType>> harmful) {
        this.harmful = harmful;
    }

    public boolean isHarmful(PotionEffect effect) {
        return harmful.get().contains(effect.getType());
    }

    /** The potion's effects: its base type's plus any custom ones, read from the item if need be. */
    public static List<PotionEffect> effectsOf(ThrownPotion potion) {
        List<PotionEffect> effects = new ArrayList<>(potion.getEffects());
        if (effects.isEmpty()) {
            PotionMeta meta = potion.getPotionMeta();
            if (meta.getBasePotionType() != null) {
                effects.addAll(meta.getBasePotionType().getPotionEffects());
            }
            effects.addAll(meta.getCustomEffects());
        }
        return effects;
    }

    public static List<PotionEffect> effectsOf(AreaEffectCloud cloud) {
        List<PotionEffect> effects = new ArrayList<>();
        PotionType base = cloud.getBasePotionType();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        effects.addAll(cloud.getCustomEffects());
        return effects;
    }

    public boolean anyHarmful(List<PotionEffect> effects) {
        return effects.stream().anyMatch(this::isHarmful);
    }

    public List<PotionEffect> beneficial(List<PotionEffect> effects) {
        return effects.stream().filter(effect -> !isHarmful(effect)).toList();
    }

    /**
     * Applies only the beneficial part of a mixed potion to a player it was blocked for, scaled the
     * way vanilla scales a splash by distance. Instant effects ignore the scaled duration.
     */
    public void applyBeneficial(LivingEntity target, List<PotionEffect> effects, double intensity) {
        for (PotionEffect effect : beneficial(effects)) {
            int duration = effect.getType().isInstant()
                    ? 1
                    : (int) Math.max(1, Math.round(effect.getDuration() * intensity));
            target.addPotionEffect(new PotionEffect(effect.getType(), duration, effect.getAmplifier(),
                    effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
    }
}
