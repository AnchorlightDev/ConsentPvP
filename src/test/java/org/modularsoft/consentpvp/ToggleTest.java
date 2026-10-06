package org.modularsoft.consentpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToggleTest extends PluginTest {

    static List<String> commands(Component component) {
        List<String> out = new ArrayList<>();
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND) {
            out.add(click.payload().toString());
        }
        for (Component child : component.children()) {
            out.addAll(commands(child));
        }
        return out;
    }

    private static boolean hasCommand(Component component, String command) {
        return commands(component).stream().anyMatch(payload -> payload.contains(command));
    }

    @Test
    void statusCarriesClickableEnableAndDisableButtons() {
        PlayerMock alice = player("Alice", false);
        alice.performCommand("pvp");
        Component status = alice.nextComponentMessage();
        assertNotNull(status);
        assertTrue(hasCommand(status, "/pvp enable"), commands(status).toString());
        assertTrue(hasCommand(status, "/pvp disable"));
    }

    @Test
    void enableAndDisableToggleAndStartTheCooldown() {
        PlayerMock alice = player("Alice", false);
        alice.performCommand("pvp enable");
        assertTrue(plugin.consent().hasConsent(alice.getUniqueId()));
        assertTrue(Fx.joined(Fx.drain(alice)).contains("PVP consent enabled"));
        alice.performCommand("pvp disable");
        assertTrue(plugin.consent().hasConsent(alice.getUniqueId()), "cooldown");
    }

    @Test
    void anAlreadySetStateSaysSo() {
        PlayerMock alice = player("Alice", false);
        alice.performCommand("pvp disable");
        assertTrue(Fx.joined(Fx.drain(alice)).contains("already disabled"));
    }

    @Test
    void theEnablePvpButtonAppearsOnlyWhenTheAttackerIsTheOneWithPvpOff() {
        PlayerMock off = player("Off", false);
        PlayerMock on = player("On", true);

        Fx.melee(off, on);
        Component attackerOff = off.nextComponentMessage();
        assertNotNull(attackerOff);
        assertTrue(hasCommand(attackerOff, "/pvp enable"), "attacker has PvP off: button offered");

        Fx.melee(on, off);
        Component defenderOff = on.nextComponentMessage();
        assertNotNull(defenderOff);
        assertFalse(hasCommand(defenderOff, "/pvp enable"), "only the defender is off: no button");
    }

    @Test
    void denialsCanGoToTheActionBarWithoutAButton() {
        plugin.configFile().set("messages.pvp_attempt_delivery", "action_bar");
        plugin.reloadPluginConfig();
        PlayerMock off = player("Off", false);
        PlayerMock on = player("On", true);
        Fx.melee(off, on);
        assertEquals(null, off.nextComponentMessage());
        Component bar = off.nextActionBar();
        assertNotNull(bar);
        assertFalse(hasCommand(bar, "/pvp enable"));
    }

    @Test
    void theDefenderCanBeToldToo() {
        plugin.configFile().set("messages.notify-defender-on-denial", true);
        plugin.reloadPluginConfig();
        PlayerMock off = player("Off", false);
        PlayerMock on = player("On", true);
        Fx.melee(off, on);
        assertTrue(Fx.joined(Fx.drain(on)).contains("Off tried to hit you"));
    }

    @Test
    void vanishedAttackersAreCancelledSilentlyAndInvisibleOnesAreAnonymous() {
        plugin.configFile().set("messages.notify-defender-on-denial", true);
        plugin.reloadPluginConfig();
        PlayerMock ghost = player("Ghost", false);
        PlayerMock victim = player("Victim", true);
        ghost.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 600, 0));
        Fx.melee(ghost, victim);
        String told = Fx.joined(Fx.drain(victim));
        assertTrue(told.contains("Anonymous") && !told.contains("Ghost"), told);

        victim.hidePlayer(plugin, ghost);
        assertTrue(Fx.melee(ghost, victim).isCancelled());
        assertTrue(Fx.drain(victim).isEmpty());
    }

    @Test
    void userInputIsNeverParsedAsMarkup() {
        PlayerMock alice = player("Alice", false);
        alice.performCommand("pvp duel <click:run_command:'/op Alice'>x");
        Component reply = alice.nextComponentMessage();
        assertNotNull(reply);
        assertTrue(commands(reply).isEmpty(), "the name was shown as text, not turned into a click event");
    }

    @Test
    void theFirstJoinExplainerIsShownOnceWithTheToggle() {
        PlayerMock newcomer = server.addPlayer("Newcomer");
        ticks(41);
        List<Component> received = new ArrayList<>();
        Component next;
        while ((next = newcomer.nextComponentMessage()) != null) {
            received.add(next);
        }
        assertTrue(received.stream().anyMatch(c -> Fx.joined(List.of(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)))
                .contains("PvP consent")));
        assertTrue(received.stream().anyMatch(c -> hasCommand(c, "/pvp enable")));
        assertTrue(plugin.data().hasSeenExplainer(newcomer.getUniqueId()));

        newcomer.disconnect();
        newcomer.reconnect();
        ticks(41);
        assertTrue(Fx.drain(newcomer).stream().noneMatch(m -> m.contains("PvP consent")), "only once");
    }
}
