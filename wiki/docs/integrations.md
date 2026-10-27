# Dependencies and integrations

All entries below are optional at plugin level. A particular enabled feature may still need them.

| Plugin/service | Used for | Setup check |
|---|---|---|
| Vault + economy provider | Money, payments and rewards | Verify an economy provider is hooked and balances change correctly |
| ExcellentCrates | Crate-key rewards | Create the referenced key IDs; test the command manually |
| UltraCosmetics | Cosmetic-key reward commands | Remove these commands if absent |
| PlaceholderAPI | CoreSuite placeholders | Expansion runs with supported enabled modules |
| Citizens / SkinsRestorer | NPC and appearance features | Check NPC setup and skin permissions |
| WorldGuard / GriefPrevention | Protection checks | Test as a non-member, non-OP player |
| Geyser / floodgate | Bedrock connections and pack integration | Java ZIPs do not automatically become Bedrock packs |
| Maintenance | Maintenance state integration | Test notifications while toggling maintenance |
| Vulcan | Configured punishment integration | Audit punishments before enabling |
| ajLeaderboards | Configured website leaderboard IDs | Create boards and confirm value units |
| AuthMe | Authentication-aware workflows | Test before and after login |
| Multiverse-Inventories | World inventory integration | Test teleport and inventory isolation |
| LuckPerms / Essentials | Optional permissions and reward commands | Groups and command syntax must exist |
| Discord | Configured bot/webhook notifications | Use your own credentials/channels |
| Website API | Read configured server data | Use a strong secret and restricted network access |

## Reward placeholders

The shared reward command adapter supports `%player%`, `%player_name%` and `{player}`. Do not assume every message field supports every placeholder: use the examples for that field.

```yaml
commands:
  - "cratekey give %player% rare 1"
  - "uc give key 1 %player%"
```

Use commands without a leading slash in configuration. The adapter supports legacy crate syntax migration and older-provider translation; the documented current format is `cratekey give <player> <key> <amount>`.

## Remote services

Enabling integrations can send player identifiers, gameplay information and server status to configured destinations. Pack clients contact the configured host. Discord avatar URLs can contact an avatar provider. Do not publish tokens, API keys, webhook URLs, database credentials or private logs with bug reports.

The website API is not a ready-made public website. Keep the localhost bind unless you intentionally provide a secured network path. Match the API key with the consuming application.
