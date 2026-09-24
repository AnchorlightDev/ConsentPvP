package org.modularsoft.consentpvp.consent;

import org.bukkit.entity.Player;
import org.modularsoft.consentpvp.api.ConsentPvPAPI;
import org.modularsoft.consentpvp.combat.CombatTagManager;
import org.modularsoft.consentpvp.duel.DuelManager;

import java.util.UUID;

/** The implementation registered with the ServicesManager. */
public final class ConsentApi implements ConsentPvPAPI {

    private final ConsentService consent;
    private final CombatTagManager combat;
    private final DuelManager duels;

    public ConsentApi(ConsentService consent, CombatTagManager combat, DuelManager duels) {
        this.consent = consent;
        this.combat = combat;
        this.duels = duels;
    }

    @Override
    public boolean canFight(Player attacker, Player defender) {
        return attacker != null && defender != null
                && consent.canFight(attacker.getUniqueId(), defender.getUniqueId());
    }

    @Override
    public boolean hasConsent(UUID player) {
        return consent.hasConsent(player);
    }

    @Override
    public boolean isInCombat(UUID player) {
        return player != null && combat.isTagged(player);
    }

    @Override
    public boolean isDueling(UUID first, UUID second) {
        return first != null && second != null && duels.isDueling(first, second);
    }
}
