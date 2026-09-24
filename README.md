# ConsentPVP

## Overview

**ConsentPVP** is a Paper plugin that makes player-versus-player combat opt-in. Two players can
only hurt each other when both have PvP enabled, or when they have agreed to a one-off duel.
Every indirect route is covered, from arrows and tamed wolves to lava, fire, TNT, end crystals,
respawn anchors and harmful potions.

It runs on **Paper 1.21.11** and on **Folia**, and needs **Java 21**.

---

## Features

- **Mutual consent.** Players turn PvP on or off for themselves. Damage, knockback, setting
  someone alight and harmful potions are all blocked unless both players consent.
- **One set of attribution rules.** The responsible player is worked out the same way for every
  source, in this order:
  1. vanilla's own damage attribution
  2. a projectile's shooter
  3. whoever lit the TNT
  4. a pet's owner
  5. tracked ownership of end crystals, poured lava, lit fire, and detonated respawn anchors and beds

  Tracked ownership expires after a configurable time.
- **Natural hazards still hurt.** Your own bed or anchor, and lava or fire nobody placed, hurt you
  whatever your setting. Consent only protects you from other players.
- **Potion filtering.** Only harmful effects (poison, instant damage, slowness, …) are blocked.
  Healing, regeneration and other beneficial effects always apply, including the beneficial part of
  a mixed potion.
- **Combat tag.** Dealing or taking PvP damage tags both players for a short time. While tagged:
  - they cannot turn PvP off, and are told how long is left;
  - teleport commands (`/home`, `/spawn`, `/tpa`, …) and teleports caused by commands or other
    plugins are blocked;
  - ender pearls, chorus fruit, elytra gliding and firework boosting, and riptide can each be
    blocked separately.

  A live timer shows in the action bar or a boss bar, with sounds on entering and leaving combat.
  The tag ends on death. Logging out while tagged carries no penalty.
- **Toggle cooldown.** Outside combat, a short cooldown applies between toggles. Toggling to the
  state you are already in does nothing and starts no cooldown.
- **Duels.** `/pvp duel <player>` asks for a one-off fight. Accepting it gives just those two
  players consent with each other, without changing either player's own setting. The duel ends on
  death, logout or a time limit. Requests expire, and the same player cannot be re-challenged
  until a cooldown passes.
- **New-player protection.** New players cannot enable PvP until they have a configurable amount
  of playtime (three hours by default).
- **Respawn protection.** Players cannot take PvP damage for a few seconds after respawning.
  Attacking someone ends it early.
- **Clickable chat.**
  - `/pvp` shows your status with **[Enable]** / **[Disable]** buttons.
  - A blocked attack offers an **[Enable PvP]** button when you are the one with PvP off.
  - Duel requests come with **[Accept]** / **[Deny]** buttons.
- **Bedrock players.** With [Floodgate](https://geysermc.org/wiki/floodgate/) installed, Geyser
  players get native forms for the toggle, duel requests and the first-join explainer instead of
  clickable chat. Without Floodgate everything still works.
- **First-join explainer.** Each player sees a short explanation of PvP consent once, with the
  toggle.
- **Anonymity.** Vanished and invisible players are never named in denial messages.
- **Name tag indicators.** A ⚔ prefix shows each player's status. This is unavailable on Folia,
  which does not support scoreboards.
- **Public API and event.** Other plugins can query consent and veto changes; see
  [Developer API](#developer-api).
- **Update checker.** Tells the console and admins when a newer GitHub release is out.
- **bStats metrics.** Includes consent-on share, duels per day and Bedrock support.

---

## Installation

1. Download the latest JAR from the [Releases page](https://github.com/ModularSoftAU/ConsentPvP/releases).
2. Put it in your server's `plugins` folder. Floodgate is optional.
3. Restart the server.

Upgrading keeps your existing `config.yml` values and `playerdata.yml`. New settings are merged into
`config.yml` automatically, with your comments and customised messages kept.

---

## Commands

| Command | Who | Description |
|---|---|---|
| `/pvp` or `/pvp status` | players | Shows your PvP status with clickable toggle buttons. |
| `/pvp enable` / `/pvp disable` | players | Turns your PvP consent on or off. |
| `/pvp duel <player>` | players | Challenges a player to a duel. |
| `/pvp accept [player]` / `/pvp deny [player]` | players | Answers a duel request. The newest one is used if you give no name. |
| `/pvp check <player>` | staff, console | Shows consent, toggle cooldown, combat tag, active duel and new-player protection. |
| `/pvp set <player> on\|off` | staff, console | Forces a player's consent, ignoring cooldowns and protection. |
| `/pvp bypass <player>` | staff, console | Clears a player's toggle cooldown and combat tag. |
| `/pvp newbie clear <player>` | staff, console | Lifts new-player protection early. |
| `/pvp death` | staff, console | Toggles whether PvP turns off when a player dies. |
| `/pvp reload` | staff, console | Reloads `config.yml`. |

Tab completion only suggests the commands you are allowed to run.

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `consentpvp.use` | everyone | Use `/pvp`. |
| `consentpvp.duel` | everyone | Send and answer duel requests. |
| `consentpvp.admin` | op | The staff commands above, and update notices on join. It does **not** bypass consent or the combat tag. |
| `consentpvp.newbie.bypass` | nobody | Skips new-player protection. |

---

## Configuration

`config.yml` is versioned (`config-version`). Every section is commented in the file itself; the
main ones are:

| Section | What it controls |
|---|---|
| `pvp.disable-on-death` | Turn PvP off when a player dies. |
| `cooldown.duration` | Minutes between toggles outside combat (decimals allowed). |
| `combat-tag.*` | Tag length, `display` (`action_bar`, `boss_bar` or `none`), sounds, `blocked-commands`, and one switch per escape route: `block-command-teleports`, `block-ender-pearls`, `block-chorus-fruit`, `block-elytra`, `block-riptide`. |
| `duels.*` | On/off, request timeout, re-challenge cooldown, maximum duel length. |
| `newbie-protection.*` | On/off and the required playtime in minutes. |
| `respawn-protection.*` | On/off and the length in seconds. |
| `potions.harmful-effects` | Which effects count as harmful. Everything else always applies. |
| `ownership.*` | How long placed lava, fire, crystals and anchor detonations stay attributed, and how often they are cleaned up. |
| `denial.throttle-seconds` | Minimum gap between repeated denial messages from continuous sources such as lingering clouds. |
| `first-join.enabled`, `bedrock.enabled`, `update-checker.*`, `metrics.enabled` | The optional features above. |
| `messages.*` | Every message the plugin sends, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Placeholders use `%name%`. The `prefix` is applied to every chat message. Leave a message empty to disable it. |
| `indicators.*` | Name tag prefixes. |

**A broken `config.yml` is never overwritten.** If the file has a YAML error, the plugin logs the
line and column, leaves the file untouched, and runs on the built-in defaults until you fix it and
run `/pvp reload`.

---

## Developer API

```java
ConsentPvPAPI api = Bukkit.getServicesManager().load(ConsentPvPAPI.class);
if (api != null && api.canFight(attacker, victim)) {
    // ...
}
```

| Method | Returns |
|---|---|
| `canFight(Player attacker, Player defender)` | Both players consent (or are dueling each other), and the defender is not respawn-protected. |
| `hasConsent(UUID)` | The player's own setting, online or offline. |
| `isInCombat(UUID)` | Whether the player is combat-tagged. |
| `isDueling(UUID, UUID)` | Whether these two players are in an active duel. |

`PvPConsentChangeEvent` fires before any consent change and can be cancelled. Its cause is
`COMMAND`, `DEATH` or `ADMIN`. Add `softdepend: [ConsentPVP]` to your `plugin.yml`.

---

## Folia

ConsentPvP declares `folia-supported: true`. All scheduling goes through region-aware schedulers:
work on a player runs on that player's thread, block work runs on the owning region, and data
cleanup runs asynchronously. Name tag indicators are disabled on Folia because it has no
scoreboard support.

---

## Building

```shell
mvn verify
```

This needs JDK 21 and produces `target/ConsentPvP-<version>.jar`. It depends on
[StoneLib](https://github.com/AnchorlightDev/StoneLib) 2.4.0 from JitPack. StoneLib, BoostedYAML
and bStats are shaded and relocated; everything else is provided by the server.

The test suite runs on [MockBukkit](https://github.com/MockBukkit/MockBukkit) with JUnit Jupiter (the
JUnit 5 programming model; version 6, which MockBukkit 4.116 is built against). GitHub
Actions runs `mvn verify` on every push and pull request.

---

## Support

If you find an issue or have a suggestion, please open an issue on our GitHub repository or join our
Discord community.
