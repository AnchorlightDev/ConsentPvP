package org.modularsoft.consentpvp.data;

import dev.anchorlight.stonelib.yaml.DebouncedFileWriter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Per-player state in playerdata.yml, held in thread-safe maps and written off the server thread.
 *
 * <h2>File format</h2>
 * Consent stays exactly where every earlier version put it, as a root-level {@code <uuid>: true}
 * entry, so an existing file loads unchanged and a downgrade still reads it. Newer state lives
 * under named sections, which older versions skip because the key is not a UUID:
 *
 * <pre>
 * 0f0e...: true
 * explainer-seen:
 *   0f0e...: true
 * newbie-cleared:
 *   0f0e...: true
 * </pre>
 *
 * <h2>Saving</h2>
 * A change marks the store dirty and a {@link DebouncedFileWriter} writes it shortly afterwards
 * on its own thread, so a burst of toggles is one write and no tick waits on disk. The content is
 * serialised from the maps on that thread; {@link #close()} flushes synchronously on disable.
 */
public final class PlayerDataStore implements AutoCloseable {

    static final String EXPLAINER_SEEN = "explainer-seen";
    static final String NEWBIE_CLEARED = "newbie-cleared";

    private final Map<UUID, Boolean> consent = new ConcurrentHashMap<>();
    private final Set<UUID> explainerSeen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> newbieCleared = ConcurrentHashMap.newKeySet();
    private final File file;
    private final Logger logger;
    private final DebouncedFileWriter writer;

    public PlayerDataStore(File file, Logger logger, Duration debounce) {
        this.file = file;
        this.logger = logger;
        this.writer = new DebouncedFileWriter(file, this::serialise, debounce, logger);
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            // Keep the unreadable file for the admin rather than saving an empty one over it.
            File backup = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyFailure) {
                logger.severe("Could not back up the unreadable " + file.getName() + ": " + copyFailure.getMessage());
            }
            logger.severe(file.getName() + " could not be read (" + ex.getMessage() + "). A copy was kept as "
                    + backup.getName() + ".");
            return;
        }
        for (String key : yaml.getKeys(false)) {
            UUID uuid = uuid(key);
            if (uuid != null && yaml.isBoolean(key)) {
                consent.put(uuid, yaml.getBoolean(key));
            }
        }
        readSet(yaml.getConfigurationSection(EXPLAINER_SEEN), explainerSeen);
        readSet(yaml.getConfigurationSection(NEWBIE_CLEARED), newbieCleared);
    }

    private static void readSet(ConfigurationSection section, Set<UUID> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid = uuid(key);
            if (uuid != null && section.getBoolean(key)) {
                into.add(uuid);
            }
        }
    }

    /** Builds the file content from the maps. Runs on the writer thread. */
    String serialise() {
        YamlConfiguration yaml = new YamlConfiguration();
        new TreeMap<>(consent).forEach((uuid, value) -> yaml.set(uuid.toString(), value));
        explainerSeen.stream().sorted().forEach(uuid -> yaml.set(EXPLAINER_SEEN + "." + uuid, true));
        newbieCleared.stream().sorted().forEach(uuid -> yaml.set(NEWBIE_CLEARED + "." + uuid, true));
        return yaml.saveToString();
    }

    public boolean hasConsent(UUID player) {
        return player != null && consent.getOrDefault(player, false);
    }

    public void setConsent(UUID player, boolean value) {
        consent.put(player, value);
        writer.markDirty();
    }

    /** Consent for every known player, for metrics. */
    public Map<UUID, Boolean> consentSnapshot() {
        return Map.copyOf(consent);
    }

    /** Records that the first-join explainer was shown. @return true the first time only */
    public boolean markExplainerSeen(UUID player) {
        boolean first = explainerSeen.add(player);
        if (first) {
            writer.markDirty();
        }
        return first;
    }

    public boolean hasSeenExplainer(UUID player) {
        return explainerSeen.contains(player);
    }

    public boolean isNewbieCleared(UUID player) {
        return newbieCleared.contains(player);
    }

    public void clearNewbie(UUID player) {
        if (newbieCleared.add(player)) {
            writer.markDirty();
        }
    }

    /** Writes now, on the calling thread. */
    public boolean flush() {
        return writer.flush();
    }

    @Override
    public void close() {
        writer.close();
    }

    private static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
