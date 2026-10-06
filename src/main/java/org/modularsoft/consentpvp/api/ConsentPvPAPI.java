package org.modularsoft.consentpvp.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * ConsentPvP's public API, registered with Bukkit's {@code ServicesManager}.
 *
 * <pre>{@code
 * ConsentPvPAPI api = Bukkit.getServicesManager().load(ConsentPvPAPI.class);
 * if (api != null && api.canFight(attacker, victim)) { ... }
 * }</pre>
 *
 * <p>Every method is safe to call from any thread: the answers come from thread-safe state and
 * touch no entity. Declare {@code softdepend: [ConsentPVP]} so the service is registered before
 * your plugin enables. Listen for {@link PvPConsentChangeEvent} to observe or veto toggles.
 */
public interface ConsentPvPAPI {

    /**
     * Whether {@code attacker} may damage {@code defender} right now: both have consented or they
     * are dueling each other (or {@link #getOverride()} forces PvP on), and the defender is not
     * respawn-protected. False for the same player, and while the override forces PvP off.
     */
    boolean canFight(Player attacker, Player defender);

    /** The player's own PvP setting. Duels and {@link #getOverride()} do not change it. */
    boolean hasConsent(UUID player);

    /**
     * Always false: combat tagging was removed. Kept so plugins compiled against earlier versions
     * do not fail with {@code NoSuchMethodError}.
     */
    @Deprecated(forRemoval = true)
    default boolean isInCombat(UUID player) {
        return false;
    }

    /** Whether these two players are in an active duel with each other. */
    boolean isDueling(UUID first, UUID second);

    /** The server-wide override staff set with {@code /pvp force}, or {@link PvPOverride#NONE}. */
    PvPOverride getOverride();
}
