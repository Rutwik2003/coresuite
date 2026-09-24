# MeerMC automatic resource pack

This repository publishes only resource-pack assets. Never add plugin configuration, tokens, server data or worlds.

## Updating music or textures
Edit files under `pack/`, then push to `main`. GitHub Actions validates JSON and sound references, builds `meermc-pack.zip`, computes SHA-1 and publishes a complete versioned release. CoreSuite checks the latest release every five minutes. The ZIP and manifest use the same immutable release tag, avoiding URL/hash races. Do not overwrite published ZIPs; publish a new release.

## Test-server setup
1. Install the rebuilt CoreSuite JAR on your test server.
2. Stop the test server. Back up `server.properties`; clear its `resource-pack` and `resource-pack-sha1` values and set `require-resource-pack=false`. This is a one-time switch from vanilla delivery to plugin delivery. Do not change the production server yet.
3. In `plugins/CoreSuite/config.yml`, set `modules.resource-pack: true`.
4. In `plugins/CoreSuite/modules/resource-pack.yml`, set `repository` to this public repository's `owner/name`.
5. Keep `required: false` and `update-online-players: false` while testing. Start the server and wait for `Verified resource pack` in the log. Rejoin using a Java client and accept the pack.
6. Check custom fish textures and boss music. Publish a small asset change; wait for a new verified version, then reconnect and confirm the update.

Set `update-online-players: true` if you want already-connected players offered updates automatically. Resource-pack reloads can interrupt their view, so the default waits for reconnect. Players can decline optional packs; reconnect to retry. Bedrock/Floodgate players are skipped: Geyser needs a separate Bedrock-compatible pack.

No server-side GitHub credential is required. The workflow uses GitHub's built-in GITHUB_TOKEN. Repository releases and pack assets must be public. Existing texture/audio redistribution permissions still apply.

The plugin validates the ZIP hash, size and root pack.mcmeta before announcing an update. Failed checks retain the last verified release, whose manifest is cached across restarts. Minecraft still needs access to that release URL. Pack metadata and assets determine client-version compatibility; automatic hosting does not convert pack formats.

## Rollback
Publish the previous good `pack/` contents as a new commit/release. Or disable `modules.resource-pack`, restore the saved mc-packs URL/hash in server.properties, and restart.

## Local build
`python scripts/build_pack.py --repository OWNER/REPOSITORY --version local-test`

## Release manifest
`https://github.com/OWNER/REPOSITORY/releases/latest/download/manifest.properties`

Publishing and downloads follow GitHub's release API. Plugin delivery uses Paper/Adventure resource-pack requests: https://docs.papermc.io/adventure/resource-pack/
