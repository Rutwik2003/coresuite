# Configuration and module controls

## Two layers

`plugins/CoreSuite/config.yml` contains master `modules` switches and storage settings. Individual files under `plugins/CoreSuite/modules/` contain each module's behavior. A module's internal `enabled` setting is not a substitute for its master toggle.

```yaml
modules:
  chat-mentions: true
  world-bosses: false
  resource-pack: false
```

This is a fragment: edit the existing keys rather than replacing the whole master file. Use spaces, not tabs. Preserve indentation and quote strings with YAML punctuation. Material names, world names, key IDs and command arguments must match your installation.

## Administrator commands

Requires `coresuite.admin`:

| Command | Purpose |
|---|---|
| `/coresuite help` | Show help |
| `/coresuite modules` | Show loaded module state and config keys |
| `/coresuite commands` | List registered command descriptions |
| `/coresuite version` | Show plugin/server/Java information |
| `/coresuite enable <module>` | Enable a module and save its master toggle on success |
| `/coresuite disable <module>` | Disable a module and save its master toggle |
| `/coresuite reload <module>` | Reload an enabled module |
| `/coresuite reload` | Apply master toggles and reload enabled modules |

Use a clean restart for storage changes, plugin dependencies and production upgrades. Reloading a module can restart tasks or encounters; avoid doing it during active fights, rewards or economy transactions. Do not use Bukkit `/reload` as an upgrade process.

## Automatic merging

The updater adds missing defaults and performs supported migrations. It does not understand your intended economy or install other plugins. Review logs and backup files after an upgrade, especially when changing crate providers or reward commands.

The [module reference](modules/index.md) shows setting names and types, not server configuration values. Inspect your installed commented configuration for defaults and units. Some ordinary source defaults are server-specific and need replacement.
