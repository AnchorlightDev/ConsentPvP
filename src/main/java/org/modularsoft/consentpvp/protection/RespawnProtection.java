package org.modularsoft.consentpvp.protection;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Players cannot take PvP damage for a short time after respawning, so a spawn camper cannot kill
 * them again before they can react. Attacking someone ends it early.
 */
public final class RespawnProtection {

    private final Map<UUID, Long> until = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RespawnProtection(LongSupplier clock) {
        this.clock = clock;
    }

    public void protect(UUID player, Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        until.put(player, clock.getAsLong() + duration.toMillis());
    }

    public boolean isProtected(UUID player) {
        return remainingMillis(player) > 0;
    }

    public long remainingMillis(UUID player) {
        Long end = until.get(player);
        if (end == null) {
            return 0;
        }
        long remaining = end - clock.getAsLong();
        if (remaining <= 0) {
            until.remove(player, end);
            return 0;
        }
        return remaining;
    }

    /** @return true when a live protection was removed */
    public boolean end(UUID player) {
        Long end = until.remove(player);
        return end != null && end > clock.getAsLong();
    }

    public void clearAll() {
        until.clear();
    }
}
