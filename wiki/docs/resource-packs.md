# Resource packs and crossplay

Module: `resource-pack`. Public defaults disable delivery and leave the repository empty.

## Java delivery

The plugin offers a URL and content hash. The client downloads from the host and can reuse a matching local cache. An offer on every join does not prove the entire pack downloads again. The plugin cannot force the client to retain files or resume a partial download.

Status progression commonly includes OFFERED, ACCEPTED, DOWNLOADED and SUCCESSFULLY_LOADED. ACCEPTED alone does not confirm the pack loaded. A quick transition to DOWNLOADED is consistent with cache use but does not expose actual client byte counts.

The server separately verifies hosted ZIPs and caches verified files. Server ZIP fetch counters are not client download counters. Changing pack bytes changes the hash and legitimately requires clients to obtain the changed pack.

## Commands

- `/pack status`: current delivery state.
- `/pack retry`: request another attempt where appropriate.
- Staff diagnostics include `/pack details <player>`; check live tab completion for other administration actions.

## Configuration

Use your own compatible repository/manifest and review required-pack policy, timeouts, retry limits, join delay and mirror settings. The existing publisher expects particular archive names; it is not a generic uploader for arbitrary ZIPs. Test hosting from multiple networks before enforcing a mandatory pack.

If required delivery fails, users may be disconnected. Give players useful troubleshooting steps rather than assuming they pressed ESC: downloads can fail because of network access, host blocking, invalid URLs, hashes or client loading failures.

## Bedrock

Java packs do not work directly on Bedrock. Geyser needs compatible Bedrock assets/mappings. The audio bridge does not automatically convert all Java models and textures. Test Bedrock on an actual client and check Geyser's own pack enforcement settings.

## Troubleshooting

Collect `/pack details`, server status lines and the player's `logs/latest.log`. Remove personal information before sharing. Compare hash/version across attempts. Do not repeatedly clear every player's cache: it forces additional downloads and does not fix an unavailable host.
