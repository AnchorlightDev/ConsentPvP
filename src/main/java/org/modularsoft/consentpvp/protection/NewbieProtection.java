package org.modularsoft.consentpvp.protection;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.modularsoft.consentpvp.Settings;
import org.modularsoft.consentpvp.data.PlayerDataStore;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * New players cannot enable PvP until they have played for a while, so a fresh account is not
 * talked into consenting and farmed. Staff can lift it early, and
 * {@code consentpvp.newbie.bypass} skips it.
 *
 * <p>Playtime is the vanilla {@code PLAY_ONE_MINUTE} statistic (in ticks, despite the name), which
 * survives restarts and needs no storage of our own. Read it on the player's own thread.
 */
public final class NewbieProtection {

    public static final String BYPASS_PERMISSION = "consentpvp.newbie.bypass";

    private final Supplier<Settings> settings;
    private final PlayerDataStore data;

    public NewbieProtection(Supplier<Settings> settings, PlayerDataStore data) {
        this.settings = settings;
        this.data = data;
    }

    /** Playtime still needed, or zero when the player is not protected. */
    public Duration remaining(Player player) {
        Settings current = settings.get();
        if (!current.newbieEnabled()
                || player.hasPermission(BYPASS_PERMISSION)
                || data.isNewbieCleared(player.getUniqueId())) {
            return Duration.ZERO;
        }
        long playedTicks = Math.max(0, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        Duration played = Duration.ofMillis(playedTicks * 50L);
        Duration left = current.newbieRequiredPlaytime().minus(played);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public boolean isProtected(Player player) {
        return !remaining(player).isZero();
    }
}
