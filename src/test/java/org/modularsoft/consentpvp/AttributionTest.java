package org.modularsoft.consentpvp;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Wolf;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modularsoft.consentpvp.attack.AttackerResolver;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** resolveAttacker's order, and that each route is enforced. */
class AttributionTest extends PluginTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private PlayerMock alice;
    private PlayerMock bob;
    private AttackerResolver resolver;

    @BeforeEach
    void players() {
        plugin.setClock(now::get);
        alice = player("Alice", false);
        bob = player("Bob", true);
        resolver = new AttackerResolver(plugin.tracker());
    }

    private Location spot() {
        return new Location(world, 0.5, 5, 0.5);
    }

    @Test
    void causingEntityComesFirst() {
        Arrow arrow = world.spawn(spot(), Arrow.class);
        arrow.setShooter(bob);
        // Vanilla names Alice as the cause even though the arrow claims Bob.
        EntityDamageEvent event = Fx.byEntity(arrow, alice, bob, EntityDamageEvent.DamageCause.PROJECTILE, DamageType.ARROW);
        assertEquals(alice.getUniqueId(), resolver.resolveAttacker(event).id());
    }

    @Test
    void projectilesResolveToTheirShooter() {
        Arrow arrow = world.spawn(spot(), Arrow.class);
        arrow.setShooter(alice);
        EntityDamageEvent event = Fx.byEntity(arrow, null, bob, EntityDamageEvent.DamageCause.PROJECTILE, DamageType.ARROW);
        assertTrue(event.isCancelled());
        assertEquals(AttackerResolver.Kind.PROJECTILE, resolver.resolveAttacker(event).kind());
    }

    @Test
    void tntResolvesToItsIgniter() {
        TNTPrimed tnt = world.spawn(spot(), TNTPrimed.class);
        tnt.setSource(alice);
        EntityDamageEvent event = Fx.byEntity(tnt, null, bob, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                DamageType.EXPLOSION);
        assertTrue(event.isCancelled());
        assertEquals(alice.getUniqueId(), resolver.resolveAttacker(event).id());
    }

    @Test
    void petsResolveToTheirOwnerAndUntamedAnimalsToNobody() {
        Wolf wolf = world.spawn(spot(), Wolf.class);
        EntityDamageEvent wild = Fx.byEntity(wolf, wolf, bob, EntityDamageEvent.DamageCause.ENTITY_ATTACK, DamageType.MOB_ATTACK);
        assertFalse(wild.isCancelled(), "a wild wolf is not PvP");
        wolf.setOwner(alice);
        EntityDamageEvent tame = Fx.byEntity(wolf, wolf, bob, EntityDamageEvent.DamageCause.ENTITY_ATTACK, DamageType.MOB_ATTACK);
        assertEquals(AttackerResolver.Kind.PET, resolver.resolveAttacker(tame).kind());
    }

    @Test
    void crystalsFallBackToThePlacerAndPlacementExpires() {
        Block base = world.getBlockAt(0, 4, 3);
        base.setType(Material.OBSIDIAN);
        EnderCrystal crystal = world.spawn(new Location(world, 0.5, 5, 3.5), EnderCrystal.class);
        Fx.call(new EntityPlaceEvent(crystal, alice, base, BlockFace.UP));
        EntityDamageEvent blast = Fx.byEntity(crystal, null, bob, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                DamageType.EXPLOSION);
        assertTrue(blast.isCancelled());
        assertEquals(AttackerResolver.Kind.CRYSTAL, resolver.resolveAttacker(blast).kind());

        now.addAndGet(300_000);
        assertNull(resolver.resolveAttacker(blast), "ownership expires");
    }

    @Test
    void lavaAndFireOwnershipExpireAndIsCleanedUp() {
        Block target = world.getBlockAt(0, 5, 0);
        world.getBlockAt(0, 4, 0).setType(Material.STONE);
        Fx.call(new PlayerBucketEmptyEvent(alice, target, world.getBlockAt(0, 4, 0), BlockFace.UP,
                Material.LAVA_BUCKET, new ItemStack(Material.LAVA_BUCKET)));
        target.setType(Material.LAVA);
        assertTrue(Fx.environmental(bob, EntityDamageEvent.DamageCause.LAVA, DamageType.LAVA, null).isCancelled());

        Block fire = world.getBlockAt(2, 5, 0);
        fire.setType(Material.FIRE);
        Fx.call(new BlockIgniteEvent(fire, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, alice));
        assertTrue(plugin.tracker().size() >= 2);

        now.addAndGet(300_000);
        assertFalse(Fx.environmental(bob, EntityDamageEvent.DamageCause.LAVA, DamageType.LAVA, null).isCancelled());
        assertTrue(plugin.tracker().purgeExpired() >= 1);
        assertEquals(0, plugin.tracker().size());
    }

    @Test
    void fireSpreadInheritsItsOwner() {
        Block lit = world.getBlockAt(3, 5, 0);
        lit.setType(Material.FIRE);
        Fx.call(new BlockIgniteEvent(lit, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, alice));
        Block spread = world.getBlockAt(4, 5, 0);
        spread.setType(Material.FIRE);
        Fx.call(new BlockIgniteEvent(spread, BlockIgniteEvent.IgniteCause.SPREAD, lit));
        assertEquals(alice.getUniqueId(), plugin.tracker().fireOwnerAt(spread));
    }

    @Test
    void windChargeKnockbackIsBlocked() {
        WindCharge charge = world.spawn(spot(), WindCharge.class);
        charge.setShooter(alice);
        EntityPushedByEntityAttackEvent push = Fx.call(new EntityPushedByEntityAttackEvent(bob,
                EntityKnockbackEvent.Cause.EXPLOSION, charge, new Vector(0, 1, 0)));
        assertTrue(push.isCancelled());
    }

    @Test
    void sweepBystandersAreProtectedSilently() {
        org.bukkit.damage.DamageSource source = org.bukkit.damage.DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(alice).withDirectEntity(alice).build();
        EntityDamageEvent sweep = Fx.call(new org.bukkit.event.entity.EntityDamageByEntityEvent(alice, bob,
                EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK, source, 1));
        assertTrue(sweep.isCancelled());
        assertTrue(Fx.drain(alice).isEmpty());
    }

    @Test
    void selfDamageIsNeverBlocked() {
        Arrow arrow = world.spawn(spot(), Arrow.class);
        arrow.setShooter(alice);
        assertFalse(Fx.byEntity(arrow, alice, alice, EntityDamageEvent.DamageCause.PROJECTILE, DamageType.ARROW)
                .isCancelled());
    }

    @Test
    void anchorsDetonatedByTheVictimThemselvesStillHurt() {
        Block anchor = world.getBlockAt(6, 5, 0);
        anchor.setType(Material.RESPAWN_ANCHOR);
        org.bukkit.block.data.type.RespawnAnchor data = (org.bukkit.block.data.type.RespawnAnchor) anchor.getBlockData();
        data.setCharges(2);
        anchor.setBlockData(data);
        Fx.call(new org.bukkit.event.player.PlayerInteractEvent(bob, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                null, anchor, BlockFace.UP));
        assertFalse(Fx.environmental(bob, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, DamageType.BAD_RESPAWN_POINT,
                new Location(world, 6.5, 5, 0.5)).isCancelled());
    }
}
