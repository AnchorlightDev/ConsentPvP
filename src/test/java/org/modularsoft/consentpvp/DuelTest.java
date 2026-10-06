package org.modularsoft.consentpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelTest extends PluginTest {

    private final AtomicLong now = new AtomicLong(5_000_000);
    private PlayerMock alice;
    private PlayerMock bob;
    private PlayerMock carol;

    @BeforeEach
    void players() {
        plugin.setClock(now::get);
        alice = player("Alice", false);
        bob = player("Bob", false);
        carol = player("Carol", false);
    }

    private void duel() {
        alice.performCommand("pvp duel Bob");
        bob.performCommand("pvp accept");
        Fx.drain(alice);
        Fx.drain(bob);
    }

    @Test
    void anAcceptedDuelAllowsFightingBetweenThoseTwoOnly() {
        assertTrue(Fx.melee(alice, bob).isCancelled());
        duel();
        assertFalse(Fx.melee(alice, bob).isCancelled());
        assertFalse(Fx.melee(bob, alice).isCancelled());
        assertTrue(Fx.melee(alice, carol).isCancelled(), "other players are unaffected");
        assertTrue(Fx.melee(carol, bob).isCancelled());
    }

    @Test
    void aDuelDoesNotChangeEitherPlayersSetting() {
        duel();
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()));
        assertFalse(plugin.consent().hasConsent(bob.getUniqueId()));
    }

    @Test
    void theRequestCarriesClickableAcceptAndDeny() {
        alice.performCommand("pvp duel Bob");
        Component request = bob.nextComponentMessage();
        assertNotNull(request);
        String commands = request.children().toString() + request.clickEvent();
        assertTrue(Fx.joined(java.util.List.of(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(request))).contains("[Accept]"));
        assertTrue(containsClick(request, "/pvp accept Alice"), commands);
        assertTrue(containsClick(request, "/pvp deny Alice"), commands);
    }

    private static boolean containsClick(Component component, String command) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND
                && click.payload().toString().contains(command)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> containsClick(child, command));
    }

    @Test
    void denyingEndsTheRequest() {
        alice.performCommand("pvp duel Bob");
        bob.performCommand("pvp deny");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("declined"));
        bob.performCommand("pvp accept");
        assertTrue(Fx.melee(alice, bob).isCancelled());
    }

    @Test
    void requestsExpire() {
        alice.performCommand("pvp duel Bob");
        now.addAndGet(60_000);
        ticks(20);
        assertTrue(Fx.joined(Fx.drain(alice)).contains("expired"));
        bob.performCommand("pvp accept");
        assertTrue(Fx.joined(Fx.drain(bob)).contains("no pending"));
    }

    @Test
    void requestsCannotBeSpammed() {
        alice.performCommand("pvp duel Bob");
        alice.performCommand("pvp duel Bob");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("already have a pending"));
        bob.performCommand("pvp deny");
        Fx.drain(alice);
        alice.performCommand("pvp duel Bob");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("Wait"), "a denied target cannot be re-challenged at once");
        Fx.drain(bob);
        bob.performCommand("pvp accept");
        assertTrue(Fx.melee(alice, bob).isCancelled());
    }

    @Test
    void duelsEndOnDeath() {
        duel();
        Fx.death(bob);
        assertTrue(Fx.melee(alice, bob).isCancelled());
        assertTrue(Fx.joined(Fx.drain(alice)).contains("ended"));
    }

    @Test
    void duelsEndOnLogout() {
        duel();
        bob.disconnect();
        assertFalse(plugin.duels().isDueling(alice.getUniqueId(), bob.getUniqueId()));
    }

    @Test
    void duelsEndAfterTheTimeLimit() {
        duel();
        now.addAndGet(300_000);
        ticks(20);
        assertTrue(Fx.melee(alice, bob).isCancelled());
    }

    @Test
    void selfAndBusyPlayersAreRefused() {
        alice.performCommand("pvp duel Alice");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("yourself"));
        duel();
        carol.performCommand("pvp duel Bob");
        assertTrue(Fx.joined(Fx.drain(carol)).contains("already in a duel"));
    }

    @Test
    void newPlayersCannotDuelOrBeChallenged() {
        PlayerMock fresh = server.addPlayer("Fresh");
        Fx.drain(fresh);
        fresh.performCommand("pvp duel Alice");
        assertTrue(Fx.joined(Fx.drain(fresh)).contains("new-player protection"));
        alice.performCommand("pvp duel Fresh");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("new-player protection"));
    }

    @Test
    void duelsCountForMetrics() {
        duel();
        assertEquals(1, plugin.duels().startedInLastDay());
    }
}
