package org.modularsoft.consentpvp;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** A running plugin on a mock server, with a world and helpers for players. */
abstract class PluginTest {

    protected ServerMock server;
    protected ConsentPVP plugin;
    protected WorldMock world;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(ConsentPVP.class);
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    /** A player past new-player protection, standing at the origin. */
    protected PlayerMock player(String name, boolean consent) {
        PlayerMock player = server.addPlayer(name);
        player.teleport(new Location(world, 0.5, 5, 0.5));
        Harness.veteran(player);
        plugin.data().setConsent(player.getUniqueId(), consent);
        Fx.drain(player);
        return player;
    }

    protected void ticks(int count) {
        server.getScheduler().performTicks(count);
    }
}
