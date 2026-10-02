# Astral Archive visual and menu update

## What changed

- Custom 128px engraved cover art with eight-frame illuminated astrolabe seals. Elemental symbols are individually drawn: flame, wave, snow crystal, dimensional arch, hearth, living branch and arcane lance.
- Native book models have parchment edges, brass spine ribs, clasps and compact hand transforms. Fewer bulky cover cubes leave the engraving readable.
- Obsidian focus wand has a forked brass cage and animated faceted crystal.
- Java uses animated textures; Bedrock receives matching static high-resolution inventory icons. Java geometry/texture animation parity on Bedrock is not claimed.
- Spell library separates browsing from actions. Eight spaced cards open individual detail pages. Learned/prepared/locked states, mana and cooldown are visible. Preparing/removing is free; upgrades get a cost review and confirmation.
- Enchanter page separates page binding, wand binding, resources, nearby scholar lessons and journal. Purchase confirmation for pages, lessons and wand binding. Maximum pages no longer show an imaginary next-page price.
- Contract cards show objective counters and reward lines rather than raw maps.
- Casting circles contract over time; impacts expand. Water forms twin spirals, frost a four-point spear, Ember a rotating hot core, Zoltraak a piercing ray with periodic rings, and Hearth a rotating double-ring sanctuary with rising boundary motes. Harvested crops and Dimension Step get their own expanding/portal seals.
- Cosmetic effects share the existing server animation loop; no new entity or per-cast task is created. Transient animations expire after 16 ticks, are cleared on shutdown, and are capped at 64 active echoes. New effects share `visuals.points-per-tick` (default 700, bounded 64–1600). `visuals.enabled` disables this choreography.

## Verification

216 JUnit tests, 238 existing regression checks and 10 Python pack checks passed. New checks exercise the global visual budget, expiration, disabling, vertical-shot geometry and animated texture metadata. Paper 26.2 startup/integration/reload/shutdown probes passed. This revision compiles against the Paper 1.21 API; the previous revision also passed a 1.21 runtime probe.

The cover-art contact sheet was inspected locally. Actual in-game model framing, animation smoothness, menu usability and multiplayer client FPS still require player acceptance. No claim of a measured performance improvement is made.

Technical particle reference: [Paper particle API](https://docs.papermc.io/paper/dev/particles/). Implementation uses APIs available in the compile-time 1.21 dependency, rather than newer-only particles.

## Test now

1. Start the local server normally, join `localhost:25565`, and accept the updated pack.
2. Open `/grimoire`: inspect a spell, prepare/remove it, review an upgrade, cancel it, then complete it near the Enchanter.
3. Open Enchanter & resources. Check page/wand/lesson costs, cancellations, maximum slots and missing resources.
4. Open `/quests`: check named objectives, numeric progress and claim behavior.
5. Cast all eight spells in daylight and darkness. Shoot vertically as well as horizontally. Check blocked impacts, piercing, Hearth boundaries and ripe crops.
6. Inspect tomes and wand in both hands, inventory, dropped form and item frames. Book seals and wand crystal animate; ordinary books and rods stay vanilla.
7. Try simultaneous casts with several players and report FPS/TPS if effects become distracting or slow.

Installed only in the stopped `.local/local-playtest` copy. Previous JAR, Java pack and server properties are under `backups/pre-astral-archive`. Java SHA-1: `c5499e9d003b1e3fd4ce1fb79db6ab15644464e1`. Plugin SHA-256: `d80f4ad1dc3f0faebbcbe15c2583f69f0af637d16ea4c2a9675db18b546e488e`.

Resource packs are local, unpublished builds. No live pack release or live server change was made.
