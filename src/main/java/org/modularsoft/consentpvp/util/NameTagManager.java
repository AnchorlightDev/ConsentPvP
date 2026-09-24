package org.modularsoft.consentpvp.util;

import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The ⚔ prefix above each player's head showing their PvP status, through two scoreboard teams.
 *
 * <p>Folia does not support scoreboards, so on Folia the indicators are switched off with one log
 * line and every call here is a no-op.
 */
public final class NameTagManager {

    private static final String TEAM_ON = "CPVP_ON";
    private static final String TEAM_OFF = "CPVP_OFF";

    private final Supplier<FileConfiguration> config;
    private final Predicate<UUID> hasConsent;
    private final PlatformScheduler scheduler;
    private final Scoreboard scoreboard;
    private boolean enabled;
    private boolean force;

    public NameTagManager(Supplier<FileConfiguration> config, Predicate<UUID> hasConsent,
                          PlatformScheduler scheduler, Logger logger) {
        this.config = config;
        this.hasConsent = hasConsent;
        this.scheduler = scheduler;
        if (PlatformScheduler.isFolia()) {
            logger.info("PvP name tag indicators are unavailable on Folia, which does not support scoreboards.");
            this.scoreboard = null;
        } else {
            this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration current = config.get();
        this.enabled = scoreboard != null && current.getBoolean("indicators.enabled", true);
        this.force = current.getBoolean("indicators.force", false);
        if (scoreboard == null) {
            return;
        }
        MiniMessage mini = MiniMessage.miniMessage();
        setupTeam(TEAM_ON, mini.deserialize(current.getString("indicators.pvp-enabled-prefix", "<green>⚔ </green>")));
        setupTeam(TEAM_OFF, mini.deserialize(current.getString("indicators.pvp-disabled-prefix", "<red>⚔ </red>")));
    }

    private void setupTeam(String teamName, Component prefix) {
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.prefix(prefix);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
    }

    public void updatePlayer(Player player) {
        if (scoreboard == null) {
            return;
        }
        if (!enabled) {
            removePlayer(player);
            return;
        }
        String entry = player.getName();
        Team currentTeam = scoreboard.getEntryTeam(entry);
        if (!force && currentTeam != null && !currentTeam.getName().startsWith("CPVP_")) {
            // Another plugin owns this player's team; do not take it over unless forced.
            return;
        }
        boolean consent = hasConsent.test(player.getUniqueId());
        Team target = scoreboard.getTeam(consent ? TEAM_ON : TEAM_OFF);
        Team other = scoreboard.getTeam(consent ? TEAM_OFF : TEAM_ON);
        if (other != null && other.hasEntry(entry)) {
            other.removeEntry(entry);
        }
        if (target != null && !target.hasEntry(entry)) {
            target.addEntry(entry);
        }
    }

    public void removePlayer(Player player) {
        if (scoreboard == null) {
            return;
        }
        for (String name : new String[]{TEAM_ON, TEAM_OFF}) {
            Team team = scoreboard.getTeam(name);
            if (team != null && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    public void updateAllPlayers() {
        if (scoreboard == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runOn(player, () -> updatePlayer(player));
        }
    }

    public void cleanup() {
        if (scoreboard == null) {
            return;
        }
        for (String name : new String[]{TEAM_ON, TEAM_OFF}) {
            Team team = scoreboard.getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
