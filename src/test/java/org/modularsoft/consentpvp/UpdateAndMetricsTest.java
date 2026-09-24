package org.modularsoft.consentpvp;

import dev.anchorlight.stonelib.update.UpdateChecker;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateAndMetricsTest extends PluginTest {

    @Test
    void adminsAreToldAboutANewReleaseOnJoin() {
        plugin.updates().accept(UpdateChecker.parse("1.2.0",
                "{\"tag_name\":\"v1.3.0\",\"html_url\":\"https://github.com/ModularSoftAU/ConsentPvP/releases/tag/v1.3.0\"}"));
        assertTrue(plugin.updates().isOutdated());

        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        admin.disconnect();
        admin.reconnect();
        assertTrue(Fx.joined(Fx.drain(admin)).contains("1.3.0 is available"));

        PlayerMock regular = server.addPlayer("Regular");
        assertTrue(Fx.drain(regular).stream().noneMatch(m -> m.contains("available")));
    }

    @Test
    void anUpToDateOrFailedCheckSaysNothing() {
        plugin.updates().accept(UpdateChecker.parse("1.3.0", "{\"tag_name\":\"v1.3.0\"}"));
        plugin.updates().accept(UpdateChecker.Result.failed("1.3.0", "offline"));
        assertFalse(plugin.updates().isOutdated());
    }

    @Test
    void consentPercentageIsBucketed() {
        assertEquals("No players", org.modularsoft.consentpvp.metrics.PluginMetricsAccess.bucket(Map.of()));
        assertEquals("50-59%", org.modularsoft.consentpvp.metrics.PluginMetricsAccess.bucket(
                Map.of(UUID.randomUUID(), true, UUID.randomUUID(), false)));
        assertEquals("100%", org.modularsoft.consentpvp.metrics.PluginMetricsAccess.bucket(Map.of(UUID.randomUUID(), true)));
    }
}
