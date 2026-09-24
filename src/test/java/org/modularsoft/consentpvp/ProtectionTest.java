package org.modularsoft.consentpvp;

import org.bukkit.Statistic;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionTest extends PluginTest {

    private final AtomicLong now = new AtomicLong(9_000_000);

    @BeforeEach
    void clock() {
        plugin.setClock(now::get);
    }

    // ------------------------------------------------------------ newbie

    @Test
    void newPlayersCannotEnablePvpAndAreToldHowLongIsLeft() {
        PlayerMock fresh = server.addPlayer("Fresh");
        fresh.setStatistic(Statistic.PLAY_ONE_MINUTE, 20 * 60 * 60); // one hour of the default three
        Fx.drain(fresh);
        fresh.performCommand("pvp enable");
        assertFalse(plugin.consent().hasConsent(fresh.getUniqueId()));
        String told = Fx.joined(Fx.drain(fresh));
        assertTrue(told.contains("2h 0m"), told);
    }

    @Test
    void enoughPlaytimeLiftsProtection() {
        PlayerMock fresh = server.addPlayer("Fresh");
        fresh.setStatistic(Statistic.PLAY_ONE_MINUTE, 20 * 60 * 180);
        fresh.performCommand("pvp enable");
        assertTrue(plugin.consent().hasConsent(fresh.getUniqueId()));
    }

    @Test
    void staffCanLiftItEarly() {
        PlayerMock fresh = server.addPlayer("Fresh");
        server.dispatchCommand(server.getConsoleSender(), "pvp newbie clear Fresh");
        fresh.performCommand("pvp enable");
        assertTrue(plugin.consent().hasConsent(fresh.getUniqueId()));
        assertTrue(plugin.data().isNewbieCleared(fresh.getUniqueId()), "and it is remembered");
    }

    @Test
    void theBypassPermissionSkipsIt() {
        PlayerMock fresh = server.addPlayer("Fresh");
        fresh.addAttachment(plugin, "consentpvp.newbie.bypass", true);
        fresh.performCommand("pvp enable");
        assertTrue(plugin.consent().hasConsent(fresh.getUniqueId()));
    }

    @Test
    void itCanBeTurnedOff() {
        plugin.configFile().set("newbie-protection.enabled", false);
        plugin.reloadPluginConfig();
        PlayerMock fresh = server.addPlayer("Fresh");
        fresh.performCommand("pvp enable");
        assertTrue(plugin.consent().hasConsent(fresh.getUniqueId()));
    }

    // ----------------------------------------------------------- respawn

    private void respawn(PlayerMock player) {
        Fx.call(new PlayerRespawnEvent(player, player.getLocation(), false));
    }

    @Test
    void respawnedPlayersCannotTakePvpDamageForAWhile() {
        PlayerMock alice = player("Alice", true);
        PlayerMock bob = player("Bob", true);
        respawn(bob);
        assertTrue(Fx.melee(alice, bob).isCancelled());
        assertTrue(Fx.joined(Fx.drain(alice)).contains("just respawned"));

        now.addAndGet(10_000);
        assertFalse(Fx.melee(alice, bob).isCancelled(), "it runs out");
    }

    @Test
    void attackingSomeoneEndsItEarly() {
        PlayerMock alice = player("Alice", true);
        PlayerMock bob = player("Bob", true);
        respawn(bob);
        Fx.drain(bob);
        assertFalse(Fx.melee(bob, alice).isCancelled());
        assertTrue(Fx.joined(Fx.drain(bob)).contains("ended because you attacked"));
        assertFalse(Fx.melee(alice, bob).isCancelled());
    }

    @Test
    void respawnProtectionCanBeTurnedOff() {
        plugin.configFile().set("respawn-protection.enabled", false);
        plugin.reloadPluginConfig();
        PlayerMock alice = player("Alice", true);
        PlayerMock bob = player("Bob", true);
        respawn(bob);
        assertFalse(Fx.melee(alice, bob).isCancelled());
    }
}
