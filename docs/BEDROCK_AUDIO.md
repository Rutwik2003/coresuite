# Automatic Bedrock boss music

## Installed server inspected

Geyser-Spigot 2.11.3 build 1245. The existing `CoreSuite-Bedrock-Fish.zip` has 207 entries and no sounds. Its configuration already has `gameplay.force-resource-packs: true`. The supplied CoreSuite config enables the resource-pack module and points to `Rutwik2003/meermc-resourcepack` with a 300-second update interval. Original server snapshots remain unchanged and excluded from Git.

## Implemented

- The same GitHub workflow now publishes the Java ZIP plus a separate Bedrock audio `.mcpack` and `bedrock-manifest.properties`.
- The audio builder reuses 14 Ogg files for 18 CoreSuite sound definitions, preserving identifiers used for boss phases and rift effects.
- CoreSuite's optional Geyser bridge verifies downloads, caches immutable files and registers the audio pack for connecting Bedrock sessions. Existing fish textures and Geyser mappings remain intact.
- The bridge uses Geyser's local pack API, avoiding direct Bedrock URL Content-Type/header limitations. It retains the last verified pack if GitHub is unavailable, uses asynchronous downloads, prevents overlapping update checks, and unregisters its listener on disable/reload.
- A stable audio-pack UUID prevents replacing unrelated packs. The builder advances the Bedrock manifest version with each GitHub workflow run.
- Java clients retain their separate required-pack flow. Bedrock pack acceptance is controlled by Geyser.

## Deployment

1. Replace the server's CoreSuite JAR with the rebuilt `build/libs/CoreSuite-1.0.0.jar` and restart normally.
2. Leave the existing Geyser fish pack in place. Do not manually install the audio overlay as a second competing updater.
3. The new `bedrock-audio.enabled: true` setting merges automatically into `modules/resource-pack.yml`.
4. Wait for `Verified Bedrock audio pack-3-1; Geyser will send it on the next connection.` (or a newer version), then reconnect a Bedrock client.
5. Future `pack/` music edits published to the resource-pack repository build both formats. CoreSuite checks every five minutes; reconnect to receive a verified update. No new server.properties edits or mc-packs uploads are needed.

On the first startup without any cache, Bedrock players connecting before verification can receive only their existing packs. Wait for verification and reconnect. Startup with a cache works during a GitHub outage. This system does not convert Java fish models or update their Bedrock mappings.

## Verification

The Java build, 175 JUnit tests, 238 regression checks and six pack-builder tests passed. An isolated Paper 26.2 server using the supplied Geyser JAR and fish pack successfully registered the cached audio overlay while preserving existing packs; startup/reload/shutdown checks passed. The smoke probe's old 45-HP Frostguard expectation was corrected to the previously implemented 30-HP balance value.

GitHub Actions published release `pack-3-1` with all four assets. Client-side audible playback remains a manual test; registration alone does not prove a device's audio settings permit music.

## Bedrock listening test

Replace `<BedrockPlayer>` with the actual player name and run:

```text
/execute at <BedrockPlayer> run playsound coresuite:boss.borealis.p1 record <BedrockPlayer> ~ ~ ~ 1 1
/stopsound <BedrockPlayer> record coresuite:boss.borealis.p1
```

Then test an actual boss phase transition, leaving the arena, rift audio, and the existing fish textures. Ensure the device's relevant sound sliders are audible.

## Sources

- https://geysermc.org/wiki/geyser/packs/
- https://raw.githubusercontent.com/GeyserMC/Geyser/master/core/src/main/java/org/geysermc/geyser/util/SoundUtils.java
- https://raw.githubusercontent.com/Mojang/bedrock-schemas/main/schemas/rp/sounds/index.schema.json

### Download diagnostic on this PC

The released 35,652,423-byte pack and its SHA-1 were verified using GitHub's release download. Direct Java requests on this PC timed out during connection, so a successful fresh remote download using Java is not claimed. Cached Geyser delivery and registration passed. A server unable to reach GitHub needs its network route fixed for automatic future updates.

An optional one-time fallback is `build/resourcepacks/bedrock-audio-cache-fallback.zip`: extract into the live `plugins/` folder while stopped, then start the updated plugin. It contains only the verified audio cache and manifest under CoreSuite, preserving Geyser's fish pack. This supplies the current audio even if the first GitHub connection fails; it is not a workaround for permanently blocked automatic updates.
