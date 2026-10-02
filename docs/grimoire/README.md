# Astral Grimoire — testing release

## Restore points (before any magic changes)
- Plugin: `e2cc0b5`, tag `backup/pre-grimoire-2026-10-02` on Rutwik2003/coresuite.
- Resource pack: `0ff3450`, same tag on Rutwik2003/meermc-resourcepack.
- Restore the plugin by building that tag in a separate checkout. Do not reset a checkout containing new work.
- Pack release `pack-3-1` is the pre-magic pack. An older release needs an explicitly pinned manifest/repository arrangement; the automatic latest-release updater otherwise moves forward again.
- Live database/config backups must be taken on the actual server before deployment. GitHub stores source, not the live player database or private plugin configs.

## Included
Three toggleable modules: grimoire, world-encounters, side-quests. The latter exposes configurable CAST, ENCOUNTER and FISH chapters, with a daily repeatable chapter. It is not an arbitrary script execution engine.
Personal spell collection, prepared slots (2–5), tier upgrades (I–III), shared mana, cooldowns, single-use authenticated tome tokens, particle projectiles, healing field, safe short blink, protected crop harvesting, traveling scholars and personal research.

Research is gained through encounter discovery, introductory/repeatable quests, duplicate tomes, and boss participation (3 MVP, 2 others). Existing cash, key, shard and shop-equipment rules remain unchanged. Research from bosses is durable for offline players; collect with /magic research or on login. Tier II costs 12 research + $100k; Tier III costs 30 research + $350k. Page 3/4/5 costs 30/60/100 research and $100k/$200k/$400k. All these costs are configurable. Vault must confirm payment; uncertain transactions remain pending for admin review rather than being automatically retried. /magicadmin pending <player> inspects; /magicadmin reconcile <player> paid|cancel applies/clears after checking the economy log. Clearing an intent does not issue a refund.
PvP magic is disabled. Damage uses the ordinary Bukkit damage pipeline and respects cancellation. No armor-bypass direct health writes, wall-passing teleports, terrain explosions, bonus crop yield, or party-wide mana regeneration loops.
Spells: Mana Dart, Ember Lance, Tidal Arrow, Frost Lance, Zoltraak, Dimension Step, Aura of the Hearth, Harvester's Touch. Projectile spells have different effects/colors; Ember Lance splashes up to five additional visible monsters at half damage, Zoltraak gains target piercing by tier. Damage rises 15% per tier. Hearth heals without requiring hunger and gains radius/duration and Dimension Step gains a little range with higher tiers.

## Quick isolated test
1. Replace the JAR while stopped; retain existing configs. Restart (not a plugin hot reload).
2. On the TEST server only, set `modules.grimoire`, `modules.world-encounters` and `modules.side-quests` to true in config.yml, then restart. Defaults are false. Confirm all three modules enabled. Set encounter `worlds` if survival is not named `world`.
3. In unclaimed loaded survival terrain run `/encounters spawn hedge-mage`.
4. Interact with the scholar; receive a Grimoire and Mana Dart. `/magicadmin start <player>` is an admin fallback.
5. Hold the Grimoire and use it to cast. Sneak + left-click cycles; sneak + right-click opens preparation.
6. `/magic next`, `/magic cast`, `/grimoire` and clickable GUI buttons are control alternatives. Touch casting requires a real Bedrock client check.
7. Cast five successful spells, then `/quests` to claim the next chapter. Discover two new encounters for the next reward.
8. Admin testing: `/magicadmin research <player> 100`, `/magicadmin unlock <player> zoltraak`, `/magicadmin tome <player> frost-lance`.
9. `/magic study <spell>` works only near the matching active scholar. `/magic upgrade <spell>` and `/magic bind` spend personal research and configured currency.
10. Restart/rejoin: spells, tiers, quest state, research, mana and cooldowns survive. Duplicate a test tome in creative: its seal can only unlock once globally.

## Mystic Enchanter
Run `/encounters setenchanter` where the spawn NPC should stand. Its location is saved; interaction opens the upgrade/loadout menu. Upgrades also remain accessible through `/magic` for servers that prefer no NPC.

## Resource pack — LOCAL TEST ONLY
No magic assets have been pushed to the live resource-pack repository and no release has been published. Do not push the pack checkout: its main branch publishes automatically. The existing main-server updater stays on the currently published pack.

## Resource pack assets
Custom Grimoire art is mapped to BOOK custom model data 71001; sealed tomes use 71002. Both legacy model overrides and modern item definitions are supplied.
Geyser `custom_mappings/coresuite_magic.json` is installed only when absent during plugin load. The deployment copy is included for custom proxy layouts. Existing mappings are never overwritten. Restart Geyser after adding a mapping; clients must reconnect for new custom item definitions.
The existing automatic Bedrock overlay now includes the Grimoire texture and original spell sounds while preserving its stable pack UUID and the separate fish pack.
Original generated artwork uses the built-in imagegen tool. Prompt: an isolated indigo-and-brass pixel-art ancient Grimoire with a cyan diamond rune in a violet sigil, cream page edges and a crimson bookmark, transparent background. Asset: resourcepack/assets/coresuite/textures/item/astral_grimoire.png.
Spell accents are original synthesized chimes; existing boss BGM is preserved.

## Boundaries / follow-up expansion
The full design is larger than this first testable release. Boss-shard Enchanter trades, general multi-objective quest authoring, dungeon encounters, signature boss quests, Angler fish-bag trades, wands and PvP zone integration remain expansion work. They are not represented as completed by these modules.
Balance and touch/offhand behavior need in-game group testing. Automated tests do not establish combat feel, actual sound playback or mobile usability.

## Testing the pack without touching production
Java: build/resourcepacks/meermc-magic-test.zip is a local client pack. For a separate test server, disable its automatic resource-pack module and clear that test server's server.properties pack URL so the old required pack cannot overwrite the local test pack. Install this ZIP in the test client's resourcepacks folder and select it. Do not alter the main server.
Bedrock: on the separate test Geyser only, turn off CoreSuite bedrock-audio delivery (or disable the test server's resource-pack module), replace the audio overlay with build/resourcepacks/meermc-magic-test.mcpack in Geyser's packs directory, retain the separate fish pack, install deployment/geyser/custom_mappings/coresuite_magic.json and restart. Avoid loading both old and test audio overlays: they intentionally share the same UUID. These are test deployment instructions, not changes applied to your live server.

## Local playtest follow-up
- Held right-click packets are suppressed using a 350 ms quiet gap (casting.repeat-gap-ms). Books do not provide a reliable release event, so client/network behavior still needs testing. Spell/global cooldowns remain enforced independently. /magic cast remains available.
- Combat spells target hostile mobs and ordinary animals by default. Set targeting.animals: false to spare animals. Tamed pets, NPCs, villagers and players remain excluded; damage still passes through Bukkit events.
- Scholars now show the current journal objective after discovery and on repeat interactions. Discovery research is separate from the sequential /quests chapters; individual merchant quest chains are not implemented.

## Hearth, farming and mana playtest update
Hearth now pulses direct MAGIC healing once per second, using Paper's cancellable healing API, rather than refreshing a short Regeneration potion. Base healing is 1 health point (half a heart) per second, scaling to 1.25/1.5 at tiers II/III. Overlapping fields use the strongest pulse only; walls block healing. It does not restore hunger or prevent starvation damage. Duration remains 8/10/12 seconds, radius 4/5/6 blocks.
Harvester uses the player block-break pipeline and replants mature wheat, carrots, potatoes and beetroot using one plain matching inventory seed/produce per crop. Keep seeds in your inventory before casting. With no planting supplies it still harvests. Placement cancellation restores the empty harvested block and consumes no seed. Custom named/modelled supplies are excluded.
Starting mana remains 100, regenerating 2 per online second after a configurable 3-second casting pause. Tier II costs 5% less; tier III costs 10% less, rounded up to tenths. No separate character XP level system was introduced. Existing saved mana and custom costs are retained; new configuration keys auto-merge. Food is not required for mana recovery. Under server lag recovery slows with ticks rather than granting catch-up bursts.
Spell trails now have distinct elemental accents: ember smoke, twin water ribbons, frost shards, violet Zoltraak sparks, warm Hearth boundary and green harvest particles. These use existing client particles and require no pack publication.

## Native book models (local test revision)
The original shared generated-image item is replaced by native opaque cuboid geometry on Java. This removes transparent-image extrusion from the Java book models. First-person scale is 0.48, with separate left/right-hand transforms. Each tome has a separate cover colour and embossed symbol. IDs: Grimoire 71001, generic legacy tome 71002, Mana Dart 71010, Ember Lance 71011, Tidal Arrow 71012, Frost Lance 71013, Zoltraak 71014, Dimension Step 71015, Hearth 71016, Harvester 71017. Existing tome items in online players' inventories have their appearance updated every five seconds without changing their single-use token. Stored items update after they enter a player's inventory.
Editable icon sources are in docs/grimoire/art/*.svg; native geometry and matching pixel icons are authored by pack-publisher/scripts/build_magic_models.py. These replace the earlier generated artwork. No image-generation tool was used for this revision; the assets are code-native models and vector icon primitives. Java uses 3D book geometry; Bedrock uses matching flat inventory/held icons, not identical 3D geometry. Existing Geyser installations must install the updated deployment mapping and test mcpack, then restart. First-time mapping extraction remains automatic.
All resource-pack work stays on the local codex/grimoire-pack-local-test branch. No release or live update was published. Cross-client visual acceptance remains an in-game check.
