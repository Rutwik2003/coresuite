# Project status and licensing

The public build is a preparation preview. Some settings and messages still need a portability review. Do not describe it as a finished, universally compatible release.

Original plugin code is under the repository's MIT licence, subject to source provenance review and third-party dependency notices. That licence does not grant rights to separately obtained textures, skins or music.

[All The Fish resource pack](https://modrinth.com/resourcepack/allthefish-resource-pack) by CYKLOPPS and Mickey Joe is listed as All Rights Reserved. Link to the original download and obtain permission before redistributing its assets.

Codex and Gemini contributed substantially to implementation, tests, documentation and project text. The owner supplied requirements, configuration decisions and gameplay testing. The documentation is AI-assisted and should be corrected when implementation or testing establishes different behavior.

Modrinth has its own [AI disclosure and eligibility policy](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai). A working GitHub documentation site does not establish Modrinth approval.

## Gambling audit behavior

The gambling module includes an administrator audit odds override in the underlying implementation. Public defaults have no audit accounts. If configured, this affects actual outcomes and economy payouts; it is not a harmless visual simulation. Review this feature transparently before enabling casino gameplay. The wiki does not claim all outcomes are unmodified or that any leaderboard contains invented results.
