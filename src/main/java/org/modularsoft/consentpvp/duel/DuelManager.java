package org.modularsoft.consentpvp.duel;

import dev.anchorlight.stonelib.bedrock.BedrockForms;
import dev.anchorlight.stonelib.invite.InviteRegistry;
import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import dev.anchorlight.stonelib.time.Durations;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.modularsoft.consentpvp.Settings;
import org.modularsoft.consentpvp.protection.NewbieProtection;
import org.modularsoft.consentpvp.util.Messages;

import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * One-off fights between two players who have not otherwise consented.
 *
 * <p>An accepted duel gives those two players mutual consent with each other only, without touching
 * either player's own setting, and ends on death, logout or a time limit. Requests expire, a player
 * has at most one open request, and the same target cannot be challenged again until a resend
 * cooldown has passed - the anti-spam rules come from StoneLib's {@link InviteRegistry}.
 */
public final class DuelManager {

    /** An active duel. */
    public record Duel(UUID first, UUID second, long startedAt, long endsAt) {
        public UUID partnerOf(UUID player) {
            return first.equals(player) ? second : first;
        }
    }

    private final InviteRegistry invites;
    private final Map<UUID, Duel> active = new ConcurrentHashMap<>();
    private final Deque<Long> recentStarts = new ConcurrentLinkedDeque<>();
    private final Supplier<Settings> settings;
    private final Messages messages;
    private final PlatformScheduler scheduler;
    private final NewbieProtection newbies;
    private final Supplier<BedrockForms> forms;
    private final LongSupplier clock;

    public DuelManager(Supplier<Settings> settings, Messages messages, PlatformScheduler scheduler,
                       NewbieProtection newbies, Supplier<BedrockForms> forms, LongSupplier clock) {
        this.settings = settings;
        this.messages = messages;
        this.scheduler = scheduler;
        this.newbies = newbies;
        this.forms = forms;
        this.clock = clock;
        Settings current = settings.get();
        this.invites = new InviteRegistry(current.duelRequestTimeout(), current.duelResendCooldown(), clock);
    }

    public void reload() {
        Settings current = settings.get();
        invites.configure(current.duelRequestTimeout(), current.duelResendCooldown());
    }

    // ------------------------------------------------------------ queries

    public boolean isDueling(UUID first, UUID second) {
        Duel duel = active.get(first);
        return duel != null && duel.partnerOf(first).equals(second) && duel.endsAt() > clock.getAsLong();
    }

    public Optional<Duel> duelOf(UUID player) {
        Duel duel = active.get(player);
        return duel != null && duel.endsAt() > clock.getAsLong() ? Optional.of(duel) : Optional.empty();
    }

    /** Duels started in the last 24 hours, for metrics. */
    public int startedInLastDay() {
        long cutoff = clock.getAsLong() - Duration.ofDays(1).toMillis();
        recentStarts.removeIf(started -> started < cutoff);
        return recentStarts.size();
    }

    // ----------------------------------------------------------- commands

    /** {@code /pvp duel <target>}. Call on the sender's thread. */
    public void request(Player sender, Player target) {
        Settings current = settings.get();
        if (!current.duelsEnabled()) {
            messages.send(sender, "duel_disabled");
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            messages.send(sender, "duel_self");
            return;
        }
        if (duelOf(sender.getUniqueId()).isPresent()) {
            messages.send(sender, "duel_you_are_dueling");
            return;
        }
        Duration senderNewbie = newbies.remaining(sender);
        if (!senderNewbie.isZero()) {
            messages.send(sender, "duel_newbie_self", "time", Durations.compact(senderNewbie));
            return;
        }
        String targetName = target.getName();
        // The target's playtime belongs to the target's thread.
        scheduler.runOn(target, () -> {
            if (!target.isOnline()) {
                return;
            }
            if (newbies.isProtected(target)) {
                messages.send(sender, "duel_newbie_target", "player", targetName);
                return;
            }
            if (duelOf(target.getUniqueId()).isPresent()) {
                messages.send(sender, "duel_already_dueling", "player", targetName);
                return;
            }
            switch (invites.send(sender.getUniqueId(), target.getUniqueId())) {
                case SELF -> messages.send(sender, "duel_self");
                case ALREADY_PENDING -> messages.send(sender, "duel_already_pending", "player", targetName);
                case TOO_SOON -> messages.send(sender, "duel_too_soon", "player", targetName, "time",
                        Durations.compact(invites.resendRemaining(sender.getUniqueId(), target.getUniqueId())));
                case SENT -> {
                    String expires = Durations.compact(current.duelRequestTimeout());
                    messages.send(sender, "duel_sent", "player", targetName, "time", expires);
                    offer(target, sender.getName(), expires);
                }
            }
        });
    }

    /** Shows the request to the target: a form on Bedrock, buttons in chat otherwise. */
    private void offer(Player target, String senderName, String expires) {
        BedrockForms bedrock = forms.get();
        if (bedrock.isBedrock(target.getUniqueId())) {
            boolean sent = bedrock.sendModal(target,
                    messages.bedrock("bedrock_duel_title"),
                    messages.bedrock("bedrock_duel_content", "player", senderName),
                    messages.bedrock("bedrock_button_accept"),
                    messages.bedrock("bedrock_button_deny"),
                    accepted -> {
                        if (accepted) {
                            accept(target, senderName);
                        } else {
                            deny(target, senderName);
                        }
                    });
            if (sent) {
                messages.send(target, "duel_received", "player", senderName, "time", expires);
                return;
            }
        }
        Component buttons = messages.buttonRow(
                messages.button("button_accept", "button_accept_hover", "/pvp accept " + senderName),
                messages.button("button_deny", "button_deny_hover", "/pvp deny " + senderName));
        messages.sendWith(target, "duel_received", buttons, "player", senderName, "time", expires);
    }

    /** {@code /pvp accept [player]}. Call on the accepting player's thread. */
    public void accept(Player target, String senderName) {
        Optional<InviteRegistry.Invite> invite = invites.take(target.getUniqueId(), idOf(senderName));
        if (invite.isEmpty()) {
            messages.send(target, "duel_no_request");
            return;
        }
        Player sender = Bukkit.getPlayer(invite.get().sender());
        if (sender == null) {
            messages.send(target, "duel_no_request");
            return;
        }
        if (duelOf(target.getUniqueId()).isPresent()) {
            messages.send(target, "duel_you_are_dueling");
            return;
        }
        if (duelOf(sender.getUniqueId()).isPresent()) {
            messages.send(target, "duel_already_dueling", "player", sender.getName());
            return;
        }
        long now = clock.getAsLong();
        Duration length = settings.get().duelMaxDuration();
        Duel duel = new Duel(sender.getUniqueId(), target.getUniqueId(), now, now + length.toMillis());
        active.put(sender.getUniqueId(), duel);
        active.put(target.getUniqueId(), duel);
        recentStarts.add(now);
        invites.clear(sender.getUniqueId());
        invites.clear(target.getUniqueId());
        String time = Durations.compact(length);
        messages.send(sender, "duel_started", "player", target.getName(), "time", time);
        messages.send(target, "duel_started", "player", sender.getName(), "time", time);
    }

    /** {@code /pvp deny [player]}. Call on the denying player's thread. */
    public void deny(Player target, String senderName) {
        Optional<InviteRegistry.Invite> invite = invites.take(target.getUniqueId(), idOf(senderName));
        if (invite.isEmpty()) {
            messages.send(target, "duel_no_request");
            return;
        }
        Player sender = Bukkit.getPlayer(invite.get().sender());
        String name = sender != null ? sender.getName() : String.valueOf(senderName);
        messages.send(target, "duel_denied_target", "player", name);
        if (sender != null) {
            messages.send(sender, "duel_denied_sender", "player", target.getName());
        }
    }

    // ---------------------------------------------------------- lifecycle

    /** Ends the player's duel, telling both players. */
    public void end(UUID player) {
        Duel duel = active.remove(player);
        if (duel == null) {
            return;
        }
        UUID partner = duel.partnerOf(player);
        active.remove(partner, duel);
        notifyEnded(player, partner);
        notifyEnded(partner, player);
    }

    public void onQuit(UUID player) {
        invites.clear(player);
        end(player);
    }

    public void onDeath(UUID player) {
        end(player);
    }

    /** Expires requests and ends timed-out duels. Call once a second. */
    public void sweep() {
        for (InviteRegistry.Invite expired : invites.purgeExpired()) {
            Player sender = Bukkit.getPlayer(expired.sender());
            Player target = Bukkit.getPlayer(expired.recipient());
            if (sender != null) {
                messages.send(sender, "duel_expired_sender", "player",
                        target != null ? target.getName() : expired.recipient().toString());
            }
        }
        long now = clock.getAsLong();
        for (Duel duel : Map.copyOf(active).values()) {
            if (duel.endsAt() <= now) {
                end(duel.first());
            }
        }
    }

    public void clearAll() {
        active.clear();
        invites.clearAll();
    }

    private void notifyEnded(UUID player, UUID partner) {
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        Player other = Bukkit.getPlayer(partner);
        messages.send(online, "duel_ended", "player",
                other != null ? other.getName() : Bukkit.getOfflinePlayer(partner).getName());
    }

    private static UUID idOf(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? new UUID(0, 0) : online.getUniqueId();
    }
}
