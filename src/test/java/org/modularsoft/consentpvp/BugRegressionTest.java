package org.modularsoft.consentpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Wolf;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per reported bug. Each drives the plugin only through Bukkit events, commands and
 * {@link Harness}, so it can be run unchanged against the pre-fix code, where every one fails.
 */
class BugRegressionTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private WorldMock world;
    private PlayerMock attacker;
    private PlayerMock defender;
    private PlayerMock bystander;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = Harness.load();
        attacker = server.addPlayer("Attacker");
        defender = server.addPlayer("Defender");
        bystander = server.addPlayer("Bystander");
        for (PlayerMock player : List.of(attacker, defender, bystander)) {
            player.teleport(new Location(world, 0.5, 5, 0.5));
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    // 1 ------------------------------------------------------------------------

    @Test
    void bug1_lingeringPotionsRespectConsentAndThrottleMessages() {
        AreaEffectCloud cloud = world.spawn(at(0, 5, 0), AreaEffectCloud.class);
        cloud.setBasePotionType(PotionType.POISON);
        cloud.setSource(attacker);

        List<LivingEntity> last = null;
        for (int i = 0; i < 3; i++) {
            last = new ArrayList<>(List.of(defender));
            Fx.call(new AreaEffectCloudApplyEvent(cloud, last));
        }

        assertFalse(last.contains(defender), "a non-consenting player is removed from the cloud");
        assertEquals(1, Fx.drain(attacker).size(), "the notice is throttled, not sent every 5 ticks");
    }

    // 2 ------------------------------------------------------------------------

    @Test
    void bug2_bedAndAnchorExplosionsStillHurtPlayersWithPvpOff() {
        Harness.consent(plugin, defender, false);
        EntityDamageEvent bed = Fx.environmental(defender, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                DamageType.BAD_RESPAWN_POINT, at(0, 5, 0));
        assertFalse(bed.isCancelled(), "a natural or self-caused block explosion is not blocked");
        assertTrue(Fx.drain(defender).isEmpty(), "and no 'Anonymous tried attacking you'");
    }

    // 3 ------------------------------------------------------------------------

    @Test
    void bug3_flameArrowsDoNotIgniteNonConsentingPlayers() {
        Arrow arrow = world.spawn(at(0, 6, 0), Arrow.class);
        arrow.setShooter(attacker);
        EntityCombustByEntityEvent combust = Fx.call(new EntityCombustByEntityEvent(arrow, defender, 5f));
        assertTrue(combust.isCancelled());
    }

    // 4 ------------------------------------------------------------------------

    @Test
    void bug4_tamedPetsCannotBypassConsent() {
        Wolf wolf = world.spawn(at(1, 5, 0), Wolf.class);
        wolf.setOwner(attacker);

        EntityDamageByEntityEvent bite = Fx.byEntity(wolf, wolf, defender,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, DamageType.MOB_ATTACK);
        assertTrue(bite.isCancelled(), "the owner is the attacker");

        EntityPushedByEntityAttackEvent push = Fx.call(new EntityPushedByEntityAttackEvent(defender,
                EntityKnockbackEvent.Cause.ENTITY_ATTACK, wolf, new Vector(0.4, 0.1, 0)));
        assertTrue(push.isCancelled(), "and its knockback");
    }

    // 5 ------------------------------------------------------------------------

    @Test
    void bug5_aBrokenConfigIsNeverOverwritten() throws Exception {
        File config = new File(plugin.getDataFolder(), "config.yml");
        String broken = "messages:\n  prefix: \"<red>My server\n  pvp_enabled: [unclosed\n\tbad: tab\n";
        Files.writeString(config.toPath(), broken, StandardCharsets.UTF_8);

        Harness.reload(plugin);

        assertEquals(broken, Files.readString(config.toPath(), StandardCharsets.UTF_8));
        assertTrue(Fx.melee(attacker, defender).isCancelled(), "and the plugin keeps protecting players");
    }

    // 6 ------------------------------------------------------------------------

    @Test
    void bug6_crystalsKeepTheirPlacerAndBlameTheDetonator() {
        Harness.consent(plugin, attacker, true);
        Harness.consent(plugin, defender, true);
        Harness.consent(plugin, bystander, false);
        Block base = world.getBlockAt(0, 4, 3);
        base.setType(Material.OBSIDIAN);
        EnderCrystal crystal = world.spawn(at(0, 5, 3), EnderCrystal.class);
        Fx.call(new EntityPlaceEvent(crystal, attacker, base, BlockFace.UP));

        // The old hack re-tagged every crystal near anyone right-clicking with a crystal.
        bystander.teleport(at(0, 5, 2));
        Fx.call(new PlayerInteractEvent(bystander, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.END_CRYSTAL),
                base, BlockFace.UP));
        server.getScheduler().performTicks(2);

        EntityDamageByEntityEvent unattributed = Fx.byEntity(crystal, null, defender,
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, DamageType.EXPLOSION);
        assertFalse(unattributed.isCancelled(), "still the consenting placer's crystal");

        EntityDamageByEntityEvent detonated = Fx.byEntity(crystal, bystander, defender,
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, DamageType.EXPLOSION);
        assertTrue(detonated.isCancelled(), "the non-consenting player who detonated it is blamed");
    }

    // 7 ------------------------------------------------------------------------

    @Test
    void bug7_lavaPickupClearsOwnership() {
        Harness.consent(plugin, attacker, false);
        Harness.consent(plugin, defender, false);
        Block target = world.getBlockAt(0, 5, 0);
        Block clicked = world.getBlockAt(0, 4, 0);
        clicked.setType(Material.STONE);

        Fx.call(new PlayerBucketEmptyEvent(attacker, target, clicked, BlockFace.UP, Material.LAVA_BUCKET,
                new ItemStack(Material.LAVA_BUCKET)));
        Fx.call(new PlayerBucketFillEvent(attacker, target, clicked, BlockFace.UP, Material.BUCKET,
                new ItemStack(Material.LAVA_BUCKET)));

        // Unrelated lava later flows into the same spot.
        target.setType(Material.LAVA);
        EntityDamageEvent burn = Fx.environmental(defender, EntityDamageEvent.DamageCause.LAVA, DamageType.LAVA, null);
        assertFalse(burn.isCancelled(), "picked-up lava grants no lasting immunity");
    }

    @Test
    void bug7_lavaIsAttributedToTheNearestOwner() {
        Harness.consent(plugin, attacker, false);
        Harness.consent(plugin, bystander, true);
        Harness.consent(plugin, defender, true);
        pour(attacker, -4);
        pour(bystander, 1);

        EntityDamageEvent burn = Fx.environmental(defender, EntityDamageEvent.DamageCause.LAVA, DamageType.LAVA, null);
        assertFalse(burn.isCancelled(), "the closer, consenting owner is responsible");
    }

    private void pour(PlayerMock who, int x) {
        Block target = world.getBlockAt(x, 5, 0);
        Block clicked = world.getBlockAt(x, 4, 0);
        clicked.setType(Material.STONE);
        Fx.call(new PlayerBucketEmptyEvent(who, target, clicked, BlockFace.UP, Material.LAVA_BUCKET,
                new ItemStack(Material.LAVA_BUCKET)));
        target.setType(Material.LAVA);
        world.getBlockAt(x, 6, 0).setType(Material.LAVA);
    }

    // 8 ------------------------------------------------------------------------

    @Test
    void bug8_anchorsDetonatedWithAnEmptyHandAreAttributed() {
        Harness.consent(plugin, attacker, false);
        Harness.consent(plugin, defender, true);
        Block anchor = world.getBlockAt(3, 5, 0);
        anchor.setType(Material.RESPAWN_ANCHOR);
        RespawnAnchor data = (RespawnAnchor) anchor.getBlockData();
        data.setCharges(1);
        anchor.setBlockData(data);

        Fx.call(new PlayerInteractEvent(attacker, Action.RIGHT_CLICK_BLOCK, null, anchor, BlockFace.UP));
        EntityDamageEvent blast = Fx.environmental(defender, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                DamageType.BAD_RESPAWN_POINT, at(3, 5, 0));

        assertTrue(blast.isCancelled(), "the detonating player is the attacker");
    }

    // 9 ------------------------------------------------------------------------

    @Test
    void bug9_splashDenialsDoNotRevealInvisiblePlayers() {
        defender.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 600, 0));
        ThrownPotion potion = world.spawn(at(0, 6, 0), ThrownPotion.class);
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.POISON);
        item.setItemMeta(meta);
        potion.setItem(item);
        potion.setShooter(attacker);

        Map<LivingEntity, Double> affected = new HashMap<>();
        affected.put(defender, 1.0);
        PotionSplashEvent splash = Fx.call(new PotionSplashEvent(potion, affected));

        assertEquals(0.0, splash.getIntensity(defender));
        String told = Fx.joined(Fx.drain(attacker));
        assertFalse(told.isEmpty());
        assertFalse(told.contains("Defender"), "the invisible player's name is not leaked: " + told);
    }

    // 10 -----------------------------------------------------------------------

    @Test
    void bug10_damageAlreadyCancelledByAnotherPluginIsLeftAlone() {
        org.bukkit.damage.DamageSource source = org.bukkit.damage.DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(attacker).withDirectEntity(attacker).build();
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(attacker, defender,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 2);
        hit.setCancelled(true);
        Fx.call(hit);
        assertTrue(Fx.drain(attacker).isEmpty(), "no denial message for a hit WorldGuard already blocked");
    }

    // 11 -----------------------------------------------------------------------

    @Test
    void bug11_prefixIsApplied() {
        attacker.performCommand("pvp status");
        String first = Fx.drain(attacker).get(0);
        assertTrue(first.startsWith("[ConsentPVP]"), first);
    }

    @Test
    void bug11_toggleToTheCurrentStateIsANoOp() {
        Harness.veteran(attacker);
        Harness.consent(plugin, attacker, true);
        attacker.performCommand("pvp enable");
        attacker.performCommand("pvp disable");
        assertFalse(Harness.hasConsent(plugin, attacker), "the no-op enable must not start the cooldown");
    }

    @Test
    void bug11_statusIsColouredByState() {
        Harness.consent(plugin, attacker, false);
        attacker.performCommand("pvp status");
        String legacy = Fx.drainLegacy(attacker).get(0);
        assertTrue(legacy.contains("§cdisabled"), legacy);
    }

    @Test
    void bug11_extinguishedFireStopsCountingAsTheIgnitersFire() {
        Harness.consent(plugin, attacker, false);
        Harness.consent(plugin, defender, false);
        Block fire = world.getBlockAt(5, 5, 0);
        Block below = world.getBlockAt(5, 4, 0);
        below.setType(Material.STONE);
        fire.setType(Material.FIRE);
        Fx.call(new BlockIgniteEvent(fire, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, attacker));

        // Punched out.
        Fx.call(new PlayerInteractEvent(attacker, Action.LEFT_CLICK_BLOCK, null, below, BlockFace.UP));
        fire.setType(Material.AIR);

        defender.teleport(at(5, 5, 1));
        EntityDamageEvent burn = Fx.environmental(defender, EntityDamageEvent.DamageCause.FIRE,
                DamageType.IN_FIRE, null);
        assertFalse(burn.isCancelled());
    }

    @Test
    void bug11_cooldownReadsLikeFourMinutesThirtyTwoSeconds() {
        Harness.veteran(attacker);
        Harness.consent(plugin, attacker, false);
        attacker.performCommand("pvp enable");
        Fx.drain(attacker);
        attacker.performCommand("pvp disable");
        String told = Fx.joined(Fx.drain(attacker));
        assertFalse(told.contains("minutes and"), told);
        assertTrue(told.matches("(?s).*\\b(\\d+m )?\\d+s\\b.*"), told);
    }
}
