package org.modularsoft.consentpvp.api;

/**
 * A server-wide override of every player's own PvP setting, set with {@code /pvp force} for events.
 * It is saved, so it lasts across restarts until it is cleared. Players' own settings are untouched.
 */
public enum PvPOverride {
    /** No override: players' own settings and duels apply. */
    NONE,
    /** Every player can hurt every other, whatever their setting. Respawn protection still applies. */
    FORCED_ON,
    /** Nobody can hurt anybody, including in duels. */
    FORCED_OFF
}
