package org.modularsoft.consentpvp;

import org.bukkit.Material;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionFilterTest extends PluginTest {

    private PlayerMock thrower;
    private PlayerMock target;

    @BeforeEach
    void players() {
        thrower = player("Thrower", false);
        target = player("Target", false);
    }

    private PotionSplashEvent splash(PotionType type, PotionEffect... custom) {
        ThrownPotion potion = world.spawn(thrower.getLocation(), ThrownPotion.class);
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (type != null) {
            meta.setBasePotionType(type);
        }
        for (PotionEffect effect : custom) {
            meta.addCustomEffect(effect, true);
        }
        item.setItemMeta(meta);
        potion.setItem(item);
        potion.setShooter(thrower);
        Map<LivingEntity, Double> affected = new HashMap<>();
        affected.put(target, 1.0);
        affected.put(thrower, 1.0);
        return Fx.call(new PotionSplashEvent(potion, affected));
    }

    @Test
    void everyListedHarmfulEffectIsBlocked() {
        for (PotionEffectType type : List.of(PotionEffectType.INSTANT_DAMAGE, PotionEffectType.POISON,
                PotionEffectType.WITHER, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
                PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS, PotionEffectType.HUNGER,
                PotionEffectType.LEVITATION, PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
                PotionEffectType.BAD_OMEN)) {
            PotionSplashEvent event = splash(null, new PotionEffect(type, 200, 0));
            assertEquals(0.0, event.getIntensity(target), type.getKey().toString());
        }
    }

    @Test
    void beneficialPotionsAlwaysApply() {
        for (PotionType type : List.of(PotionType.HEALING, PotionType.REGENERATION, PotionType.SWIFTNESS,
                PotionType.FIRE_RESISTANCE)) {
            assertEquals(1.0, splash(type).getIntensity(target), type.name());
        }
        assertTrue(Fx.drain(thrower).isEmpty(), "no denial for a helpful potion");
    }

    @Test
    void theThrowerIsNeverFilteredForThemselves() {
        assertEquals(1.0, splash(PotionType.POISON).getIntensity(thrower));
    }

    @Test
    void theBeneficialPartOfAMixedPotionStillApplies() {
        PotionSplashEvent event = splash(null, new PotionEffect(PotionEffectType.POISON, 200, 0),
                new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
        assertEquals(0.0, event.getIntensity(target));
        assertTrue(target.hasPotionEffect(PotionEffectType.REGENERATION));
        assertFalse(target.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void consentingPlayersGetTheFullPotion() {
        plugin.data().setConsent(thrower.getUniqueId(), true);
        plugin.data().setConsent(target.getUniqueId(), true);
        assertEquals(1.0, splash(PotionType.POISON).getIntensity(target));
    }

    @Test
    void lingeringCloudsFilterTheSameWay() {
        AreaEffectCloud harmful = world.spawn(thrower.getLocation(), AreaEffectCloud.class);
        harmful.setBasePotionType(PotionType.HARMING);
        harmful.setSource(thrower);
        List<LivingEntity> affected = new ArrayList<>(List.of(target));
        Fx.call(new AreaEffectCloudApplyEvent(harmful, affected));
        assertFalse(affected.contains(target));

        AreaEffectCloud healing = world.spawn(thrower.getLocation(), AreaEffectCloud.class);
        healing.setBasePotionType(PotionType.REGENERATION);
        healing.setSource(thrower);
        List<LivingEntity> healed = new ArrayList<>(List.of(target));
        Fx.call(new AreaEffectCloudApplyEvent(healing, healed));
        assertTrue(healed.contains(target));
    }

    @Test
    void theHarmfulListIsConfigurable() {
        plugin.configFile().set("potions.harmful-effects", List.of("poison"));
        plugin.reloadPluginConfig();
        assertEquals(1.0, splash(null, new PotionEffect(PotionEffectType.SLOWNESS, 200, 0)).getIntensity(target));
        assertEquals(0.0, splash(PotionType.POISON).getIntensity(target));
    }
}
