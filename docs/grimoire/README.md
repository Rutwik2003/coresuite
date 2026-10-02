# MeerMC Grimoire, encounters and contracts

Latest visual/menu revision: [Astral Archive](ASTRAL_ARCHIVE.md), including the new acceptance checklist.

## Current build
An opt-in expansion for CoreSuite: eight spells, persistent spell preparation, mana, traveling scholars, material/fish trades, shared stock, merchant contracts, elemental boss rewards, signature inscriptions, Enchanter upgrades, page binding and an advanced wand.

**Start testing with [TEST_PLAN.md](TEST_PLAN.md).** It contains setup commands, expected outcomes, costs, two-player tests and rollback instructions. The local gameplay server is deliberately stopped. No magic resource pack has been published.

## Restore points and deliverables
- Before this expansion: CoreSuite GitHub `test` checkpoint `0739fcd`.
- Before any magic: `e2cc0b5`, tag `backup/pre-grimoire-2026-10-02`.
- Build: `build/libs/CoreSuite-1.0.0.jar`.
- Java test pack: `build/resourcepacks/meermc-magic-test.zip`.
- Bedrock test overlay: `build/resourcepacks/meermc-magic-test.mcpack`.
- Local installed copy: `.local/local-playtest/`; safety backup: `backups/pre-phase2/` within it.
- Source rollback and database rollback are separate: restore a matched plugin/database backup while stopped.

## Enabling the modules
`config.yml` module keys `grimoire`, `world-encounters` and `side-quests` remain false by default. All three are true in the isolated gameplay copy. New YAML keys merge automatically; existing administrator values stay intact. Storage uses the shared database manager; profile writes and reward claims use revision checks and SQL transactions.

## Spells and mana
Mana Dart, Ember Lance, Tidal Arrow, Frost Lance, Zoltraak, Dimension Step, Aura of the Hearth, Harvester's Touch.
- Default mana: 100; recovery 2 per online second after a 3-second casting pause; hunger-independent.
- Tier II/III reduce cost 5%/10% and increase damage 15%/30%. Values are bounded; mana does not refill on book swaps or relogging.
- Hearth heals 1 health point per second at tier I, 1.25/1.5 at tiers II/III. Overlapping fields use the highest pulse; walls block healing. Duration 8/10/12 seconds, radius 4/5/6 blocks. It does not restore hunger.
- Harvester uses the player break pipeline and replant placement events. One plain matching inventory seed/carrot/potato is consumed per replant. With no supplies it only harvests.
- Water extinguishes/pushes, frost slows and fire briefly burns only after accepted damage. Pets/NPCs are excluded. Ordinary animals are configurable targets.
- Damage uses normal Bukkit protection events. Teleporting stops at walls, unloaded terrain and arena restrictions. These are deliberate improvements over the original armor-bypassing/wall-passing concept.
- PvP is off by default. Optional zones require both players in the same configured cuboid; world PvP and normal WorldGuard damage cancellation still apply. Per-spell `pvp: false` disables individual offensive spells against players.
- Input gestures are configurable. Held-right-click repeats use a quiet-gap filter because books do not supply a reliable release event. Commands remain available for touch clients.

## Progression
- Start with two prepared slots; configure starting/max slots within 2–5.
- Duplicates redeem once per sealed token and grant five research plus a persistent spell-specific duplicate credit.
- Tier II default: 12 research, $100k, 5 elemental shards, 1 duplicate credit.
- Tier III: 30 research, $350k, 15 shards, 2 duplicate credits. Per-spell tier overrides supported.
- Binding pages 3/4/5 costs 30/60/100 research and $100k/$200k/$400k; optional arcane-shard requirements.
- `/encounters setenchanter` places the utility NPC. Upgrades/binding require proximity by default; administrators can configure that requirement.
- Wand binding requires maximum slots, five spells, 100 research, $500k, and 25 of each elemental shard. Bound owners get +10% recovery and can use `/magic cast` with their wand carried. Gestures still require holding a focus, preserving ordinary item controls. `/magic wand` retrieves the item after binding or loss.

## Encounters and contracts
Eight temporary scholar types use safe loaded terrain near online Survival players, with configurable biome weights, worlds, duration, stock, materials, exact custom-fish species, money and virtual shard costs. Stock is global per visit and reserved transactionally; a cancelled/uncertain reservation conservatively consumes one stock slot for that visit. Scholars teach permanent unlocks rather than manufacturing tradeable items in a crash-sensitive delivery step.

The river scholar accepts exact custom-fish species from player inventory first, then unlocked fish-bag pages. The existing fishing Angler remains a separate NPC. Custom item names cannot substitute for authenticated fish metadata.

`/quests` opens eight configurable multi-objective contracts. One active contract per player, accepted near the matching scholar. Objectives include named spells, hostile mob kills, fish catches, magic harvesting and eligible boss participation. Rewards are saved research, shards, optional unlocks and duplicate credits. Repeat cooldowns persist. `/quests chapter` keeps the original introductory chapter progression available.

## Boss rewards
Magic shards are virtual and separate from boss-equipment-shop shards. Valerius → fire; Aethelgard → nature; Xolthos → arcane; Borealis → ice. Defaults: MVP 3 shards/3 research; other eligible participants 1 shard/2 research. Signature fragments have a configurable 2% default chance for Xolthos/Aethelgard. Research, shards and fragments for one reward are inserted atomically and cannot be claimed twice. Existing boss equipment remains shop-only.

`/magic inscribe zoltraak` or `aura-of-the-hearth` uses signature fragments at the Enchanter. If already learned, inscription grants a duplicate credit. Boss contract progress requires being online when the new eligible reward is processed; offline currency/shard receipts remain claimable.

## Resource pack
Java uses solid cuboid Grimoire/tome/wand models, compact hand transforms, distinct colour/symbol pairs, and exact model-ID fallback ranges. This replaces the old transparent-image extrusion. Bedrock has matching flat icons and original spell audio; it does not yet use identical Java-style 3D held geometry.

Book IDs: 71001 Grimoire; 71002 legacy generic tome; 71010–71017 the eight spells. Blaze rod 71030 is the wand. Existing tome inventory items migrate their model ID without changing their redemption token. Vanilla items keep their normal models.

Sources: `pack-publisher/scripts/build_magic_models.py`, `pack-publisher/scripts/magic_art_direction.py`, and `resourcepack/`. Current covers use authored vector primitives rendered with Pillow; the SVG directory retains the earlier icon drafts. Generated Java/Bedrock mappings share item IDs. No image-generation tool was used for this revision; earlier generated artwork has been replaced.

Standard Geyser-Spigot layouts receive missing mappings on plugin load. Old mappings are backed up, custom entries preserved, and the original shared tome icon upgraded. Custom-ID collisions require operator review. Proxy/custom layouts use `deployment/geyser/custom_mappings/coresuite_magic.json` manually. Restart Geyser after mapping changes.

**The separate pack repository stays local on `codex/grimoire-pack-local-test`. Its main branch auto-publishes: do not push main or run its release workflow until explicitly approved.**

## Payment safety and limits
The database can atomically save virtual resources and stock; Vault, player inventories and fish bags are separate systems. Persisted pending intents record all costs before external debits. A failed or ambiguous payment never automatically replays charges. Inspect `/magicadmin pending PLAYER`, verify actual external payments, then reconcile `paid` or `cancel`. Cancellation does not issue a refund and does not replenish visit stock. Fish-bag payment must be confirmed saved before the spell unlock completes.

Runtime bounds: one traveling scholar, 96 animated projectiles, 16 Hearth fields, bounded input/progress queues, no spell-driven chunk generation. SQL uses the shared executor/pool with closed statements/connections and indexes for receipt claims. Reload/disable removes effects and NPCs. Confirm performance under real player load in the test guide.

## Validation boundary
Automated build, SQL, input, model-mapping and progression tests complement startup checks. Real Java/Bedrock visuals, touch input, payment-provider behavior, multiplayer balance and actual MySQL deployment still require acceptance testing. Additional spell concepts (Anti-Magic Burst, Zephyr's Leap, Aura of the Sea), procedural dungeons and broader world-story campaigns are not part of this eight-spell core release.
