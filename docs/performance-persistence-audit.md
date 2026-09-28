# Performance and persistence maintenance — September 25, 2026

## Changes

- Boss portal manager replacement now releases the module listener registry reference on reload; old managers no longer accumulate there. Fishing clears transient activity/streak/chunk caches on disable.

- Playtime saves capture data before logout clears caches; reads are ordered behind saves on the database worker. Session tokens discard late login/reload callbacks. Loaded online players are reinitialized after a module reload.
- Playtime claims recheck eligibility and atomically reserve the database row before payouts. Repeated clicks, stale menus and failed reads no longer grant duplicate rewards. Receipts remain reserved if an external payout is uncertain; they are not replayed automatically.
- Playtime menu requests coalesce per player while a read is outstanding. Page size is constrained to available inventory slots. Large admin adjustments cannot wrap integer values. Admin reset persists an adjustment instead of deleting the row needed by later saves.
- Casino game results increment database totals directly, preserving lifetime totals with a cold cache or after restart. Stats reads use session tokens so logout/reload cannot restore obsolete cache entries. Leaderboard query size is capped at 100.
- Boss discoveries use asynchronous reads rather than acquiring SQLite's sole connection on the server thread. Completed reads cannot recreate logged-out players' entries. Removed redundant nested write jobs.
- Boss terrain preparation uses Paper asynchronous chunk loading, loads only the arena footprint, prevents duplicate requests and times out after 60 seconds. Despawn/reload/disable invalidate pending preparation. Rifts wait while terrain prepares, and entry is held until preparation completes.
- Podium skin fetching is limited to one outstanding pass. Unchanged leaders skip repeated skin fetches; Citizens mutations return to the server thread and discard callbacks from an old module lifecycle.
- Archaeology cache loads discard stale/logout callbacks; fragment sets use concurrent collections. Set redemption revalidates saved fragments inside the transaction so a stale menu cannot repeatedly redeem a consumed set. Updated touched table definitions/inserts for SQLite/MySQL syntax.
- SQLite gets a 5-second busy timeout. MySQL uses explicit connection/socket timeouts and no automatic reconnection inside transactions. Hikari reports connections held over 60 seconds. Database backlog warnings identify stalled storage; shutdown drain time is configurable under `storage.shutdown-timeout-seconds` (default 30, bounded 10–120).

## Extended playtime rewards

All are one-time milestones, based on non-AFK playtime. XP means points, not levels.

| Hours | Cash | XP points |
| --- | ---: | ---: |
| 350 | 40,000 | 750 |
| 400 | 50,000 | 1,000 |
| 500 | 75,000 | 1,500 |
| 600 | 100,000 | 2,000 |
| 750 | 125,000 | 2,500 |
| 1,000 | 150,000 | 3,000 |
| 1,250 | 175,000 | 4,000 |
| 1,500 | 200,000 | 5,000 |
| 2,000 | 250,000 | 6,000 |

Edit `plugins/CoreSuite/modules/playtime-rewards.yml`. Each reward exposes `required-seconds`, `money`, `commands`, display fields and messages. Apply with `/playtimereward reload`. Missing milestones merge automatically; existing customized values are preserved. Keep reward IDs stable to preserve claim history. Extended milestones also grant Enchanter, Potions and UltraCosmetics keys. Elite keys are limited to 1 at 1,000 hours, 1 at 1,500 hours and 2 at 2,000 hours. There are no new titles, trophies, ranks or boss equipment. Stock cash/XP-only command lists upgrade automatically; customized command lists and claim history stay intact. UltraCosmetics uses `uc give key <amount> %player_name%`; CoreSuite replaces the player placeholder before dispatch.

## Interrupted reward delivery

Use `/playtimereward review` to list reserved claims. After checking money and command rewards, use `/playtimereward resolve <uuid> <reward-id> delivered` to record a completed payout. Use `retry` only after verifying that replay will not duplicate any partial payout. It unlocks the claim; it does not pay immediately. These commands require the existing `playtimereward.admin` permission.

Generic third-party commands cannot participate in a database transaction. A crash between an external payout and its receipt can require manual reconciliation. A command returning success also does not prove that a third-party plugin delivered its reward. This change prevents automatic duplicate retries; it cannot provide exactly-once transactions across arbitrary plugins.

## Verification and limits

The final build passed 146 JUnit tests and 238 scripted boss regression checks. Paper 1.21 and 26.2 passed startup/reload/shutdown and asynchronous boss spawn probes with zero plugin failures after the terrain fix.

Regression tests cover SQLite reservations, duplicate/one-time/cooldown claims, database errors, persisted data reload, shutdown draining after a failed job, casino accumulation, config merging, asynchronous spawn cancellation and stale discovery reads. The isolated Paper smoke suite covers startup, reload, boss spawning and cleanup, and shutdown.

This is not a claim of zero leaks or guaranteed CPU/RAM usage. Live MySQL, production economy/protection/anti-cheat integrations and long-duration tests with actual players require validation on the test server. The database task queue remains ordered and unbounded so accepted save jobs are not silently discarded; a prolonged storage outage can still grow the queue, now accompanied by backlog warnings. Do not treat an idle startup test as a loaded-server benchmark.

### Revised key rewards

| Hours | Enchanter | Potions | Elite | UC |
| --- | ---: | ---: | ---: | ---: |
| 350 | 2 | 1 | 0 | 1 |
| 400 | 1 | 2 | 0 | 2 |
| 500 | 2 | 1 | 0 | 3 |
| 600 | 2 | 2 | 0 | 2 |
| 750 | 1 | 3 | 0 | 3 |
| 1,000 | 3 | 0 | 1 | 4 |
| 1,250 | 2 | 4 | 0 | 4 |
| 1,500 | 4 | 0 | 1 | 5 |
| 2,000 | 0 | 5 | 2 | 6 |
