# MeerMC — Complete current shop update and event plan

The broad rebalance and extra-item additions were reverted after the user's scope correction. Original equipment, potion, shulker, spawner, disabled-item and price settings are restored. Earlier reports and the earlier package are in `.local/archive/scratch/shop-audit/superseded/` and must not be deployed.

The earlier addition is 26 sulfur/cinnabar building blocks on Natural Blocks page 4, using its existing 28-slot layout. Prices range from $150 to $450; all new blocks are buy-only. No heavy core, mace, armor, tools, shulker boxes, potions, mob buckets or rare progression items were added. See `added-items.csv` for the complete current list.

`.local/archive/scratch/shop-audit/shop-changes.zip` contains `shops/Building/natural.yml` and the subsequently authorized `shops/Z_EverythingElse.yml` copper fix. Back up the live file before installing. Existing pages and settings were verified against the original backup; every other original config file, except the subsequently authorized Z_EverythingElse copper changes, is byte-identical to that backup. YAML parsing and page capacity passed. Runtime GUI testing has not been performed.

If the earlier ZIP was already installed on a server, this two-file package does not undo those deployed changes. Restore the affected files from your live backup and remove `shops/RecentSurvival.yml` and `sections/RecentSurvival.yml` before installing this corrected file. The local pre-edit backup is `.local/archive/scratch/shop-audit/original-configs.zip`; use it only if it matches your live configuration.

The historical transaction findings remain available for review. Only the six Z_EverythingElse copper-slab resale paths and three duplicate copper purchase prices have subsequently been fixed. Other broad price changes remain reverted. Future exploit fixes should be targeted to confirmed paths while preserving intentional exclusions and disabled categories.

## Authorized targeted copper fix

Six exposed/weathered/oxidized cut copper slabs (waxed and unwaxed) now sell for $30 instead of $40. Three waxed exposed/weathered/oxidized copper blocks now cost $400 instead of $300, matching Decorative Blocks. Exactly nine price fields changed; no bone-block, equipment, potion, spawner or other settings changed. YAML and the supported plain-item recipe scan passed, with zero Z_EverythingElse resale loops found in that model. Live permissions and cross-plugin conversions are not covered.

Use `.local/archive/scratch/shop-audit/copper-fix-only.zip` to install only this correction; it contains `shops/Z_EverythingElse.yml`. The combined `.local/archive/scratch/shop-audit/shop-changes.zip` also includes the previously authorized sulfur/cinnabar additions. Back up live files before installing while the server is stopped.

## Historical audit findings — not additional applied changes

Reviewed 22,515 logged transactions. Exactly attributable historical payouts included $59,319,540 for trapped chests, $28,359,648 for waxed weathered copper, $14,920,425 for redstone blocks, and $5,190,690 for rotten flesh. These identify currency sources, not proof of cheating. Mixed baskets were not assigned to individual items. Full totals are in `historical-sales.csv`.

The broad farm nerfs, disabled catch-all sales, potion/book resale changes, diamond equipment price increases, menu rearrangements and extra progression items were reverted. Those are NOT included in the current update. Other suspected resale paths outside the targeted copper changes have not been fixed.

## Current files

- `.local/archive/scratch/shop-audit/shop-changes.zip`: complete current update, containing only Natural Blocks and Z_EverythingElse YAML files.
- `.local/archive/scratch/shop-audit/copper-fix-only.zip`: just the nine copper price corrections.
- `added-items.csv`: all 26 new building blocks and their prices.
- `targeted-copper-changes.json`: exact nine before/after price values.
- `.local/archive/scratch/shop-audit/original-configs.zip`: original local configuration backup.
- `.local/archive/scratch/shop-audit/superseded/`: rejected earlier package and reports; do not deploy.

## Events that work with 4–5 players

Start with one reliable weekly 30–45 minute event, at a time your regulars choose. Announce 24 hours ahead and run it even if only two players arrive. Publish screenshots afterwards and invite players to suggest next week's theme.

1. **Cooperative scavenger hunt:** six clues around existing builds; everyone contributes, with an optional timed second round. Give each finisher one UC key, once per event. Budget a maximum of five keys initially.
2. **Fishing evening:** a 20-minute contest with separate longest-fish and unusual-catch prizes. Let each person win only one prize; use one UC key per winner, not large cash payouts.
3. **Small-build challenge:** 30 minutes, supplied equal building kits, theme revealed at start. Vote after a short tour. Offer a Discord showcase and one cosmetic key rather than gear or repeatable money.
4. **Boss preparation night:** spend 15 minutes preparing together, then attempt one boss. Use the normal boss rewards, without extra key/shard multipliers; rotate who leads the group.
5. **Sulfur Cube course:** a small supervised obstacle/puzzle course using the new mob, with personal best times instead of a large-player-count requirement. Test containment and grief protection first.

These are proposals, not scheduled events or changes to reward commands. Avoid unattended hourly cash giveaways: they reward idling and work against the harder economy.


## Current repository locations

Deployable configs are tracked under `deployment/economyshopgui/`. ZIPs/backups remain local under `.local/archive/scratch/shop-audit/`. The full supplied shop snapshot is under `.local/server-data/economy shop gui/`. The later request for a large server-wide event is covered in [the Frozen Kingdom event plan](SERVER_EVENT_PLAN.md); the small activities above remain optional ideas.
