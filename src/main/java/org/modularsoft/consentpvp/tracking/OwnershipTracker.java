package org.modularsoft.consentpvp.tracking;

import dev.anchorlight.stonelib.ownership.BlockOwnership;
import dev.anchorlight.stonelib.ownership.ExpiringOwners;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.time.Duration;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Who is responsible for environmental hazards, so damage they cause can be attributed to a player.
 *
 * <ul>
 *   <li>lava poured from a bucket, by block;</li>
 *   <li>fire lit with flint and steel or a fire charge, and fire that spreads from it, by block;</li>
 *   <li>respawn anchors detonated outside the Nether, by block, recording who detonated it;</li>
 *   <li>end crystals, by entity UUID, recording who placed it.</li>
 * </ul>
 *
 * <p>Every entry expires, so a spot is not attributed to its placer for the rest of the server's
 * uptime. Lookups also check that the block is still what was recorded - lava that was scooped up
 * or fire that burnt out no longer counts even before the entry is cleaned up.
 *
 * <p>Thread-safe. Block checks read the world, so call the lookups from the thread that owns the
 * location, which a damage handler for a nearby victim already is.
 */
public final class OwnershipTracker {

    /** How far lava is searched from a victim: lava flows up to seven blocks from its source. */
    public static final int LAVA_RADIUS = 5;
    public static final int FIRE_RADIUS = 3;
    public static final int FIRE_SPREAD_RADIUS = 2;
    /** A charged anchor's blast reaches about five blocks; search a little further for the source. */
    public static final int ANCHOR_RADIUS = 8;
    /**
     * A detonation is recorded on the click and the blast follows in the same tick, so it only
     * needs to be remembered briefly. A long window would blame the detonator for an unrelated
     * explosion nearby minutes later.
     */
    public static final Duration ANCHOR_WINDOW = Duration.ofSeconds(5);

    private final BlockOwnership lava;
    private final BlockOwnership fire;
    private final BlockOwnership anchors;
    private final ExpiringOwners<UUID> crystals;

    public OwnershipTracker(Duration ttl, LongSupplier clock) {
        this.lava = new BlockOwnership(ttl, clock);
        this.fire = new BlockOwnership(ttl, clock);
        this.anchors = new BlockOwnership(shortest(ttl, ANCHOR_WINDOW), clock);
        this.crystals = new ExpiringOwners<>(ttl, clock);
    }

    public void setTtl(Duration ttl) {
        lava.setTtl(ttl);
        fire.setTtl(ttl);
        anchors.setTtl(shortest(ttl, ANCHOR_WINDOW));
        crystals.setTtl(ttl);
    }

    // ------------------------------------------------------------------ lava

    public void recordLava(Block block, UUID owner) {
        put(lava, block, owner);
    }

    public void clearLava(Block block) {
        remove(lava, block);
    }

    public UUID lavaOwnerNear(Location location) {
        return nearest(lava, location, LAVA_RADIUS, block -> block.getType() == Material.LAVA);
    }

    // ------------------------------------------------------------------ fire

    public void recordFire(Block block, UUID owner) {
        put(fire, block, owner);
    }

    public void clearFire(Block block) {
        remove(fire, block);
    }

    public UUID fireOwnerNear(Location location, int radius) {
        return nearest(fire, location, radius, block -> Tag.FIRE.isTagged(block.getType()));
    }

    /** The owner recorded at exactly this block, whether or not it is still burning. */
    public UUID fireOwnerAt(Block block) {
        return fire.owner(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    // --------------------------------------------------------------- anchors

    public void recordAnchorDetonation(Block anchor, UUID detonator) {
        put(anchors, anchor, detonator);
    }

    /**
     * The detonator of the closest recorded anchor. The anchor block is gone once it explodes, so
     * this does not check the block.
     */
    public UUID anchorDetonatorNear(Location location) {
        return nearest(anchors, location, ANCHOR_RADIUS, block -> true);
    }

    // -------------------------------------------------------------- crystals

    public void recordCrystal(UUID crystal, UUID owner) {
        crystals.put(crystal, owner);
    }

    public UUID crystalOwner(UUID crystal) {
        return crystals.owner(crystal);
    }

    // --------------------------------------------------------------- general

    /** Drops expired entries. @return how many were removed */
    public int purgeExpired() {
        return lava.purgeExpired() + fire.purgeExpired() + anchors.purgeExpired() + crystals.purgeExpired();
    }

    public int size() {
        return lava.size() + fire.size() + anchors.size() + crystals.size();
    }

    public void clear() {
        lava.clear();
        fire.clear();
        anchors.clear();
        crystals.clear();
    }

    private static Duration shortest(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static void put(BlockOwnership index, Block block, UUID owner) {
        index.put(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), owner);
    }

    private static void remove(BlockOwnership index, Block block) {
        index.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * The nearest live owner whose block still passes {@code stillValid}. Stale entries found on the
     * way are removed, so a block that changed without an event does not keep answering.
     */
    private static UUID nearest(BlockOwnership index, Location location, int radius, Predicate<Block> stillValid) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockOwnership.Match match = index.nearest(world.getUID(), location.getX(), location.getY(),
                    location.getZ(), radius);
            if (match == null) {
                return null;
            }
            if (stillValid.test(world.getBlockAt(match.x(), match.y(), match.z()))) {
                return match.owner();
            }
            index.remove(world.getUID(), match.x(), match.y(), match.z());
        }
        return null;
    }
}
