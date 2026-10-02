# CoreSuite workspace context for Antigravity / Gemini

This is the existing MeerMC Paper plugin repository, not a new project. Preserve user edits and inspect the current Git state before changing files. Other editors/agents may share this checkout; do not run concurrent writers on the same files.

## Start here for the magic redesign

Read these files before implementation:

1. `docs/grimoire/ANTIGRAVITY_START_HERE.md`
2. `docs/grimoire/MAGIC_REDESIGN_HANDOFF_2026-10-03.md` (complete plan, current implementation, source map, research, invariants and tests)
3. `docs/grimoire/IMPLEMENTATION_LOG.md` (new work and latest checkpoint)

The handoff is a proposal, not a list of completed features. The owner rejected the current GUI and wants simpler Java/Bedrock controls plus better magical art and animation. Default starting task is Phase A, a two-spell reserved-empty-hotbar-slot prototype behind an experimental toggle. Do not implement a full inventory swap or silently move real equipment. Verify that this is still the user's intended task before following stale plan details.

## Project constraints

- Work on `test`; do not switch branches over uncommitted user work. Do not force-push or reset the checkout.
- `meermc-resourcepack/` is a SEPARATE repository whose releases affect the live server. Do not push it, publish releases or run its publishing workflow without explicit owner authorization.
- Keep experimental packs local. Do not change live server URLs, credentials or production config/data.
- Do not stage `.local/`, `My server plugins/`, private server backups, databases, tokens or secrets.
- Preserve real inventory items, food, potions, totems, armour durability, custom metadata, spells, progression and pending-purchase journals.
- Boss equipment remains shop-only. Magic resources are separate from equipment-shop shards.
- Keep normal protection/damage events, arena restrictions and opt-in magic PvP. Cancelled damage must not apply secondary fire, slowing or push.
- Java and Bedrock need tested usable controls. No keyboard-only required action. Do not claim Bedrock acceptance without an actual client test.
- Main-thread Bukkit inventory/entity work; immutable snapshots on the database executor. No synchronous SQL or unbounded per-cast tasks.
- Do not start/stop servers or replace a running JAR just because it would simplify a test. Check current activity and the user's task authorization first.

## Build and evidence

PowerShell from repository root:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
.\gradlew.bat build smokeJar
python -m unittest discover -s pack-publisher/scripts -p "test_*.py"
```

Release compiles against the oldest supported Paper API using Java 21. The local Paper 26.2 server uses Java 25. Output JAR is `build/libs/CoreSuite-1.0.0.jar`. Asset generators/builders live under `pack-publisher/scripts/`; generating a pack does not publish it.

Implement small, buildable steps. Revalidate GUI quotes and session ownership. Test item preservation, repeated packets, death, reconnect, world change and cleanup before visual polish. Record changed files, actual test results, unresolved risks and next task in IMPLEMENTATION_LOG.md. Historical tests in the handoff do not validate new changes. Do not mark manual acceptance complete from unit tests.

This file supplies context only. Reading it does not itself authorize sending messages, publishing, deploying or starting an autonomous task.
