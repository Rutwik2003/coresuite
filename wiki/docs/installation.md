# Installation

## Requirements

- Paper and the Java runtime required by that Paper release.
- A CoreSuite JAR built for testing on your chosen version.
- Vault **and an economy provider** for money features. Vault by itself does not store balances.
- Optional plugins required by your selected modules: see [integrations](integrations.md).

CoreSuite is compiled to Java 21 bytecode. This does not mean newer Paper versions run on Java 21. Folia, Fabric, Forge and arbitrary Spigot compatibility are not claimed. Select only actually tested versions when publishing a download.

## Fresh server

1. Stop your server cleanly and take a backup.
2. Put one CoreSuite plugin JAR in `plugins/`. Do not install a smoke-test probe, sources JAR or multiple CoreSuite variants.
3. Start the server. Read the CoreSuite startup log, then stop it again.
4. Open `plugins/CoreSuite/config.yml`. Public-preview module toggles start false.
5. Open the relevant `plugins/CoreSuite/modules/<module>.yml`. Configure commands, prices, worlds, integrations and permissions.
6. Set the desired master module toggle to true. Start the server and check `/coresuite modules`.
7. Test as a player without OP. Test the administrator workflow separately.

Enable features in small groups. For a first test, chat mentions is simpler than bosses, magic or a public website API. Do not enable every module at once and assume missing dependencies will configure themselves.

## Existing server

Replacing the JAR preserves existing settings. New defaults can be merged, but previously configured modules remain enabled and reward commands remain your responsibility. Stop, back up, replace, start, inspect logs and retest. Never delete the database to fix a YAML error.

## Resource packs

No fish texture or music pack is bundled with the public preview. Install compatible assets separately, respecting their licence. Pack delivery must point to a repository you control, not MeerMC's live resource-pack repository.
