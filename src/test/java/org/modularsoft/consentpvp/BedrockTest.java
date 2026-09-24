package org.modularsoft.consentpvp;

import dev.anchorlight.stonelib.bedrock.BedrockField;
import dev.anchorlight.stonelib.bedrock.BedrockForms;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bedrock players get forms instead of clickable chat; everyone else is unaffected. */
class BedrockTest extends PluginTest {

    /** Stands in for Floodgate: the named players are on Bedrock. */
    static final class FakeForms implements BedrockForms {
        final Set<UUID> bedrock;
        final List<String> titles = new ArrayList<>();
        Consumer<Boolean> lastChoice;

        FakeForms(Set<UUID> bedrock) {
            this.bedrock = bedrock;
        }

        @Override public boolean available() { return true; }
        @Override public boolean isBedrock(UUID player) { return bedrock.contains(player); }

        @Override
        public boolean sendModal(Player player, String title, String content, String first, String second,
                                 Consumer<Boolean> onChoice) {
            titles.add(title + "|" + first + "|" + second);
            lastChoice = onChoice;
            return true;
        }

        @Override
        public boolean sendButtons(Player player, String title, String content, List<String> buttons, IntConsumer onChoice) {
            return false;
        }

        @Override
        public boolean sendCustom(Player player, String title, List<BedrockField> fields,
                                  Consumer<Map<String, Object>> onSubmit, Runnable onClosed) {
            return false;
        }
    }

    private PlayerMock bedrock;
    private PlayerMock java;
    private FakeForms forms;

    @BeforeEach
    void players() {
        bedrock = player("BedrockPlayer", false);
        java = player("JavaPlayer", false);
        forms = new FakeForms(Set.of(bedrock.getUniqueId()));
        plugin.setBedrockForms(forms);
    }

    @Test
    void theToggleIsAFormForBedrockPlayers() {
        bedrock.performCommand("pvp");
        assertEquals(List.of("PvP|Enable PvP|Disable PvP"), forms.titles);
        forms.lastChoice.accept(true);
        assertTrue(plugin.consent().hasConsent(bedrock.getUniqueId()), "the form's answer runs the toggle rules");
    }

    @Test
    void javaPlayersStillGetChatButtons() {
        java.performCommand("pvp");
        assertTrue(forms.titles.isEmpty());
        assertFalse(ToggleTest.commands(java.nextComponentMessage()).isEmpty());
    }

    @Test
    void duelRequestsAreAFormToo() {
        java.performCommand("pvp duel BedrockPlayer");
        assertEquals(List.of("Duel request|Accept|Deny"), forms.titles);
        forms.lastChoice.accept(true);
        assertTrue(plugin.duels().isDueling(java.getUniqueId(), bedrock.getUniqueId()));
    }

    @Test
    void theDenialButtonIsLeftOffForBedrock() {
        PlayerMock target = player("Target", true);
        Fx.melee(bedrock, target);
        assertTrue(ToggleTest.commands(bedrock.nextComponentMessage()).isEmpty());
    }

    @Test
    void bedrockSupportCanBeTurnedOffInConfig() {
        plugin.configFile().set("bedrock.enabled", false);
        plugin.reloadPluginConfig();
        bedrock.performCommand("pvp");
        assertTrue(forms.titles.isEmpty());
    }

    @Test
    void withoutFloodgateEverythingFallsBackToChat() {
        plugin.setBedrockForms(null);
        assertFalse(plugin.bedrockForms().available());
        bedrock.performCommand("pvp duel JavaPlayer");
        java.performCommand("pvp accept");
        assertTrue(plugin.duels().isDueling(java.getUniqueId(), bedrock.getUniqueId()));
    }
}
