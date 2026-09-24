package org.modularsoft.consentpvp.listeners;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import dev.anchorlight.stonelib.time.Durations;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.modularsoft.consentpvp.Settings;
import org.modularsoft.consentpvp.combat.CombatTagManager;
import org.modularsoft.consentpvp.util.Messages;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * What a combat tag forbids: the configured teleport commands, any teleport a command or plugin
 * causes, and the escape items - ender pearls, chorus fruit, elytra flight and boosting, riptide.
 * Each item is switched separately in config.
 */
public final class CombatRestrictionListener implements Listener {

    private final CombatTagManager combat;
    private final Messages messages;
    private final Supplier<Settings> settings;

    public CombatRestrictionListener(CombatTagManager combat, Messages messages, Supplier<Settings> settings) {
        this.combat = combat;
        this.messages = messages;
        this.settings = settings;
    }

    private boolean tagged(Player player) {
        return combat.isTagged(player.getUniqueId());
    }

    private String remaining(Player player) {
        return Durations.compact(combat.remaining(player.getUniqueId()).plusMillis(999));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!tagged(player)) {
            return;
        }
        String label = commandLabel(event.getMessage());
        if (label != null && settings.get().blockedCommands().contains(label)) {
            event.setCancelled(true);
            messages.send(player, "combat_command_blocked", "command", label, "time", remaining(player));
        }
    }

    /**
     * {@code "/Essentials:Home base"} to {@code "home"}: lower case, slash and namespace removed.
     */
    static String commandLabel(String message) {
        if (message == null || message.length() < 2) {
            return null;
        }
        String body = message.charAt(0) == '/' ? message.substring(1) : message;
        int space = body.indexOf(' ');
        String label = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    /**
     * Catches what the command list cannot: aliases, plugin menus, and other plugins teleporting a
     * tagged player on their behalf. Pearls and chorus fruit are caught here too, for a pearl that
     * was already in the air when the tag began.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!tagged(player)) {
            return;
        }
        Settings current = settings.get();
        String cause = event.getCause().name();
        switch (cause) {
            case "COMMAND", "PLUGIN" -> {
                if (current.blockCommandTeleports()) {
                    event.setCancelled(true);
                    messages.send(player, "combat_teleport_blocked", "time", remaining(player));
                }
            }
            case "ENDER_PEARL" -> {
                if (current.blockEnderPearls()) {
                    event.setCancelled(true);
                    messages.send(player, "combat_pearl_blocked");
                }
            }
            case "CHORUS_FRUIT", "CONSUMABLE_EFFECT" -> {
                if (current.blockChorusFruit()) {
                    event.setCancelled(true);
                    messages.send(player, "combat_chorus_blocked");
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearl(PlayerLaunchProjectileEvent event) {
        if (!(event.getProjectile() instanceof EnderPearl) || !settings.get().blockEnderPearls()
                || !tagged(event.getPlayer())) {
            return;
        }
        event.setShouldConsume(false);
        event.setCancelled(true);
        messages.send(event.getPlayer(), "combat_pearl_blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.CHORUS_FRUIT || !settings.get().blockChorusFruit()
                || !tagged(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "combat_chorus_blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player player)
                || !settings.get().blockElytra() || !tagged(player)) {
            return;
        }
        event.setCancelled(true);
        messages.send(player, "combat_elytra_blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent event) {
        if (!settings.get().blockElytra() || !tagged(event.getPlayer())) {
            return;
        }
        event.setShouldConsume(false);
        event.setCancelled(true);
        messages.send(event.getPlayer(), "combat_elytra_blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        if (!settings.get().blockRiptide() || !tagged(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "combat_riptide_blocked");
    }
}
