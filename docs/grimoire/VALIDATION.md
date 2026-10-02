# Magic expansion validation — 2 October 2026

## Automated results

- Gradle build: successful; 214 JUnit tests, zero failures.
- Regression/E2E runner: 238 checks passed.
- Python resource-pack checks: 9 passed.
- Paper 26.2 / Java 25: startup, magic initialization, integration probes, reload and shutdown passed.
- Paper 1.21 / Java 21: startup, magic initialization, boss probes and reload passed. The first run exposed a randomized test assumption: the probe now sets potion resistance to zero in its private test configuration before asserting a successful Thermal Shock. Production resistance is unchanged.
- Geyser on the 26.2 smoke server registered the custom item mappings without mapping errors.

Raw smoke logs are local under `build/smoke/26.2/smoke-console.log` and `build/smoke/1.21/smoke-console.log`. These are bounded compatibility probes, not exhaustive gameplay or load tests across every intermediate Minecraft version.

## Prepared artifacts

- Plugin SHA-256: `30b23a6608dd4d50bec9126b0fb13dbeb5adb50ca2e1b19cbb60f11be407bf1e`.
- Java pack SHA-1: `555a5e0a8732b31be0a794dbab7d3284fbb7e9b7`.
- Bedrock overlay SHA-256: `3f3ff4b63d3abd987353a3db3d6c7fbbfbc1b7f4a54f63be664b554543eb4f9f`.
- Updated plugin and Java pack installed in the stopped `.local/local-playtest` copy; previous files and CoreSuite data backed up under its `backups/pre-phase2` directory.
- No resource-pack release published and no gameplay server started for this handoff.

## Still requires player acceptance

Follow [TEST_PLAN.md](TEST_PLAN.md), especially Java model appearance, Bedrock controls/rendering, actual Vault payments and fish-bag debits, interrupted purchases, multiplayer protection and combat balance. MySQL and sustained multiplayer performance have not been exercised in these local checks. Bedrock uses matching flat item icons rather than Java's held 3D geometry.
