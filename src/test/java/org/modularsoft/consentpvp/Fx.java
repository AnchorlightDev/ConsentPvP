package org.modularsoft.consentpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

/** Event and message helpers shared by the tests. */
public final class Fx {

    private Fx() {
    }

    public static <T extends Event> T call(T event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    /** A melee hit, with the damage source vanilla builds for one. */
    public static EntityDamageByEntityEvent melee(Player attacker, Player defender) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(attacker).withDirectEntity(attacker).build();
        return call(new EntityDamageByEntityEvent(attacker, defender, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                source, 2));
    }

    /** Damage by an entity, with explicit causing and direct entities. */
    public static EntityDamageByEntityEvent byEntity(Entity damager, Entity causing, Player defender,
                                                     EntityDamageEvent.DamageCause cause, DamageType type) {
        DamageSource.Builder builder = DamageSource.builder(type).withDirectEntity(damager);
        if (causing != null) {
            builder.withCausingEntity(causing);
        }
        return call(new EntityDamageByEntityEvent(damager, defender, cause, builder.build(), 4));
    }

    /** Damage with no entity behind it: lava, fire, a block explosion. */
    public static EntityDamageEvent environmental(Player defender, EntityDamageEvent.DamageCause cause,
                                                  DamageType type, Location at) {
        DamageSource.Builder builder = DamageSource.builder(type);
        if (at != null) {
            builder.withDamageLocation(at);
        }
        return call(new EntityDamageEvent(defender, cause, builder.build(), 4));
    }

    public static org.bukkit.event.entity.PlayerDeathEvent death(Player player) {
        return call(new org.bukkit.event.entity.PlayerDeathEvent(player,
                DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>(), 0, ""));
    }

    /** Every chat message the player has received since the last call, as plain text. */
    public static List<String> drain(PlayerMock player) {
        List<String> out = new ArrayList<>();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            out.add(PlainTextComponentSerializer.plainText().serialize(next));
        }
        return out;
    }

    /** Every message, with legacy colour codes, so colour can be asserted. */
    public static List<String> drainLegacy(PlayerMock player) {
        List<String> out = new ArrayList<>();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            out.add(LegacyComponentSerializer.legacySection().serialize(next));
        }
        return out;
    }

    public static String joined(List<String> messages) {
        return String.join("\n", messages);
    }
}
