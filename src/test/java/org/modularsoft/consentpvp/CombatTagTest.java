package org.modularsoft.consentpvp;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Firework;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTagTest extends PluginTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void fighters() {
        plugin.setClock(now::get);
        alice = player("Alice", true);
        bob = player("Bob", true);
    }

    private void fight() {
        assertFalse(Fx.melee(alice, bob).isCancelled());
    }

    private void set(String path, Object value) {
        plugin.configFile().set(path, value);
        plugin.reloadPluginConfig();
    }

    @Test
    void pvpDamageTagsBothFighters() {
        fight();
        assertTrue(plugin.combat().isTagged(alice.getUniqueId()));
        assertTrue(plugin.combat().isTagged(bob.getUniqueId()));
    }

    @Test
    void blockedDamageDoesNotTag() {
        plugin.data().setConsent(bob.getUniqueId(), false);
        assertTrue(Fx.melee(alice, bob).isCancelled());
        assertFalse(plugin.combat().isTagged(alice.getUniqueId()));
    }

    @Test
    void taggedPlayersCannotDisablePvpAndAreToldHowLong() {
        fight();
        Fx.drain(alice);
        alice.performCommand("pvp disable");
        assertTrue(plugin.consent().hasConsent(alice.getUniqueId()));
        String told = Fx.joined(Fx.drain(alice));
        assertTrue(told.contains("in combat") && told.matches("(?s).*\\d+s.*"), told);
    }

    @Test
    void theTagExpiresAndThenDisablingWorks() {
        fight();
        now.addAndGet(15_000);
        ticks(20);
        assertFalse(plugin.combat().isTagged(alice.getUniqueId()));
        assertTrue(Fx.joined(Fx.drain(alice)).contains("no longer in combat"));
        alice.performCommand("pvp disable");
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()));
    }

    @Test
    void adminBypassClearsTheTagAndTheCooldown() {
        fight();
        PlayerMock admin = player("Admin", false);
        admin.setOp(true);
        admin.performCommand("pvp bypass Alice");
        assertFalse(plugin.combat().isTagged(alice.getUniqueId()));
        alice.performCommand("pvp disable");
        assertFalse(plugin.consent().hasConsent(alice.getUniqueId()));
    }

    @Test
    void configuredTeleportCommandsAreBlockedIncludingNamespacedOnes() {
        fight();
        for (String command : List.of("/home", "/spawn", "/tpa Bob", "/tpaccept", "/warp shop", "/back", "/rtp",
                "/essentials:home base", "/HOME")) {
            PlayerCommandPreprocessEvent event = Fx.call(new PlayerCommandPreprocessEvent(alice, command));
            assertTrue(event.isCancelled(), command);
        }
        assertFalse(Fx.call(new PlayerCommandPreprocessEvent(alice, "/msg Bob hi")).isCancelled());
    }

    @Test
    void commandsAreFreeOutsideCombat() {
        assertFalse(Fx.call(new PlayerCommandPreprocessEvent(alice, "/home")).isCancelled());
    }

    @Test
    void commandAndPluginTeleportsAreCancelledSoAliasesCannotEscape() {
        fight();
        Location to = new Location(world, 100, 5, 100);
        for (PlayerTeleportEvent.TeleportCause cause : List.of(PlayerTeleportEvent.TeleportCause.COMMAND,
                PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            assertTrue(Fx.call(new PlayerTeleportEvent(alice, alice.getLocation(), to, cause)).isCancelled(), cause.name());
        }
        assertFalse(Fx.call(new PlayerTeleportEvent(bob, bob.getLocation(), to,
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)).isCancelled());
    }

    @Test
    void enderPearlsAreBlockedAtLaunchAndLanding() {
        fight();
        EnderPearl pearl = world.spawn(alice.getLocation(), EnderPearl.class);
        PlayerLaunchProjectileEvent launch = Fx.call(new PlayerLaunchProjectileEvent(alice,
                new ItemStack(Material.ENDER_PEARL), pearl));
        assertTrue(launch.isCancelled());
        assertFalse(launch.shouldConsume(), "the pearl is not used up");
        assertTrue(Fx.call(new PlayerTeleportEvent(alice, alice.getLocation(), new Location(world, 9, 5, 9),
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL)).isCancelled());
    }

    @Test
    void chorusFruitElytraAndRiptideAreBlocked() {
        fight();
        assertTrue(Fx.call(new PlayerItemConsumeEvent(alice, new ItemStack(Material.CHORUS_FRUIT), EquipmentSlot.HAND))
                .isCancelled());
        assertTrue(Fx.call(new EntityToggleGlideEvent(alice, true)).isCancelled());
        Firework firework = world.spawn(alice.getLocation(), Firework.class);
        PlayerElytraBoostEvent boost = Fx.call(new PlayerElytraBoostEvent(alice,
                new ItemStack(Material.FIREWORK_ROCKET), firework, EquipmentSlot.HAND));
        assertTrue(boost.isCancelled());
        assertTrue(Fx.call(new PlayerRiptideEvent(alice, new ItemStack(Material.TRIDENT))).isCancelled());
    }

    @Test
    void eachEscapeItemCanBeAllowedSeparately() {
        set("combat-tag.block-chorus-fruit", false);
        set("combat-tag.block-riptide", false);
        fight();
        assertFalse(Fx.call(new PlayerItemConsumeEvent(alice, new ItemStack(Material.CHORUS_FRUIT), EquipmentSlot.HAND))
                .isCancelled());
        assertFalse(Fx.call(new PlayerRiptideEvent(alice, new ItemStack(Material.TRIDENT))).isCancelled());
        assertTrue(Fx.call(new EntityToggleGlideEvent(alice, true)).isCancelled(), "elytra is still blocked");
    }

    @Test
    void enteringCombatCutsAnElytraFlight() {
        bob.setGliding(true);
        fight();
        assertFalse(bob.isGliding());
    }

    @Test
    void actionBarTimerRunsAndClearsWhenTheTagEnds() {
        fight();
        assertTrue(plugin.combat().hasDisplay(alice.getUniqueId()));
        assertTrue(Fx.joined(List.of(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(alice.nextActionBar()))).contains("In combat"));
        now.addAndGet(15_000);
        ticks(20);
        assertFalse(plugin.combat().hasDisplay(alice.getUniqueId()));
    }

    @Test
    void bossBarTimerIsAvailable() {
        set("combat-tag.display", "boss_bar");
        fight();
        assertTrue(plugin.combat().hasBossBar(alice.getUniqueId()));
        assertEquals(1, alice.getBossBars().size());
        plugin.combat().clear(alice.getUniqueId());
        assertTrue(alice.getBossBars().isEmpty());
    }

    @Test
    void soundsPlayOnEnteringAndLeavingAndCanBeTurnedOff() {
        fight();
        assertFalse(alice.getHeardSounds().isEmpty(), "enter sound");
        int heard = alice.getHeardSounds().size();
        now.addAndGet(15_000);
        ticks(20);
        assertTrue(alice.getHeardSounds().size() > heard, "leave sound");

        set("combat-tag.sounds.enabled", false);
        PlayerMock carol = player("Carol", true);
        assertFalse(Fx.melee(carol, bob).isCancelled());
        assertTrue(carol.getHeardSounds().isEmpty());
    }

    @Test
    void deathClearsTheTag() {
        fight();
        Fx.death(bob);
        assertFalse(plugin.combat().isTagged(bob.getUniqueId()));
        assertTrue(plugin.combat().isTagged(alice.getUniqueId()), "only the dead player's tag ends");
    }

    @Test
    void loggingOutInCombatHasNoPenalty() {
        fight();
        bob.disconnect();
        assertFalse(plugin.combat().isTagged(bob.getUniqueId()));
        assertTrue(plugin.consent().hasConsent(bob.getUniqueId()), "no punishment, the setting is kept");
    }
}
