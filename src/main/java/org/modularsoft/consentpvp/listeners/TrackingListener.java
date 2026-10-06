package org.modularsoft.consentpvp.listeners;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.modularsoft.consentpvp.tracking.OwnershipTracker;

import java.util.UUID;

/**
 * Records who is responsible for hazards that hurt players later: end crystals, poured lava, lit
 * fire and detonated respawn anchors and beds. Recording runs at {@code MONITOR}, so only actions
 * that actually happened are tracked.
 */
public final class TrackingListener implements Listener {

    private final OwnershipTracker tracker;

    public TrackingListener(OwnershipTracker tracker) {
        this.tracker = tracker;
    }

    // --------------------------------------------------------------- crystals

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal && event.getPlayer() != null) {
            tracker.recordCrystal(crystal.getUniqueId(), event.getPlayer().getUniqueId());
        }
    }

    // ------------------------------------------------------------------- lava

    /** {@link PlayerBucketEmptyEvent#getBlock()} is already the block the liquid goes into. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET) {
            tracker.recordLava(event.getBlock(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack result = event.getItemStack();
        if (result != null && result.getType() == Material.LAVA_BUCKET) {
            tracker.clearLava(event.getBlock());
        }
    }

    /** Lava turned to obsidian, cobblestone or stone by water. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        tracker.clearLava(event.getBlock());
    }

    // ------------------------------------------------------------------- fire

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        switch (event.getCause()) {
            case FLINT_AND_STEEL, FIREBALL -> {
                Player player = event.getPlayer();
                if (player != null) {
                    tracker.recordFire(block, player.getUniqueId());
                }
            }
            case SPREAD -> {
                UUID owner = tracker.fireOwnerNear(block.getLocation().add(0.5, 0.5, 0.5),
                        OwnershipTracker.FIRE_SPREAD_RADIUS);
                if (owner != null) {
                    tracker.recordFire(block, owner);
                }
            }
            case LAVA -> {
                UUID owner = tracker.lavaOwnerNear(block.getLocation().add(0.5, 0.5, 0.5));
                if (owner != null) {
                    tracker.recordFire(block, owner);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (Tag.FIRE.isTagged(event.getBlock().getType())) {
            tracker.clearFire(event.getBlock());
        }
    }

    /** Water or lava flowing into a fire or lava block replaces it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (Tag.FIRE.isTagged(to.getType())) {
            tracker.clearFire(to);
        }
    }

    /** A block placed where fire or lava was. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material replaced = event.getBlockReplacedState().getType();
        if (replaced == Material.LAVA) {
            tracker.clearLava(event.getBlock());
        } else if (Tag.FIRE.isTagged(replaced)) {
            tracker.clearFire(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (Tag.FIRE.isTagged(event.getBlock().getType())) {
            tracker.clearFire(event.getBlock());
        }
    }

    // ------------------------------------------------ extinguish and detonation

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Punching the top of a block puts out fire on it.
            Block above = clicked.getRelative(event.getBlockFace());
            if (Tag.FIRE.isTagged(above.getType())) {
                tracker.clearFire(above);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (detonates(clicked, event.getItem())) {
            tracker.recordAnchorDetonation(clicked, event.getPlayer().getUniqueId());
        }
    }

    /**
     * Whether right-clicking {@code block} with {@code item} blows it up: a charged respawn anchor
     * outside a dimension where anchors work (unless the click is adding glowstone to a partly
     * charged one), or a bed where beds do not work. An empty hand detonates too.
     */
    // isBedWorks is deprecated in 1.21.11 in favour of environment attributes, but it still answers
    // correctly and is the call that also works on the older 1.21 builds.
    @SuppressWarnings("deprecation")
    static boolean detonates(Block block, ItemStack item) {
        World world = block.getWorld();
        Material type = block.getType();
        if (type == Material.RESPAWN_ANCHOR && block.getBlockData() instanceof RespawnAnchor anchor) {
            if (world.isRespawnAnchorWorks() || anchor.getCharges() <= 0) {
                return false;
            }
            boolean charging = item != null && item.getType() == Material.GLOWSTONE
                    && anchor.getCharges() < anchor.getMaximumCharges();
            return !charging;
        }
        return Tag.BEDS.isTagged(type) && !world.isBedWorks();
    }
}
