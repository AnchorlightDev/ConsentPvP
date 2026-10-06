package org.modularsoft.consentpvp;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The few plugin internals the bug regression tests need, behind one class.
 *
 * <p>{@link BugRegressionTest} talks to the plugin only through Bukkit events, commands and this
 * class, so the same test file can be run against the code from before the fixes (with a
 * {@code Harness} written for the old internals) to show every one of those tests failing there.
 */
public final class Harness {

    private Harness() {
    }

    public static JavaPlugin load() {
        return MockBukkit.load(ConsentPVP.class);
    }

    public static void consent(JavaPlugin plugin, Player player, boolean value) {
        ((ConsentPVP) plugin).data().setConsent(player.getUniqueId(), value);
    }

    public static boolean hasConsent(JavaPlugin plugin, Player player) {
        return ((ConsentPVP) plugin).consent().hasConsent(player.getUniqueId());
    }

    public static void reload(JavaPlugin plugin) {
        ((ConsentPVP) plugin).reloadPluginConfig();
    }

    /** Enough playtime that new-player protection does not apply. */
    public static void veteran(Player player) {
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 20 * 60 * 60 * 24);
    }
}
