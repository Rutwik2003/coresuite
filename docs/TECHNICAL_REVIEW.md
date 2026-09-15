# CoreSuite technical review and fixes

Updated: 2026-09-16

## Scope and evidence

Reviewed the build and plugin descriptor, module registration/lifecycles, shared services, command and event registrations, configuration structure, SQL and scheduler usage, and the high-risk payment, migration, website and fishing paths. The most detailed execution-path analysis in this pass covers harvest sales, database shutdown/migration, configuration/reload behavior, website request handling, bank-note redemption and fish-bag persistence. Verification includes automated regressions, static linkage checks across 14 Paper API releases, and isolated Paper startup/reload/shutdown tests. It is not a complete multiplayer audit of every module and third-party integration.

The repository began clean. The user's Java 21 Gradle build succeeded before changes. The baseline Gradle build did not execute the custom boss test runner. Source backups, decompiled copies, repair scripts, stored database files and archived plugins were not modified or executed. Generated output was produced only by build/test tools.

## Architecture and compatibility

- One Gradle Java project, Java 21, Gradle wrapper 8.14.3, Shadow 9.0.0-beta4.
- Default compile/test baseline is Paper 1.21-R0.1-SNAPSHOT with Java 21 bytecode; plugin.yml declares API 1.21. A Gradle property supports compilation against newer APIs.
- Static API checks include Paper 26.2 build 123; isolated runtime checks also passed on build 124 with Java 25. See COMPATIBILITY.md for exact evidence.
- Main entry: CoreSuitePlugin. Shared services: DatabaseManager, EconomyManager, IntegrationManager and GuiManager.
- 27 registered modules, with per-module configuration, commands, listeners, managers and caches. Registration order and direct manager lookups provide implicit dependencies.
- Module supports listener/task tracking, but managers also own schedulers and external executors. Stopping a module and stopping the entire plugin are different cleanup boundaries.
- SQLite is the default, with one pooled connection and one worker. MySQL has a pool configuration, but multiple modules still use SQLite SQL.
- Test-only dependencies were added for JUnit, Mockito, Vault API and SQLite JDBC. No new deployed runtime dependencies were added.

## Implemented fixes

### Harvest payment failure

SellManager previously removed crops and consumed quota before ignoring the Vault deposit result. It now validates amount/value, snapshots only affected inventory slots, reserves items, restores those exact stacks on an unsuccessful deposit, and increments quota/calls success only after payment succeeds. Existing in-season, out-of-season and golden pricing remains intact. Quota writes no longer use a redundant nested submission; their upsert syntax accounts for the configured database dialect.

Golden-crop sale messages now total successful payments at click time. Normal sale failures tell the player that crops were not consumed. Tests cover failed payment with both complete and partial stack removal, successful quota splitting, invalid numbers, insufficient items and off-season sales.

Limitation: Vault and SQL are independent systems; this is not a crash-proof distributed transaction. It assumes the normal synchronous Vault contract and main-thread callers, as used by the existing GUIs. Provider exceptions after externally committing money remain ambiguous.

### Shared economy validation

Deposit/withdraw reject NaN and infinity as well as non-positive amounts. Balance checks reject negative/non-finite requests. Mutating transactions call Vault once and never retry through the player-name overload. Confirmed rejection returns false; provider exceptions raise an uncertain-outcome error after logging. Tests verify invalid values never reach Vault, successful deposits work, and failed/throwing mutations are not retried.

### Database shutdown

Shutdown previously stopped executor submissions before queued tasks could enqueue their child writes. Pending tasks are now counted, and the executor remains open while they drain, within the existing ten-second budget. Child work stays queued rather than executing inline: an existing caller can still own SQLite's sole connection. A deterministic concurrency test checks shutdown draining and parent-before-child execution. Timeouts now report unfinished work.

This does not make the queue bounded or make work posted back to Bukkit during shutdown safe. A shutdown exceeding the deadline can still lose unfinished work, as before, but is now reported.

### Legacy migration safety

The previous six-table importer used REPLACE, committed each table separately, swallowed errors, and could rename an incompletely copied source as migrated. LegacyDatabaseMigration now checks source-table coverage and empty destinations before writing, uses named INSERT columns, and copies in one destination transaction. Batch failures roll back. DatabaseManager honors the configured SQLite path and retains the source on failure or when an existing migration backup is present.

This intentionally refuses newer schemas containing tables that the legacy importer cannot cover. It does not implement full MySQL support. Tests use real in-memory SQLite JDBC databases to verify batch copies, column-order independence, source preservation, missing destinations, populated destinations, unsupported tables and rollback across tables. MySQL engine/driver behavior has not been integration-tested. Destination tables must support transactions. Storage changes still require a complete migration and cache/startup validation; do not interpret refusal as successful migration or automatic fallback to SQLite.

### Configuration and reload behavior

Configuration streams have explicit lifetimes. Invalid YAML now fails module enable instead of being silently treated as an empty configuration and filled with defaults. Configuration loading is inside the module exception boundary. A test verifies malformed configuration is preserved byte-for-byte.

ModuleManager reports failed enables during reload and catches custom reload exceptions, disabling the failed module to clean tracked resources. CoreSuiteCommand reports failures rather than always claiming success, and removes a redundant async-to-sync scheduler hop. KillPodium fails enable when required plugins are absent, and uses its declared coresuite.podium.admin permission.

Failed startup now invokes module cleanup if initialization began, then cleans tracked tasks/listeners. Linkage failures are isolated. Module configuration failure does not call cleanup for an initialization that never started. Some independently scheduled feature callbacks still need lifecycle coverage.

### Website request resource limits

POST bodies are capped at 64 KiB, with HTTP 413 for larger payloads, including requests without a usable Content-Length. Bounded reads retain the exact UTF-8 content required by existing HMAC authentication. The web worker pool is limited to eight workers and a queue of 64 requests, replacing an unbounded cached pool. Saturated submissions can be rejected/closed; this is not a guarantee of HTTP 503 responses. Existing Bearer and HMAC protocols were retained. A failed HTTP bind/start now closes resources and fails module enable instead of appearing successful.

Tests cover normal UTF-8 payloads, the exact limit, and an oversized stream without reading its entire content. This is resource hardening, not a complete authentication or denial-of-service audit.

### Build and regression execution

Gradle test now discovers JUnit regression tests. The existing E2ETestRunner is invoked by e2eTest using the declared test runtime classpath and Java 21, and check depends on it. This avoids the legacy Python runner's practice of adding every cached Gradle JAR to the classpath.

Connecting the existing suite revealed two baseline inconsistencies. Git history explicitly identifies a Valerius balance nerf, and both runtime and configuration use 5 healing with a 15-per-pulse cap. Its outdated 20-healing assertion was corrected and the cap asserted. Web-spit configuration now explicitly lists the existing runtime defaults (range 6, damage 10, snare 4 seconds, poison 5 seconds, slowness 4 seconds). Those baseline checks predated the subsequent encounter overhaul described in WORLD_BOSSES.md.

### Fishing persistence and menus

Fish-bag and fishing-profile loads now carry session tokens and apply to the cache on the server thread. A late callback cannot overwrite a reconnect or restore a logged-out session. Missing/corrupt/failed loads remain unavailable; commands and catches cannot fabricate defaults while loading. Fish profiles are inserted before becoming usable. Saves capture values before entering the SQL queue, and bag serialization runs before SQL submission, avoiding worker access to mutable Bukkit items.

Fish bags preserve their existing serialized format, flush output before encoding, validate serialized slot counts, and normalize supported smaller bags to 450 slots. Loaded bag menus use an owner/session-aware InventoryHolder. Page changes close/save the old page before reading the next; sorting cannot be undone by the old close event. Inventory transitions run after click handling, and drag/hotbar/offhand/control-slot paths are checked. A catch closes an open bag before storing overflow fish. Module shutdown closes bags while their listener is still registered and queues final saves before database shutdown.

Fishing sales validate value before removing one exact stack, restoring its slot on a confirmed failed payment. Bag sales persist only after payment. Bag-page, enchantment, and rod-quality purchases now require successful withdrawal. Retired-species deletion is disabled by default; the explicitly destructive maintenance.remove-retired-fish option preserves deliberate cleanup as an opt-in operation.

Citizens is optional: absent Citizens creates no NPC timers, summon attempts explain the missing dependency, and cleanup avoids Citizens class access. The deferred cleanup task is cancelled on shutdown. Stale-NPC cleanup requires CoreSuite's persistent-data marker rather than matching another NPC's display name.

Tests cover deferred cache application, logout/reconnect callbacks, database failures, and corrupt inventory sizes. The separate live-server probe verifies real ItemStack serialization/deserialization and Citizens-free cleanup on Paper 1.21 and 26.2. Full player-driven menu and economy workflows still need staging coverage.

### Bank-note settlement and delivery

Single and bulk claims use one reservation/payout path. SQL conditionally reserves an available note; separate connections cannot reserve it twice. Items stay in the player's inventory while SQL runs, then ownership is rechecked and XP/Vault payout occurs on the server thread. A confirmed rejection releases the reservation and retains the note. Successful payment consumes one item and finalizes its ledger entry. A disconnect before payment does not require reconstructing or dropping a refund.

Issuance creates a pending ledger row before withdrawing value. Activation and durable delivery insertion are one SQL transaction. Delivery retries on join and /claimnote, keeps its unique note ID, and waits for inventory space. Player data is saved before delivery acknowledgement; XP withdrawals and payouts also save player data before ledger finalization. This is a deliberate, infrequent server-thread persistence cost: Bukkit player saves cannot safely be moved to a general worker. SQL remains queued off-thread.

Pending states persist across crashes. Administrators can inspect up to 25 entries with /withdrawadmin pending. State 2 means a redemption may be in progress or have an uncertain outcome; state 3 means issuance has not finished. The row records the claimant name or issuance recipient UUID, and amount. Do not blindly reset these states: verify the actual XP/economy outcome and logs before reconciling. Vault does not provide a shared transaction or idempotency key with Minecraft/SQLite, so uncertain cross-system outcomes require review rather than an automatic second payout. This is not an exactly-once guarantee under arbitrary provider/disk failures.

Tests cover repeated and concurrent reservation attempts, confirmed-failure release, retained pending state, activation rollback when delivery insertion fails, and invalid XP values. Pending chat prompts are concurrent-safe, history messages return to the server thread, and XP totals use a bounded formula rather than looping over potentially enormous levels.

## World boss overhaul

See [WORLD_BOSSES.md](WORLD_BOSSES.md) for adaptive participant-based health, finite healing, safe spawn searches and totems, persistent participation rewards, one-time default migration, and administrator receipt review. Real boss lifecycle probes pass on Paper 1.21 and 26.2.

## Legacy fish artwork

The maintained legacy pack generator emits matching stable IDs, model aliases and static fish artwork for Java 1.21–1.21.3. It validates reachable custom model and texture references and excludes unused incomplete source-pack assets. The source pack remains unchanged. Native client rendering still needs visual staging validation; newer seasonal/plated-food variants are intentionally absent from this legacy artifact.

## Remaining issues and unverified scope

### MySQL portability and migration

Orders, well, fishing and other modules still contain SQLite-specific AUTOINCREMENT, TEXT keys, last_insert_rowid and ON CONFLICT statements. Full MySQL operation is not verified or claimed. Driver availability is not explicitly declared for every production database choice. The conservative legacy importer guards prevent unsafe partial copies but do not convert every module schema or make switching storage safe. Docker's database service was not running; no MySQL server or production database was modified.

### Integrations and gameplay

The isolated tests use default SQLite, no third-party plugin set, and no authenticated player clients. Twenty-five default modules enable; KillPodium and Discord remain disabled by their default switches. Citizens, Vault/economy, SkinsRestorer, PlaceholderAPI, LuckPerms, Vulcan and other integration providers must match the selected server version. Their full workflows, permissions/protection interactions, and populated-server load have not been certified.

WorldManager retains configured lobby routing but now waits for AuthMe authentication. Boss spawn/block checks integrate with WorldGuard and GriefPrevention; Multiverse-Inventories has soft-dependency ordering, while actual inventory-group behavior still needs staging tests. Bedrock avatar detection uses a prefix heuristic. Some quest callbacks, manager caches and tasks still lack comprehensive reload/disconnect tests.

### Performance and distributed persistence

- Clear-lag enumerates loaded entities synchronously. Boss biome searches now use bounded asynchronous chunk loading.
- Frequent fishing, casino and boss tasks scale with players/encounters; no representative multiplayer profile was collected.
- Shared database work uses one unbounded worker queue. Slow SQL can delay unrelated features; shutdown has a ten-second drain deadline.
- Website startup still performs SQL synchronously, and Discord shutdown includes synchronous HTTP.
- Vault, player persistence and plugin tables do not participate in one transaction. A provider reporting an uncertain outcome requires reconciliation.

## Verification

The user's Java 21 build command passes: 43 JUnit tests and all 238 existing boss checks. The boss suite combines actual-class checks, source/config inspection and simulation; it is not a complete multiplayer test. Source also compiles against Paper 26.2 using Java 25 while retaining Java 21 output compatibility.

See COMPATIBILITY.md for the exact API matrix, smoke-test commands, adapter behavior, and log locations. No production deployment, restart, world conversion, or database migration was performed. The test-only smoke probe is packaged separately and excluded from CoreSuite-1.0.0.jar.

See [SERVER_STACK.md](SERVER_STACK.md) for the AuthMe, protection and crate safeguards added after reviewing your installed plugin list.
