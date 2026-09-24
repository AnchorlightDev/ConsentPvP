package org.modularsoft.consentpvp.ui;

import dev.anchorlight.stonelib.bedrock.BedrockForms;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.modularsoft.consentpvp.consent.ConsentService;
import org.modularsoft.consentpvp.util.Messages;

import java.util.function.Supplier;

/**
 * Shows a player their PvP status with a way to change it: clickable [Enable] / [Disable] buttons
 * in chat, or a native form for a Bedrock player, who cannot click chat.
 */
public final class StatusPresenter {

    private final ConsentService consent;
    private final Messages messages;
    private final Supplier<BedrockForms> forms;

    public StatusPresenter(ConsentService consent, Messages messages, Supplier<BedrockForms> forms) {
        this.consent = consent;
        this.messages = messages;
        this.forms = forms;
    }

    /** The coloured status word, for any message's %status%. */
    public Component statusWord(boolean enabled) {
        return messages.plain(enabled ? "status_enabled" : "status_disabled");
    }

    /** {@code /pvp} and {@code /pvp status}. */
    public void showStatus(Player player) {
        present(player, "pvp_status");
    }

    /** The one-time first-join explainer. */
    public void explain(Player player) {
        present(player, "first_join");
    }

    private void present(Player player, String key) {
        boolean enabled = consent.hasConsent(player.getUniqueId());
        Component status = statusWord(enabled);
        if (sendForm(player, enabled)) {
            messages.send(player, key, "status", status);
            return;
        }
        messages.sendWith(player, key, toggleButtons(), "status", status);
    }

    public Component toggleButtons() {
        return messages.buttonRow(
                messages.button("button_enable", "button_enable_hover", "/pvp enable"),
                messages.button("button_disable", "button_disable_hover", "/pvp disable"));
    }

    private boolean sendForm(Player player, boolean enabled) {
        BedrockForms bedrock = forms.get();
        if (!bedrock.isBedrock(player.getUniqueId())) {
            return false;
        }
        return bedrock.sendModal(player,
                messages.bedrock("bedrock_toggle_title"),
                messages.bedrock("bedrock_toggle_content", "status", statusWord(enabled)),
                messages.bedrock("bedrock_button_enable"),
                messages.bedrock("bedrock_button_disable"),
                enable -> consent.requestToggle(player, enable));
    }
}
