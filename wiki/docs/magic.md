# Magic, encounters and quests

**Experimental.** Modules: `grimoire`, `world-encounters`, `side-quests`.

The current direction is wand progression, spell discovery and quest-based advancement. Old design documents describing inventory replacement are not instructions for the current player workflow.

## Setup

Enable the related modules on a staging server and inspect all three configurations. Review wand acquisition, attunement costs, mana progression, cooldowns, spell research, scholar trades and quest objectives. Install the compatible pack separately if you want custom models and audio.

The module contains baseline mana settings and progression settings; do not assume `mana.maximum` alone defines every new player's effective capacity. Test with a fresh player profile rather than an administrator's old upgraded profile.

## Player entry points

- `/magic`: spells, research and the quest journal.
- `/grimoire`: spell preparation interface.
- `/encounters`: traveling scholar interface.
- `/quests`: quest journal.

Follow the active in-game journey to earn and attune the wand. Configure repeat quests and research costs with progression in mind. Duplicate tomes can contribute to progression; they are not necessarily a wasted duplicate.

## Casting and protection

Mana, spell cooldowns and repeated-use suppression constrain casting. Briefly release right-click between intentional casts. Targeting and protection checks apply; players, pets and NPCs are not equivalent to hostile mobs. PvP magic is not advertised as enabled in this release.

Hearth healing and harvest replanting have separate configuration. Replanting consumes matching seeds or crops from inventory. Spell fields should not be assumed to stack just because their particles overlap.

## Test before enabling publicly

- New-player quest acquisition, payment, wand binding and reconnect persistence.
- Spell preparation and casting from Java and Bedrock.
- Mana recovery delay, cost, failed casts and held-button behavior.
- Crop maturity, protection, seed consumption and replanting.
- Healing with low hunger and multiple overlapping fields.
- Damage against permitted mobs, protection regions and boss counters.
- No inventory swapping, item duplication or sale of temporary control items.

Keep a database backup before testing progression migrations.
