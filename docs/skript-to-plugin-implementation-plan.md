# Skript → Java Plugin Conversion: Implementation Plan

**Target:** Convert the full uploaded Skript pack (14 active scripts, ~4,300 lines) into one
professional, modular, lightweight Java plugin — call it **`CoreSuite`** for now
(placeholder name, easy to rename in `plugin.yml`/package later).

**Short answer to "can we": yes.** Everything in this archive is well within normal
Bukkit/Paper plugin territory. Converting it will make it lighter (no Skript parser
overhead per trigger, no SkBee/skript-reflect dependency, compiled bytecode instead
of interpreted script), and far more configurable (real `config.yml` per module
instead of magic numbers baked into function calls).

This is a big scope — 14 independent systems. Below is the deep analysis of what
you actually have, then the architecture and phased build plan.

---

## 1. What's in the archive

| File | Lines | System | Depends on |
|---|---|---|---|
| `playtimereward.sk` + `playtimerewardgui.sk` | 722 + 222 | Playtime-gated reward ladder w/ AFK filtering, paginated claim GUI | SkBee (stat access), Vault |
| `combatTimer.sk` | 130 | PvP combat-tag, command block, combat-log punish | UltraCosmetics (allowlist, per your memory) |
| `customfish.sk` | 124 | Fishing loot table, streak/session multiplier, angler leveling | Vault/economy commands, ExcellentCrates |
| `automessager.sk` | 113 | Rotating broadcast messages | — |
| `mention.sk` | 45 | Chat @mention highlighting + toggle | — |
| `reward.sk` | 79 | Single hardcoded redeem-code command | Vault, UltraCosmetics, ExcellentCrates |
| `world-manager.sk` | 71 | Lobby/hub/last-location teleport | Multiverse (via console commands) |
| `jumppad.sk` | 153 | Physical launch pads via Vector math | — |
| `topkills-podium.sk` | 152 | Syncs Citizens NPC skins to leaderboard top 5 | Citizens, SkinsRestorer, PlaceholderAPI, ajLeaderboards |
| `BankNotes.sk` + `banknotes-gui.sk` | 503 + 84 | Physical currency notes (NBT-identity) + withdraw GUI | Vault, SkBee (NBT) |
| `ChatGames.sk` | 854 | Trivia/math/word-scramble chat minigame w/ leaderboard | — |
| `referral.sk` | 313 | Referral code + reward system | Vault, UltraCosmetics |
| `vulcan-punish.sk` | 590 | 7-tier anticheat punishment escalation w/ webhook | Vulcan Anticheat, Discord webhook |
| `clearlag.sk` | 85 | Scheduled + on-demand entity clearing with countdown | — |

Plus one dormant folder (`PlaytimeReward/*.yml`) — empty scaffolding, not live data,
safe to ignore.

**Total surface:** ~4,300 lines of Skript, 14 command trees, roughly 60 permission
nodes, and 6 external plugin integrations (Vault, Citizens, SkinsRestorer,
PlaceholderAPI, UltraCosmetics, Vulcan). None of this requires SkBee or
skript-reflect once it's real Java — those were only needed to reach into Bukkit/NMS
APIs from Skript. A native plugin talks to those APIs directly.

---

## 2. Recommended architecture: one plugin, modular internals

Given you explicitly want a **single dynamic multi-purpose plugin**, not 14 separate
jars, the cleanest professional pattern (same one EssentialsX/CMI use) is:

```
CoreSuite/
├── CoreSuite-api/          (optional, only if you want other plugins to hook in later)
├── CoreSuite-plugin/
│   ├── src/main/java/net/yourname/coresuite/
│   │   ├── CoreSuitePlugin.java          <- main class, module loader
│   │   ├── module/
│   │   │   ├── Module.java               <- interface: enable(), disable(), name()
│   │   │   ├── ModuleManager.java        <- reads config, registers enabled modules
│   │   ├── modules/
│   │   │   ├── playtime/                 <- PlaytimeRewardModule
│   │   │   ├── combat/                   <- CombatTagModule
│   │   │   ├── fishing/                  <- CustomFishingModule
│   │   │   ├── automessage/              <- AutoMessengerModule
│   │   │   ├── mentions/                 <- MentionModule
│   │   │   ├── rewardcodes/              <- RedeemCodeModule
│   │   │   ├── worldmanager/             <- WorldManagerModule
│   │   │   ├── jumppad/                  <- JumpPadModule
│   │   │   ├── podium/                   <- KillPodiumModule
│   │   │   ├── banknotes/                <- BankNotesModule
│   │   │   ├── chatgames/                <- ChatGamesModule
│   │   │   ├── referral/                 <- ReferralModule
│   │   │   ├── vulcanpunish/             <- VulcanPunishModule
│   │   │   └── clearlag/                 <- ClearLagModule
│   │   ├── common/
│   │   │   ├── config/                   <- typed config loader (per-module config.yml)
│   │   │   ├── storage/                  <- SQLite/HikariCP data layer + DAOs
│   │   │   ├── economy/                  <- Vault wrapper
│   │   │   ├── gui/                      <- reusable inventory-GUI framework
│   │   │   ├── text/                     <- MiniMessage/legacy color + placeholder util
│   │   │   ├── command/                  <- brigadier-lite command framework + /coresuite help
│   │   │   └── integration/              <- soft-dependency wrappers (Citizens, SkinsRestorer, PAPI, Vulcan)
│   ├── src/main/resources/
│   │   ├── plugin.yml
│   │   ├── config.yml                    <- master toggle file (which modules are enabled)
│   │   └── modules/*.yml                 <- one config per module, hot-reloadable
```

**Why modular-inside-one-plugin, not 14 separate plugins:**
- Single dependency to install/update on the server.
- Shared services (economy hook, storage, GUI framework, placeholder resolution,
  permission checks) get written **once** instead of copy-pasted 14 times — this is
  where most of the "lighter and more maintainable" win comes from.
- Each module still fully toggles independently (`modules.enabled: [...]` in
  `config.yml`), so you can disable e.g. Vulcan-punish without touching anything else.
- One unified `/coresuite help`, `/coresuite modules`, `/coresuite reload` admin
  surface, while each module keeps its own player-facing command (`/playtime`,
  `/bank`, `/chatgame`, etc.) exactly like today — no muscle-memory relearning for
  your players.

**Why not one single giant "god command"** (e.g. everything under `/core fish`,
`/core bank`) — real professional multi-purpose plugins (EssentialsX, CMI) keep
each subsystem's command namespace separate and only centralize *admin* and *help*
commands. I'd keep that pattern rather than forcing all 14 systems behind one verb
tree, since your existing command names are already what players know.

---

## 3. Cross-cutting engineering upgrades (this is where "lighter" comes from)

1. **Storage: SQLite via HikariCP, not Skript variable-flatfile.**
   Several modules (playtime, banknotes, chat games, referral, vulcan offense
   history) are pure per-player key/value or per-player-history data — exactly what
   Skript's `variables.csv`/flatfile was straining under. A single SQLite file with
   indexed tables, written through async tasks, removes disk I/O from the main
   thread entirely and scales to a much bigger playerbase.
2. **No more per-second "does anything even need to run" loops running unconditionally.**
   `playtimereward.sk`'s AFK loop, `jumppad.sk`'s every-tick pad scan, and
   `clearlag.sk`'s broadcast chain all become event-driven or use Paper's
   region/async schedulers where possible, and early-exit immediately when 0 players
   are online (some already do this — keep it, apply it everywhere).
3. **One shared GUI framework** instead of 3 independent hand-rolled inventory-click
   handlers (`playtimerewardgui.sk`, `banknotes-gui.sk`, and the ad-hoc inventory
   checks elsewhere) — cuts duplicate "cancel event / match by slot" boilerplate to
   near zero per module.
4. **Config-driven everything.** Every reward table, message list, threshold, and
   tier definition currently hardcoded inside a Skript function
   (`_ptr_registerDefaultRewards`, the fishing `_rewards::*` table, the trivia
   question banks, the Vulcan 7-tier ladder) moves into per-module YAML that hot
   reloads with `/coresuite reload <module>` — no more editing script source to
   change a reward tier.
5. **Real permission tree registered in `plugin.yml`**, with sane defaults
   (`op`/`false`), instead of ad-hoc string permission checks scattered through
   trigger code — this is what makes the plugin feel "professional" in a LuckPerms
   tab-complete sense.

---

## 4. Per-module notes, issues found, and design decisions

### PlaytimeRewards (core + GUI) — highest complexity, do first
- Direct port of the "raw vanilla stat minus tracked AFK seconds plus admin
  adjustment" model — it's a genuinely good design, keep it. In Java: read
  `Statistic.PLAY_ONE_MINUTE` directly via `Player#getStatistic()`, no SkBee needed.
- Reward table moves from 19 chained `_ptr_defineReward(...)` calls into
  `modules/playtime.yml` — a list of reward objects (id, required-seconds, material,
  display, commands, money, permission, repeatable/interval, sound, slot).
- Reproduce the claim-lock + cooldown + double-claim guards exactly — that's
  concurrency-safety logic worth keeping, not simplifying.
- GUI: paginated chest inventory, "claim all" button, progress bar — straightforward
  1:1 port into the shared GUI framework.
- **Bug to fix in the port:** `_ptr_getOnlinePlayer` falls back to `player` (the
  command sender) if the target isn't found online — that's a silent
  wrong-player bug in the current script's `/playtimereward claim` admin path; the
  Java version should hard-fail with a clear error instead.

### CombatTag
- Straightforward: `damage(EntityDamageByEntityEvent)`, per-second `BukkitRunnable`
  countdown via action bar, block commands while tagged, combat-log kill-on-quit.
- Keep the allowlist-based UltraCosmetics gadget block (per your memory, this
  replaced a blocklist that broke on real item names) — move the allowlist into
  config instead of hardcoding it.

### CustomFishing
- Loot table (currently a 16-row `_rewards::*` string-split table) becomes a proper
  YAML list of weighted entries with `rarity`, `chance`, `item`/`command`, `sound`.
- Streak/fatigue math (sqrt-curve streak bonus, session fatigue decay) is genuinely
  nice tuning — port the formulas as-is, expose the constants (0.045, 0.6, 25,
  0.03) in config so you can retune without recompiling.

### AutoMessenger
- Trivial: rotating index into a broadcast list on a timer. Config-driven message
  list + interval, `/coresuite automessage reload|toggle|list|add|remove`.

### ChatMentions
- **Bug to fix:** current substring match (`message contains "%loop-player%"`)
  false-positives on partial name collisions (a player named `Al` triggers on any
  message containing "Al", e.g. "Alright"). Java port should tokenize the message
  and match whole words only.

### Reward Codes (`reward.sk`)
- Currently a single hardcoded code baked into the script. Per your memory you've
  since built a proper `/reward <code>` system with UUID-tracked redemption and
  admin management — this module should absorb that design directly rather than
  re-deriving the older single-code version: multiple codes, each with its own
  reward set, expiry, and max-uses, stored in `modules/rewardcodes.yml`.

### WorldManager
- Replace Multiverse console-command dispatch (`mv tp %player% spawn`) with direct
  Bukkit `Location`/`World` teleports where the destination is a plain location
  (lobby spawn, last-location) — only keep an external call if you genuinely need
  Multiverse's world-inheritance/portal behavior for something specific. Otherwise
  this whole module needs zero external dependency.

### JumpPad
- Vector launch math ports 1:1 to Java (`org.bukkit.util.Vector`), no more `import:`
  workaround needed since it's native Java now. Pad/destination storage moves from
  flat variables into SQLite (name → pad location, dest location).
- The current "loop all players × loop all pads every tick" is O(players × pads)
  every 20 ticks/second — fine at small scale, but worth switching to a spatial
  check (only test the block directly under a player when they move, via
  `PlayerMoveEvent` with a coarse from/to-block filter) rather than a blind tick
  loop once you have more than a handful of pads.

### KillPodium (topkills-podium.sk)
- Heaviest external-integration module: Citizens `SkinTrait`, SkinsRestorer's
  `PlayerStorage`/Mojang API fallback, PlaceholderAPI resolution of
  `ajlb_lb_*_name` placeholders. All three have real Java APIs — this is actually
  a *cleaner* port than the Skript version, since you can call
  `SkinsRestorerProvider.get()` and `CitizensAPI` directly instead of through
  skript-reflect's import shim.
- Keep the "skip entirely if 0 players online" guard and the placeholder-didn't-
  resolve detection (`contains "%%"`) — both are good defensive checks worth
  preserving verbatim.

### BankNotes + GUI
- The NBT-based note identity (serial + secret token, decoupled from display name)
  is a solid design per your memory — port via `PersistentDataContainer` instead of
  SkBee NBT tags, which is the "real" Bukkit-native way to do this and is actually
  more robust than SkBee's NBT compound access.
- Redemption, `/claimnote` (recover renamed notes), and the withdraw-amount GUI all
  port cleanly into the shared GUI framework.

### ChatGames — largest single file (854 lines), do this one in its own pass
- Three question generators (trivia bank with anti-repeat picker + aliases, math,
  word-scramble) plus a leaderboard. This is naturally three `QuestionProvider`
  implementations behind one interface in Java — cleaner than the current
  `generateXQuestion()` function-per-type structure, and makes adding a 4th game
  type later trivial.
- Trivia bank (currently likely inline in `loadChatGameConfig()`) moves to
  `modules/chatgames-trivia.yml` so you can edit/add questions without touching code.

### Referral
- Straightforward code-generation + reward-on-redeem + staff-alert system. Port
  reward tiers to config; keep the periodic cleanup timer as a scheduled task.

### VulcanPunish — second largest file (590 lines), security-sensitive
- The 7-tier escalation ladder, offense-expiry window, and staff alerting port
  directly. **Per your memory, the current config has an exposed webhook URL** —
  in the Java port this must load from `config.yml` (which you keep out of version
  control / share carefully) rather than being hardcoded in source, and the webhook
  call should go through an async `HttpClient` request, never on the main thread.
- Also re-verify the ping-threshold and allowlisted-client issues you flagged
  previously when you port the trigger conditions — this is a good opportunity to
  fix both while the logic is already being rewritten.

### ClearLag
- Convert the `wait N seconds` broadcast chain into a single `BukkitRunnable` that
  ticks down and fires broadcasts at fixed remaining-time checkpoints, rather than
  a script literally sleeping across ~9 sequential wait statements — functionally
  identical to players, but doesn't hold a thread through the whole countdown.

---

## 5. Unified command & help surface

- `/coresuite help` — auto-generated from each module's registered subcommands
  (paginated, permission-filtered so players only see commands they can use).
- `/coresuite modules` — list enabled/disabled modules and their status.
- `/coresuite reload [module]` — hot-reload one module's config or all of them.
- Each module keeps its existing player-facing command name (`/playtime`,
  `/bank`, `/chatgame`, `/launchpad`, `/referral`, `/clearlag`, `/reward`, `/hub`,
  etc.) — a `/coresuite` alias table can additionally expose them as
  `/coresuite playtime`, `/coresuite bank`, etc. for a single-entry-point feel if
  you want that, purely as a router.

---

## 6. Build order (phased, so the server always has a working subset)

| Phase | Modules | Why this grouping |
|---|---|---|
| **0. Foundation** | Core plugin skeleton, ModuleManager, config layer, SQLite storage layer, Vault wrapper, shared GUI framework, `/coresuite` admin command | Nothing else can start until this exists |
| **1. Low-risk, no external deps** | AutoMessenger, ChatMentions, ClearLag, WorldManager, JumpPad | Validates the module system end-to-end on simple, self-contained logic before tackling the hard ones |
| **2. Economy-integrated** | BankNotes+GUI, Reward Codes, Referral | Exercises the Vault wrapper and the shared GUI framework a second time |
| **3. The big one** | PlaytimeRewards + GUI | Highest line count, most stateful logic, most valuable to get right — do it once the framework is proven on phases 1–2 |
| **4. Gameplay systems** | CombatTag, CustomFishing | PvP/event-driven logic |
| **5. Content-heavy** | ChatGames | Biggest file, benefits from the QuestionProvider interface being designed carefully |
| **6. External-integration heavy** | KillPodium, VulcanPunish | Depends on Citizens/SkinsRestorer/PAPI and Vulcan being present on your dev/test server to verify against real APIs |

Each phase ships as a working plugin build — you're never without a functioning
server between phases, since old Skript files for not-yet-ported modules can stay
active until their Java module is ready, then get retired one at a time.

---

## 7. What I need from you to start Phase 0

- Confirm the plugin/package name (placeholder `net.yourname.coresuite` above).
- Confirm target: Paper 1.21.x API (matches your `Server: Paper 26.1.2` comment in
  the playtime script — I'll assume that build line) and Java 21.
- Confirm build tool preference: Maven or Gradle (Gradle is a bit lighter for
  multi-module layouts like this, Maven if you're more comfortable there).
- Confirm whether ExcellentCrates has a plugin API you want me to hook into
  directly, or whether it's fine to keep dispatching its crate-key console commands
  as the current scripts do (safe default, keeps the module decoupled from an
  ExcellentCrates version bump).

Once confirmed I'll scaffold Phase 0 (project skeleton, `pom.xml`/`build.gradle`,
`plugin.yml`, module interface, config/storage/GUI/economy layers) as the first
concrete deliverable.
