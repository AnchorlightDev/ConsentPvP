package org.modularsoft.consentpvp;

import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.modularsoft.consentpvp.api.ConsentPvPAPI;
import org.modularsoft.consentpvp.api.PvPOverride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceTest extends PluginTest {

    private void console(String command) {
        server.dispatchCommand(server.getConsoleSender(), command);
    }

    @Test
    void forcedOnLetsAnyoneFightWithoutTouchingTheirSettings() {
        PlayerMock alice = player("Alice", false);
        PlayerMock bob = player("Bob", false);
        console("pvp force on");
        assertFalse(Fx.melee(alice, bob).isCancelled());
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()), "own setting is untouched");
        assertTrue(Fx.joined(Fx.drain(bob)).contains("turned PvP on for everyone"));

        console("pvp force clear");
        assertTrue(Fx.melee(alice, bob).isCancelled());
        assertTrue(Fx.joined(Fx.drain(bob)).contains("override has been lifted"));
    }

    @Test
    void forcedOnStillHonoursRespawnProtection() {
        PlayerMock alice = player("Alice", false);
        PlayerMock bob = player("Bob", false);
        console("pvp force on");
        Fx.call(new PlayerRespawnEvent(bob, bob.getLocation(), false));
        assertTrue(Fx.melee(alice, bob).isCancelled());
    }

    @Test
    void forcedOffBlocksConsentingPlayersAndSaysWhy() {
        PlayerMock alice = player("Alice", true);
        PlayerMock bob = player("Bob", true);
        console("pvp force off");
        Fx.drain(alice);
        assertTrue(Fx.melee(alice, bob).isCancelled());
        ticks(1);
        String told = Fx.joined(Fx.drain(alice));
        assertTrue(told.contains("turned off for everyone"), told);
        assertFalse(told.contains("Enable PvP"), told);
    }

    @Test
    void forcingEndsDuelsAndBlocksNewOnes() {
        PlayerMock alice = player("Alice", false);
        PlayerMock bob = player("Bob", false);
        alice.performCommand("pvp duel Bob");
        bob.performCommand("pvp accept");
        assertTrue(plugin.duels().isDueling(alice.getUniqueId(), bob.getUniqueId()));
        console("pvp force off");
        assertFalse(plugin.duels().isDueling(alice.getUniqueId(), bob.getUniqueId()));
        assertTrue(Fx.melee(alice, bob).isCancelled());
        Fx.drain(alice);
        alice.performCommand("pvp duel Bob");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("Duels are unavailable"));
    }

    @Test
    void statusAndJoinMentionTheOverride() {
        PlayerMock alice = player("Alice", false);
        console("pvp force on");
        Fx.drain(alice);
        alice.performCommand("pvp");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("currently forced on"));
        PlayerMock late = server.addPlayer("Late");
        assertTrue(Fx.joined(Fx.drain(late)).contains("currently forced on"));
    }

    @Test
    void theApiReportsTheOverride() {
        ConsentPvPAPI api = Bukkit.getServicesManager().load(ConsentPvPAPI.class);
        PlayerMock alice = player("Alice", false);
        PlayerMock bob = player("Bob", false);
        assertEquals(PvPOverride.NONE, api.getOverride());
        console("pvp force on");
        assertEquals(PvPOverride.FORCED_ON, api.getOverride());
        assertTrue(api.canFight(alice, bob));
        assertFalse(api.hasConsent(alice.getUniqueId()));
    }

    @Test
    void itSurvivesAReload() {
        console("pvp force off");
        console("pvp reload");
        assertEquals(PvPOverride.FORCED_OFF, plugin.consent().override());
    }

    @Test
    void onlyStaffCanForce() {
        PlayerMock alice = player("Alice", false);
        alice.performCommand("pvp force on");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("don't have permission"));
        assertEquals(PvPOverride.NONE, plugin.consent().override());
    }
}
