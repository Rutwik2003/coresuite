# PlaceholderAPI reference

Install PlaceholderAPI and enable the corresponding CoreSuite module. The built-in expansion is named `coresuite`; it refreshes snapshots once per second. Player-specific values use online player snapshots. Disabled modules or unavailable profiles can return no value; do not interpret this as confirmed zero progress.

| Placeholder | Meaning |
|---|---|
| `%coresuite_magic_mana%` | Current mana, integer display |
| `%coresuite_magic_max_mana%` | Effective maximum mana |
| `%coresuite_magic_attunement_level%` | Wand attunement level |
| `%coresuite_magic_selected_spell%` | Selected spell identifier or none |
| `%coresuite_magic_research%` | Research balance |
| `%coresuite_boss_active%` | Whether an active boss exists |
| `%coresuite_boss_name%` | Boss type identifier or none |
| `%coresuite_boss_phase%` | Current phase number or zero |
| `%coresuite_boss_next_spawn%` | Remaining scheduler seconds |
| `%coresuite_fishing_level%` | Fishing level |
| `%coresuite_fishing_xp%` | Fishing XP |
| `%coresuite_fishing_catches%` | Total catches |
| `%coresuite_fishing_streak%` | Current unexpired streak |
| `%coresuite_harvest_season_crops%` | Comma-separated seasonal crop names |
| `%coresuite_harvest_quota_remaining%` | Player's remaining selling quota |
| `%coresuite_well_pot%` | Current pot, two decimal places |
| `%coresuite_well_next_draw%` | Seconds until the next draw |
| `%coresuite_playtime_formatted%` | Formatted tracked playtime |

Use these in a compatible scoreboard, hologram or menu plugin. These are display values, not transaction APIs. Do not use a once-per-second display snapshot as authority for paying money or awarding items.
