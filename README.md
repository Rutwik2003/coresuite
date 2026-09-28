# CoreSuite

MeerMC's modular Paper server plugin. Production builds target Java 21 bytecode; newer Paper servers may require a newer Java runtime. See [compatibility notes](docs/COMPATIBILITY.md).

## Build and verify

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot'
.\gradlew.bat build smokeJar
```

`build` runs the JUnit tests and the boss regression runner. The deployable plugin is `build/libs/CoreSuite-1.0.0.jar`. `scripts/smoke_paper.py` provides isolated server compatibility checks; consult its help and the compatibility guide before running it.

## Folder guide

| Folder | Contents |
|---|---|
| `src/` | Plugin source, default configs, unit tests and smoke probe |
| `docs/` | Compatibility, module documentation, guides and audit reports |
| `docs/tools/` | Fishing calculator, simulator and Angler HTML guide |
| `deployment/economyshopgui/` | Only the two approved shop config changes |
| `config-examples/` | Example integration configs |
| `scripts/` | Maintained build and verification helpers |
| `resourcepack/` | Resource-pack source used by tooling |
| `CoreSuite-UltimateFishPack/` | Existing fish pack assets, retained at their established path |
| `pack-publisher/` | GitHub resource-pack publication tooling/template |
| `meermc-resourcepack/` | Separate local Git checkout; not included in this repository |
| `skripts/` | Existing Skript integrations |
| `build/`, `.gradle/` | Generated outputs and local caches; not uploaded |
| `.local/` | Private server snapshots, databases, backups and old patch scripts; not uploaded |

## Latest work

The release includes pending boss balancing and arena fixes, fishing/bag improvements, reward-code editing, playtime rewards, maintenance integration, withdrawal messages, persistence fixes and regression coverage. See the module docs and [release summary](docs/RELEASE-2026-09-29.md).

Shop changes are intentionally limited: 26 buy-only sulfur/cinnabar building blocks and nine copper price corrections. Equipment, potions, shulkers, spawners and other intentional restrictions retain the supplied settings. See [the current shop report and event ideas](docs/shop-audit/README.md).

## Local files moved during cleanup

Nothing was deleted from the local archive. Old root scripts, recovery Java files, logs and archives are under `.local/archive/root-files/`; scratch packs and the original shop audit are under `.local/archive/scratch/`. Supplied server configs and the complete shop folder are under `.local/server-data/`. `.local/organization-moves.json` records exact old/new locations. Historical scripts are archived references, not maintained commands; their old hard-coded paths may no longer work.

## Branch workflow

This publication updates `main` as requested. Resume subsequent development on `test`; publish future production changes to `main` only when requested. Keep secrets, player databases, live configuration snapshots and generated backups out of commits.
