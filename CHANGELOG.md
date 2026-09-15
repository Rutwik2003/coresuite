# CoreSuite changelog

## 2026-09-16 — Compatibility, persistence and world boss overhaul

This update builds on commit `58e15ce`. It targets Paper 1.21 onward with Java 21 plugin bytecode. It does not claim that every gameplay path, third-party combination, MySQL schema or translated client is fully verified.

### Paper compatibility and build

- Set the release compilation baseline and plugin API declaration to Paper 1.21; update the startup banner.
- Preserve the existing Java 21 Gradle build command.
- Add a `paperApiVersion` build override and Java 25 toolchain selection for the newer 26.x API.
- Resolve renamed Attribute constants through `PaperCompat`.
- Resolve configured Sound fields safely across enum/interface API changes.
- Enumerate Biomes through the registry and access keys through `Keyed`.
- Apply native item models when available, with mapped legacy CustomModelData on older APIs.
- Apply these adapters to fishing, announcements, combat tags, jump pads, mentions, playtime rewards and world bosses.
- Integrate JUnit and the existing boss regression runner into the normal build.
- Add a separate test-only smoke plugin; exclude it and test dependencies from the release JAR.
- Add maintained scripts for direct API linkage checks, isolated Paper server tests and legacy resource-pack generation.
- Keep generated servers, databases, archives and build artifacts ignored by Git.

### Economy and harvest market

- Reject non-finite, negative or otherwise invalid transaction values before calling Vault.
- Perform each deposit/withdrawal mutation once; do not retry a possibly completed transaction through another Vault overload.
- Distinguish confirmed rejection from an uncertain provider exception.
- Validate crop sale quantities and prices, snapshot affected slots and restore them on confirmed payment failure.
- Consume harvest quota only after successful payment; use database-specific quota upsert syntax.
- Report actual successful sale totals in market menus and clarify failed-sale messages.

### Database and module lifecycle

- Track queued database work and permit child writes while shutdown drains the queue.
- Retain the ten-second drain deadline and report unfinished work on timeout.
- Make the legacy importer transactional, use named columns and refuse unsupported tables or populated destinations.
- Preserve migration sources after failure and avoid overwriting an existing migration backup.
- Honor the configured SQLite path.
- Preserve malformed YAML and fail module loading instead of silently replacing it with defaults.
- Clean up partially initialized modules and tracked tasks/listeners.
- Isolate linkage/initialization errors and report real reload failures to administrators.
- Require KillPodium dependencies and correct its administrator permission.

### Bank notes

- Reserve redemptions atomically so the same note cannot be settled twice through concurrent claims.
- Recheck ownership before paying; retain the item and release the reservation after confirmed failure.
- Retain uncertain settlement records for manual reconciliation.
- Create pending issuance records before withdrawals and activate issued notes with durable delivery records.
- Deliver pending notes on join or `/claimnote`, waiting for inventory space.
- Save player data before acknowledging delivered notes and XP changes.
- Add `/withdrawadmin pending` for unresolved issuance/redemption records.
- Bound XP calculations and make chat prompts/history callbacks thread-safe.
- Check AuthMe before commands, interactions, delivery, withdrawal and redemption settlement.

### Fishing, fish bags and Citizens

- Add session tokens to profile/bag loading so stale callbacks cannot overwrite a reconnect or logged-out state.
- Keep failed/corrupt loads unavailable rather than exposing empty/default data that could overwrite saved data.
- Insert new profiles before exposing them, capture save snapshots and apply cache updates on the server thread.
- Validate bag sizes, support smaller legacy bags, flush serialization and preserve the established format.
- Use owner/session-aware inventory holders and check drag, hotbar, offhand and control-slot operations.
- Save the old page before opening another page; prevent closing a stale GUI from undoing sorting or new catches.
- Close active bag menus before catch storage and before module shutdown saves.
- Restore exact fish stacks after confirmed sale-payment failure; require successful payments for upgrades/purchases.
- Make retired-fish deletion opt-in with `maintenance.remove-retired-fish`.
- Avoid Citizens API access/timers when Citizens is absent, gate NPC summons and cancel cleanup tasks correctly.
- Remove stale NPCs only when tagged by CoreSuite rather than matching their display names.

### Resource packs and guides

- Add stable fish model IDs shared by the plugin and legacy pack generator.
- Generate `CoreSuite-FishPack-1.21-1.21.3.zip` for the 190 configured fish/unknown models.
- Generate model aliases for 1.21.2/1.21.3 and CustomModelData overrides for 1.21/1.21.1.
- Validate reachable custom model/texture references and exclude unused incomplete models.
- Use static fallback artwork for newer seasonal/name-based variants; preserve the source pack.
- Document pack generation, deployment and ID-preservation requirements.
- Include the expanded Ancient Angler HTML guide: quest-path encyclopedia, player/staff commands, habitat directory, troubleshooting, quick reference and navigation/filtering. This is documentation, not a new quest-engine implementation.
- Add compatibility, technical review, world boss and server-stack documentation.

### Website and server integrations

- Limit HTTP request bodies to 64 KiB and reject larger bodies with HTTP 413.
- Bound website workers and queued requests; close resources after bind/start failure.
- Preserve the existing Bearer/HMAC protocols and exact UTF-8 request content.
- Add optional AuthMe authentication checks that fail closed if the installed provider cannot be queried.
- Wait for login and a short settling interval before automatic bank/boss delivery and WorldManager lobby routing.
- Do not overwrite saved survival locations with unauthenticated login locations; gate WorldManager commands.
- Declare AuthMe, WorldGuard, GriefPrevention and Multiverse-Inventories load ordering as soft dependencies.
- Add non-player WorldGuard/GriefPrevention checks for boss spawn points and temporary block placement.
- Migrate the stock boss crate command alias to the documented `crates key give` form.

### Dedicated world boss changes

#### Spawning and lifecycle

- Replace synchronous biome searches with bounded asynchronous chunk loading and server-thread location inspection.
- Use the block above the surface as the spawn feet position.
- Check world height, loaded chunks, border, solid support and a clear 5×5 area with five blocks of headroom.
- Reject hazardous ground, unchecked fallback positions and Peaceful worlds.
- Restrict searches to configured allowed worlds; retain underground requirements for cave bosses.
- Add configurable attempt limits, timeout, biome fallback and retry delay.
- Retry failed scheduled spawns after five minutes instead of waiting the full encounter interval.
- Permit scheduled encounters with one online player by default.
- Prevent overlapping pending spawns and cancel pending searches on reload/disable.
- Reject synchronous search calls from the server thread.
- Apply shared location validation to totems and administrator spawns; preserve totems when spawning fails.
- Check horizontal right-click-air summon positions near suitable ground.
- Hold spawn chunks while a boss is active and release them on cleanup.
- Prevent boss persistence/orphans where possible and clean only plugin-tagged boss entities.
- Avoid synchronously loading new chunks merely to precompute domain particles.
- Allow ten minutes for arrival before an unengaged boss despawns; allow three empty minutes after engagement.

#### Combat and balance

- Scale health to nearby recently active participants, not total online players.
- Default health is `base × 0.60 × (1 + 0.50 × (fighters - 1))`, with bounded participant counts.
- Preserve the percentage of remaining health when the participating group changes.
- Reduce boss-attributed outgoing damage for smaller groups.
- Share a lifetime healing budget of 15% across healing sources, normalized when health scales.
- Prevent healing from reversing boss phases.
- Reject NaN/infinite damage and credit actual applied damage rather than overkill.
- Prevent environmental or unattributed damage from bypassing the virtual boss health pool.
- Resolve boss/minion projectile ownership for outgoing damage and participation.
- Distinguish tridents from bow attacks.
- Remove the guaranteed minimum glancing hit and redundant attack-charge multiplier.
- Aethelgard: arrows deal reduced damage instead of healing; reduce/cap soil regeneration; cap lifesteal through the common healing budget.
- Valerius: route siphon healing through the common healing budget.
- Xol'thos: default evasion 25%, hard cap 35%; retain Night Vision counterplay; remove unsafe dodge teleports.
- Refresh tactical/codex text to describe adaptive health and the revised weaknesses.

#### Participation and rewards

- Track boss damage, minion damage, tanking and Water/Night Vision weakness counters as participation.
- Award every recorded participant the configured base tier, with at least one shard; remove the five-percent exclusion threshold.
- Award top-three rank rewards in addition to participation rewards.
- Retain configured rare-key/signature-weapon rolls for ranked contributors.
- Queue rewards for disconnected participants instead of skipping them.
- Persist item, currency and command components separately in a reward mailbox.
- Plan inventory insertion before mutation; keep full-inventory rewards pending rather than dropping them.
- Retry pending delivery on authenticated join or `/boss claim`.
- Preserve confirmed-failure rewards and quarantine uncertain deliveries rather than automatically paying twice.
- Add `/bossadmin rewards` to inspect pending receipts.
- Preserve contribution rankings/discoveries and persist kill statistics.
- Migrate exact former stock minimum-player, evasion, regeneration and crate-alias values once while retaining other custom settings.
- Check protection providers before spawning and placing fire/cobwebs; failed provider lookup blocks the action.

### Verification

- Java 21 Gradle build passes.
- 43 JUnit regressions and all 238 existing boss checks pass.
- Final release has no unresolved direct Bukkit/Paper/Adventure references against 14 checked API artifacts.
- Isolated lifecycle/item/boss probes passed on Paper 1.21 and 26.2, including server build 124.
- Earlier startup/reload/shutdown checks also passed on 1.21.11 and 26.1.2; these preceded the final encounter changes.
- Probe coverage includes real boss entities, bounded healing, invalid damage, defeat, capped overkill and one reward callback per boss, using a simulated participant.
- Legacy pack generation validates all reachable custom model/texture references.

### Remaining limitations

- Full MySQL portability/migration is unresolved; validated runtime storage is SQLite.
- The owner's full 49-plugin configuration and authenticated multiplayer fights were not reproduced.
- Client-side visuals, Geyser/floodgate, ViaVersion/ViaBackwards and populated-server performance need staging checks.
- Spawn-point/block protection checks do not certify every point of a full arena or another plugin's custom rules.
- Configured crate/key IDs must exist; command success is not an atomic delivery guarantee.
- Vault, player data and SQL cannot share one atomic transaction; uncertain outcomes require receipt review.
- No production server/world/database was changed.

Further detail: [World bosses](docs/WORLD_BOSSES.md), [compatibility](docs/COMPATIBILITY.md), [technical review](docs/TECHNICAL_REVIEW.md), [server stack](docs/SERVER_STACK.md).
