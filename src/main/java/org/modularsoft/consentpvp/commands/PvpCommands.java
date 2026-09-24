package org.modularsoft.consentpvp.commands;

import dev.anchorlight.stonelib.command.CommandRouter;
import dev.anchorlight.stonelib.command.SubCommand;
import dev.anchorlight.stonelib.time.Durations;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.modularsoft.consentpvp.ConsentPVP;
import org.modularsoft.consentpvp.api.PvPConsentChangeEvent;
import org.modularsoft.consentpvp.consent.ConsentService;
import org.modularsoft.consentpvp.duel.DuelManager;
import org.modularsoft.consentpvp.util.Messages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * {@code /pvp} and its sub-commands, routed through StoneLib's {@link CommandRouter}.
 *
 * <p>Every reply comes from config, including the router's own usage, unknown-option,
 * no-permission and players-only answers. Tab completion only offers what the sender may run, and
 * staff commands work from the console.
 */
public final class PvpCommands {

    public static final String ADMIN = "consentpvp.admin";
    public static final String DUEL = "consentpvp.duel";

    private final ConsentPVP plugin;
    private final Messages messages;
    private final CommandRouter router;

    public PvpCommands(ConsentPVP plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.router = new CommandRouter(plugin, "pvp").feedback(new CommandRouter.Feedback() {
            @Override
            public void usage(CommandSender sender, String rootLabel, Collection<String> names) {
                messages.send(sender, "usage", "commands", String.join("|", names));
            }

            @Override
            public void unknown(CommandSender sender, String rootLabel, String input) {
                messages.send(sender, "unknown_subcommand", "input", input,
                        "commands", String.join(", ", router().visibleNames(sender)));
            }

            @Override
            public void noPermission(CommandSender sender, SubCommand sub) {
                messages.send(sender, "no_permission");
            }

            @Override
            public void playersOnly(CommandSender sender, SubCommand sub) {
                messages.send(sender, "players_only");
            }
        });

        SubCommand status = player("status", null, (sender, args) -> plugin.statusPresenter().showStatus((Player) sender));
        router.onNoArguments(status);
        router.register(status);
        router.register(player("enable", null, (sender, args) -> plugin.consent().requestToggle((Player) sender, true)));
        router.register(player("disable", null, (sender, args) -> plugin.consent().requestToggle((Player) sender, false)));
        router.register(new Duel());
        router.register(new Answer("accept", true));
        router.register(new Answer("deny", false));
        router.register(new Bypass());
        router.register(new Check());
        router.register(new SetConsent());
        router.register(new Newbie());
        router.register(simple("reload", ADMIN, (sender, args) -> {
            boolean healthy = plugin.reloadPluginConfig();
            messages.send(sender, healthy ? "config_reloaded" : "config_reload_failed");
        }));
        router.register(simple("death", ADMIN, (sender, args) -> {
            boolean value = !plugin.settings().disablePvpOnDeath();
            plugin.setDisablePvpOnDeath(value);
            messages.send(sender, "pvp_death_toggle", "status",
                    plugin.statusPresenter().statusWord(value));
        }));
    }

    private CommandRouter router() {
        return router;
    }

    public boolean dispatch(CommandSender sender, String[] args) {
        return router.dispatch(sender, args);
    }

    public List<String> complete(CommandSender sender, String[] args) {
        List<String> out = router.tabComplete(sender, args);
        return out == null ? Collections.emptyList() : out;
    }

    // -------------------------------------------------------------- helpers

    private static SubCommand simple(String name, String permission, BiConsumer<CommandSender, String[]> body) {
        return new SubCommand() {
            @Override public String getName() { return name; }
            @Override public String getPermission() { return permission; }
            @Override public void execute(CommandSender sender, String[] args) { body.accept(sender, args); }
        };
    }

    private static SubCommand player(String name, String permission, BiConsumer<CommandSender, String[]> body) {
        return new SubCommand() {
            @Override public String getName() { return name; }
            @Override public String getPermission() { return permission; }
            @Override public boolean playerOnly() { return true; }
            @Override public void execute(CommandSender sender, String[] args) { body.accept(sender, args); }
        };
    }

    private static List<String> onlineNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return StringUtil.copyPartialMatches(prefix, names, new ArrayList<>());
    }

    /** An exactly-named online player, or an offline one the server has seen. */
    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private abstract static class Targeted implements SubCommand {
        @Override public String getPermission() { return ADMIN; }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return args.length == 1 ? onlineNames(args[0]) : Collections.emptyList();
        }
    }

    // ------------------------------------------------------------ commands

    private final class Duel implements SubCommand {
        @Override public String getName() { return "duel"; }
        @Override public String getPermission() { return DUEL; }
        @Override public boolean playerOnly() { return true; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                messages.send(sender, "usage_duel");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !((Player) sender).canSee(target)) {
                messages.send(sender, "player_not_found", "player", args[0]);
                return;
            }
            plugin.duels().request((Player) sender, target);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return args.length == 1 ? onlineNames(args[0]) : Collections.emptyList();
        }
    }

    private final class Answer implements SubCommand {
        private final String name;
        private final boolean accept;

        Answer(String name, boolean accept) {
            this.name = name;
            this.accept = accept;
        }

        @Override public String getName() { return name; }
        @Override public String getPermission() { return DUEL; }
        @Override public boolean playerOnly() { return true; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            DuelManager duels = plugin.duels();
            String from = args.length > 0 ? args[0] : null;
            if (accept) {
                duels.accept((Player) sender, from);
            } else {
                duels.deny((Player) sender, from);
            }
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            return args.length == 1 ? onlineNames(args[0]) : Collections.emptyList();
        }
    }

    /** Clears the toggle cooldown and the combat tag. */
    private final class Bypass extends Targeted {
        @Override public String getName() { return "bypass"; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                messages.send(sender, "usage_bypass");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "player_not_found", "player", args[0]);
                return;
            }
            plugin.consent().cooldowns().clear(target.getUniqueId(), ConsentService.TOGGLE_COOLDOWN);
            plugin.combat().clear(target.getUniqueId());
            messages.send(sender, "bypass_cooldown_sender", "player", target.getName());
            messages.send(target, "bypass_cooldown_target");
        }
    }

    private final class Check extends Targeted {
        @Override public String getName() { return "check"; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                messages.send(sender, "usage_check");
                return;
            }
            OfflinePlayer target = findPlayer(args[0]);
            if (target == null) {
                messages.send(sender, "player_not_found", "player", args[0]);
                return;
            }
            UUID id = target.getUniqueId();
            String none = plainNone();
            Component consent = plugin.statusPresenter().statusWord(plugin.consent().hasConsent(id));
            Duration cooldown = plugin.consent().cooldowns().remaining(id, ConsentService.TOGGLE_COOLDOWN);
            Duration combat = plugin.combat().remaining(id);
            String duel = plugin.duels().duelOf(id)
                    .map(d -> {
                        OfflinePlayer partner = Bukkit.getOfflinePlayer(d.partnerOf(id));
                        return partner.getName() == null ? d.partnerOf(id).toString() : partner.getName();
                    })
                    .orElse(none);
            String name = target.getName() == null ? args[0] : target.getName();
            Player online = target.getPlayer();
            if (online == null) {
                send(sender, name, consent, cooldown, combat, duel, none);
                return;
            }
            // Playtime is the target's own state, so it is read on the target's thread.
            plugin.scheduler().runOn(online, () -> {
                Duration newbie = plugin.newbies().remaining(online);
                send(sender, name, consent, cooldown, combat, duel,
                        newbie.isZero() ? none : Durations.compact(newbie));
            });
        }

        private void send(CommandSender sender, String name, Component consent, Duration cooldown, Duration combat,
                          String duel, String newbie) {
            String none = plainNone();
            messages.send(sender, "check_output",
                    "player", name,
                    "consent", consent,
                    "cooldown", cooldown.isZero() ? none : Durations.compact(cooldown),
                    "combat", combat.isZero() ? none : Durations.compact(combat),
                    "duel", duel,
                    "newbie", newbie);
        }

        private String plainNone() {
            return dev.anchorlight.stonelib.message.MiniMessages.plain(messages.plain("check_none"));
        }
    }

    private final class SetConsent extends Targeted {
        @Override public String getName() { return "set"; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                messages.send(sender, "usage_set");
                return;
            }
            String state = args[1].toLowerCase(Locale.ROOT);
            if (!state.equals("on") && !state.equals("off")) {
                messages.send(sender, "usage_set");
                return;
            }
            OfflinePlayer target = findPlayer(args[0]);
            if (target == null) {
                messages.send(sender, "player_not_found", "player", args[0]);
                return;
            }
            boolean enable = state.equals("on");
            UUID id = target.getUniqueId();
            Player online = target.getPlayer();
            boolean already = plugin.consent().hasConsent(id) == enable;
            if (!already && !plugin.consent().force(id, online, enable, PvPConsentChangeEvent.Cause.ADMIN)) {
                messages.send(sender, "toggle_cancelled");
                return;
            }
            Component word = plugin.statusPresenter().statusWord(enable);
            String name = target.getName() == null ? args[0] : target.getName();
            messages.send(sender, "set_sender", "player", name, "status", word);
            if (online != null && !already) {
                messages.send(online, "set_target", "status", word);
            }
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return onlineNames(args[0]);
            }
            if (args.length == 2) {
                return StringUtil.copyPartialMatches(args[1], List.of("on", "off"), new ArrayList<>());
            }
            return Collections.emptyList();
        }
    }

    private final class Newbie extends Targeted {
        @Override public String getName() { return "newbie"; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2 || !args[0].equalsIgnoreCase("clear")) {
                messages.send(sender, "usage_newbie");
                return;
            }
            OfflinePlayer target = findPlayer(args[1]);
            if (target == null) {
                messages.send(sender, "player_not_found", "player", args[1]);
                return;
            }
            plugin.data().clearNewbie(target.getUniqueId());
            messages.send(sender, "newbie_cleared_sender", "player",
                    target.getName() == null ? args[1] : target.getName());
            if (target.getPlayer() != null) {
                messages.send(target.getPlayer(), "newbie_cleared_target");
            }
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], List.of("clear"), new ArrayList<>());
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
                return onlineNames(args[1]);
            }
            return Collections.emptyList();
        }
    }
}
