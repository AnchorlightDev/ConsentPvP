package org.modularsoft.consentpvp.api;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired before a player's PvP consent changes. Cancel it to keep the old setting.
 *
 * <p>Fired on the thread that made the change: the player's own for a command or death, the
 * command sender's for a staff change.
 */
public class PvPConsentChangeEvent extends Event implements Cancellable {

    /** What caused the change. */
    public enum Cause {
        /** The player ran /pvp enable or /pvp disable, or used a toggle button or form. */
        COMMAND,
        /** The player died with disable-on-death on. */
        DEATH,
        /** A staff member used /pvp set. */
        ADMIN
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final boolean newConsent;
    private final Cause cause;
    private boolean cancelled;

    public PvPConsentChangeEvent(UUID player, boolean newConsent, Cause cause) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.newConsent = newConsent;
        this.cause = cause;
    }

    public UUID getPlayer() {
        return player;
    }

    /** The consent the player will have if the event is not cancelled. */
    public boolean getNewConsent() {
        return newConsent;
    }

    public Cause getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
