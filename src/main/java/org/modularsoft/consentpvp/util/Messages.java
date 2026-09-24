package org.modularsoft.consentpvp.util;

import dev.anchorlight.stonelib.message.Buttons;
import dev.anchorlight.stonelib.message.MessageService;
import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * Every player-facing string, read from the {@code messages:} section of config.yml.
 *
 * <p>Templates use {@code %name%} placeholders, as they always have. Values are substituted as
 * inert text (or as pre-built components), never concatenated into the template, so a player name
 * or any other runtime value cannot inject MiniMessage tags. The chat prefix is applied to every
 * chat message; action bars and form text are unprefixed.
 *
 * <p>Sends to a player run on that player's own thread, which matters on Folia when the message is
 * about somebody else, such as the shooter of an arrow in another region.
 */
public final class Messages {

    private final MessageService service;
    private final PlatformScheduler scheduler;
    private final Supplier<AttemptMessageDelivery> delivery;

    public Messages(Supplier<? extends ConfigurationSection> section, PlatformScheduler scheduler,
                    Supplier<AttemptMessageDelivery> delivery) {
        this.service = new MessageService(section).legacyPercentPlaceholders(true);
        this.scheduler = scheduler;
        this.delivery = delivery;
    }

    public void reload() {
        service.reload();
    }

    /** A prefixed chat message. */
    public Component chat(String key, Object... pairs) {
        return service.getNamed(key, pairs);
    }

    /** An unprefixed fragment: a status word, a button label, an action bar. */
    public Component plain(String key, Object... pairs) {
        return service.getNamedUnprefixed(key, pairs);
    }

    public boolean has(String key) {
        return service.has(key);
    }

    public void send(CommandSender sender, String key, Object... pairs) {
        if (sender == null || !service.has(key)) {
            return;
        }
        deliver(sender, chat(key, pairs));
    }

    /** Sends a prefixed message with {@code suffix} (usually buttons) after it. */
    public void sendWith(CommandSender sender, String key, Component suffix, Object... pairs) {
        if (sender == null || !service.has(key)) {
            return;
        }
        Component message = chat(key, pairs);
        if (suffix != null) {
            message = message.append(Component.space()).append(suffix);
        }
        deliver(sender, message);
    }

    /** Sends an already-built component with the prefix in front. */
    public void sendComponent(CommandSender sender, Component body) {
        deliver(sender, service.prefixed(body));
    }

    public void actionBar(Player player, Component message) {
        scheduler.runOn(player, () -> player.sendActionBar(message));
    }

    /**
     * A PvP denial notice, to chat or the action bar as configured. {@code button} is only shown in
     * chat, where it can be clicked.
     */
    public void attempt(Player player, String key, Component button, Object... pairs) {
        if (player == null || !service.has(key)) {
            return;
        }
        if (delivery.get() == AttemptMessageDelivery.ACTION_BAR) {
            actionBar(player, plain(key, pairs));
            return;
        }
        sendWith(player, key, button, pairs);
    }

    /** A clickable button whose label and hover come from config. */
    public Component button(String labelKey, String hoverKey, String command) {
        return Buttons.runCommand(plain(labelKey), command, service.has(hoverKey) ? plain(hoverKey) : null);
    }

    public Component buttonRow(Component... buttons) {
        return Buttons.row(plain("button_separator"), buttons);
    }

    /** Plain text with legacy colour codes, which is all a Bedrock form can show. */
    public String bedrock(String key, Object... pairs) {
        return LegacyComponentSerializer.legacySection().serialize(plain(key, pairs));
    }

    private void deliver(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            scheduler.runOn(player, () -> player.sendMessage(message));
        } else {
            sender.sendMessage(message);
        }
    }
}
