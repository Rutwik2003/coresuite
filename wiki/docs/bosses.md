# World bosses

Module: `world-bosses`. Configuration: `modules/world-bosses.yml`.

The system includes Valerius, Aethelgard, Xolthos and Borealis, with distinct attacks, phases, counters and contribution rewards. Difficulty depends on your saved balance settings and encounter size.

## Set up an arena first

The source defaults contain example fixed-location coordinates. Those coordinates are not a ready-built arena. Replace them and the player spawn locations before enabling scheduled encounters.

1. Choose an existing world and safe, walkable arena.
2. Set the fixed location and safe player arrival points, or configure named arenas with `/bossadmin arena add <name>` and `/bossadmin arena remove <name>`.
3. Review `domain.radius`, `domain.leash-distance`, spectator distance and protection.
4. Review portal search bounds, forming time, entry fee and exit behavior.
5. Test a manual encounter as an ordinary player. Test entrance payment, death/re-entry and leaving after the fight.

`domain.anti-grief-protection: false` also skips region spawn preflight at the configured fixed location. It does not globally turn off WorldGuard or bypass every terrain/protection check. Decide whether your region plugin or CoreSuite should protect the arena and test the combination.

## Rift lifecycle

Rifts form before opening. First admission and re-entry are separate: an admitted player can have free re-entry where configured. Awakening starts through the admission flow; do not treat old configuration comments or historical 15-second immunity as permission to damage a sleeping boss.

Review the lifecycle settings in the [full reference](modules/world-bosses.md). Test empty-server encounters, no combat, late arrivals and cleanup after defeat. The system has inactivity retreat and post-fight evacuation behavior; retreat is not a normal rewarded kill.

## Rewards and contribution

Players may qualify through boss damage or registered support actions. Simply standing in the arena is not sufficient. Eligibility thresholds, ranked bonuses, shard limits, cash and commands are configurable. The source default damage threshold is 150 health points; inspect your installed values before announcing eligibility to players.

Boss equipment should be purchased through the boss shop. Signature tools/armor are identified by plugin item tags and expected material, not by renaming ordinary netherite items.

`/boss claim` is used for pending rewards. If delivery fails, check console reasons: missing crate IDs, incompatible provider APIs or insufficient inventory space. Do not manually grant the same reward and then also allow the pending reward to claim without reconciling it.

## Useful player flows

- `/boss` opens the boss interface; aliases depend on the command registration.
- `/boss portals` locates entrances.
- `/boss enter` is constrained to nearby entrance rifts; it is not an unrestricted arena teleport.
- `/boss claim` attempts pending deliveries.

Administrator commands can spawn/despawn encounters, manage arenas, skip timer intervals and give configured items. Use live help/tab completion for exact arguments and test outside an active scheduled fight.

## Acceptance tests

- Every boss spawns in the configured arena, with no suffocation or inaccessible exits.
- Late entrants, deaths and spectators do not corrupt health scaling or rewards.
- Counter abilities and minions work without unlimited healing loops.
- Players can eat and use potions during fights.
- Full inventory rewards remain recoverable and do not duplicate after reconnect.
- Music is stable across the arena boundary and stops when appropriate.
