package org.modularsoft.consentpvp.listeners;

import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import dev.anchorlight.stonelib.time.Durations;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.modularsoft.consentpvp.Settings;
import org.modularsoft.consentpvp.api.PvPConsentChangeEvent;
import org.modularsoft.consentpvp.combat.CombatTagManager;
import org.modularsoft.consentpvp.consent.ConsentService;
import org.modularsoft.consentpvp.data.PlayerDataStore;
import org.modularsoft.consentpvp.duel.DuelManager;
import org.modularsoft.consentpvp.protection.RespawnProtection;
import org.modularsoft.consentpvp.ui.StatusPresenter;
import org.modularsoft.consentpvp.update.UpdateNotifier;
import org.modularsoft.consentpvp.util.Messages;
import org.modularsoft.consentpvp.util.NameTagManager;

import java.util.function.Supplier;

/** Join, quit, death and respawn. */
public final class PlayerListener implements Listener {

    /** Wait a moment after joining so the explainer is not lost under the join spam. */
    private static final long EXPLAINER_DELAY_TICKS = 40;

    private final ConsentService consent;
    private final PlayerDataStore data;
    private final CombatTagManager combat;
    private final DuelManager duels;
    private final RespawnProtection respawn;
    private final StatusPresenter status;
    private final NameTagManager nameTags;
    private final UpdateNotifier updates;
    private final Messages messages;
    private final PlatformScheduler scheduler;
    private final Supplier<Settings> settings;

    public PlayerListener(ConsentService consent, PlayerDataStore data, CombatTagManager combat, DuelManager duels,
                          RespawnProtection respawn, StatusPresenter status, NameTagManager nameTags,
                          UpdateNotifier updates, Messages messages, PlatformScheduler scheduler,
                          Supplier<Settings> settings) {
        this.consent = consent;
        this.data = data;
        this.combat = combat;
        this.duels = duels;
        this.respawn = respawn;
        this.status = status;
        this.nameTags = nameTags;
        this.updates = updates;
        this.messages = messages;
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        nameTags.updatePlayer(player);
        if (settings.get().firstJoinEnabled() && data.markExplainerSeen(player.getUniqueId())) {
            scheduler.entityLater(player, () -> status.explain(player), EXPLAINER_DELAY_TICKS);
        }
        updates.notifyOnJoin(player);
    }

    /** Logging out in combat carries no penalty; the tag and any duel simply end. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        combat.clear(player.getUniqueId());
        duels.onQuit(player.getUniqueId());
        respawn.end(player.getUniqueId());
        nameTags.removePlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        combat.clear(player.getUniqueId());
        duels.onDeath(player.getUniqueId());
        if (settings.get().disablePvpOnDeath()
                && consent.force(player.getUniqueId(), player, false, PvPConsentChangeEvent.Cause.DEATH)) {
            messages.send(player, "pvp_disabled_on_death");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Settings current = settings.get();
        if (!current.respawnProtectionEnabled() || current.respawnProtection().isZero()) {
            return;
        }
        Player player = event.getPlayer();
        respawn.protect(player.getUniqueId(), current.respawnProtection());
        messages.send(player, "respawn_protection_start", "time", Durations.compact(current.respawnProtection()));
    }
}
