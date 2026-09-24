package org.modularsoft.consentpvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.modularsoft.consentpvp.api.ConsentPvPAPI;
import org.modularsoft.consentpvp.api.PvPConsentChangeEvent;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTest extends PluginTest {

    private ConsentPvPAPI api() {
        ConsentPvPAPI api = server.getServicesManager().load(ConsentPvPAPI.class);
        assertNotNull(api, "registered with the ServicesManager");
        return api;
    }

    @Test
    void canFightFollowsConsentDuelsAndRespawnProtection() {
        PlayerMock alice = player("Alice", true);
        PlayerMock bob = player("Bob", false);
        assertFalse(api().canFight(alice, bob));
        assertFalse(api().canFight(alice, alice));

        plugin.data().setConsent(bob.getUniqueId(), true);
        assertTrue(api().canFight(alice, bob));

        plugin.respawn().protect(bob.getUniqueId(), java.time.Duration.ofSeconds(10));
        assertFalse(api().canFight(alice, bob));
        assertTrue(api().canFight(bob, alice), "protection is the defender's");
    }

    @Test
    void hasConsentWorksForOfflinePlayersToo() {
        UUID offline = UUID.randomUUID();
        plugin.data().setConsent(offline, true);
        assertTrue(api().hasConsent(offline));
        assertFalse(api().hasConsent(UUID.randomUUID()));
    }

    @Test
    void isInCombatAndIsDueling() {
        PlayerMock alice = player("Alice", false);
        PlayerMock bob = player("Bob", false);
        alice.performCommand("pvp duel Bob");
        bob.performCommand("pvp accept");
        assertTrue(api().isDueling(alice.getUniqueId(), bob.getUniqueId()));
        assertTrue(api().isDueling(bob.getUniqueId(), alice.getUniqueId()));
        assertFalse(api().isInCombat(alice.getUniqueId()));
        Fx.melee(alice, bob);
        assertTrue(api().isInCombat(alice.getUniqueId()));
    }

    @Test
    void theChangeEventFiresAndCanVetoAToggle() {
        List<PvPConsentChangeEvent> seen = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void on(PvPConsentChangeEvent event) {
                seen.add(event);
                event.setCancelled(true);
            }
        }, plugin);
        PlayerMock alice = player("Alice", false);
        alice.performCommand("pvp enable");

        assertEquals(1, seen.size());
        assertTrue(seen.get(0).getNewConsent());
        assertEquals(PvPConsentChangeEvent.Cause.COMMAND, seen.get(0).getCause());
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()), "vetoed");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("blocked"));
    }

    @Test
    void deathAndStaffChangesFireTheEventWithTheirCause() {
        List<PvPConsentChangeEvent.Cause> causes = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void on(PvPConsentChangeEvent event) {
                causes.add(event.getCause());
            }
        }, plugin);
        PlayerMock alice = player("Alice", false);
        server.dispatchCommand(server.getConsoleSender(), "pvp set Alice on");
        plugin.configFile().set("pvp.disable-on-death", true);
        plugin.reloadPluginConfig();
        Fx.death(alice);
        assertEquals(List.of(PvPConsentChangeEvent.Cause.ADMIN, PvPConsentChangeEvent.Cause.DEATH), causes);
    }
}
