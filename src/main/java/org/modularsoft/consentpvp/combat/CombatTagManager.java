package org.modularsoft.consentpvp.combat;

import dev.anchorlight.stonelib.combat.CombatTagService;
import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import dev.anchorlight.stonelib.sound.Sounds;
import dev.anchorlight.stonelib.time.Durations;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.modularsoft.consentpvp.Settings;
import org.modularsoft.consentpvp.util.Messages;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The combat tag as players experience it: who is tagged (via StoneLib's {@link CombatTagService}),
 * plus the live timer, the enter and leave sounds and messages, and cutting off an elytra flight.
 *
 * <p>Everything that touches a player runs on that player's own thread through the
 * {@link PlatformScheduler}, since a tag is often applied from the other fighter's thread.
 */
public final class CombatTagManager {

    private final CombatTagService tags;
    private final PlatformScheduler scheduler;
    private final Messages messages;
    private final Supplier<Settings> settings;
    private final Map<UUID, PlatformScheduler.Task> timers = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public CombatTagManager(CombatTagService tags, PlatformScheduler scheduler, Messages messages,
                            Supplier<Settings> settings) {
        this.tags = tags;
        this.scheduler = scheduler;
        this.messages = messages;
        this.settings = settings;
        tags.onEnter(id -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                scheduler.runOn(player, () -> entered(player));
            }
        });
        tags.onLeave((id, reason) -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                scheduler.runOn(player, () -> left(player, reason));
            } else {
                stopDisplay(id, null);
            }
        });
    }

    /** Tags both fighters. Safe from either fighter's thread. */
    public void tagPair(Player first, Player second) {
        tag(first);
        tag(second);
    }

    public void tag(Player player) {
        Settings current = settings.get();
        if (player == null || !current.combatTagEnabled()) {
            return;
        }
        tags.tag(player.getUniqueId(), current.combatTagDuration());
    }

    public boolean isTagged(UUID player) {
        return tags.isTagged(player);
    }

    public Duration remaining(UUID player) {
        return tags.remaining(player);
    }

    /** Ends a tag now: death, logout, a staff bypass. */
    public boolean clear(UUID player) {
        return tags.untag(player);
    }

    /** Expires finished tags. Call once a second. */
    public void sweep() {
        tags.expireDue();
    }

    public void shutdown() {
        for (UUID id : Map.copyOf(timers).keySet()) {
            // On Folia, disable runs off every player's thread; their bars go with the connection.
            stopDisplay(id, PlatformScheduler.isFolia() ? null : Bukkit.getPlayer(id));
        }
        tags.clearAll();
    }

    private void entered(Player player) {
        Settings current = settings.get();
        if (current.combatSounds()) {
            Sounds.toPlayer(player, current.combatEnterSound(), current.soundVolume(), current.soundPitch());
        }
        messages.send(player, "combat_enter", "time", Durations.compact(current.combatTagDuration()));
        if (current.blockElytra() && player.isGliding()) {
            player.setGliding(false);
            messages.send(player, "combat_elytra_blocked");
        }
        startDisplay(player);
    }

    private void left(Player player, CombatTagService.LeaveReason reason) {
        stopDisplay(player.getUniqueId(), player);
        if (!player.isOnline()) {
            return;
        }
        Settings current = settings.get();
        if (current.combatSounds()) {
            Sounds.toPlayer(player, current.combatLeaveSound(), current.soundVolume(), current.soundPitch());
        }
        messages.send(player, "combat_leave");
    }

    private void startDisplay(Player player) {
        Settings.CombatDisplay display = settings.get().combatDisplay();
        if (display == Settings.CombatDisplay.NONE) {
            return;
        }
        UUID id = player.getUniqueId();
        PlatformScheduler.Task previous = timers.remove(id);
        if (previous != null) {
            previous.cancel();
        }
        render(player, display);
        timers.put(id, scheduler.entityTimer(player, () -> render(player, display),
                () -> stopDisplay(id, null), 20, 20));
    }

    private void render(Player player, Settings.CombatDisplay display) {
        UUID id = player.getUniqueId();
        long left = tags.remainingMillis(id);
        if (left <= 0) {
            return;
        }
        String time = Durations.compact(Duration.ofMillis(left + 999));
        if (display == Settings.CombatDisplay.ACTION_BAR) {
            player.sendActionBar(messages.plain("combat_timer", "time", time));
            return;
        }
        float progress = (float) Math.min(1.0, left / (double) Math.max(1, settings.get().combatTagDuration().toMillis()));
        BossBar bar = bars.computeIfAbsent(id, ignored -> {
            BossBar created = BossBar.bossBar(messages.plain("combat_timer", "time", time), progress,
                    BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bar.name(messages.plain("combat_timer", "time", time));
        bar.progress(progress);
    }

    private void stopDisplay(UUID id, Player player) {
        PlatformScheduler.Task timer = timers.remove(id);
        if (timer != null) {
            timer.cancel();
        }
        BossBar bar = bars.remove(id);
        if (player != null) {
            if (bar != null) {
                player.hideBossBar(bar);
            }
            if (settings.get().combatDisplay() == Settings.CombatDisplay.ACTION_BAR) {
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
            }
        }
    }

    /** Whether a live display is running for the player. For tests. */
    public boolean hasDisplay(UUID player) {
        return timers.containsKey(player) || bars.containsKey(player);
    }

    public boolean hasBossBar(UUID player) {
        return bars.containsKey(player);
    }
}
