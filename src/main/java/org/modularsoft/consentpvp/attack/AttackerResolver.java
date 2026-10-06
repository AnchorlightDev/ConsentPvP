package org.modularsoft.consentpvp.attack;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.modularsoft.consentpvp.tracking.OwnershipTracker;

import java.util.UUID;

/**
 * Works out which player is responsible for damage, knockback or fire, however indirect.
 *
 * <p>One resolution order for every handler, so a new damage route is handled everywhere at once:
 * <ol>
 *   <li>{@link DamageSource#getCausingEntity()} - vanilla's own attribution, which already names
 *       the player behind most arrows, tridents, TNT and a crystal they detonated;</li>
 *   <li>the shooter of a projectile;</li>
 *   <li>the igniter of primed TNT;</li>
 *   <li>the owner of a tamed animal, so a wolf's bite is its owner's attack;</li>
 *   <li>tracked ownership: who placed an end crystal, lava or fire, or detonated a respawn anchor.</li>
 * </ol>
 * Each step is applied to whatever the previous one found, so an arrow shot by a skeleton horse's
 * rider or TNT lit by a fire arrow still resolves to a player.
 */
public final class AttackerResolver {

    /** How the attacker was identified, for message wording. */
    public enum Kind {
        DIRECT, PROJECTILE, EXPLOSIVE, PET, CRYSTAL, LAVA, FIRE, ANCHOR, POTION
    }

    /**
     * The responsible player. {@code player} is null when they are offline, which is possible for
     * tracked hazards.
     */
    public record Attacker(UUID id, Player player, Kind kind) {

        public String name() {
            if (player != null) {
                return player.getName();
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            return offline.getName() == null ? id.toString() : offline.getName();
        }
    }

    private static final int MAX_DEPTH = 4;

    private final OwnershipTracker tracker;

    public AttackerResolver(OwnershipTracker tracker) {
        this.tracker = tracker;
    }

    /** The player responsible for {@code event}, or null when it was not a player. */
    public Attacker resolveAttacker(EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();
        Attacker attacker = fromEntity(source.getCausingEntity(), 0);
        if (attacker == null && event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = fromEntity(byEntity.getDamager(), 0);
        }
        if (attacker == null) {
            attacker = fromEntity(source.getDirectEntity(), 0);
        }
        if (attacker == null) {
            attacker = fromTracked(event, source);
        }
        return attacker;
    }

    /** The player responsible for an entity: a pusher, a combuster, a cloud. */
    public Attacker resolveAttacker(Entity entity) {
        return fromEntity(entity, 0);
    }

    private Attacker fromEntity(Entity entity, int depth) {
        if (entity == null || depth > MAX_DEPTH) {
            return null;
        }
        if (entity instanceof Player player) {
            return new Attacker(player.getUniqueId(), player, depth == 0 ? Kind.DIRECT : Kind.PROJECTILE);
        }
        if (entity instanceof Projectile projectile) {
            return retag(fromSource(projectile.getShooter(), depth + 1), Kind.PROJECTILE);
        }
        if (entity instanceof TNTPrimed tnt) {
            return retag(fromEntity(tnt.getSource(), depth + 1), Kind.EXPLOSIVE);
        }
        if (entity instanceof Tameable pet && pet.isTamed()) {
            UUID owner = pet.getOwnerUniqueId();
            return owner == null ? null : fromId(owner, Kind.PET);
        }
        if (entity instanceof EnderCrystal crystal) {
            UUID owner = tracker.crystalOwner(crystal.getUniqueId());
            return owner == null ? null : fromId(owner, Kind.CRYSTAL);
        }
        if (entity instanceof AreaEffectCloud cloud) {
            Attacker thrower = fromSource(cloud.getSource(), depth + 1);
            if (thrower == null && cloud.getOwnerUniqueId() != null) {
                thrower = fromId(cloud.getOwnerUniqueId(), Kind.POTION);
            }
            return retag(thrower, Kind.POTION);
        }
        return null;
    }

    private Attacker fromSource(ProjectileSource source, int depth) {
        return source instanceof Entity entity ? fromEntity(entity, depth) : null;
    }

    private Attacker fromTracked(EntityDamageEvent event, DamageSource source) {
        Location at = event.getEntity().getLocation();
        UUID owner = switch (event.getCause()) {
            case LAVA -> tracker.lavaOwnerNear(at);
            case FIRE -> tracker.fireOwnerNear(at, OwnershipTracker.FIRE_RADIUS);
            case BLOCK_EXPLOSION -> tracker.anchorDetonatorNear(
                    source.getDamageLocation() != null ? source.getDamageLocation() : at);
            default -> null;
        };
        if (owner == null) {
            return null;
        }
        Kind kind = switch (event.getCause()) {
            case LAVA -> Kind.LAVA;
            case FIRE -> Kind.FIRE;
            default -> Kind.ANCHOR;
        };
        return fromId(owner, kind);
    }

    private static Attacker fromId(UUID id, Kind kind) {
        return new Attacker(id, Bukkit.getPlayer(id), kind);
    }

    private static Attacker retag(Attacker attacker, Kind kind) {
        return attacker == null ? null : new Attacker(attacker.id(), attacker.player(),
                attacker.kind() == Kind.DIRECT || attacker.kind() == Kind.PROJECTILE ? kind : attacker.kind());
    }
}
