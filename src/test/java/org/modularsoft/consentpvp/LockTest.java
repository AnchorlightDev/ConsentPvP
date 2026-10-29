package org.modularsoft.consentpvp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /pvp lock} and {@code /pvp unlock}: a per-player lock on self-toggling. */
class LockTest extends PluginTest {

    private ConsoleCommandSenderMock console;
    private PlayerMock alice;

    @BeforeEach
    void setUpSenders() {
        console = server.getConsoleSender();
        alice = player("Alice", false);
    }

    private String console(String command) {
        while (console.nextMessage() != null) {
            // drain
        }
        server.dispatchCommand(console, command);
        List<String> out = new ArrayList<>();
        String line;
        while ((line = console.nextMessage()) != null) {
            out.add(line.replaceAll("§.", ""));
        }
        return String.join("\n", out);
    }

    @Test
    void lockSetsConsentLocksItAndTellsBothSides() {
        String out = console("pvp lock Alice on");
        assertTrue(out.contains("Locked Alice's PVP"), out);
        assertTrue(plugin.consent().hasConsent(alice.getUniqueId()));
        assertTrue(plugin.consent().isLocked(alice.getUniqueId()));
        assertTrue(Fx.joined(Fx.drain(alice)).contains("staff member locked your PVP"));
    }

    @Test
    void aLockedPlayerCannotToggleTheirOwnPvp() {
        console("pvp lock Alice off");
        Fx.drain(alice);
        alice.performCommand("pvp enable");
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()), "the lock held");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("locked your PVP"));
    }

    @Test
    void unlockReleasesTheLockAndLetsThePlayerToggleAgain() {
        console("pvp lock Alice off");
        String out = console("pvp unlock Alice");
        assertTrue(out.contains("Unlocked Alice's PVP"), out);
        assertFalse(plugin.consent().isLocked(alice.getUniqueId()));
        Fx.drain(alice);
        alice.performCommand("pvp enable");
        assertTrue(plugin.consent().hasConsent(alice.getUniqueId()), "free to toggle again");
    }

    @Test
    void unlockAnUnlockedPlayerSaysSo() {
        assertTrue(console("pvp unlock Alice").contains("not locked"));
    }

    @Test
    void deathDoesNotUnsetALockedPlayersConsent() {
        plugin.configFile().set("pvp.disable-on-death", true);
        plugin.reloadPluginConfig();
        console("pvp lock Alice on");
        Fx.death(alice);
        assertTrue(plugin.consent().hasConsent(alice.getUniqueId()), "lock survives death-disable");
    }

    @Test
    void checkReportsTheLockState() {
        console("pvp lock Alice on");
        assertTrue(console("pvp check Alice").contains("Locked: yes"));
        console("pvp unlock Alice");
        assertTrue(console("pvp check Alice").contains("Locked: no"));
    }

    @Test
    void lockValidatesItsArguments() {
        assertTrue(console("pvp lock Alice maybe").contains("Usage"));
        assertTrue(console("pvp lock Nobody on").contains("Player not found"));
        assertTrue(console("pvp unlock").contains("Usage"));
    }

    @Test
    void lockAndUnlockNeedTheAdminPermission() {
        alice.performCommand("pvp lock Alice on");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("don't have permission"));
        alice.performCommand("pvp unlock Alice");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("don't have permission"));
        assertFalse(plugin.consent().isLocked(alice.getUniqueId()));
    }

    @Test
    void tabCompletionOffersPlayersThenStates() {
        org.bukkit.command.Command command = plugin.getCommand("pvp");
        List<String> states = command.tabComplete(console, "pvp", new String[] {"lock", "Alice", ""});
        assertEquals(List.of("on", "off"), states);
        assertTrue(command.tabComplete(console, "pvp", new String[] {"lock", "Al"}).contains("Alice"));
    }
}
