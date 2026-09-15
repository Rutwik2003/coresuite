# Server integration review — 2026-09-16

The supplied server runs Paper 26.2 build 123, Vault/Essentials, AuthMe, WorldGuard, GriefPrevention, Multiverse-Inventories, ExcellentCrates, Citizens, Geyser/floodgate, ViaVersion/ViaBackwards and the other plugins listed by the owner. Provider version numbers, configuration files and the CoreSuite storage setting were not supplied.

## Implemented safeguards

- Boss rewards and pending bank-note deliveries wait for AuthMe authentication on join, followed by a short settling delay. Claims and bank-note settlement recheck authentication. An installed but disabled or incompatible AuthMe API cannot silently authorize delivery. Join polling stops after three minutes; late users can claim manually.
- WorldManager's automatic lobby teleport waits for authentication, and it does not save an unauthenticated player's temporary login location as their survival location. This retains the module's configured lobby routing; check it against your AuthMe/Essentials spawn settings.
- Boss spawn points and fire/cobweb placement query WorldGuard BUILD/MOB_SPAWNING/MOB_DAMAGE as a non-player actor, and reject GriefPrevention claims. A present provider whose API cannot be queried blocks the action and logs a warning. These checks do not certify an entire arena perimeter or every other plugin's custom protection rules.
- The supplied boss crate commands use the documented `/crates key give <player> <key> [amount]` form. The former stock `crate key give %player% ...` spelling migrates once. Crate IDs must exist in your ExcellentCrates setup; Bukkit command success is not a transactional proof of delivery. Uncertain reward receipts require review.
- Water/Night Vision weakness users receive participation credit, alongside boss/minion damage and tanking participants.

## Verification and remaining staging work

43 JUnit checks and 238 existing boss checks pass. Tests cover absent/disabled/authenticated/unauthenticated/failing authentication providers and fail-closed protection lookup. The same JAR passes direct API linkage checks on 14 Paper versions. Isolated real-server probes verify boss lifecycle and item metadata; they do not run the supplied 49-plugin stack or authenticated clients.

Test `/boss claim`, bank-note issuance/redemption, login/session restoration and world changes with your actual Multiverse inventory groups. Confirm the configured crate IDs and economy provider. Try a summon inside/outside a GP claim and WG region, and play solo/duo/group encounters. Citizens, Vulcan, Geyser/floodgate and translated older clients need matching provider builds and gameplay checks. Full MySQL portability remains unresolved; the validated storage mode is SQLite. No production server or database was changed.

## References

- [AuthMe integration API](https://github.com/AuthMe/AuthMeReloaded/wiki/Hooking-into-AuthMe)
- [WorldGuard non-player flag queries](https://worldguard.enginehub.org/en/latest/developer/regions/flag-calculation/)
- [ExcellentCrates commands](https://nightexpressdev.com/excellentcrates/commands/)

Latest isolated run used Paper 26.2 build 124 and passed startup, boss probes, reload and shutdown. Final release SHA-256: `50b30fd31c5e69895ef02c8c34ac5dd5ed184426405ee36d173c594db1e8be3a`. All 14 static API reports match that release.
