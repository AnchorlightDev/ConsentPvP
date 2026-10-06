package org.modularsoft.consentpvp.update;

import dev.anchorlight.stonelib.update.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.modularsoft.consentpvp.util.Messages;

import java.util.logging.Logger;

/**
 * Checks GitHub for a newer release once, asynchronously, on startup. When one exists it tells the
 * console straight away and each admin as they join. A failed check (offline, rate-limited) is
 * logged quietly and changes nothing.
 */
public final class UpdateNotifier {

    public static final String ADMIN_PERMISSION = "consentpvp.admin";

    private final Messages messages;
    private final Logger logger;
    private volatile UpdateChecker.Result outdated;

    public UpdateNotifier(Messages messages, Logger logger) {
        this.messages = messages;
        this.logger = logger;
    }

    /** Starts the check. {@code repository} is {@code owner/name}. */
    public void check(String repository, String currentVersion) {
        String[] parts = repository == null ? new String[0] : repository.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            logger.warning("update-checker.repository must look like owner/name, got: " + repository);
            return;
        }
        new UpdateChecker(parts[0], parts[1], currentVersion).check().thenAccept(this::accept);
    }

    /** Applies a result. Public for tests. */
    public void accept(UpdateChecker.Result result) {
        if (!result.succeeded()) {
            logger.fine("Update check failed: " + result.error());
            return;
        }
        if (result.outdated()) {
            outdated = result;
            logger.warning("A newer ConsentPvP is available: " + result.latest() + " (running " + result.current()
                    + "). " + (result.url() == null ? "" : result.url()));
        }
    }

    public boolean isOutdated() {
        return outdated != null;
    }

    public void notifyOnJoin(Player player) {
        UpdateChecker.Result result = outdated;
        if (result == null || !player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        String url = result.url() == null ? "" : result.url();
        Component link = url.isEmpty()
                ? Component.empty()
                : Component.text(url).clickEvent(ClickEvent.openUrl(url));
        messages.send(player, "update_available", "latest", result.latest(), "current", result.current(),
                "url", link);
    }
}
