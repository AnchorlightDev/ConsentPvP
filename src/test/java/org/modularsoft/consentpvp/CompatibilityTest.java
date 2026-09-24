package org.modularsoft.consentpvp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Existing servers upgrade in place: their config keys keep working and their playerdata.yml loads.
 * These write the files before the plugin loads, as an upgraded server would have them.
 */
class CompatibilityTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** MockBukkit's data folder for the plugin ({@code <name>-<version>}), created before it loads. */
    private File dataFolder() throws Exception {
        YamlConfiguration description = new YamlConfiguration();
        description.load(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("plugin.yml"),
                StandardCharsets.UTF_8));
        File folder = new File(server.getPluginsFolder(), "ConsentPVP-" + description.getString("version"));
        folder.mkdirs();
        return folder;
    }

    @Test
    void anUnversionedLegacyConfigKeepsItsValuesAndGainsTheNewKeys() throws Exception {
        server = MockBukkit.mock();
        String legacy = """
                pvp:
                  disable-on-death: true
                cooldown:
                  duration: 7
                messages:
                  prefix: "<gray>[Custom] "
                  pvp_enabled: "<green>You are now fair game."
                  pvp_status: "<white>Status: <green>%status%"
                """;
        File config = new File(dataFolder(), "config.yml");
        Files.writeString(config.toPath(), legacy, StandardCharsets.UTF_8);

        ConsentPVP plugin = MockBukkit.load(ConsentPVP.class);

        assertTrue(plugin.settings().disablePvpOnDeath());
        assertEquals(7 * 60_000, plugin.settings().toggleCooldown().toMillis());
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(config);
        assertEquals("<green>You are now fair game.", migrated.getString("messages.pvp_enabled"));
        assertEquals(15, migrated.getInt("combat-tag.duration-seconds"), "new keys are merged in");
        assertEquals(2, migrated.getInt("config-version"));

        PlayerMock alice = server.addPlayer("Alice");
        alice.performCommand("pvp status");
        String legacyStatus = Fx.drainLegacy(alice).get(0);
        assertTrue(legacyStatus.contains("[Custom]"), legacyStatus);
        assertTrue(legacyStatus.contains("§cdisabled"), "an old <green>%status% template still colours by state: " + legacyStatus);
    }

    @Test
    void theBundledConfigIsTheCurrentVersion() throws Exception {
        server = MockBukkit.mock();
        ConsentPVP plugin = MockBukkit.load(ConsentPVP.class);
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.load(new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8));
        assertEquals(2, bundled.getInt("config-version"));
        assertTrue(plugin.configFile().isHealthy());
    }

    @Test
    void anExistingPlayerdataFileLoadsUnchanged() throws Exception {
        server = MockBukkit.mock();
        UUID on = UUID.randomUUID();
        UUID off = UUID.randomUUID();
        File data = new File(dataFolder(), "playerdata.yml");
        Files.writeString(data.toPath(), on + ": true\n" + off + ": false\n", StandardCharsets.UTF_8);

        ConsentPVP plugin = MockBukkit.load(ConsentPVP.class);
        assertTrue(plugin.consent().hasConsent(on));
        assertFalse(plugin.consent().hasConsent(off));

        plugin.data().setConsent(UUID.randomUUID(), true);
        plugin.data().clearNewbie(on);
        assertTrue(plugin.data().flush());
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(data);
        assertTrue(saved.getBoolean(on.toString()), "consent stays a root-level boolean, readable by older versions");
        assertFalse(saved.getBoolean(off.toString()));
        assertTrue(saved.getBoolean("newbie-cleared." + on));
    }

    @Test
    void savesAreDebouncedOffTheServerThreadAndFlushedOnDisable() throws Exception {
        server = MockBukkit.mock();
        ConsentPVP plugin = MockBukkit.load(ConsentPVP.class);
        File data = new File(plugin.getDataFolder(), "playerdata.yml");
        UUID player = UUID.randomUUID();
        plugin.data().setConsent(player, true);
        assertFalse(data.exists() && Files.readString(data.toPath()).contains(player.toString()),
                "not written synchronously on the change");

        server.getPluginManager().disablePlugin(plugin);
        assertTrue(Files.readString(data.toPath()).contains(player + ": true"), "flushed by onDisable");
    }

    @Test
    void anUnreadablePlayerdataFileIsKeptAside() throws Exception {
        server = MockBukkit.mock();
        File data = new File(dataFolder(), "playerdata.yml");
        Files.writeString(data.toPath(), "abc: [unclosed\n", StandardCharsets.UTF_8);
        MockBukkit.load(ConsentPVP.class);
        File[] backups = dataFolder().listFiles((dir, name) -> name.startsWith("playerdata.yml.broken-"));
        assertTrue(backups != null && backups.length == 1);
    }

    @Test
    void pluginYmlDeclaresFoliaSupportAndTheSoftDependency() throws Exception {
        server = MockBukkit.mock();
        ConsentPVP plugin = MockBukkit.load(ConsentPVP.class);
        YamlConfiguration yml = new YamlConfiguration();
        yml.load(new InputStreamReader(plugin.getResource("plugin.yml"), StandardCharsets.UTF_8));
        assertTrue(yml.getBoolean("folia-supported"));
        assertTrue(yml.getStringList("softdepend").contains("floodgate"));
        assertTrue(yml.contains("permissions.consentpvp.newbie.bypass"));
    }
}
