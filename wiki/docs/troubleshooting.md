# Troubleshooting

| Symptom | First checks |
|---|---|
| Command does nothing or module unavailable | `/coresuite modules`, master toggle, startup exception, permission and command collision |
| Missing economy | Vault and actual provider installed; check hook log |
| Crate reward pending | Exact key ID, ExcellentCrates version and current command syntax |
| Boss arena unavailable | World exists, safe coordinates, chunks/terrain preparation and protection reason |
| Boss does not spawn | Scheduler state, encounter already active, portal lifecycle and console rejection |
| Ordinary helmet grants a boss effect | Verify tagged item identity and installed build; other plugins can also grant regeneration |
| Missing textures | Compatible pack/mapping, successful load, custom model identifiers |
| Rejoin says pack downloaded | Check timings and diagnostic counters; status text alone does not prove a fresh network transfer |
| Fish trade fails | Required species/quantity, bag contents and inventory space |
| Reward text differs from actual payout | Synchronize display text with money/commands; inspect third-party command success |
| Magic quest or mana looks wrong | Existing profile progression, effective config and all related modules enabled |
| Website unavailable | API bind address, port, secret, firewall and enabled module |

## Useful report

Include CoreSuite version, Paper version, Java version, related plugin versions, enabled modules, exact reproduction steps, expected result, actual result and relevant error stack trace. Explain whether it happens on Java, Bedrock or both.

Do not share API keys, database passwords, Discord tokens, webhook URLs, player IP addresses or entire private server backups. Prefer a small sanitized configuration excerpt.

## Safe recovery

Back up before changing data. Avoid clearing databases, deleting player progress or replaying reward commands as a first response. Reproduce on staging and change one factor at a time.
