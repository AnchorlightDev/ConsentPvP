package org.modularsoft.consentpvp.consent;

import dev.anchorlight.stonelib.cooldown.CooldownService;
import dev.anchorlight.stonelib.time.Durations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.modularsoft.consentpvp.Settings;
import org.modularsoft.consentpvp.api.PvPConsentChangeEvent;
import org.modularsoft.consentpvp.combat.CombatTagManager;
import org.modularsoft.consentpvp.data.PlayerDataStore;
import org.modularsoft.consentpvp.duel.DuelManager;
import org.modularsoft.consentpvp.protection.NewbieProtection;
import org.modularsoft.consentpvp.protection.RespawnProtection;
import org.modularsoft.consentpvp.util.Messages;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The consent rules: who may fight whom, and when a player may change their own setting.
 */
public final class ConsentService {

    public static final String TOGGLE_COOLDOWN = "toggle";

    private final PlayerDataStore data;
    private final CooldownService cooldowns;
    private final Supplier<Settings> settings;
    private final Messages messages;
    private CombatTagManager combat;
    private DuelManager duels;
    private NewbieProtection newbies;
    private RespawnProtection respawn;
    private Consumer<Player> onChanged = player -> { };

    public ConsentService(PlayerDataStore data, CooldownService cooldowns, Supplier<Settings> settings,
                          Messages messages) {
        this.data = data;
        this.cooldowns = cooldowns;
        this.settings = settings;
        this.messages = messages;
    }

    /** Wires the services that depend on this one; called once during enable. */
    public void wire(CombatTagManager combat, DuelManager duels, NewbieProtection newbies,
                     RespawnProtection respawn, Consumer<Player> onChanged) {
        this.combat = combat;
        this.duels = duels;
        this.newbies = newbies;
        this.respawn = respawn;
        this.onChanged = onChanged;
    }

    public boolean hasConsent(UUID player) {
        return data.hasConsent(player);
    }

    /** Mutual consent, or a duel between exactly these two. Ignores respawn protection. */
    public boolean consentsTo(UUID attacker, UUID defender) {
        if (attacker.equals(defender)) {
            return false;
        }
        return (data.hasConsent(attacker) && data.hasConsent(defender)) || duels.isDueling(attacker, defender);
    }

    /** {@link #consentsTo}, and the defender is not respawn-protected. */
    public boolean canFight(UUID attacker, UUID defender) {
        return consentsTo(attacker, defender) && !respawn.isProtected(defender);
    }

    /** What happened to a toggle request. */
    public enum ToggleResult {
        CHANGED, ALREADY, NEWBIE, IN_COMBAT, COOLDOWN, CANCELLED
    }

    /**
     * A player asking to change their own consent. Runs every rule, tells them the outcome, and on
     * success applies the toggle cooldown. Call on the player's thread.
     */
    public ToggleResult requestToggle(Player player, boolean enable) {
        UUID id = player.getUniqueId();
        if (data.hasConsent(id) == enable) {
            messages.send(player, enable ? "already_enabled" : "already_disabled");
            return ToggleResult.ALREADY;
        }
        if (enable) {
            Duration newbieLeft = newbies.remaining(player);
            if (!newbieLeft.isZero()) {
                messages.send(player, "newbie_blocked", "time", Durations.compact(newbieLeft));
                return ToggleResult.NEWBIE;
            }
        } else if (combat.isTagged(id)) {
            messages.send(player, "combat_tagged_toggle", "time", Durations.compact(combat.remaining(id)));
            return ToggleResult.IN_COMBAT;
        }
        if (cooldowns.isOnCooldown(id, TOGGLE_COOLDOWN)) {
            messages.send(player, "on_cooldown", "time",
                    Durations.compact(cooldowns.remaining(id, TOGGLE_COOLDOWN)));
            return ToggleResult.COOLDOWN;
        }
        if (!change(player, enable, PvPConsentChangeEvent.Cause.COMMAND)) {
            messages.send(player, "toggle_cancelled");
            return ToggleResult.CANCELLED;
        }
        cooldowns.apply(id, TOGGLE_COOLDOWN, settings.get().toggleCooldown());
        messages.send(player, enable ? "pvp_enabled" : "pvp_disabled");
        return ToggleResult.CHANGED;
    }

    /**
     * Changes consent unconditionally apart from the {@link PvPConsentChangeEvent}, which may veto
     * it. For death and staff changes. {@code player} may be null for an offline player.
     *
     * @return true when the value changed
     */
    public boolean force(UUID id, Player player, boolean enable, PvPConsentChangeEvent.Cause cause) {
        if (data.hasConsent(id) == enable) {
            return false;
        }
        PvPConsentChangeEvent event = new PvPConsentChangeEvent(id, enable, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        data.setConsent(id, enable);
        if (player != null) {
            onChanged.accept(player);
        }
        return true;
    }

    private boolean change(Player player, boolean enable, PvPConsentChangeEvent.Cause cause) {
        return force(player.getUniqueId(), player, enable, cause);
    }

    public CooldownService cooldowns() {
        return cooldowns;
    }
}
