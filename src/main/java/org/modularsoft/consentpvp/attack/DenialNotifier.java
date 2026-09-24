package org.modularsoft.consentpvp.attack;

import dev.anchorlight.stonelib.bedrock.BedrockForms;
import dev.anchorlight.stonelib.message.MiniMessages;
import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import dev.anchorlight.stonelib.vanish.VanishStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.modularsoft.consentpvp.Settings;
import org.modularsoft.consentpvp.consent.ConsentService;
import org.modularsoft.consentpvp.util.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Tells players an attack was blocked, without giving away anyone who is hidden.
 *
 * <p>The same anonymity rule applies to every route - melee, projectiles, potions, fire: a player
 * who is vanished (hidden from the viewer, or carrying the {@code vanished} metadata most vanish
 * plugins set) or invisible is named as anonymous. Each player's state is read on that player's own
 * thread.
 *
 * <p>When the attacker is the one without PvP on, their notice carries a clickable [Enable PvP]
 * button. It is left off when only the defender has PvP off, since enabling it would not help, and
 * for Bedrock players and action-bar delivery, where it cannot be clicked.
 */
public final class DenialNotifier {

    private final Messages messages;
    private final PlatformScheduler scheduler;
    private final Supplier<Settings> settings;
    private final ConsentService consent;
    private final Supplier<BedrockForms> forms;
    private final LongSupplier clock;
    private final Map<String, Long> lastNotified = new ConcurrentHashMap<>();

    public DenialNotifier(Messages messages, PlatformScheduler scheduler, Supplier<Settings> settings,
                          ConsentService consent, Supplier<BedrockForms> forms, LongSupplier clock) {
        this.messages = messages;
        this.scheduler = scheduler;
        this.settings = settings;
        this.consent = consent;
        this.forms = forms;
        this.clock = clock;
    }

    /** True when {@code subject} should be anonymous to {@code viewer}. Call on the subject's thread. */
    public static boolean hiddenFrom(Player subject, Player viewer) {
        return subject.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || VanishStatus.isVanished(subject)
                || (viewer != null && !viewer.canSee(subject));
    }

    /**
     * An attack on {@code defender} was blocked for lack of consent. Call on the defender's thread.
     *
     * @param throttled true for continuous sources (lingering clouds), which would otherwise send a
     *                  notice every few ticks
     */
    public void denied(Player defender, AttackerResolver.Attacker attacker, boolean throttled) {
        if (throttled && !allow(attacker.id(), defender.getUniqueId())) {
            return;
        }
        boolean defenderHidden = defender.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || VanishStatus.isVanished(defender);
        String defenderName = defender.getName();
        Player attackerPlayer = attacker.player();

        if (attackerPlayer != null) {
            Component button = enableButton(attackerPlayer);
            scheduler.runOn(attackerPlayer, () -> {
                boolean anonymous = defenderHidden || !attackerPlayer.canSee(defender);
                if (anonymous) {
                    messages.attempt(attackerPlayer, "pvp_not_consented_attacker_anonymous", button);
                } else {
                    messages.attempt(attackerPlayer, "pvp_not_consented_attacker", button, "player", defenderName);
                }
            });
        }

        if (!settings.get().notifyDefenderOnDenial()) {
            return;
        }
        if (attackerPlayer == null) {
            messages.attempt(defender, "pvp_not_consented_defender", null, "player", attacker.name());
            return;
        }
        String attackerName = attackerPlayer.getName();
        scheduler.runOn(attackerPlayer, () -> {
            boolean anonymous = hiddenFrom(attackerPlayer, null);
            scheduler.runOn(defender, () -> {
                if (anonymous || !defender.canSee(attackerPlayer)) {
                    messages.attempt(defender, "pvp_not_consented_defender_anonymous", null);
                } else {
                    messages.attempt(defender, "pvp_not_consented_defender", null, "player", attackerName);
                }
            });
        });
    }

    /**
     * A splash potion was blocked for several players at once. Call on the thread that owns the
     * splash; the defenders are all within a few blocks of it.
     */
    public void deniedSplash(Player thrower, List<Player> defenders) {
        // Defender state is read here, where the splash is; the thrower's on the thrower's thread.
        List<Boolean> defenderHidden = new ArrayList<>();
        List<String> defenderNames = new ArrayList<>();
        for (Player defender : defenders) {
            defenderHidden.add(defender.hasPotionEffect(PotionEffectType.INVISIBILITY) || VanishStatus.isVanished(defender));
            defenderNames.add(defender.getName());
        }
        Component button = enableButton(thrower);
        String anonymous = anonymousName();
        boolean notifyDefenders = settings.get().notifyDefenderOnDenial();
        String throwerName = thrower.getName();
        scheduler.runOn(thrower, () -> {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < defenders.size(); i++) {
                boolean hidden = defenderHidden.get(i) || !thrower.canSee(defenders.get(i));
                names.add(hidden ? anonymous : defenderNames.get(i));
            }
            messages.attempt(thrower, "pvp_not_consented_attacker_multiple", button, "players", String.join(", ", names));
            if (!notifyDefenders) {
                return;
            }
            boolean throwerHidden = hiddenFrom(thrower, null);
            for (Player defender : defenders) {
                scheduler.runOn(defender, () -> {
                    if (throwerHidden || !defender.canSee(thrower)) {
                        messages.attempt(defender, "pvp_not_consented_defender_anonymous", null);
                    } else {
                        messages.attempt(defender, "pvp_not_consented_defender", null, "player", throwerName);
                    }
                });
            }
        });
    }

    /** The defender is respawn-protected. Throttled, since a camper will keep swinging. */
    public void respawnProtected(Player defender, Player attacker) {
        if (attacker == null || !allow(attacker.getUniqueId(), defender.getUniqueId())) {
            return;
        }
        boolean hidden = defender.hasPotionEffect(PotionEffectType.INVISIBILITY) || VanishStatus.isVanished(defender);
        String name = hidden ? anonymousName() : defender.getName();
        messages.attempt(attacker, "respawn_protected_attacker", null, "player", name);
    }

    private Component enableButton(Player attacker) {
        if (consent.hasConsent(attacker.getUniqueId()) || forms.get().isBedrock(attacker.getUniqueId())) {
            return null;
        }
        return messages.button("button_enable_pvp", "button_enable_pvp_hover", "/pvp enable");
    }

    private boolean allow(UUID attacker, UUID defender) {
        long interval = settings.get().denialThrottle().toMillis();
        if (interval <= 0) {
            return true;
        }
        long now = clock.getAsLong();
        String key = attacker + ">" + defender;
        Long previous = lastNotified.get(key);
        if (previous != null && now - previous < interval) {
            return false;
        }
        lastNotified.put(key, now);
        if (lastNotified.size() > 4096) {
            lastNotified.values().removeIf(at -> now - at >= interval);
        }
        return true;
    }

    /** The configured word for an anonymous player, as plain text for a list of names. */
    private String anonymousName() {
        return messages.has("anonymous_name") ? MiniMessages.plain(messages.plain("anonymous_name")) : "?";
    }
}
