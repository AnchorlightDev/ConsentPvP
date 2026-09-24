package org.modularsoft.consentpvp;

import dev.anchorlight.stonelib.bedrock.BedrockForms;
import dev.anchorlight.stonelib.combat.CombatTagService;
import dev.anchorlight.stonelib.config.VersionedConfig;
import dev.anchorlight.stonelib.cooldown.CooldownService;
import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.modularsoft.consentpvp.api.ConsentPvPAPI;
import org.modularsoft.consentpvp.attack.AttackerResolver;
import org.modularsoft.consentpvp.attack.DenialNotifier;
import org.modularsoft.consentpvp.attack.PotionFilter;
import org.modularsoft.consentpvp.combat.CombatTagManager;
import org.modularsoft.consentpvp.commands.PvpCommands;
import org.modularsoft.consentpvp.consent.ConsentApi;
import org.modularsoft.consentpvp.consent.ConsentService;
import org.modularsoft.consentpvp.data.PlayerDataStore;
import org.modularsoft.consentpvp.duel.DuelManager;
import org.modularsoft.consentpvp.listeners.CombatListener;
import org.modularsoft.consentpvp.listeners.CombatRestrictionListener;
import org.modularsoft.consentpvp.listeners.PlayerListener;
import org.modularsoft.consentpvp.listeners.TrackingListener;
import org.modularsoft.consentpvp.metrics.PluginMetrics;
import org.modularsoft.consentpvp.protection.NewbieProtection;
import org.modularsoft.consentpvp.protection.RespawnProtection;
import org.modularsoft.consentpvp.tracking.OwnershipTracker;
import org.modularsoft.consentpvp.ui.StatusPresenter;
import org.modularsoft.consentpvp.update.UpdateNotifier;
import org.modularsoft.consentpvp.util.Messages;
import org.modularsoft.consentpvp.util.NameTagManager;

import java.io.File;
import java.time.Duration;
import java.util.function.LongSupplier;

public class ConsentPVP extends JavaPlugin {

    /** Set by the test run: no bStats and no GitHub requests. */
    private static final boolean OFFLINE = Boolean.getBoolean("consentpvp.offline");
    /** How long playerdata.yml waits after a change before it is written. */
    private static final Duration SAVE_DEBOUNCE = Duration.ofSeconds(2);

    private PlatformScheduler scheduler;
    private VersionedConfig config;
    private volatile Settings settings;
    private Messages messages;
    private PlayerDataStore data;
    private ConsentService consent;
    private NewbieProtection newbies;
    private RespawnProtection respawn;
    private CombatTagManager combat;
    private DuelManager duels;
    private OwnershipTracker tracker;
    private NameTagManager nameTags;
    private StatusPresenter statusPresenter;
    private UpdateNotifier updates;
    private PluginMetrics metrics;
    private BedrockForms detectedForms = BedrockForms.none();
    private PlatformScheduler.Task cleanupTask;
    /** Every expiry (tags, duels, protection, ownership, throttles) reads this, so tests can move time. */
    private volatile LongSupplier clock = System::currentTimeMillis;

    @Override
    public void onEnable() {
        this.scheduler = new PlatformScheduler(this);
        this.config = new VersionedConfig(this, "config.yml");
        this.settings = Settings.from(config.config(), getLogger());
        this.messages = new Messages(() -> config.config().getConfigurationSection("messages"), scheduler,
                () -> settings.attemptDelivery());
        this.data = new PlayerDataStore(new File(getDataFolder(), "playerdata.yml"), getLogger(), SAVE_DEBOUNCE);
        this.detectedForms = BedrockForms.detect(this, scheduler);

        this.consent = new ConsentService(data, new CooldownService(), this::settings, messages);
        this.newbies = new NewbieProtection(this::settings, data);
        this.respawn = new RespawnProtection(this::now);
        this.combat = new CombatTagManager(new CombatTagService(this::now), scheduler, messages, this::settings);
        this.duels = new DuelManager(this::settings, messages, scheduler, newbies, this::bedrockForms,
                this::now);
        this.nameTags = new NameTagManager(config::config, consent::hasConsent, scheduler, getLogger());
        consent.wire(combat, duels, newbies, respawn,
                player -> scheduler.runOn(player, () -> nameTags.updatePlayer(player)));

        this.tracker = new OwnershipTracker(settings.ownershipExpiry(), this::now);
        AttackerResolver resolver = new AttackerResolver(tracker);
        DenialNotifier notifier = new DenialNotifier(messages, scheduler, this::settings, consent,
                this::bedrockForms, this::now);
        PotionFilter potions = new PotionFilter(() -> settings.harmfulEffects());
        this.statusPresenter = new StatusPresenter(consent, messages, this::bedrockForms);
        this.updates = new UpdateNotifier(messages, getLogger());

        PvpCommands commands = new PvpCommands(this);
        PluginCommand pvp = getCommand("pvp");
        if (pvp != null) {
            pvp.setExecutor((sender, command, label, args) -> commands.dispatch(sender, args));
            pvp.setTabCompleter((sender, command, alias, args) -> commands.complete(sender, args));
        }

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new CombatListener(resolver, consent, notifier, respawn, combat, potions, messages), this);
        plugins.registerEvents(new TrackingListener(tracker), this);
        plugins.registerEvents(new CombatRestrictionListener(combat, messages, this::settings), this);
        plugins.registerEvents(new PlayerListener(consent, data, combat, duels, respawn, statusPresenter, nameTags,
                updates, messages, scheduler, this::settings), this);

        // Tag and duel expiry: pure data plus messages, which hop to each player's own thread.
        scheduler.globalTimer(() -> {
            combat.sweep();
            duels.sweep();
        }, 20, 20);
        scheduleCleanup();

        getServer().getServicesManager().register(ConsentPvPAPI.class, new ConsentApi(consent, combat, duels),
                this, ServicePriority.Normal);

        if (!OFFLINE) {
            if (settings.metricsEnabled()) {
                metrics = new PluginMetrics();
                metrics.start(this, data::consentSnapshot, duels::startedInLastDay,
                        () -> bedrockForms().available());
            }
            if (settings.updateCheckerEnabled()) {
                updates.check(settings.updateRepository(), getPluginMeta().getVersion());
            }
        }
        nameTags.updateAllPlayers();
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        if (combat != null) {
            combat.shutdown();
        }
        if (duels != null) {
            duels.clearAll();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (data != null) {
            // Synchronous: the plugin's classes may be gone before an async write would run.
            data.close();
        }
        if (nameTags != null) {
            nameTags.cleanup();
        }
    }

    /**
     * Reloads config.yml. A file that does not parse is left untouched and the built-in defaults are
     * used until it is fixed.
     *
     * @return false when the file could not be parsed
     */
    public boolean reloadPluginConfig() {
        boolean healthy = config.reload();
        this.settings = Settings.from(config.config(), getLogger());
        messages.reload();
        tracker.setTtl(settings.ownershipExpiry());
        duels.reload();
        scheduleCleanup();
        nameTags.loadConfig();
        nameTags.updateAllPlayers();
        return healthy;
    }

    /** {@code /pvp death}: flips disable-on-death and saves it, comments intact. */
    public void setDisablePvpOnDeath(boolean value) {
        config.set("pvp.disable-on-death", value);
        this.settings = Settings.from(config.config(), getLogger());
    }

    private void scheduleCleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        Duration interval = settings.ownershipCleanupInterval();
        cleanupTask = scheduler.asyncTimer(tracker::purgeExpired, interval, interval);
    }

    // ------------------------------------------------------------- accessors

    public long now() {
        return clock.getAsLong();
    }

    /** For tests: replaces the clock every expiry reads. */
    public void setClock(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public Settings settings() {
        return settings;
    }

    /** Floodgate forms when Floodgate is installed and Bedrock support is enabled in config. */
    public BedrockForms bedrockForms() {
        return settings.bedrockEnabled() ? detectedForms : BedrockForms.none();
    }

    /** For tests: stands in for Floodgate. */
    public void setBedrockForms(BedrockForms forms) {
        this.detectedForms = forms == null ? BedrockForms.none() : forms;
    }

    public PlatformScheduler scheduler() {
        return scheduler;
    }

    public VersionedConfig configFile() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public PlayerDataStore data() {
        return data;
    }

    public ConsentService consent() {
        return consent;
    }

    public NewbieProtection newbies() {
        return newbies;
    }

    public RespawnProtection respawn() {
        return respawn;
    }

    public CombatTagManager combat() {
        return combat;
    }

    public DuelManager duels() {
        return duels;
    }

    public OwnershipTracker tracker() {
        return tracker;
    }

    public NameTagManager nameTags() {
        return nameTags;
    }

    public StatusPresenter statusPresenter() {
        return statusPresenter;
    }

    public UpdateNotifier updates() {
        return updates;
    }
}
