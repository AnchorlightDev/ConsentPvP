package org.modularsoft.consentpvp;

import dev.anchorlight.StoneLib.config.ConfigUpdater;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.modularsoft.consentpvp.commands.PVPCommand;
import org.modularsoft.consentpvp.events.PVPEventListener;
import org.modularsoft.consentpvp.util.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ConsentPVP extends JavaPlugin {

    private CooldownManager cooldownManager;
    private PVPManager pvpManager;
    private MessageManager messageManager;
    private EndCrystalManager endCrystalManager;
    private RespawnAnchorManager respawnAnchorManager;
    private LavaManager lavaManager;
    private FireManager fireManager;
    private NameTagManager nameTagManager;

    private MiniMessage miniMessage;
    private String messagePrefix;
    private boolean disablePvpOnDeath;
    private AttemptMessageDelivery attemptMessageDelivery;
    private boolean notifyDefenderOnDenial;
    private boolean indicatorsEnabled;

    @Override
    public void onEnable() {
        this.miniMessage = MiniMessage.miniMessage();

        // Initialize managers
        this.cooldownManager = new CooldownManager(this);
        this.pvpManager = new PVPManager(this);
        this.messageManager = new MessageManager(this);
        this.endCrystalManager = new EndCrystalManager();
        this.respawnAnchorManager = new RespawnAnchorManager();
        this.lavaManager = new LavaManager();
        this.fireManager = new FireManager();
        this.nameTagManager = new NameTagManager(this);

        // Load configuration
        ConfigUpdater.update(this, "config.yml");
        reloadConfig();
        reloadPluginConfig();


        // Register commands
        getCommand("pvp").setExecutor(new PVPCommand(this));
        getCommand("pvp").setTabCompleter(new org.modularsoft.consentpvp.commands.PVPTabCompleter());

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PVPEventListener(this), this);

        // Schedule periodic cleanup every 5 minutes (6000 ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            cooldownManager.cleanupExpiredCooldowns();
            pvpManager.cleanupOfflinePlayers();
        }, 6000L, 6000L);
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public PVPManager getPVPManager() {
        return pvpManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public EndCrystalManager getEndCrystalManager() {
        return endCrystalManager;
    }

    public RespawnAnchorManager getRespawnAnchorManager() {
        return respawnAnchorManager;
    }

    public LavaManager getLavaManager() {
        return lavaManager;
    }

    public FireManager getFireManager() {
        return fireManager;
    }

    public NameTagManager getNameTagManager() {
        return nameTagManager;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }

    public boolean isPvpDisabledOnDeath() {
        return disablePvpOnDeath;
    }

    public void setDisablePvpOnDeath(boolean disablePvpOnDeath) {
        this.disablePvpOnDeath = disablePvpOnDeath;
    }

    public AttemptMessageDelivery getAttemptMessageDelivery() {
        return attemptMessageDelivery;
    }

    public boolean shouldNotifyDefenderOnDenial() {
        return notifyDefenderOnDenial;
    }

    public boolean areIndicatorsEnabled() {
        return indicatorsEnabled;
    }

    @Override
    public void onDisable() {
        if (nameTagManager != null) {
            nameTagManager.cleanup();
        }
    }

    public void reloadPluginConfig() {
        applyConfigDefaults();
        reloadConfig();
        loadSettings();

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            pvpManager.loadConsentForPlayer(player);
        }

        if (nameTagManager != null) {
            nameTagManager.loadConfig();
            nameTagManager.updateAllPlayers();
        }
    }

    private void applyConfigDefaults() {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        try (InputStream configStream = getResource("config.yml")) {
            if (configStream == null) {
                return;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(configStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            config.save(configFile);
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to apply default configuration values", exception);
        }
    }

    private void loadSettings() {
        this.messagePrefix = getConfig().getString("messages.prefix", "<gray>[<red>ConsentPVP<gray>] <white>");
        this.disablePvpOnDeath = getConfig().getBoolean("pvp.disable-on-death", false);
        this.attemptMessageDelivery = AttemptMessageDelivery.fromConfig(
            getConfig().getString("messages.pvp_attempt_delivery", "chat")
        );
        this.notifyDefenderOnDenial = getConfig().getBoolean("messages.notify-defender-on-denial", false);
        this.indicatorsEnabled = getConfig().getBoolean("indicators.enabled", true);
    }
}
