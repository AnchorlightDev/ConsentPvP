package org.modularsoft.consentpvp;

import org.bukkit.command.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffCommandTest extends PluginTest {

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
    void checkShowsEverythingFromTheConsole() {
        Harness.veteran(alice);
        String out = console("pvp check Alice");
        assertTrue(out.contains("Alice") && out.contains("Consent") && out.contains("Toggle cooldown")
                && out.contains("Combat tag") && out.contains("Duel") && out.contains("New-player protection"), out);
    }

    @Test
    void checkReportsNewbieTimeLeft() {
        PlayerMock fresh = server.addPlayer("Fresh");
        String out = console("pvp check Fresh");
        assertTrue(out.contains("3h 0m"), out);
    }

    @Test
    void setForcesConsentAndTellsThePlayer() {
        console("pvp set Alice on");
        assertTrue(plugin.consent().hasConsent(alice.getUniqueId()));
        assertTrue(Fx.joined(Fx.drain(alice)).contains("staff member set your PVP"));
        console("pvp set Alice off");
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()));
    }

    @Test
    void setIgnoresNewbieProtectionAndCombat() {
        PlayerMock fresh = server.addPlayer("Fresh");
        console("pvp set Fresh on");
        assertTrue(plugin.consent().hasConsent(fresh.getUniqueId()));
    }

    @Test
    void setValidatesItsArguments() {
        assertTrue(console("pvp set Alice maybe").contains("Usage"));
        assertTrue(console("pvp set Nobody on").contains("Player not found"));
    }

    @Test
    void deathAndReloadWorkFromTheConsole() {
        assertTrue(console("pvp death").contains("enabled"));
        assertTrue(plugin.settings().disablePvpOnDeath());
        assertTrue(plugin.configFile().config().getBoolean("pvp.disable-on-death"), "saved to config.yml");
        assertTrue(console("pvp reload").contains("reloaded"));
    }

    @Test
    void staffCommandsNeedTheAdminPermission() {
        for (String sub : List.of("check Alice", "set Alice on", "bypass Alice", "newbie clear Alice", "reload", "death")) {
            alice.performCommand("pvp " + sub);
            assertTrue(Fx.joined(Fx.drain(alice)).contains("don't have permission"), sub);
        }
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()));
    }

    @Test
    void playerCommandsTellTheConsoleTheyArePlayersOnly() {
        assertTrue(console("pvp").contains("Only players"));
        assertTrue(console("pvp enable").contains("Only players"));
    }

    @Test
    void unknownOptionsGetAConfiguredReply() {
        String out = console("pvp frobnicate");
        assertTrue(out.contains("Unknown option"), out);
    }

    // ------------------------------------------------------------- completion

    private List<String> complete(org.bukkit.command.CommandSender sender, String... args) {
        Command command = plugin.getCommand("pvp");
        assertNotNull(command);
        return command.tabComplete(sender, "pvp", args);
    }

    @Test
    void tabCompletionHidesAdminCommandsFromPlayers() {
        List<String> options = complete(alice, "");
        assertTrue(options.containsAll(List.of("status", "enable", "disable", "duel", "accept", "deny")), options.toString());
        for (String admin : List.of("check", "set", "bypass", "newbie", "reload", "death")) {
            assertFalse(options.contains(admin), admin);
        }
        assertTrue(complete(console, "").containsAll(List.of("check", "set", "bypass", "newbie", "reload", "death")));
    }

    @Test
    void tabCompletionReturnsEmptyListsNotNull() {
        assertEquals(List.of(), complete(alice, "enable", ""));
        assertEquals(List.of(), complete(alice, "check", ""), "no hints for a command they cannot run");
        assertEquals(List.of("on", "off"), complete(console, "set", "Alice", ""));
        assertEquals(List.of("clear"), complete(console, "newbie", ""));
        assertTrue(complete(alice, "duel", "Al").contains("Alice"));
    }
}
