# CoreSuite compatibility and verification

Updated 2026-09-16. Target platform: Paper. Latest live server checked: Paper 26.2 build 124; static API matrix includes 26.2 build 123.

## Build and server requirements

The release compiles against the oldest supported API (Paper 1.21), with Java 21 bytecode. The existing build command remains valid:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
.\gradlew.bat build
```

Deploy `build/libs/CoreSuite-1.0.0.jar`. The separate `CoreSuite-SmokeProbe.jar` is a test artifact, not a production plugin.

Paper 1.21 through 1.21.11 uses Java 21. Paper 26.1+ requires Java 25 on the server. Using Java 21 to build this plugin does not remove that server requirement. [Paper requirements](https://docs.papermc.io/paper/getting-started/)

Compile against the latest checked API without changing the release baseline:

```powershell
.\gradlew.bat compileJava '-PpaperApiVersion=26.2.build.123-stable'
# Then rebuild the release against the default oldest API:
.\gradlew.bat build
```

The latest-API compilation uses an installed Java 25 toolchain. [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/)

## Compatibility changes

- The plugin descriptor and startup banner specify Paper 1.21 as the minimum.
- Attribute constants use a small adapter for the old GENERIC_* names and newer names.
- Biomes are enumerated through the registry and accessed through Keyed, avoiding enum-to-interface linkage errors.
- Configured sounds resolve static fields instead of calling a valueOf method whose owner changed from class to interface.
- Item models use the native ItemMeta API when available. On 1.21/1.21.1, supplied fish keys receive stable CustomModelData IDs matched by the generated legacy pack. Build it with `python scripts/build_legacy_fish_pack.py`; output is `build/resourcepacks/CoreSuite-FishPack-1.21-1.21.3.zip`. It contains the 190 configured fish/unknown models with static fallback artwork; newer seasonal/name-based variants and Bedrock/protocol translation are not claimed. On 1.21.2/1.21.3 the generated pack also provides item-model aliases. Unknown external model keys preserve their existing metadata.
- No NMS/server-internal mappings were introduced.

## Same-JAR static linkage matrix

The checker resolves direct Bukkit, Paper and Adventure member references, including descriptors and class/interface invocation kind. One resolved API artifact per version was checked, not every historic server build. All 14 entries passed with no unresolved direct API references.

| Paper API | Linkage | Live startup/reload/shutdown |
| --- | --- | --- |
| 1.21-R0.1-SNAPSHOT | Pass | Pass, build 130; real-item/adapter and boss lifecycle probes passed |
| 1.21.1-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.3-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.4-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.5-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.6-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.7-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.8-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.9-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.10-R0.1-SNAPSHOT | Pass | Not run |
| 1.21.11-R0.1-SNAPSHOT | Pass | Earlier compatibility pass, before encounter overhaul |
| 26.1.1.build.29-alpha | Pass | Not run; upstream labels this build alpha |
| 26.1.2.build.74-stable | Pass | Earlier compatibility pass, before encounter overhaul |
| 26.2.build.123-stable | Pass | Server builds 123 and 124 passed; item/adapter and boss lifecycle probes passed |

Paper's published version list contains no 1.21.2 or 26.1 server build. These are therefore not invented test targets. [Official Paper project metadata](https://fill.papermc.io/v3/projects/paper), [current download](https://papermc.io/downloads/paper)

Reports under `build/compatibility/*.report.json` include the release JAR SHA-256, resolved classpath file, reference count and errors. The checker does not prove dynamic reflection, provider compatibility, permissions, gameplay logic, or every API behavior; live probes complement it.

Reproduce a linkage check:

```powershell
.\gradlew.bat writeCompatibilityClasspath '-PpaperApiVersion=26.2.build.123-stable'
python scripts/check_paper_linkage.py build/libs/CoreSuite-1.0.0.jar build/compatibility/26.2.build.123-stable.classpath 'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot'
```

The checker's final argument must point to a full JDK containing `jmods`; it reads JDK classes to resolve inherited methods.

## Isolated live tests

The user explicitly accepted the Minecraft EULA for these local tests. The harness downloads official Paper JARs, checks their SHA-256, binds Minecraft and website ports to loopback, creates isolated worlds, lists modules, reloads CoreSuite, and stops the server. It never connects to or changes a production server.

```powershell
.\gradlew.bat build smokeJar
python scripts/smoke_paper.py 1.21 'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot' --accept-eula
python scripts/smoke_paper.py 26.2 'C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot' --accept-eula
```

Test folders/logs: `build/smoke/<version>/smoke-console.log`. Probe success appears as `CORESUITE_PROBE_PASS`. The probe checks real attribute access, sound lookup, item-model adaptation, fish-bag serialization and NPC cleanup without Citizens. The boss probe spawns all three real entities, checks healing limits and invalid/overkill damage, defeats them with a simulated participant, and verifies one reward callback each. See WORLD_BOSSES.md.

The default SQLite configuration enabled 25 of 27 modules and reloaded/stopped cleanly. KillPodium and Discord are disabled by their default settings. Tests used no third-party providers and no authenticated player clients. Windows OSHI performance-counter warnings on Java 25 originated from server system-info collection, not CoreSuite. An initial harness console-encoding failure was corrected and tests rerun with fresh worlds.

## Regression coverage and limits

`build` runs 43 JUnit regressions and the 238 existing boss checks. JUnit covers crop payment rollback, invalid economy values/single-attempt mutation, queue shutdown, safe legacy migration, configuration/lifecycle failures, HTTP body bounds, fishing session/corrupt-data behavior, and bank-note reservation/rollback (including two database connections).

These results support the Paper compatibility changes and covered fixes. They do not certify every player workflow or claim zero defects. MySQL portability still needs dedicated work. Matching Vault/economy, Citizens, SkinsRestorer and other plugin releases, resource packs, live player interactions, and representative load need staging validation. Bank-note states with uncertain external payment outcomes remain available for administrator review through `/withdrawadmin pending`. See `TECHNICAL_REVIEW.md` for remaining risks and implementation detail.

See [SERVER_STACK.md](SERVER_STACK.md) for the AuthMe, protection and crate safeguards added after reviewing your installed plugin list.
