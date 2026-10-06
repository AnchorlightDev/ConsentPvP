package org.modularsoft.consentpvp;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.modularsoft.consentpvp.util.AttemptMessageDelivery;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * An immutable snapshot of config.yml, read once per load so handlers never parse config on the
 * hot path. A reload builds a new snapshot.
 */
public record Settings(
        boolean disablePvpOnDeath,
        Duration toggleCooldown,
        boolean duelsEnabled,
        Duration duelRequestTimeout,
        Duration duelResendCooldown,
        Duration duelMaxDuration,
        boolean newbieEnabled,
        Duration newbieRequiredPlaytime,
        boolean respawnProtectionEnabled,
        Duration respawnProtection,
        Set<PotionEffectType> harmfulEffects,
        Duration ownershipExpiry,
        Duration ownershipCleanupInterval,
        Duration denialThrottle,
        boolean firstJoinEnabled,
        boolean bedrockEnabled,
        boolean updateCheckerEnabled,
        String updateRepository,
        boolean metricsEnabled,
        AttemptMessageDelivery attemptDelivery,
        boolean notifyDefenderOnDenial,
        boolean indicatorsEnabled
) {

    public static Settings from(FileConfiguration config, Logger logger) {
        return new Settings(
                config.getBoolean("pvp.disable-on-death", false),
                minutes(config.getDouble("cooldown.duration", 1)),
                config.getBoolean("duels.enabled", true),
                seconds(config.getDouble("duels.request-timeout-seconds", 60)),
                seconds(config.getDouble("duels.resend-cooldown-seconds", 30)),
                seconds(config.getDouble("duels.max-duration-seconds", 300)),
                config.getBoolean("newbie-protection.enabled", true),
                minutes(config.getDouble("newbie-protection.required-playtime-minutes", 180)),
                config.getBoolean("respawn-protection.enabled", true),
                seconds(config.getDouble("respawn-protection.duration-seconds", 10)),
                effects(config.getStringList("potions.harmful-effects"), logger),
                seconds(Math.max(1, config.getDouble("ownership.expire-after-seconds", 300))),
                seconds(Math.max(1, config.getDouble("ownership.cleanup-interval-seconds", 60))),
                seconds(config.getDouble("denial.throttle-seconds", 2)),
                config.getBoolean("first-join.enabled", true),
                config.getBoolean("bedrock.enabled", true),
                config.getBoolean("update-checker.enabled", true),
                config.getString("update-checker.repository", "ModularSoftAU/ConsentPvP"),
                config.getBoolean("metrics.enabled", true),
                AttemptMessageDelivery.fromConfig(config.getString("messages.pvp_attempt_delivery", "chat")),
                config.getBoolean("messages.notify-defender-on-denial", false),
                config.getBoolean("indicators.enabled", true));
    }

    private static Duration seconds(double value) {
        return Duration.ofMillis(Math.round(Math.max(0, value) * 1000));
    }

    private static Duration minutes(double value) {
        return Duration.ofMillis(Math.round(Math.max(0, value) * 60_000));
    }

    private static Set<PotionEffectType> effects(List<String> keys, Logger logger) {
        Set<PotionEffectType> out = new HashSet<>();
        for (String raw : keys) {
            NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
            PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
            if (type == null) {
                logger.warning("Unknown potion effect in potions.harmful-effects: " + raw);
                continue;
            }
            out.add(type);
        }
        return Set.copyOf(out);
    }
}
