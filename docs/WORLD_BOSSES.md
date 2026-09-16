# World boss encounters

## Participation and rewards

A player participates by attacking the boss, damaging its minions, taking damage from the boss/minions, or applying the Water/Night Vision weakness counter. Being online or standing nearby alone does not qualify. Every recorded participant receives the configured participation tier, with at least one boss shard. The top three damage contributors receive their rank tier in addition to the participation tier. The former five-percent damage threshold no longer excludes participants.

Rewards are saved per player in `coresuite_boss_rewards`, including participants who disconnect before victory. Delivery runs on join and `/boss claim`. Items wait for enough inventory space; currency waits for a working economy. Command rewards run for the claimant when they are online. The default crate commands require a compatible crate plugin and configuration.

`/bossadmin rewards` lists up to 25 pending receipts. State 0 is awaiting delivery, 1 is acknowledged, and 2 is reserved or has an uncertain outcome. Do not automatically reset state 2: check the receipt, inventory/economy outcome and logs first. Minecraft player saves, Vault and SQL cannot share an atomic transaction. Database enqueue failures log a recovery payload; disk/provider failures are not an exactly-once delivery guarantee.

## Adaptive encounters

Only nearby participants active in the last 60 seconds count toward scaling. The pool defaults to:

`configured health × 0.60 × (1 + 0.50 × (fighters - 1))`

| Fighters | Valerius | Aethelgard | Xol'thos |
| --- | --- | --- | --- |
| 1 | 720 | 1,200 | 1,680 |
| 2 | 1,080 | 1,800 | 2,520 |
| 4 | 1,800 | 3,000 | 4,200 |
| 10 | 3,960 | 6,600 | 9,240 |

These values assume the supplied base health configuration. Scaling preserves the fraction of health remaining when players join or leave. It refreshes during combat; participants remain eligible for rewards even when no longer counted in scaling. Boss-attributed outgoing damage starts at 65% for a solo encounter and rises to 100% for larger groups. Environmental damage and unattributed attacks cannot kill the physical entity behind the virtual health pool.

All healing shares a lifetime budget of 15% of maximum health, normalized across scaling changes. Aethelgard's arrows deal half damage rather than healing him, and soil healing is capped per pulse. Xol'thos defaults to 25% evasion, capped at 35%; Night Vision remains a useful weakness, but ordinary attacks can defeat her. Healing cannot reverse phases.

## Spawning

Scheduled encounters can spawn with one online player. Failed attempts retry after five minutes. Bosses allow ten minutes for players to arrive, and three empty minutes after engagement before abandoning the encounter.

Searches load chunks asynchronously, use the block above the surface as the feet position, check the world border and ground, and require a clear 3×3 area with four blocks of headroom (harmless passable plants are allowed). No unchecked fallback location is used. Cave searches stay underground. Reload/disable cancels pending searches; a pending attempt cannot create a second active encounter. Biome fallback, attempts, timeout, distances and allowed worlds remain configurable.

Totems and administrator spawns use the same location check. Right-click a suitable ground block to summon. An unsafe spot retains the totem. Boss spawn chunks are held while the encounter is active, and cleanup only removes plugin-tagged bosses rather than ordinary named mobs.

On first upgrade, the exact former stock values (scheduled minimum 3, evasion 0.90, soil regeneration 30) become 1, 0.25 and 6 respectively. Other custom values are retained, with runtime safety caps. New `balance` and timeout options are merged into existing configurations. The `balance.defaults-upgraded` marker makes this a one-time migration.

## Validation

Fifty-six JUnit regressions and 238 existing checks pass. Tests cover scaling/progress, healing limits, invalid damage, participation, inventory capacity without partial mutation, durable reward reservation/reconciliation, crate key preparation, and spawn clearance/borders/chunks. The isolated Paper probe exercises real boss entities on 1.21 and 26.2: spawning, bounded healing, defeat, capped overkill credit, and one reward callback per boss. It uses a simulated participant, not an authenticated gameplay client. Multiplayer difficulty and third-party protection/economy workflows still need staging playtests.

Player-issued `/bossadmin spawn <boss>` now searches a bounded nearby loaded area when the exact position is unsuitable. Explicit coordinates are kept exact. Rejections show the actual reason in chat and console; protection denials are not silently treated as terrain failure.

## Encounter state, arena safety and contribution

Each live encounter has a unique ID and progresses through `PREPARING`, `FIGHTING`, then either `DEFEATED` or `ABANDONED`. Rewards only run from `DEFEATED`. The arena checker samples its centre and perimeter for loaded terrain, border access and protection, so a boss does not begin across a claim or an unloaded edge.

CoreSuite tracks transient fire and cobwebs it places for a fight. It restores them at expiry and terminal cleanup only when the block still matches the encounter's own placed block, preserving later player edits.

`rewards.participation.minimum-damage` defaults to `20.0` and `minimum-contribution` defaults to `3`. Damage grants contribution activity; tanking boss/minion damage gives two points; a successful Water/Night Vision counter gives three; and a minion kill gives one. Meeting either threshold earns participation loot. The player action bar shows qualification status while fighting. Per-boss values under `<boss>.loot.participation` override the global values.

Valerius builds heat when he uses abilities. At the configured threshold he exposes his core for eight seconds and takes 1.35× damage, rather than continuing overlapping ability pressure. Water cools the meter and applies Quenched. Xol'thos only ignores attacks during her clear Shadow Phase; Night Vision reveals and stuns her. The old Xol'thos evasion setting remains readable for existing configurations but defaults to zero for the new behavior.

## ExcellentCrates rewards and recovery

Stock `crate/crates/excellentcrates key give %player% <key> [amount]` rewards use the installed ExcellentCrates key API. Existing configuration and pending command payloads do not need rewriting. Physical rewards use real provider key items, wait for enough inventory space, and never drop overflow. Virtual keys use the provider's balance methods and check the resulting balance. Missing keys or unavailable APIs leave the reward pending and report why; custom commands outside this supported form retain command dispatch behavior.

The Paper 26.2 integration probe with ExcellentCrates 6.6.1 and NightCore 2.16.4 confirms that full inventory defers delivery, freeing a slot permits delivery of a provider-recognized key, and an unknown key ID is deferred. This test uses a simulated inventory; virtual balance behavior has unit coverage.

For a receipt already marked `RESERVED / REVIEW`, `/boss claim` intentionally does not replay it. Run `/bossadmin rewards` to see its command and inspect the player's inventory/virtual balance and provider logs. If no reward was delivered, use `/bossadmin rewards retry <receipt>` then have the player free space and run `/boss claim`. If it was already delivered or manually compensated, use `/bossadmin rewards delivered <receipt>`. These admin actions are logged, refuse active claims, and cannot reopen acknowledged rewards. Do not retry an uncertain receipt without checking: command return values alone do not prove whether a plugin changed inventory or balance.

Provider references: [ExcellentCrates commands](https://nightexpressdev.com/excellentcrates/commands/) and [6.6.1 key manager source](https://github.com/nulli0n/ExcellentCrates-spigot/blob/d9ba2393a5dc812368379acc907f61a34aabb7bb/src/main/java/su/nightexpress/excellentcrates/key/KeyManager.java).
