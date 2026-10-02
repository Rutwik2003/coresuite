# MeerMC magic — full local acceptance test

Prepared 2 October 2026. This is an acceptance checklist, not a claim that these player tests have passed.

## Start here
- Local server is deliberately STOPPED. Run `.local/local-playtest/Start-Local.ps1` when home.
- Minecraft Java 26.2: connect to `localhost:25565`. Accept the new required local pack.
- This copy listens on 127.0.0.1 only. Friends cannot join it remotely without a separate, deliberate networking setup.
- World data is copied from your old backup. CoreSuite uses its own local SQLite database and fresh plugin configurations. Live website/Discord integration and the GitHub pack updater are disabled here.
- CoreSuite, Vault/Essentials, WorldGuard/FAWE and ExcellentCrates are installed. No Geyser is installed in this local gameplay copy yet. The old Essentials version warns that this Paper version is unsupported; check actual money movements carefully.
- Use `/stop` before closing Windows or replacing JARs. Do not hot-swap a running plugin.
- Java pack: `build/resourcepacks/meermc-magic-test.zip`. Bedrock test overlay: `build/resourcepacks/meermc-magic-test.mcpack`.
- No magic resource pack was published to the live GitHub repository.

## Test accounts and preparation
Use your existing player for regression checks. Use another test account for the natural first-time journey, or back up local CoreSuite data before resetting yourself.

Operator setup (replace PLAYER with the exact player name):
```
/magicadmin start PLAYER
/magicadmin research PLAYER 500
/eco give PLAYER 5000000
/encounters setenchanter
```
Research admin command ADDS research. Shards, duplicates, mana, slots and tier commands below SET their value.
```
/magicadmin shards PLAYER fire 100
/magicadmin shards PLAYER ice 100
/magicadmin shards PLAYER arcane 100
/magicadmin shards PLAYER nature 100
/magicadmin shards PLAYER zoltraak-fragment 2
/magicadmin shards PLAYER hearth-fragment 2
```
Keep separate screenshots of your balance and `/magic shards` before each purchase. Do not use these admin grants in the natural progression test.

## 1. Resource pack and old-item migration
- [ ] Accept the pack; no client missing-texture or model errors.
- [ ] Grimoire is compact in main hand and offhand, with no stretched edge lines.
- [ ] Give tomes with `/magicadmin tome PLAYER <spell>` for all eight IDs below. Every cover and symbol differs.
- [ ] Existing tomes become the right model within five seconds in inventory. Their names and sealed tokens remain usable once only.
- [ ] Check inventory GUI, third-person hand, dropped item and item frame.
- [ ] Vanilla books/blaze rods retain their normal appearance.
- [ ] `/magicadmin wand PLAYER` gives a distinct wand. Test its two hands and dropped model.

Spell IDs: `mana-dart`, `fireball`, `water-cannon`, `frost-lance`, `zoltraak`, `dimension-door`, `aura-of-the-hearth`, `harvesters-touch`.

## 2. Natural introduction, controls and mana
- [ ] Fresh account meets a Hedge Mage: `/encounters spawn hedge-mage`. It receives Mana Dart and a Grimoire, starting with 100 mana and two prepared slots.
- [ ] `/grimoire` prepares/removes only learned spells and respects slot capacity.
- [ ] Right-click casts, sneak-left-click cycles, sneak-right-click opens the menu.
- [ ] Hold right-click ten seconds: no continuous mana drain. Release briefly (default 350 ms gap) before another click. Try both air and blocks.
- [ ] `/magic cast` and `/magic next` work as touch alternatives.
- [ ] Ordinary food in the main hand still works with a Grimoire in the offhand.
- [ ] Insufficient mana reports the required amount; cooldown reports remaining seconds.
- [ ] Stop casting: recovery begins after three seconds at two mana per online second. Hunger is irrelevant; mana never exceeds its cap.
- [ ] Swap books, die and reconnect: no free mana refill or erased cooldown.
- [ ] `/magic toggle` pauses casting; repeat enables it.
- [ ] `/quests chapter`: cast five spells, claim, then discover two encounters. Same encounter cannot count twice. Last chapter is five fish, repeatable after 24 hours.

## 3. Spell behavior
Use `/magicadmin unlock PLAYER <spell>` for tier I and `/magicadmin tier PLAYER <spell> 2` or `3` for scaling. These are admin testing shortcuts.

| Spell | Expected behavior |
|---|---|
| Mana Dart | Small bright projectile; base 4 damage before armor/events |
| Ember Lance | Flame/smoke trail, impact splash capped at five extra targets, brief fire after uncancelled damage |
| Tidal Arrow | Twin watery accents, damage, extinguish and modest push after uncancelled damage |
| Frost Lance | Snowflake trail, damage and brief Slowness I after uncancelled damage |
| Zoltraak | White/violet piercing projectile; tier I/II/III can hit 1/2/3 distinct targets |
| Dimension Step | Safe short teleport, stops at walls/unloaded terrain; rejected in boss arenas |
| Hearth | Warm boundary; half a heart/second at tier I, higher at II/III, 8/10/12 seconds |
| Harvester | Mature wheat/carrots/potatoes/beetroot near your feet; replants using plain matching inventory supplies |

- [ ] Distinct trails read clearly in daytime, night and rain. No excessive particles or client FPS drop.
- [ ] Zombies, skeletons, spiders, ghasts and ordinary farm animals take damage.
- [ ] Pets, NPCs, villagers and players outside configured PvP zones do not.
- [ ] In Survival, take damage and reduce hunger. Hearth heals while inside; starvation can still cause damage. Leaving range or standing behind a wall stops healing.
- [ ] Two Hearth fields overlap: healing does not multiply. A teammate/raid ally can benefit; unrelated players cannot.
- [ ] Harvester with supplies consumes exactly one seed/produce per replant. No supplies: harvest only. Named/custom items must not be consumed.
- [ ] Immature crops and protected crops remain untouched. Test protection with a NON-OP account; operators can bypass WorldGuard.

## 4. Merchant lessons and shared stock
Natural scholars appear near online Survival players in configured worlds/biomes, using loaded safe terrain. One is active at a time. Default duration 15 minutes and interval 30 minutes after that visit.

Admin: `/encounters despawn`, then `/encounters spawn <type>`.

| Scholar | Lesson | Additional payment beyond configured research | Stock per visit |
|---|---|---|---:|
| hedge-mage | Harvester | 64 oak logs | 3 |
| river-sage | Tidal Arrow | 16 prismarine shards + 3 custom `atlantic_cod` fish | 3 |
| illusionist | Dimension Step | $150k + 16 ender pearls | 3 |
| ancient-scholar | Zoltraak | $250k + 5 arcane shards | 3 |
| frost-pilgrim | Frost Lance | 64 packed ice + 3 ice shards | 3 |
| hearthkeeper | Hearth | 16 honey bottles + 3 nature shards | 3 |
| ember-scribe | Ember Lance | 16 blaze rods + 3 fire shards | 3 |
| greenwarden | Harvester | 64 wheat + 32 iron ingots | 3 |

- [ ] Discovery gives research once per player per visit. Clicking again shows the task instead of paying another discovery reward.
- [ ] Nearby `/grimoire` shows the lesson and all costs. Too far away or expired scholar: purchase denied.
- [ ] Insufficient money/materials/research/shards/fish: no resource deduction or unlock.
- [ ] Custom named vanilla items cannot substitute for plain materials.
- [ ] River scholar finds exact custom-fish species in inventory first, then unlocked fish-bag pages. Wrong species and locked pages cannot pay.
- [ ] After successful fish trade, reconnect and inspect both inventory and bag; deductions persist.
- [ ] Use multiple fresh accounts to exhaust one visit's stock. Extra buyer receives nothing and pays nothing. One account cannot buy an already learned lesson repeatedly.
- [ ] Rapid repeated GUI clicks do not double-charge or double-unlock.
- [ ] New visit gets new stock; server reload despawns the old visit. A failed reserved purchase conservatively uses a stock slot for that visit.

## 5. Scholar contracts
`/quests` opens clickable cards. Accept near the matching scholar. One active contract at a time; `/quests list` prints counters. Claim through the card or `/quests claim`; `/quests abandon` gives no reward.

| Contract | Objectives | Reward | Repeat |
|---|---|---|---|
| first-sparks | 10 Mana Dart casts + 5 zombie kills | 8 research, 1 arcane, Mana Dart duplicate | 24h |
| river-memory | 10 fish catches + 5 Tidal casts | 10 research, 1 ice, Tidal duplicate | 24h |
| lost-footsteps | 5 Dimension casts + 8 spiders | 10 research, 2 arcane, Dimension duplicate | 24h |
| forbidden-thesis | 10 Zoltraak casts + Xolthos participation | 20 research, 3 arcane, Zoltraak duplicate | 48h |
| broken-winter | 10 Frost casts + Borealis participation | 20 research, 3 ice, Frost duplicate | 48h |
| hearth-oath | 5 Hearth casts + Aethelgard participation | 20 research, 3 nature, Hearth duplicate | 48h |
| ash-and-embers | 10 Ember casts + Valerius participation | 20 research, 3 fire, Ember duplicate | 48h |
| patient-garden | 40 crops harvested with magic | 10 research, 1 nature, Harvester duplicate | 24h |

- [ ] Wrong scholar/out of range cannot accept. Unstarted magic profile cannot accept.
- [ ] Only the specified spell/mob/objective advances its counter; failed casts do not count.
- [ ] Reconnect and restart preserve accepted contract and saved counters.
- [ ] Try claiming early, double-click claiming, abandoning, and accepting during cooldown. No duplicate rewards.
- [ ] Boss contracts require eligible participation and being online when the magic boss award is processed.
- [ ] Test rapid harvesting/kills, then check counters after a few seconds. Report any progress loss, especially on disconnect during a save.

## 6. Boss magic rewards and signature fragments
Existing equipment-shop rewards remain buy-only. Magic adds virtual resources, separate from equipment shards:
- Valerius → fire; Aethelgard → nature; Xolthos → arcane; Borealis → ice.
- Default MVP: 3 elemental shards + 3 research. Other eligible participants: 1 shard + 2 research.
- Xolthos/Aethelgard have a 2% per-recipient signature fragment chance. Do not expect one every kill.
- `/magic research` or login collects pending magic rewards; `/magic shards` displays balances.

- [ ] Kill each boss with two eligible players; check old rewards and new magic balances.
- [ ] Repeated `/magic research` must not multiply rewards.
- [ ] Disconnect before collection, reconnect and claim; rewards survive.
- [ ] No swords, armor or boss-shop equipment is added by magic rewards.
- [ ] At the Enchanter, `/magic inscribe zoltraak` consumes 1 Zoltraak fragment + 5 arcane + 20 research + $150k. Hearth consumes 1 Hearth fragment + 5 nature + 15 research + $100k.
- [ ] Inscription unlocks an unknown signature spell; if already known, it grants one duplicate credit.

## 7. Upgrades, binding and wand
Stand near the saved Mystic Enchanter. Defaults:
- Tier II: 12 research + $100k + 5 matching elemental shards + 1 duplicate credit.
- Tier III: 30 research + $350k + 15 matching shards + 2 duplicate credits.
- Page 3/4/5: 30/60/100 research + $100k/$200k/$400k. Optional per-page arcane-shard costs are configurable.
- Wand: maximum slots, at least 5 learned spells, 100 research, $500k and 25 of EACH elemental shard.
- Duplicate tomes grant 5 research and one spell-specific saved credit. Copied seals still redeem only once.

Testing credits: `/magicadmin duplicates PLAYER fireball 3`.
- [ ] Costs in GUI match deductions. Insufficient resources and maximum tier/page purchases are rejected.
- [ ] Tier II costs 5% less mana; III costs 10% less (rounded to tenths). Damage increases 15% per tier.
- [ ] Move away from Enchanter: upgrades/binding denied unless administrator disabled that location requirement.
- [ ] `/magic wand` binds; run again to retrieve the focus after purchase. Replacements require a free slot and cannot bypass the bound entitlement.
- [ ] Bound wand in inventory permits `/magic cast`; holding it permits gestures. A borrowed unbound wand cannot cast.
- [ ] Wand entitlement and +10% recovery persist. No free mana refill when swapping focuses.

## 8. PvP and protection (requires a second player)
Default PvP is OFF. To test, set `pvp.enabled: true` and define one `pvp.zones.<id>` with `world`, `min-x`, `min-y`, `min-z`, `max-x`, `max-y`, `max-z`, then `/coresuite reload`. Put the players in Survival in the same box and enable world PvP.
- [ ] Both inside the same box: offensive spells can damage.
- [ ] One outside, different boxes/worlds, missing bounds, or disabled PvP: no damage.
- [ ] Same raid party or scoreboard team: no damage.
- [ ] WorldGuard denying PvP/damage cancels both damage and spell after-effects.
- [ ] Set `spells.<id>.pvp: false`; that spell cannot hurt players even inside a zone.
- [ ] Boss arena teleport restrictions and protected farming remain effective for non-operators.

## 9. Persistence, payment recovery and administration
Run normal restart first. Fault-injection tests are optional and belong only on an isolated copy of its database.
- [ ] Spells, tiers, slots, mana, cooldowns, shards, duplicates, contract progress and wand ownership survive `/stop` and restart.
- [ ] Existing phase-one profiles and edited YAML values survive automatic addition of new keys.
- [ ] `/magicadmin pending PLAYER` lists an interrupted purchase's action, cash, materials, fish and virtual costs.
- [ ] Only after reviewing payment logs: `/magicadmin reconcile PLAYER paid` applies once without charging again. `cancel` clears intent and makes NO refund; reserved merchant stock remains consumed.
- [ ] Never mark `paid` unless ALL external payments (cash/materials/fish) are confirmed. SQL and Minecraft inventories/Vault cannot share one transaction. The journal intentionally leaves ambiguous failures for review.
- [ ] Admin setters: `/magicadmin mana PLAYER 50`, `slots PLAYER 3`, `tier PLAYER fireball 2`, `shards PLAYER fire 10`, `duplicates PLAYER fireball 1`.
- [ ] `/magicadmin reset PLAYER confirm` resets magic progression only; pending purchases must be resolved first. Redeemed tome seals remain spent.
- [ ] Regular players cannot use admin commands or extract GUI button items with shift-click, number keys, drag or double-click.

## 10. Bedrock and performance
Bedrock test server only: install updated `deployment/geyser/custom_mappings/coresuite_magic.json` and the new `.mcpack`, replacing the old audio overlay (same UUID). Retain the fish pack. Restart Geyser and reconnect. CoreSuite now merges missing mappings automatically on standard Geyser-Spigot layouts; it backs up the old mapping and keeps custom entries. Conflicting custom IDs require admin review.
- [ ] All books and wand have distinct icons and sounds; touch command alternatives work.
- [ ] Compare Java and Bedrock casts; no double mana debit or inventory duplication.
- [ ] Test two to four simultaneous casters for ten minutes. Record TPS, memory and client FPS; bounds are 96 projectiles and 16 Hearth fields globally.
- [ ] Reload while scholars/fields exist: no orphan NPCs or lingering magic tasks.

## Reporting a problem
Record player/client version, spell and tier, command/action, expected vs actual result, approximate time, relevant console lines, and a screenshot/video. Include whether you were OP, in Creative, inside a claim/arena, using a wand, or had a pending purchase.

## Rollback
Source checkpoint before this expansion: GitHub CoreSuite `test` commit `0739fcd`. Earlier pre-magic tag: `backup/pre-grimoire-2026-10-02`.
The local playtest backup is in `.local/local-playtest/backups/pre-phase2/`. Stop the server before restoring the matching JAR and CoreSuite data together. Do not overwrite your production database with local test data.
